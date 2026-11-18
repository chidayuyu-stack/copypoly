package com.caca.copypoly.api;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.VertexEditing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolyShapeTest {
    private static PolyShape cube(){return PolyShape.fromGeometry(Polyhedron.CUBE);}
    private static PolyShape dent() {
        var b=cube().toBuilder();int id=b.addCustomVertex(8,4,8);
        for(int target:new int[]{2,3,6,7})b.connect(id,target);return b.build();
    }
    private static void sameSurface(Polyhedron a,Polyhedron b) {
        for(double x:new double[]{1,7,9,15})for(double y:new double[]{1,7,9,15})for(double z:new double[]{1,7,9,15})
            assertEquals(a.contains(x,y,z),b.contains(x,y,z),"Surface differs at "+x+","+y+","+z);
    }
    @Test void unconnectedOrSingleConnectionNeverAttachesToNearestFace() {
        var b=cube().toBuilder();int id=b.addCustomVertex(8,4,8);
        var initial=b.build().compile();
        assertEquals(PolyShape.PendingReason.NEEDS_CONNECTIONS,initial.pending().get(id));
        b.connect(id,2);var one=b.build().compile();sameSurface(Polyhedron.CUBE,one.geometry());
        assertTrue(one.activeCustomVertices().isEmpty());
        assertEquals(12,one.geometry().triangles().size());
    }
    @Test void twoTargetsSplitAnExistingEdgeAndCanBeRemovedWithoutGuessingAHole() {
        var b=cube().toBuilder();int id=b.addCustomVertex(4,4,8);b.connect(id,0).connect(id,1);
        var shape=b.build();assertTrue(shape.compile().activeCustomVertices().contains(id));
        assertEquals(14,shape.compile().geometry().triangles().size());
        sameSurface(Polyhedron.CUBE,shape.toBuilder().removeVertex(id).build().compile().geometry());
    }
    @Test void quadPatchMakesADentAndDeletingCustomVertexRestoresTheEntireFace() {
        var shape=dent();var compiled=shape.compile();
        assertEquals(Set.of(8),compiled.activeCustomVertices());assertTrue(compiled.pending().isEmpty());
        assertFalse(compiled.geometry().contains(8,12,8));assertTrue(compiled.geometry().contains(8,2,8));
        assertEquals(14,compiled.geometry().triangles().size());
        var restored=shape.toBuilder().removeVertex(8).build();
        sameSurface(Polyhedron.CUBE,restored.compile().geometry());assertTrue(restored.connections().isEmpty());
        assertEquals(Polyhedron.CUBE.collisionBoxes(),restored.compile().geometry().collisionBoxes());
    }
    @Test void trianglePatchAndOutwardBulgeUseExplicitTargets() {
        var inset=cube().transformPositions(p->new Polyhedron.Point(2+p.x()*.75,2+p.y()*.75,2+p.z()*.75));
        var triangle=inset.compile().geometry().triangles().stream().filter(t->Arrays.stream(t.indices()).allMatch(i->inset.vertex(i).position().y()==14)).findFirst().orElseThrow();
        double x=Math.round(Arrays.stream(triangle.indices()).mapToDouble(i->inset.vertex(i).position().x()).average().orElseThrow()*10)/10.;
        double z=Math.round(Arrays.stream(triangle.indices()).mapToDouble(i->inset.vertex(i).position().z()).average().orElseThrow()*10)/10.;
        var b=inset.toBuilder();int id=b.addCustomVertex(x,16,z);
        for(int target:triangle.indices())b.connect(id,target);
        assertEquals(Set.of(id),b.build().compile().activeCustomVertices());
        assertTrue(b.build().compile().geometry().contains(x,14.5,z));
    }
    @Test void unrelatedTargetsAndSelfIntersectionsRemainPendingAndLeaveBaseIntact() {
        var b=cube().toBuilder();int id=b.addCustomVertex(8,4,8);b.connect(id,0).connect(id,7);
        assertEquals(PolyShape.PendingReason.INVALID_BOUNDARY,b.build().compile().pending().get(id));
        sameSurface(Polyhedron.CUBE,b.build().compile().geometry());
        var collapsed=dent().toBuilder().moveVertex(8,8,0,8).build();
        assertThrows(IllegalArgumentException.class,collapsed::compile);
        assertFalse(collapsed.draftSurface().closed());
    }
    @Test void undirectedNeighborsAndDeletionClearIncomingLinks() {
        var b=dent().toBuilder();int id=b.addCustomVertex(4,8,4);b.connect(id,2).connect(id,8);
        var shape=b.build();assertEquals(Set.of(8,id),shape.compile().activeCustomVertices());
        assertEquals(List.of(2,3,6,7,id),shape.targets(8));
        var removed=shape.toBuilder().removeVertex(8).build();
        assertEquals(List.of(2),removed.targets(id));assertTrue(removed.compile().activeCustomVertices().isEmpty());
        var next=removed.toBuilder();assertTrue(next.addCustomVertex(8,8,8)>id);
    }
    @Test void unresolvedCustomCyclesNeverAcquireSurfacesAutomatically() {
        var b=cube().toBuilder();int first=b.addCustomVertex(4,4,4),second=b.addCustomVertex(12,12,12);
        b.connect(first,second).connect(first,0).connect(second,first).connect(second,7);
        var compiled=b.build().compile();assertTrue(compiled.activeCustomVertices().isEmpty());
        assertEquals(PolyShape.PendingReason.INVALID_BOUNDARY,compiled.pending().get(first));
        sameSurface(Polyhedron.CUBE,compiled.geometry());
    }
    @Test void graphAndClipboardPreserveTypesLinksAndExplicitLegacyFaces() {
        var original=dent();var unpacked=PolyShape.fromPacked(original.packed());assertEquals(original,unpacked);
        assertEquals(original,PolyShapeCodec.fromText(PolyShapeCodec.toText(original)));
        var selection=PolyShapeCodec.readVertices(PolyShapeCodec.copyVertices(original,List.of(2,3,8)));
        assertEquals(3,selection.vertices().size());assertEquals(VertexType.CUSTOM,selection.vertex(2).type());
        assertEquals(List.of(0,1),selection.targets(2));
        var single=PolyShapeCodec.readVertices(PolyShapeCodec.copyVertices(original,List.of(8)));
        assertEquals(VertexType.CUSTOM,single.vertex(0).type());assertTrue(single.connections().isEmpty());
        var legacy=PolyShape.fromGeometry(Polyhedron.freeformFromHull(Polyhedron.CUBE_POINTS));
        var roundTrip=PolyShapeCodec.fromText(PolyShapeCodec.toText(legacy));
        assertTrue(roundTrip.hasExplicitBaseFaces());assertEquals(legacy.baseFaces(),roundTrip.baseFaces());
    }
    @Test void normalVertexAndPlainCoordinateClipboardStayCompatible() {
        var text=PolyShapeCodec.copyVertices(cube(),List.of(2));assertEquals("0, 16, 0",text);
        assertEquals(List.of(new Polyhedron.Point(0,16,0)),PolyShapeCodec.readVertices(text).vertices().stream().map(PolyShape.Vertex::position).toList());
        sameSurface(Polyhedron.CUBE,PolyShapeCodec.fromText("0,0,0;0,0,16;0,16,0;0,16,16;16,0,0;16,0,16;16,16,0;16,16,16").compile().geometry());
    }
    @Test void mirroredExplicitBaseAndCustomPatchKeepOutwardWinding() {
        var fixed=PolyShape.fromGeometry(Polyhedron.freeformFromHull(Polyhedron.CUBE_POINTS));
        var b=fixed.toBuilder();int id=b.addCustomVertex(8,4,8);for(int t:new int[]{2,3,6,7})b.connect(id,t);
        var reflected=b.build().transformPositions(p->VertexEditing.mirror(p,0));
        assertEquals(Set.of(id),reflected.compile().activeCustomVertices());assertFalse(reflected.compile().geometry().contains(8,12,8));
    }
    @Test void hostileGraphCountsIdsAndConnectionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PolyShape.fromPacked(new int[]{1,0,8,65}));
        assertThrows(IllegalArgumentException.class,()->PolyShape.fromPacked(new int[]{1,0,1,1,0,0,2,0,0}));
        assertThrows(IllegalArgumentException.class,()->cube().toBuilder().connect(0,0));
        assertThrows(IllegalArgumentException.class,()->PolyShape.fromPacked(Arrays.copyOf(cube().packed(),3)));
        var bad=cube().packed();bad[7]=0;assertThrows(IllegalArgumentException.class,()->PolyShape.fromPacked(bad));
        assertThrows(IllegalArgumentException.class,()->PolyShape.builder().triangle(0,1,2).build());
    }
}
