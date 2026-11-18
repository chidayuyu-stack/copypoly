package com.caca.copypoly.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Subpixel GPU geometry with a one-framebuffer-pixel alpha fringe, independent of GUI scale. */
final class SmoothOverlay {
    private SmoothOverlay() {}
    static double pixel() { return 1/Minecraft.getInstance().getWindow().getGuiScale(); }

    static void line(GuiGraphics g,Vec3 a,Vec3 b,int color,double width) {
        if(a==null || b==null)return;
        double dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);
        if(!Double.isFinite(length) || length<1e-6)return;
        // Clip in floating point; geometry count stays constant even for off-screen endpoints.
        double lo=0,hi=1;
        double[] p={-dx,dx,-dy,dy},q={a.x+8,g.guiWidth()+8-a.x,a.y+8,g.guiHeight()+8-a.y};
        for(int i=0;i<4;i++) {
            if(Math.abs(p[i])<1e-9){if(q[i]<0)return;}
            else if(p[i]<0)lo=Math.max(lo,q[i]/p[i]);else hi=Math.min(hi,q[i]/p[i]);
        }
        if(lo>hi)return;
        double ax=a.x+lo*dx,ay=a.y+lo*dy,bx=a.x+hi*dx,by=a.y+hi*dy;
        double nx=-dy/length,ny=dx/length,feather=pixel();
        double inner=Math.max(0,width/2-feather/2),outer=width/2+feather/2;
        int clear=color&0x00ffffff;
        quad(g,ax+nx*inner,ay+ny*inner,bx+nx*inner,by+ny*inner,bx-nx*inner,by-ny*inner,ax-nx*inner,ay-ny*inner,color,color,color,color);
        quad(g,ax+nx*outer,ay+ny*outer,bx+nx*outer,by+ny*outer,bx+nx*inner,by+ny*inner,ax+nx*inner,ay+ny*inner,clear,clear,color,color);
        quad(g,ax-nx*inner,ay-ny*inner,bx-nx*inner,by-ny*inner,bx-nx*outer,by-ny*outer,ax-nx*outer,ay-ny*outer,color,color,clear,clear);
        double ex=dx/length*feather,ey=dy/length*feather;
        quad(g,ax+nx*inner,ay+ny*inner,ax-nx*inner,ay-ny*inner,ax-nx*outer-ex,ay-ny*outer-ey,ax+nx*outer-ex,ay+ny*outer-ey,color,color,clear,clear);
        quad(g,bx-nx*inner,by-ny*inner,bx+nx*inner,by+ny*inner,bx+nx*outer+ex,by+ny*outer+ey,bx-nx*outer+ex,by-ny*outer+ey,color,color,clear,clear);
    }

    static void circle(GuiGraphics g,double x,double y,double radius,int color) {
        if(x < -16 || y < -16 || x > g.guiWidth()+16 || y > g.guiHeight()+16)return;
        int clear=color&0x00ffffff,segments=32;
        double inner=Math.max(0,radius-pixel()/2),outer=radius+pixel()/2;
        for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments;
            double ax=Math.cos(a),ay=Math.sin(a),bx=Math.cos(b),by=Math.sin(b);
            quad(g,x,y,x+ax*inner,y+ay*inner,x+bx*inner,y+by*inner,x,y,color,color,color,color);
            quad(g,x+ax*inner,y+ay*inner,x+ax*outer,y+ay*outer,x+bx*outer,y+by*outer,x+bx*inner,y+by*inner,color,clear,clear,color);
        }
    }

    static void arrow(GuiGraphics g,Vec3 a,Vec3 tip,int color) {
        double dx=tip.x-a.x,dy=tip.y-a.y,length=Math.hypot(dx,dy);
        if(length<1e-6)return;
        double unit=pixel(),head=Math.min(20*unit,length*.3),ux=dx/length,uy=dy/length;
        Vec3 base=new Vec3(tip.x-ux*head,tip.y-uy*head,0);
        line(g,a,base,0xff17212a,8*unit);
        line(g,a,base,color,6*unit);
        triangle(g,tip.x,tip.y,base.x-uy*9*unit,base.y+ux*9*unit,base.x+uy*9*unit,base.y-ux*9*unit,color);
    }

    static void triangle(GuiGraphics g,double ax,double ay,double bx,double by,double cx,double cy,int color) {
        double[] x={ax,bx,cx},y={ay,by,cy},ox=new double[3],oy=new double[3];
        double sign=Math.signum((bx-ax)*(cy-ay)-(by-ay)*(cx-ax));
        if(sign==0)return;
        for(int i=0;i<3;i++) {
            int previous=(i+2)%3,next=(i+1)%3;
            double dx1=x[i]-x[previous],dy1=y[i]-y[previous],l1=Math.hypot(dx1,dy1);
            double dx2=x[next]-x[i],dy2=y[next]-y[i],l2=Math.hypot(dx2,dy2);
            double nx1=sign*dy1/l1,ny1=-sign*dx1/l1,nx2=sign*dy2/l2,ny2=-sign*dx2/l2;
            double factor=pixel()/Math.max(.2,1+nx1*nx2+ny1*ny2);
            ox[i]=x[i]+(nx1+nx2)*factor;oy[i]=y[i]+(ny1+ny2)*factor;
        }
        quad(g,ax,ay,bx,by,cx,cy,cx,cy,color,color,color,color);
        for(int i=0;i<3;i++) {
            int j=(i+1)%3,clear=color&0x00ffffff;
            quad(g,x[i],y[i],ox[i],oy[i],ox[j],oy[j],x[j],y[j],color,clear,clear,color);
        }
    }

    private static void quad(GuiGraphics g,double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy,int ca,int cb,int cc,int cd) {
        VertexConsumer consumer=g.bufferSource().getBuffer(RenderType.gui());Matrix4f pose=g.pose().last().pose();
        // The GUI projection flips Y. Keep all quads front-facing, including feather strips.
        if((bx-ax)*(cy-ay)-(by-ay)*(cx-ax)>0) {
            vertex(consumer,pose,dx,dy,cd);vertex(consumer,pose,cx,cy,cc);vertex(consumer,pose,bx,by,cb);vertex(consumer,pose,ax,ay,ca);
        } else {
            vertex(consumer,pose,ax,ay,ca);vertex(consumer,pose,bx,by,cb);vertex(consumer,pose,cx,cy,cc);vertex(consumer,pose,dx,dy,cd);
        }
    }
    private static void vertex(VertexConsumer consumer,Matrix4f pose,double x,double y,int color) {
        consumer.addVertex(pose,(float)x,(float)y,0).setColor(color);
    }
}
