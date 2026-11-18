package com.caca.copypoly.api;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.VertexEditing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArbitraryMeshTest {
    static PolyShape prism() {
        double[][] ring={{0,0},{16,0},{16,6},{6,6},{6,16},{0,16}};
        var b=PolyShape.builder().meshFaces(List.of());
        for(int y:new int[]{0,16})for(var p:ring)b.addNormalVertex(p[0],y,p[1]);
        b.face(List.of(0,1,2,3,4,5)).face(List.of(6,7,8,9,10,11));
        for(int i=0;i<6;i++)b.face(List.of(i,(i+1)%6,(i+1)%6+6,i+6));return b.build();
    }
    static PolyShape tunnel() {
        double[][] ring={{0,0},{16,0},{16,16},{0,16},{4,4},{12,4},{12,12},{4,12}};
        var b=PolyShape.builder().meshFaces(List.of());
        for(int y:new int[]{0,16})for(var p:ring)b.addNormalVertex(p[0],y,p[1]);
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            b.face(List.of(i,j,j+8,i+8));b.face(List.of(i+4,j+4,j+12,i+12));
            b.face(List.of(i,j,j+4,i+4));b.face(List.of(i+8,j+8,j+12,i+12));
        }
        return b.build();
    }
    @Test void concavePolygonProducesAnLPrismWithoutFillingItsNotch() {
        var shape=prism();var mesh=shape.compile().geometry();
        assertTrue(mesh.contains(3,8,12));assertTrue(mesh.contains(12,8,3));assertFalse(mesh.contains(12,8,12));
        assertFalse(covered(mesh,12,8,12));assertTrue(mesh.collisionBoxes().size()<=64);
        assertEquals(20,mesh.triangles().size());assertEquals(12,shape.vertices().size());
    }
    @Test void aSixteenVertexThroughHoleStaysOpenInGeometryAndCollision() {
        var shape=tunnel();var mesh=shape.compile().geometry();
        assertEquals(16,shape.vertices().size());assertEquals(32,mesh.triangles().size());
        for(int y:new int[]{1,8,15}){assertFalse(mesh.contains(8,y,8));assertTrue(mesh.contains(2,y,8));assertFalse(covered(mesh,8,y,8));}
        var round=PolyShape.fromPacked(shape.packed());assertEquals(shape,round);
        assertFalse(PolyShapeCodec.fromText(PolyShapeCodec.toText(shape)).compile().geometry().contains(8,8,8));
        assertFalse(shape.transformPositions(p->VertexEditing.mirror(p,0)).compile().geometry().contains(8,8,8));
    }
    @Test void nestedShellsCreateAnEnclosedCavityAndDisconnectedSolidsAreAllowed() {
        var cube=Polyhedron.CUBE;var points=new ArrayList<>(cube.points());
        points.addAll(cube.points().stream().map(p->new Polyhedron.Point(4+p.x()/2,4+p.y()/2,4+p.z()/2)).toList());
        var faces=new ArrayList<>(cube.triangles());for(var t:cube.triangles())faces.add(new Polyhedron.Triangle(t.a()+8,t.b()+8,t.c()+8));
        var hollow=Polyhedron.orientedMesh(points,faces);assertFalse(hollow.contains(8,8,8));assertTrue(hollow.contains(2,8,8));assertFalse(covered(hollow,8,8,8));
        var split=new ArrayList<Polyhedron.Point>();
        split.addAll(cube.points().stream().map(p->new Polyhedron.Point(p.x()*.375,p.y()*.375,p.z()*.375)).toList());
        split.addAll(cube.points().stream().map(p->new Polyhedron.Point(10+p.x()*.375,10+p.y()*.375,10+p.z()*.375)).toList());
        var separate=Polyhedron.orientedMesh(split,faces);assertTrue(separate.contains(3,3,3));assertTrue(separate.contains(13,13,13));assertFalse(separate.contains(8,8,8));
    }
    @Test void manualFacesCanReferenceCustomVerticesWithoutAConvexHullOrFanPatch() {
        var b=PolyShape.builder().meshFaces(List.of());
        b.addCustomVertex(0,0,0);b.addCustomVertex(16,0,0);b.addCustomVertex(0,16,0);b.addCustomVertex(0,0,16);
        b.face(List.of(0,1,2)).face(List.of(0,1,3)).face(List.of(0,2,3)).face(List.of(1,2,3));
        var shape=b.build();assertEquals(Set.of(0,1,2,3),shape.compile().activeCustomVertices());
        assertTrue(shape.compile().geometry().contains(2,2,2));assertFalse(shape.compile().geometry().contains(10,10,10));
    }
    @Test void removingAndRebuildingFacesKeepsAnOpenDraftWithoutInventingCaps() {
        var full=tunnel();var open=full.toBuilder().removeFaces(List.of(0,1)).build();
        assertThrows(IllegalArgumentException.class,open::compile);assertFalse(open.draftSurface().closed());
        assertTrue(open.draftSurface().collisionBoxes().isEmpty());assertFalse(open.draftSurface().contains(2,8,1));
        assertEquals(30,open.draftSurface().triangles().size());
        assertEquals(open,PolyShapeCodec.fromText(PolyShapeCodec.toText(open)));
        var repaired=open.toBuilder().face(List.of(0,1,9,8)).build();
        assertFalse(repaired.compile().geometry().contains(8,8,8));assertTrue(repaired.compile().geometry().contains(8,8,2));
    }
    @Test void deletingManualVerticesRemovesTheirFacesAndLeavesAnEditableHole() {
        var shape=tunnel().toBuilder().removeVertex(0).build();assertEquals(15,shape.vertices().size());
        assertTrue(shape.baseFaces().stream().noneMatch(t->t.uses(0)));assertThrows(IllegalArgumentException.class,shape::compile);
        assertFalse(shape.draftSurface().faces().isEmpty());
    }
    @Test void bakingAutomaticDeformationPreservesTheActualDentAndStableIds() {
        var b=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();int id=b.addCustomVertex(8,4,8);
        for(int target:new int[]{2,3,6,7})b.connect(id,target);var old=b.build();var manual=old.editableMesh();
        assertEquals(old.vertices(),manual.vertices());assertTrue(manual.manualMesh());
        assertFalse(manual.compile().geometry().contains(8,12,8));assertEquals(old.compile().geometry().triangles(),manual.compile().geometry().triangles());
        // The screenshot's bottom-face dent is also a valid closed fan when inside its boundary.
        var bottom=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();int tip=bottom.addCustomVertex(8,12,8);
        for(int target:new int[]{0,1,4,5})bottom.connect(tip,target);
        assertTrue(bottom.build().compile().activeCustomVertices().contains(tip));assertFalse(bottom.build().compile().geometry().contains(8,4,8));
    }
    @Test void invalidBowtiesNonplanarPolygonsAndNonManifoldSurfacesAreRejected() {
        var b=PolyShape.fromGeometry(Polyhedron.CUBE).editableMesh().toBuilder();
        assertThrows(IllegalArgumentException.class,()->b.face(List.of(2,7,3,6)));
        assertThrows(IllegalArgumentException.class,()->b.face(List.of(0,1,2,7)));
        var cube=Polyhedron.CUBE;var faces=new ArrayList<>(cube.triangles());faces.add(new Polyhedron.Triangle(0,3,7));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.orientedMesh(cube.points(),faces));
        var malformed=tunnel().packed();malformed[0]=1;assertThrows(IllegalArgumentException.class,()->PolyShape.fromPacked(malformed));
    }
    private static boolean covered(Polyhedron shape,double x,double y,double z){return shape.collisionBoxes().stream().anyMatch(b->x>b.x0() && x<b.x1() && y>b.y0() && y<b.y1() && z>b.z0() && z<b.z1());}
}
