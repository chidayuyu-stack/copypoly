package com.caca.copypoly.geometry;

import com.caca.copypoly.api.PolyShape;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshOpeningsTest {
    private static Polyhedron tunnel() {
        double[][] ring={{0,0},{16,0},{16,16},{0,16},{4,4},{12,4},{12,12},{4,12}};
        var b=PolyShape.builder().meshFaces(List.of());for(int y:new int[]{0,16})for(var p:ring)b.addNormalVertex(p[0],y,p[1]);
        for(int i=0;i<4;i++){int j=(i+1)%4;b.face(List.of(i,j,j+8,i+8)).face(List.of(i+4,j+4,j+12,i+12)).face(List.of(i,j,j+4,i+4)).face(List.of(i+8,j+8,j+12,i+12));}
        return b.build().compile().geometry();
    }
    @Test void aClosedOShapeHasNoMissingFaces() {
        var ring=tunnel();assertTrue(ring.closed());assertFalse(ring.contains(8,8,8));
        assertTrue(MeshOpenings.boundaryLoops(ring.triangles()).isEmpty());assertTrue(MeshOpenings.previewCaps(ring.points(),ring.triangles()).isEmpty());
    }
    @Test void aRemovedCubeFaceIsHighlightedOnlyAcrossItsActualOpening() {
        var cube=Polyhedron.CUBE;var open=cube.triangles().stream().filter(t->!onTop(cube,t)).toList();
        assertEquals(1,MeshOpenings.boundaryLoops(open).size());var cap=MeshOpenings.previewCaps(cube.points(),open);
        assertEquals(2,cap.size());assertEquals(256,area(cube.points(),cap),1e-8);
        assertTrue(cap.stream().allMatch(t->Arrays.stream(t.indices()).allMatch(i->cube.points().get(i).y()==16)));
    }
    @Test void aMissingRingRimHighlightsTheAnnulusWhileLeavingTheIntendedCentralHoleClear() {
        var ring=tunnel();var open=ring.triangles().stream().filter(t->!onTop(ring,t)).toList();
        assertEquals(2,MeshOpenings.boundaryLoops(open).size());var cap=MeshOpenings.previewCaps(ring.points(),open);
        assertFalse(cap.isEmpty());assertEquals(192,area(ring.points(),cap),1e-8);
        assertTrue(cap.stream().noneMatch(t->coversCenter(ring.points(),t)),"Opening preview painted the intended O-shaped through-hole");
    }
    @Test void boundaryPerimetersAndUnorderedPlanarSelectionsCanCreateFaces() {
        var cube=PolyShape.fromGeometry(Polyhedron.CUBE).editableMesh();
        var b=cube.toBuilder();var points=cube.vertices().stream().map(PolyShape.Vertex::position).toList();
        var top=new ArrayList<Integer>();for(int i=0;i<cube.baseFaces().size();i++)if(Arrays.stream(cube.baseFaces().get(i).indices()).allMatch(id->points.get(id).y()==16))top.add(i);
        var open=b.removeFaces(top).build();var closed=open.toBuilder().faceAutomatically(List.of(7,2,6,3)).build();
        assertTrue(closed.compile().geometry().contains(8,15,8));
        var order=MeshOpenings.orderFace(points,List.of(),List.of(7,2,6,3));assertEquals(2,Polyhedron.triangulatePolygon(points,order).size());
    }
    @Test void oneMissingTriangleDoesNotFlagTheRestOfTheCube() {
        var cube=Polyhedron.CUBE;var faces=new ArrayList<>(cube.triangles());var missing=faces.removeFirst();
        var cap=MeshOpenings.previewCaps(cube.points(),faces);assertEquals(1,cap.size());assertEquals(new HashSet<>(Arrays.stream(missing.indices()).boxed().toList()),new HashSet<>(Arrays.stream(cap.getFirst().indices()).boxed().toList()));
    }
    @Test void automaticallyCreatedWarpedQuadsAreTriangulatedWithoutRequiringCoplanarity() {
        var cube=PolyShape.fromGeometry(Polyhedron.CUBE).editableMesh();var points=cube.vertices().stream().map(PolyShape.Vertex::position).toList();
        List<Integer> top=new ArrayList<>();for(int i=0;i<cube.baseFaces().size();i++)if(Arrays.stream(cube.baseFaces().get(i).indices()).allMatch(id->points.get(id).y()==16))top.add(i);
        var repaired=cube.toBuilder().removeFaces(top).moveVertex(7,16,14,16).faceAutomatically(List.of(7,2,6,3)).build();
        assertDoesNotThrow(repaired::compile);assertTrue(repaired.compile().geometry().contains(8,8,8));
    }
    @Test void twoThroughHolesAreKeptClearWhenHighlightingTheMissingEndSurfaces() {
        double[][] rings={{0,0},{16,0},{16,16},{0,16},{2,2},{6,2},{6,6},{2,6},{10,10},{14,10},{14,14},{10,14}};
        var b=PolyShape.builder().meshFaces(List.of());for(int y:new int[]{0,16})for(var p:rings)b.addNormalVertex(p[0],y,p[1]);
        for(int start:new int[]{0,4,8})for(int i=0;i<4;i++){int a=start+i,c=start+(i+1)%4;b.face(List.of(a,c,c+12,a+12));}
        var shape=b.build().draftSurface();assertEquals(6,MeshOpenings.boundaryLoops(shape.triangles()).size());
        var cap=MeshOpenings.previewCaps(shape.points(),shape.triangles());assertEquals(448,area(shape.points(),cap),1e-8);
        var closed=new ArrayList<>(shape.triangles());closed.addAll(cap);var solid=Polyhedron.orientedMesh(shape.points(),closed);
        assertFalse(solid.contains(4,8,4));assertFalse(solid.contains(12,8,12));assertTrue(solid.contains(8,8,8));
    }
    private static boolean onTop(Polyhedron p,Polyhedron.Triangle t){return Arrays.stream(t.indices()).allMatch(i->p.points().get(i).y()==16);}
    private static double area(List<Polyhedron.Point> points,List<Polyhedron.Triangle> triangles) {
        double area=0;for(var t:triangles){var a=points.get(t.a());var b=points.get(t.b());var c=points.get(t.c());area+=Math.abs((b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x()))/2;}return area;
    }
    private static boolean coversCenter(List<Polyhedron.Point> p,Polyhedron.Triangle t) {
        var a=p.get(t.a());var b=p.get(t.b());var c=p.get(t.c());
        double x=(b.x()-a.x())*(8-a.z())-(b.z()-a.z())*(8-a.x()),y=(c.x()-b.x())*(8-b.z())-(c.z()-b.z())*(8-b.x()),z=(a.x()-c.x())*(8-c.z())-(a.z()-c.z())*(8-c.x());
        return x>=-1e-8&&y>=-1e-8&&z>=-1e-8 || x<=1e-8&&y<=1e-8&&z<=1e-8;
    }
}
