package com.caca.copypoly.client;

import com.caca.copypoly.api.PolyShape;
import net.minecraft.world.phys.Vec3;
import java.util.function.Function;

/** Picks only the connections drawn by the editor, in the same projection as their overlay. */
final class EditorLinePicking {
    private EditorLinePicking() {}
    static PolyShape.Connection pick(PolyShape shape,double x,double y,Function<Vec3,Vec3> project,double radius) {
        PolyShape.Connection closest=null;double best=radius*radius,depth=Double.POSITIVE_INFINITY;
        for(var link:shape.connections()) {
            Vec3 a=project.apply(PolyGizmo.position(shape.vertex(link.source()).position()));
            Vec3 b=project.apply(PolyGizmo.position(shape.vertex(link.target()).position()));
            if(a==null || b==null)continue;
            double dx=b.x-a.x,dy=b.y-a.y,length=dx*dx+dy*dy;
            if(!Double.isFinite(length) || length<1e-12)continue;
            double t=Math.clamp(((x-a.x)*dx+(y-a.y)*dy)/length,0,1);
            double distance=(x-a.x-t*dx)*(x-a.x-t*dx)+(y-a.y-t*dy)*(y-a.y-t*dy);
            double z=a.z+t*(b.z-a.z);
            if(distance<=radius*radius && (closest==null || distance<best-1e-6 || Math.abs(distance-best)<=1e-6 && z<depth)) {
                closest=link;best=distance;depth=z;
            }
        }
        return closest;
    }
}
