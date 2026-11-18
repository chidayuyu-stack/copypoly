package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;

/** Raycast the actual surface, rather than its stair-step collision boxes. */
final class EditorSurfacePicking {
    record TriangleHit(int index,double distance) {}
    static TriangleHit triangle(Polyhedron shape,Vec3 origin,Vec3 direction) {
        double nearest=Double.POSITIVE_INFINITY;int hit=-1,index=0;
        for(var t:shape.triangles()) {
            Vec3 a=PolyGizmo.position(shape.points().get(t.a())),b=PolyGizmo.position(shape.points().get(t.b())),c=PolyGizmo.position(shape.points().get(t.c()));
            double distance=distance(origin,direction,a,b,c);if(distance>=0 && distance<nearest){nearest=distance;hit=index;}index++;
        }
        return hit<0?null:new TriangleHit(hit,nearest);
    }
    private static double distance(Vec3 origin,Vec3 direction,Vec3 a,Vec3 b,Vec3 c) {
        Vec3 e=b.subtract(a),f=c.subtract(a),h=direction.cross(f);
        double det=e.dot(h);if(Math.abs(det)<1e-10)return -1;
        Vec3 s=origin.subtract(a);double u=s.dot(h)/det;if(u< -1e-8 || u>1+1e-8)return -1;
        Vec3 q=s.cross(e);double v=direction.dot(q)/det;if(v< -1e-8 || u+v>1+1e-8)return -1;
        return f.dot(q)/det;
    }
    static Polyhedron.Point hit(Polyhedron shape,Vec3 origin,Vec3 direction,boolean fine) {
        double nearest=Double.POSITIVE_INFINITY;
        for(var face:shape.faces())for(int i=1;i<face.vertices().size()-1;i++) {
            Vec3 a=PolyGizmo.position(face.vertices().getFirst());
            Vec3 b=PolyGizmo.position(face.vertices().get(i));
            Vec3 c=PolyGizmo.position(face.vertices().get(i+1));
            double t=distance(origin,direction,a,b,c);if(t>=0 && t<nearest)nearest=t;
        }
        if(!Double.isFinite(nearest))return null;
        Vec3 p=origin.add(direction.scale(nearest));double step=fine?.1:1;
        return new Polyhedron.Point(snap(p.x*16,step),snap(p.y*16,step),snap(p.z*16,step));
    }
    private static double snap(double p,double step){return Math.clamp(Math.round(p/step)*step,0,16);}
    private EditorSurfacePicking(){}
}
