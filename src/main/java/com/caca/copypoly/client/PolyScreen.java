package com.caca.copypoly.client;

import com.caca.copypoly.*;
import com.caca.copypoly.geometry.Polyhedron;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class PolyScreen extends AbstractContainerScreen<PolyMenu> {
    // Row identity survives hull sorting/deduplication and deletion of other rows.
    private static final class Row {
        final String[] values;
        private Polyhedron.Point cached;
        private boolean parsed;
        Row(Polyhedron.Point p) { values=p.coordinates();cached=p;parsed=true; }
        Polyhedron.Point point() {
            if(!parsed) {
                try {cached=Polyhedron.Point.parse(values[0],values[1],values[2]);}
                catch(IllegalArgumentException e) {cached=null;}
                parsed=true;
            }
            return cached;
        }
        void edit(int axis,String value) {values[axis]=value;parsed=false;}
    }
    private final List<Row> rows=new ArrayList<>();
    private final Map<Row,EditBox[]> fields=new IdentityHashMap<>();
    private Row selected;
    private int page, visibleRows, tableWidth, previewX, previewWidth;
    private Button apply, addInPreview, deleteInPreview;
    private Polyhedron preview;
    private List<BakedQuad> previewQuads=List.of();
    private BlockState material;
    private boolean dirty, valid=true, waiting, rotating, rotated, adding;
    private int dragAxis=-1;
    private double pressX,pressY;
    private PreviewGeometry.Drag drag;
    private long changedAt,lastValidation;
    private Component error=Component.empty();
    private double yaw=.65,pitch=.45,zoom=1;
    public PolyScreen(PolyMenu menu,Inventory inv,Component title) {
        super(menu,inv,title); preview=menu.initial;
        setRows(preview.points());
    }
    private void setRows(List<Polyhedron.Point> points) {
        rows.clear();for(var p:points) rows.add(new Row(p));page=0;selected=null;adding=false;dragAxis=-1;rotating=false;
    }
    @Override protected void init() {
        imageWidth=Math.min(480,width-12);imageHeight=Math.min(324,height-12);
        super.init();
        tableWidth=Math.max(176,imageWidth/2-12);previewX=leftPos+tableWidth+16;previewWidth=imageWidth-tableWidth-28;
        visibleRows=Math.max(3,(imageHeight-126)/22);
        material=minecraft.level.getBlockEntity(menu.pos) instanceof PolyBlockEntity be?be.getMaterial():AllBlocks.COPYCAT_BASE.getDefaultState();
        rebuild();updatePreview();
    }
    private Component tr(String key,Object...args) {return Component.translatable("screen.copypoly."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());
    }
    private void rebuild() {
        clearWidgets();fields.clear();
        int maxPage=Math.max(0,(rows.size()-1)/visibleRows);page=Math.min(page,maxPage);
        int fieldWidth=(tableWidth-44)/3;
        for(int line=0;line<visibleRows;line++) {
            int index=page*visibleRows+line;if(index>=rows.size())break;
            Row row=rows.get(index);EditBox[] boxes=new EditBox[3];fields.put(row,boxes);
            for(int axis=0;axis<3;axis++) {
                final int a=axis;
                EditBox box=new EditBox(font,leftPos+26+axis*(fieldWidth+2),topPos+42+line*22,fieldWidth,18,
                    Component.literal((index+1)+" "+"XYZ".charAt(axis)));
                box.setMaxLength(32);box.setValue(row.values[axis]);boxes[axis]=box;
                box.setTooltip(Tooltip.create(tr("coordinate_tooltip")));
                box.setResponder(value->{row.edit(a,value);markDirty();});addRenderableWidget(box);
            }
            addRenderableWidget(Button.builder(Component.literal("×"),b->delete(row))
                .bounds(leftPos+tableWidth-10,topPos+41+line*22,18,20).build());
        }
        int navY=topPos+43+visibleRows*22;
        Button add=button("add",leftPos+12,navY,tableWidth-66,()->{
            // Find an unoccupied position near the center for keyboard/table editing.
            Polyhedron.Point point=null;double best=Double.POSITIVE_INFINITY;
            for(int x=0;x<=16;x++)for(int yy=0;yy<=16;yy++)for(int z=0;z<=16;z++) {
                var p=new Polyhedron.Point(x,yy,z);double distance=(x-8)*(x-8)+(yy-8)*(yy-8)+(z-8)*(z-8);
                if(distance<best && !occupied(p,null)){point=p;best=distance;}
            }
            if(point!=null)addPoint(point);
        });add.active=rows.size()<Polyhedron.MAX_POINTS;
        Button prev=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuild();}).bounds(leftPos+tableWidth-48,navY,24,20).build());prev.active=page>0;
        Button next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuild();}).bounds(leftPos+tableWidth-20,navY,24,20).build());next.active=page<maxPage;
        int y=topPos+imageHeight-57;
        int w=(tableWidth-6)/3;
        button("copy",leftPos+12,y,w,()->minecraft.keyboardHandler.setClipboard(String.join("\n",
            rows.stream().map(r->r.point()==null?String.join(", ",r.values):r.point().toString()).toList())));
        button("paste",leftPos+14+w,y,w,()->{
            try {
                String clipboard=minecraft.keyboardHandler.getClipboard();
                if(clipboard.length()>4096)throw new IllegalArgumentException("count");
                List<Polyhedron.Point> points=Polyhedron.parse(clipboard);
                if(new HashSet<>(points).size()!=points.size())throw new IllegalArgumentException("duplicate");
                Polyhedron.of(points);setRows(points);markDirty();rebuild();
            } catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
        });
        button("reset",leftPos+16+2*w,y,w,()->{setRows(Polyhedron.CUBE_POINTS);markDirty();rebuild();});
        int toolWidth=(previewWidth-14)/2;
        addInPreview=button(adding?"add_cancel":"add",previewX+4,topPos+imageHeight-83,toolWidth,()->{
            if(dirty)validate();adding=!adding;setFocused(null);rebuild();
        });
        deleteInPreview=button("delete_selected",previewX+8+toolWidth,topPos+imageHeight-83,toolWidth,()->delete(selected));
        addInPreview.setTooltip(Tooltip.create(tr("add_tooltip")));
        deleteInPreview.setTooltip(Tooltip.create(tr("delete_tooltip")));
        apply=button("apply",leftPos+imageWidth-146,topPos+imageHeight-28,64,()->{
            validate();if(!valid || waiting)return;
            waiting=true;apply.active=false;error=tr("saving");
            PacketDistributor.sendToServer(new PolyPackets.Apply(menu.containerId,menu.revision,preview.packed()));
        });
        button("cancel",leftPos+imageWidth-76,topPos+imageHeight-28,64,this::onClose);
        apply.active=valid && !dirty && !waiting;
    }
    private String text() {return String.join("\n",rows.stream().map(r->String.join(", ",r.values)).toList());}
    private void markDirty() {dirty=true;changedAt=System.currentTimeMillis();if(apply!=null)apply.active=false;}
    private void validate() {
        dirty=false;lastValidation=System.currentTimeMillis();
        try {
            List<Polyhedron.Point> points=Polyhedron.parse(text());
            if(new HashSet<>(points).size()!=points.size())throw new IllegalArgumentException("duplicate");
            preview=Polyhedron.of(points);valid=true;error=Component.empty();updatePreview();
        }
        catch(IllegalArgumentException ex){valid=false;error=tr("error."+ex.getMessage());}
        if(apply!=null)apply.active=valid && !waiting;
    }
    private void updatePreview() {
        if(minecraft.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState()) instanceof PolyModel model)
            previewQuads=model.preview(preview,material);
    }
    public void result(String message) {
        waiting=false;
        if(message.isEmpty()){onClose();return;}
        error=tr("error."+message);apply.active=valid && !dirty;
    }
    @Override protected void containerTick() {
        if(dirty && System.currentTimeMillis()-(dragAxis>=0?lastValidation:changedAt)>=(dragAxis>=0?50:160))validate();
        if(!menu.stillValid(minecraft.player))onClose();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff18252b);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+25,0xff273c43);
        g.drawString(font,title,leftPos+12,topPos+9,0xffe3f2ec,false);
        int fieldWidth=(tableWidth-44)/3;
        for(int a=0;a<3;a++)g.drawString(font,""+"XYZ".charAt(a),leftPos+26+a*(fieldWidth+2),topPos+30,0xff94b5ad,false);
        for(int i=0;i<visibleRows && page*visibleRows+i<rows.size();i++) {
            boolean chosen=rows.get(page*visibleRows+i)==selected;
            if(chosen)g.fill(leftPos+7,topPos+40+i*22,leftPos+tableWidth+9,topPos+62+i*22,0xff3e665c);
            g.drawString(font,""+(page*visibleRows+i+1),leftPos+9,topPos+47+i*22,chosen?0xffffffff:0xff91a6ac,false);
        }
        g.fill(previewX,topPos+30,previewX+previewWidth,topPos+imageHeight-64,0xff101c22);
        drawPreview(g,mx,my);
        g.drawString(font,tr("count",rows.size(),Polyhedron.MAX_POINTS),previewX+5,topPos+35,0xffa6c9bd,false);
        addInPreview.active=!waiting && (adding || rows.size()<Polyhedron.MAX_POINTS);
        deleteInPreview.active=!waiting && selected!=null;
        g.enableScissor(previewX,topPos+imageHeight-61,previewX+previewWidth,topPos+imageHeight-30);
        g.drawWordWrap(font,tr(adding?"add_hint":selected!=null?"move_hint":"drag"),previewX+5,topPos+imageHeight-59,previewWidth-10,0xffa6b4b9);
        g.disableScissor();
        if(!error.getString().isEmpty()) {
            g.enableScissor(leftPos+10,topPos+imageHeight-36,leftPos+imageWidth-155,topPos+imageHeight-8);
            g.drawWordWrap(font,error,leftPos+12,topPos+imageHeight-31,imageWidth-170,waiting?0xffaacfc1:0xffffa599);
            g.disableScissor();
        } else g.drawString(font,tr("range"),leftPos+12,topPos+imageHeight-24,0xff9bb0b6,false);
    }
    private int viewTop() {return topPos+47;}
    private int viewBottom() {return topPos+imageHeight-88;}
    private PreviewGeometry.View view() {
        double scale=Math.max(20,Math.min(previewWidth-60,viewBottom()-viewTop()-45))*.65*zoom/16;
        return new PreviewGeometry.View(yaw,pitch,scale,previewX+previewWidth*.5,(viewTop()+viewBottom())*.5);
    }
    private Vec3 rotate(Vec3 p) {return view().rotate(p);}
    private Vec3 project(Vec3 p) {
        return view().project(p.scale(16));
    }
    private record PreviewQuad(BakedQuad source,Vec3[] points,double depth) {}
    private void drawPreview(GuiGraphics g,int mx,int my) {
        g.enableScissor(previewX,viewTop(),previewX+previewWidth,viewBottom());
        List<PreviewQuad> sorted=new ArrayList<>();
        for(BakedQuad q:valid?previewQuads:List.<BakedQuad>of()) {
            int[] data=q.getVertices();int stride=data.length/4;Vec3[] p=new Vec3[4];double depth=0;
            for(int i=0;i<4;i++){p[i]=project(new Vec3(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])));depth+=p[i].z;}
            // Screen Y is inverted; clockwise screen triangles face the viewer.
            if((p[1].x-p[0].x)*(p[2].y-p[0].y)-(p[1].y-p[0].y)*(p[2].x-p[0].x)>=0)continue;
            sorted.add(new PreviewQuad(q,p,depth/4));
        }
        sorted.sort(Comparator.comparingDouble(PreviewQuad::depth));
        g.flush();
        for(PreviewQuad quad:sorted) {
            int tint=quad.source.isTinted()?minecraft.getBlockColors().getColor(material,minecraft.level,menu.pos,quad.source.getTintIndex()):0xffffff;
            float shade=.68f+.32f*Math.max(0,(float)rotate(Vec3.atLowerCornerOf(quad.source.getDirection().getNormal())).z);
            int color=0xff000000 | ((int)(((tint>>16)&255)*shade)<<16) | ((int)(((tint>>8)&255)*shade)<<8) | (int)((tint&255)*shade);
            VertexConsumer v=g.bufferSource().getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
            int[] data=quad.source.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++)v.addVertex(g.pose().last().pose(),(float)quad.points[i].x,(float)quad.points[i].y,0)
                .setColor(color).setUv(Float.intBitsToFloat(data[i*stride+4]),Float.intBitsToFloat(data[i*stride+5])).setLight(LightTexture.FULL_BRIGHT);
        }
        g.flush();
        for(var face:valid?preview.faces():List.<Polyhedron.Face>of()) {
            Vec3 n=new Vec3(face.plane().x(),face.plane().y(),face.plane().z());if(rotate(n).z<=0)continue;
            for(int i=0;i<face.vertices().size();i++) {
                var a=face.vertices().get(i);var b=face.vertices().get((i+1)%face.vertices().size());
                line(g,project(new Vec3(a.x()/16.,a.y()/16.,a.z()/16.)),project(new Vec3(b.x()/16.,b.y()/16.,b.z()/16.)),0xff93d3bc);
            }
        }
        Row hovered=insideView(mx,my)?pickPoint(mx,my):null;
        for(int i=0;i<rows.size();i++) {
            Row row=rows.get(i);var point=row.point();if(point==null)continue;
            Vec3 p=screenPoint(point);boolean chosen=row==selected;
            int size=chosen?4:row==hovered?3:2,color=chosen?0xff72ffe1:row==hovered?0xffffffff:0xffffdc8b;
            g.fill((int)p.x-size-1,(int)p.y-size-1,(int)p.x+size+2,(int)p.y+size+2,0xff101c22);
            g.fill((int)p.x-size,(int)p.y-size,(int)p.x+size+1,(int)p.y+size+1,color);
            g.drawString(font,""+(i+1),(int)p.x+6,(int)p.y-9,color,false);
        }
        if(!adding)drawHandles(g,mx,my);
        if(adding && insideView(mx,my)) {
            var candidate=placement(mx,my);
            if(candidate!=null) {
                Vec3 p=screenPoint(candidate);int color=occupied(candidate,null)?0xffff7979:0xff72ffe1;
                line(g,p.add(-5,0,0),p.add(5,0,0),color);line(g,p.add(0,-5,0),p.add(0,5,0),color);
                g.drawString(font,candidate.toString(),(int)p.x+7,(int)p.y+6,color,false);
            }
        }
        g.disableScissor();
    }
    private static void line(GuiGraphics g,Vec3 a,Vec3 b,int color) {
        int n=(int)Math.ceil(Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y)));
        for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;int x=(int)Math.round(a.x+(b.x-a.x)*t),y=(int)Math.round(a.y+(b.y-a.y)*t);g.fill(x,y,x+1,y+1,color);}
    }
    private boolean insideView(double x,double y) {
        return x>=previewX && x<previewX+previewWidth && y>=viewTop() && y<viewBottom();
    }
    private Vec3 screenPoint(Polyhedron.Point p) {return view().project(new Vec3(p.x(),p.y(),p.z()));}
    private boolean occupied(Polyhedron.Point p,Row except) {
        return rows.stream().anyMatch(r->r!=except && p.equals(r.point()));
    }
    private void select(Row row) {
        selected=row;setFocused(null);
        if(row!=null) {int next=rows.indexOf(row)/visibleRows;if(page!=next){page=next;rebuild();}}
    }
    private void addPoint(Polyhedron.Point p) {
        if(waiting || rows.size()>=Polyhedron.MAX_POINTS)return;
        if(occupied(p,null)){error=tr("error.duplicate");return;}
        Row row=new Row(p);rows.add(row);selected=row;page=(rows.size()-1)/visibleRows;adding=false;
        markDirty();validate();rebuild();setFocused(null);
    }
    private void delete(Row row) {
        if(waiting || row==null)return;
        rows.remove(row);if(selected==row)selected=null;
        dragAxis=-1;adding=false;markDirty();validate();rebuild();setFocused(null);
    }
    private void moveSelected(Polyhedron.Point point) {
        if(selected==null || point.equals(selected.point()))return;
        if(occupied(point,selected)){error=tr("error.duplicate");return;}
        String[] values=point.coordinates();
        EditBox[] boxes=fields.get(selected);
        for(int a=0;a<3;a++) {
            selected.edit(a,values[a]);
            if(boxes!=null && !boxes[a].getValue().equals(values[a]))boxes[a].setValue(values[a]);
        }
        markDirty();
    }
    private Row pickPoint(double x,double y) {
        Row best=null;double distance=7,depth=Double.NEGATIVE_INFINITY;
        for(Row row:rows) {
            var point=row.point();if(point==null)continue;Vec3 p=screenPoint(point);
            if(!insideView(p.x,p.y))continue;
            double d=Math.hypot(x-p.x,y-p.y);
            if(d<=7 && (best==null || d<distance-1 || Math.abs(d-distance)<=1 && p.z>depth)) {
                best=row;distance=d;depth=p.z;
            }
        }
        return best;
    }
    private record Handle(int axis,Vec3 start,Vec3 end,boolean usable) {}
    private List<Handle> handles() {
        if(selected==null || selected.point()==null)return List.of();
        Vec3 center=screenPoint(selected.point());List<Handle> result=new ArrayList<>();
        for(int axis=0;axis<3;axis++) {
            Vec3 vector=view().axis(axis);double length=vector.length();
            boolean usable=length/view().scale()>=.12;
            if(!usable)vector=new Vec3(axis==0?-1:1,axis==1?-1:1,0).normalize();
            else vector=vector.scale(1/length);
            result.add(new Handle(axis,center.add(vector.scale(10)),center.add(vector.scale(34)),usable));
        }
        return result;
    }
    private int pickAxis(double x,double y) {
        int best=-1;double distance=5;
        for(Handle handle:handles()) {
            double d=PreviewGeometry.segmentDistance(x,y,handle.start,handle.end);
            if(handle.usable && d<distance){distance=d;best=handle.axis;}
        }
        return best;
    }
    private void drawHandles(GuiGraphics g,int mx,int my) {
        int hovered=insideView(mx,my)?pickAxis(mx,my):-1;
        int[] colors={0xffff7777,0xff79e692,0xff7cb5ff};
        for(Handle h:handles()) {
            int color=!h.usable?0xff75818a:dragAxis==h.axis || hovered==h.axis?0xffffffff:colors[h.axis];
            Vec3 direction=h.end.subtract(h.start).normalize();
            line(g,h.start,h.end,color);
            Vec3 back=h.end.subtract(direction.scale(6)),side=new Vec3(-direction.y*3,direction.x*3,0);
            line(g,h.end,back.add(side),color);line(g,h.end,back.subtract(side),color);
            g.drawString(font,""+"XYZ".charAt(h.axis),(int)h.end.x+3,(int)h.end.y-4,color,false);
        }
    }
    private Polyhedron.Point placement(double x,double y) {
        // If the draft has no volume, the block bounds provide a surface for rebuilding it.
        Vec3 hit=view().surface(valid?preview:Polyhedron.CUBE,x,y);
        return hit==null?null:PreviewGeometry.snap(hit);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(waiting)return true;
        if(dragAxis>=0)return true;
        if(button==1 && insideView(x,y)) {
            if(adding){adding=false;rebuild();return true;}
            rotating=true;rotated=false;pressX=x;pressY=y;setFocused(null);return true;
        }
        if(button==0 && insideView(x,y)) {
            setFocused(null);pressX=x;pressY=y;
            if(dirty)validate();
            if(adding) {
                var p=placement(x,y);if(p!=null)addPoint(p);else error=tr("error.surface");
                return true;
            }
            Row hit=pickPoint(x,y);
            if(hit!=null){select(hit==selected?null:hit);return true;}
            int axis=pickAxis(x,y);
            if(axis>=0) {dragAxis=axis;drag=new PreviewGeometry.Drag(selected.point(),axis,view().axis(axis),x,y,hasShiftDown());return true;}
            rotating=true;rotated=false;return true;
        }
        if(button==0)for(var entry:fields.entrySet()) {
            if(Arrays.stream(entry.getValue()).anyMatch(box->box.isMouseOver(x,y))){selected=entry.getKey();adding=false;addInPreview.setMessage(tr("add"));break;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(waiting)return true;
        if(dragAxis>=0 && button==0) {
            moveSelected(drag.move(x,y,hasShiftDown(),selected.point()));return true;
        }
        if(rotating && (button==0 || button==1)) {
            if(!rotated && Math.hypot(x-pressX,y-pressY)<3)return true;
            rotated=true;yaw+=dx*.015;pitch=Math.max(-1.4,Math.min(1.4,pitch+dy*.015));return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button==0 && dragAxis>=0){moveSelected(drag.move(x,y,hasShiftDown(),selected.point()));dragAxis=-1;validate();return true;}
        if(rotating && (button==0 || button==1)) {
            if(button==0 && !rotated && insideView(x,y))select(null);
            rotating=false;return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(dragAxis>=0 || rotating || waiting)return true;
        if(insideView(x,y)){zoom=Math.max(.5,Math.min(1.5,zoom+vertical*.08));return true;}
        if(x<leftPos || x>=leftPos+tableWidth+10 || y<topPos+30 || y>=topPos+imageHeight-60)return super.mouseScrolled(x,y,horizontal,vertical);
        page=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/visibleRows),page-(int)Math.signum(vertical)));rebuild();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE){
            if(dragAxis>=0){dragAxis=-1;moveSelected(drag.original);validate();return true;}
            if(adding){adding=false;rebuild();return true;}
            if(selected!=null){select(null);return true;}
            onClose();return true;
        }
        if(waiting)return true;
        if(dragAxis>=0 && (key==GLFW.GLFW_KEY_LEFT_SHIFT || key==GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            drag.mode(true,selected.point());return true;
        }
        if(getFocused() instanceof EditBox box && box.keyPressed(key,scan,mods))return true;
        // Inventory hotkey must not close an editor while a text field is focused.
        if(getFocused() instanceof EditBox)return false;
        if(dragAxis>=0 || rotating)return true;
        if((key==GLFW.GLFW_KEY_DELETE || key==GLFW.GLFW_KEY_BACKSPACE) && selected!=null){delete(selected);return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key,int scan,int mods) {
        if(dragAxis>=0 && (key==GLFW.GLFW_KEY_LEFT_SHIFT || key==GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            drag.mode(hasShiftDown(),selected.point());return true;
        }
        return super.keyReleased(key,scan,mods);
    }
}
