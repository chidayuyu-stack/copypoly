package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron.Point;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** A marquee always computes a replacement selection, including an empty result. */
final class VertexMarquee {
    private boolean pending,active;
    private double startX,startY,endX,endY;
    void begin(double x,double y){pending=true;active=false;startX=endX=x;startY=endY=y;}
    void drag(double x,double y,double threshold){
        if(!pending)return;
        endX=x;endY=y;
        active|=Math.hypot(endX-startX,endY-startY)>=threshold;
    }
    boolean pending(){return pending;}
    boolean active(){return active;}
    void cancel(){pending=active=false;}
    double minX(){return Math.min(startX,endX);}
    double maxX(){return Math.max(startX,endX);}
    double minY(){return Math.min(startY,endY);}
    double maxY(){return Math.max(startY,endY);}
    List<Integer> find(List<Point> points,Function<Vec3,Vec3> project,Predicate<Vec3> inCanvas){
        List<Integer> result=new ArrayList<>();
        if(!active)return result;
        for(int i=0;i<points.size();i++){
            Vec3 p=project.apply(PolyGizmo.position(points.get(i)));
            if(p!=null && inCanvas.test(p) && p.x>=minX() && p.x<=maxX() && p.y>=minY() && p.y<=maxY())result.add(i);
        }
        return result;
    }
}
