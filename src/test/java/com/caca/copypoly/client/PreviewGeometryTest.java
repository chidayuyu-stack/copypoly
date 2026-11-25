package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewGeometryTest {
    @Test void projectionAndPickingAgreeAcrossViewsAndZooms() {
        for(double yaw:new double[]{0,.65,Math.PI/2,Math.PI,4.3})
            for(double pitch:new double[]{-1.4,0,.45,1.4})
                for(double scale:new double[]{2,5,10}) {
                    var view=new PreviewGeometry.View(yaw,pitch,scale,150,100);
                    Vec3 hit=view.surface(Polyhedron.CUBE,150,100);
                    assertNotNull(hit);
                    assertTrue(Polyhedron.CUBE.contains(hit.x,hit.y,hit.z));
                    assertTrue(Polyhedron.CUBE.faces().stream().anyMatch(f->Math.abs(f.plane().distance(hit.x,hit.y,hit.z))<1e-7));
                    Vec3 screen=view.project(hit);
                    assertEquals(150,screen.x,1e-7);assertEquals(100,screen.y,1e-7);
                    assertTrue(screen.z>0,"Pick the front surface, not the back");
                    assertNull(view.surface(Polyhedron.CUBE,1000,1000));
                }
    }

    @Test void surfacePickingRespectsSlopingFaces() {
        var tetrahedron=Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16"));
        var view=new PreviewGeometry.View(0,0,10,80,80);
        Vec3 hit=view.surface(tetrahedron,40,120);
        assertNotNull(hit);assertEquals(4,hit.x,1e-8);assertEquals(4,hit.y,1e-8);assertEquals(8,hit.z,1e-8);
        assertNull(view.surface(tetrahedron,140,20));
    }

    @Test void draggingUsesProjectedAxisAndIgnoresPerpendicularMotion() {
        for(double yaw:new double[]{0,.65,2.1})for(int axis=0;axis<3;axis++) {
            var view=new PreviewGeometry.View(yaw,.45,6,150,100);
            Vec3 vector=view.axis(axis);
            if(vector.length()<.1)continue;
            assertEquals(11,PreviewGeometry.draggedCoordinate(8,3*vector.x,3*vector.y,vector));
            assertEquals(5,PreviewGeometry.draggedCoordinate(8,-3*vector.x,-3*vector.y,vector));
            assertEquals(8,PreviewGeometry.draggedCoordinate(8,-vector.y,vector.x,vector));
            assertEquals(16,PreviewGeometry.draggedCoordinate(8,100*vector.x,100*vector.y,vector));
            assertEquals(0,PreviewGeometry.draggedCoordinate(8,-100*vector.x,-100*vector.y,vector));
        }
        assertEquals(8,PreviewGeometry.draggedCoordinate(8,100,100,Vec3.ZERO));
    }

    @Test void gridSnappingAndHandleHitAreasAreBounded() {
        assertEquals(new Polyhedron.Point(0,9,16),PreviewGeometry.snap(new Vec3(-.1,8.6,16.01)));
        assertEquals(3,PreviewGeometry.segmentDistance(5,3,Vec3.ZERO,new Vec3(10,0,0)),1e-8);
        assertEquals(5,PreviewGeometry.segmentDistance(14,3,Vec3.ZERO,new Vec3(10,0,0)),1e-8);
    }
    @Test void fineDraggingPreservesInputPrecisionAndAccumulatesSmallMoves() {
        Vec3 axis=new Vec3(10,0,0);
        var original=new Polyhedron.Point(8.125,4.001,6.002);
        var drag=new PreviewGeometry.Drag(original,0,axis,0,0,true);
        var current=original;
        for(int i=1;i<=10;i++)current=drag.move(i*.1,0,true,current);
        assertEquals(new Polyhedron.Point(8.135,4.001,6.002),current);
        assertEquals(original,drag.original);
        assertEquals(0,PreviewGeometry.draggedCoordinate(.001,-1000,0,axis,true));
        assertEquals(16,PreviewGeometry.draggedCoordinate(15.999,1000,0,axis,true));
    }
    @Test void switchingShiftDoesNotMoveThePointAndRetainsTheUndoOrigin() {
        Vec3 axis=new Vec3(10,0,0);
        var original=new Polyhedron.Point(7,3.125,4);
        var drag=new PreviewGeometry.Drag(original,0,axis,0,0,false);
        var current=drag.move(10,0,false,original);
        assertEquals(8,current.x());
        drag.mode(true,current);
        assertEquals(current,drag.move(10,0,true,current));
        current=drag.move(33,0,true,current);
        assertEquals(8.23,current.x());
        drag.mode(false,current);
        assertEquals(current,drag.move(33,0,false,current));
        assertEquals(current,drag.move(33,100,false,current));
        current=drag.move(39,100,false,current);
        assertEquals(9,current.x());
        assertEquals(3.125,current.y());
        assertEquals(original,drag.original);
    }
    @Test void decimalOriginsDoNotSnapOnClickReleaseOrPerpendicularMovement() {
        var original=new Polyhedron.Point(8.125,4,6);
        var drag=new PreviewGeometry.Drag(original,0,new Vec3(10,0,0),5,5,false);
        assertEquals(original,drag.move(5,5,false,original));
        assertEquals(original,drag.move(5,40,false,original));
        assertEquals(original,drag.move(5,40,true,original));
        assertEquals(8.135,drag.move(6,40,true,original).x());
    }
    @Test void rayPickingSupportsDecimalFacesAndThinHulls() {
        var hull=Polyhedron.of(Polyhedron.CUBE_POINTS.stream().map(p->new Polyhedron.Point(
            p.x(),p.y(),8.125+p.z()/16*.001)).toList());
        var view=new PreviewGeometry.View(0,0,10,80,80);
        var hit=view.surface(hull,80,80);
        assertNotNull(hit);
        assertEquals(8.126,hit.z,1e-9);
        assertTrue(hull.contains(hit.x,hit.y,hit.z));
    }
}
