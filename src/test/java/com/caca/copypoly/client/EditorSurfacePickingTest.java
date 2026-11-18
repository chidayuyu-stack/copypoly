package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorSurfacePickingTest {
    @Test void facePickingUsesNearestTriangleAndCanHitBothSidesOfAnOpenDraft() {
        var shape=Polyhedron.surface(Polyhedron.CUBE.points(),Polyhedron.CUBE.triangles().subList(0,2));
        var triangle=shape.triangles().getFirst();
        var a=PolyGizmo.position(shape.points().get(triangle.a()));var b=PolyGizmo.position(shape.points().get(triangle.b()));var c=PolyGizmo.position(shape.points().get(triangle.c()));
        var center=a.add(b).add(c).scale(1/3.);var normal=b.subtract(a).cross(c.subtract(a)).normalize();
        var hit=EditorSurfacePicking.triangle(shape,center.add(normal),normal.reverse());assertNotNull(hit);assertEquals(0,hit.index());
        assertNotNull(EditorSurfacePicking.triangle(shape,center.subtract(normal),normal));
    }
    @Test void picksNearestRealSurfaceWithPixelAndFineSnapping() {
        var origin=new Vec3(.421,.527,3);var ray=new Vec3(0,0,-1);
        assertEquals(new Polyhedron.Point(7,8,16),EditorSurfacePicking.hit(Polyhedron.CUBE,origin,ray,false));
        assertEquals(new Polyhedron.Point(6.7,8.4,16),EditorSurfacePicking.hit(Polyhedron.CUBE,origin,ray,true));
        assertNull(EditorSurfacePicking.hit(Polyhedron.CUBE,new Vec3(2,2,3),ray,false));
        assertNull(EditorSurfacePicking.hit(Polyhedron.CUBE,origin,ray.reverse(),false));
    }
}
