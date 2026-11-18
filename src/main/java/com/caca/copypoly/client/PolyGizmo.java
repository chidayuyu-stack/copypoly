package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron.Point;
import com.caca.copypoly.geometry.VertexEditing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.IntPredicate;

/** Shared picking and axis handles for the preview and world editors. */
final class PolyGizmo {
    static final int[] COLORS = {0xffff6868, 0xff91e66c, 0xff65aaff};
    static final double LENGTH = .6;
    @FunctionalInterface interface AxisCoordinate { double at(double x, double y, Vec3 origin, int axis); }
    int selected = -1;
    private final Set<Integer> selection=new LinkedHashSet<>();
    private int dragAxis = -1;
    private Map<Integer,Point> startPoints=Map.of();
    private Vec3 startOrigin;
    private double startParameter;
    private AxisCoordinate coordinate;

    static Vec3 position(Point p) { return new Vec3(p.x()/16, p.y()/16, p.z()/16); }
    static Vec3 axis(int axis) { return axis == 0 ? new Vec3(1,0,0) : axis == 1 ? new Vec3(0,1,0) : new Vec3(0,0,1); }
    boolean dragging() { return dragAxis >= 0; }
    void release() { dragAxis = -1; coordinate = null; startPoints=Map.of(); }
    void clearSelection(){selection.clear();selected=-1;release();}
    boolean isSelected(int index){return selection.contains(index);}
    int selectedCount(){return selection.size();}
    List<Integer> selectedIndices(){return selection.stream().sorted().toList();}
    List<Integer> selectionOrder(){return List.copyOf(selection);}
    static int pickVertex(double x,double y,List<Point> points,Function<Vec3,Vec3> project,int exclude) {
        return pickVertex(x,y,points,project,exclude,8);
    }
    static int pickVertex(double x,double y,List<Point> points,Function<Vec3,Vec3> project,int exclude,double radius) {
        int closest=-1;double best=radius*radius,depth=Double.POSITIVE_INFINITY;
        for(int i=0;i<points.size();i++) {
            if(i==exclude)continue;Vec3 p=project.apply(position(points.get(i)));if(p==null)continue;
            double d=square(p.x-x)+square(p.y-y);
            if(d<=radius*radius && (d<best-1 || d<=best+1 && p.z<depth)){closest=i;best=d;depth=p.z;}
        }
        return closest;
    }
    void replaceSelection(Collection<Integer> indices) {
        clearSelection();
        for(int index:indices){selection.add(index);selected=index;}
    }
    void select(int index,boolean multiple) {
        release();
        if(multiple && selection.contains(index)) {
            selection.remove(index);
            if(selected==index)selected=selection.isEmpty()?-1:selection.iterator().next();
        } else {
            if(!multiple)selection.clear();selection.add(index);selected=index;
        }
    }
    void removePoint(int index) {
        release();Set<Integer> shifted=new LinkedHashSet<>();
        for(int value:selection)if(value!=index)shifted.add(value>index?value-1:value);
        selection.clear();selection.addAll(shifted);
        selected=selected==index?-1:selected>index?selected-1:selected;
        if(selected<0 && !selection.isEmpty())selected=selection.iterator().next();
    }
    private Vec3 origin(List<Point> points) {
        Vec3 center=Vec3.ZERO;int count=0;
        for(int index:selection)if(index>=0 && index<points.size()){center=center.add(position(points.get(index)));count++;}
        return count==0?null:center.scale(1.0/count);
    }

    boolean click(double x, double y, List<Point> points, Function<Vec3,Vec3> project, AxisCoordinate mapper) {
        return click(x,y,points,project,mapper,false);
    }
    boolean click(double x, double y, List<Point> points, Function<Vec3,Vec3> project, AxisCoordinate mapper,boolean multiple) {
        // Points take precedence where a handle crosses another vertex.
        int closest=pickVertex(x,y,points,project,-1);
        if (closest >= 0) { select(closest,multiple); return true; }
        return clickAxes(x,y,points,project,mapper);
    }
    boolean clickAxes(double x,double y,List<Point> points,Function<Vec3,Vec3> project,AxisCoordinate mapper) {
        if (selected < 0 || selected >= points.size()) return false;
        Vec3 origin = origin(points);
        if(origin==null)return false;
        Vec3 a = project.apply(origin);
        if (a == null) return false;
        double distance = 49;
        int hit = -1;
        for (int axis=0; axis<3; axis++) {
            Vec3 b = project.apply(origin.add(axis(axis).scale(LENGTH)));
            if (b == null || square(b.x-a.x)+square(b.y-a.y)<144) continue;
            double t = ((x-a.x)*(b.x-a.x)+(y-a.y)*(b.y-a.y))/(square(b.x-a.x)+square(b.y-a.y));
            if (t < .15 || t > 1.12) continue;
            double d = square(x-a.x-t*(b.x-a.x))+square(y-a.y-t*(b.y-a.y));
            if (d < distance) { distance=d; hit=axis; }
        }
        if (hit < 0) return false;
        double parameter = mapper.at(x,y,origin,hit);
        if (!Double.isFinite(parameter)) return false;
        dragAxis=hit;startOrigin=origin;coordinate=mapper;startParameter=parameter;
        Map<Integer,Point> snapshot=new LinkedHashMap<>();
        for(int index:selection)if(index>=0 && index<points.size())snapshot.put(index,points.get(index));
        startPoints=snapshot;
        return true;
    }

    Point drag(double x, double y, boolean fine) {
        return dragSelection(x,y,fine).get(selected);
    }
    Map<Integer,Point> dragSelection(double x,double y,boolean fine) {
        if(!dragging())return Map.of();
        double parameter=coordinate.at(x,y,startOrigin,dragAxis);
        if(!Double.isFinite(parameter))return Map.of();
        List<Point> moved=VertexEditing.translate(List.copyOf(startPoints.values()),dragAxis,parameter-startParameter,fine);
        Map<Integer,Point> result=new LinkedHashMap<>();int i=0;
        for(int index:startPoints.keySet())result.put(index,moved.get(i++));
        return result;
    }

    void draw(GuiGraphics g, Font font, List<Point> points, Function<Vec3,Vec3> project) {
        draw(g,font,points,project,i->0xffffdc8b,i->false);
    }
    void draw(GuiGraphics g,Font font,List<Point> points,Function<Vec3,Vec3> project,IntUnaryOperator color,IntPredicate pending) {
        draw(g,font,points,project,color,pending,true);
    }
    void draw(GuiGraphics g,Font font,List<Point> points,Function<Vec3,Vec3> project,IntUnaryOperator color,IntPredicate pending,boolean showAxes) {
        for (int i=0; i<points.size(); i++) {
            Vec3 p = project.apply(position(points.get(i)));
            if (p == null) continue;
            double unit=SmoothOverlay.pixel(),r=(isSelected(i)?8:6)*unit;
            SmoothOverlay.circle(g,p.x,p.y,r+2*unit,color.applyAsInt(i));
            SmoothOverlay.circle(g,p.x,p.y,r+unit,0xff17212a);
            SmoothOverlay.circle(g,p.x,p.y,r,isSelected(i)?(i==selected?0xffffffff:0xff86e7ff):pending.test(i)?0xff263240:color.applyAsInt(i));
            if (isSelected(i)) g.drawString(font,Integer.toString(i+1),(int)(p.x+7),(int)(p.y-5),0xffffffff,false);
        }
        if (!showAxes || selected < 0 || selected >= points.size()) return;
        Vec3 origin=origin(points);if(origin==null)return;
        Vec3 a=project.apply(origin);
        if (a == null) return;
        for (int axis=0; axis<3; axis++) {
            Vec3 b=project.apply(origin.add(axis(axis).scale(LENGTH)));
            if (b == null) continue;
            double dx=b.x-a.x, dy=b.y-a.y, len=Math.hypot(dx,dy);
            if (len < 12) continue; // End-on axes cannot be dragged reliably; rotate the view first.
            int axisColor=axis==dragAxis?0xffffff8b:COLORS[axis];
            SmoothOverlay.arrow(g,a,b,axisColor);
            g.drawString(font,""+"XYZ".charAt(axis),(int)b.x+5,(int)b.y-7,axisColor,false);
        }
    }

    static void line(GuiGraphics g, Vec3 a, Vec3 b, int color, int thickness) {
        SmoothOverlay.line(g,a,b,color,3*thickness*SmoothOverlay.pixel());
    }
    private static double square(double n) { return n*n; }
}
