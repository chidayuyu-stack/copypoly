package com.caca.copypoly.api;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.VertexEditing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceNetworkTest {
    static final int[][] CUTS={{2,8},{6,9},{7,10},{3,11},{8,9},{9,10},{10,11},{11,8}};
    static PolyShape inset(boolean normal,boolean reversed) {
        var b=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();
        for(var p:new double[][]{{4,16,4},{12,16,4},{12,16,12},{4,16,12}})b.addVertex(normal?VertexType.NORMAL:VertexType.CUSTOM,new Polyhedron.Point(p[0],p[1],p[2]));
        for(var cut:CUTS)b.connect(reversed?cut[1]:cut[0],reversed?cut[0]:cut[1]);return b.build();
    }
    private static PolyShape lower(PolyShape shape){Map<Integer,Polyhedron.Point> moved=new LinkedHashMap<>();for(int id=8;id<12;id++)moved.put(id,shape.vertex(id).position().withAxis(1,8));return shape.withPositions(moved);}
    @Test void ordinaryVerticesAndCustomVerticesBothPartitionTheInsetDiagram() {
        for(boolean normal:new boolean[]{true,false}) {
            var shape=inset(normal,false);assertTrue(shape.lineMesh());assertTrue(shape.pendingConnections().isEmpty());assertTrue(shape.pendingVertices().isEmpty());
            assertEquals(20,shape.compile().geometry().triangles().size());
            for(var cut:CUTS)assertTrue(shape.surfaceTriangles().stream().anyMatch(t->t.uses(cut[0])&&t.uses(cut[1])),"A drawn cut was not recovered");
            var dent=lower(shape).compile().geometry();assertFalse(dent.contains(8,12,8));assertTrue(dent.contains(8,2,8));assertTrue(dent.contains(1,13,8));
            assertTrue(dent.faces().stream().anyMatch(f->f.vertices().stream().allMatch(p->p.y()==8)),"The inner flat bottom disappeared");
            assertFalse(dent.collisionBoxes().stream().anyMatch(b->b.x0()<8&&b.x1()>8&&b.y0()<13&&b.y1()>13&&b.z0()<8&&b.z1()>8));
        }
    }
    @Test void directionAndCreationOrderDoNotChangeConnectivityOrDefaultTriangulation() {
        var forward=inset(false,false);var reverse=inset(false,true);assertEquals(forward,reverse);
        var b=forward.toBuilder();for(var cut:CUTS)b.disconnect(cut[1],cut[0]);
        for(int i=CUTS.length-1;i>=0;i--)b.connect(CUTS[i][1],CUTS[i][0]);
        assertEquals(forward.surfaceTriangles(),b.build().surfaceTriangles());
        assertEquals(List.of(2,9,11),forward.targets(8));assertTrue(forward.targets(2).contains(8));
    }
    @Test void singleBranchesStayPendingAndAnOrdinaryDiagonalImmediatelySplitsAFace() {
        var b=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();int custom=b.addCustomVertex(8,16,8);b.connect(2,custom);
        var branch=b.build();assertEquals(1,branch.pendingConnections().size());assertEquals(12,branch.surfaceTriangles().size());
        assertTrue(branch.compile().geometry().contains(8,15,8));
        var cut=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder().connect(3,6).build();
        assertTrue(cut.pendingConnections().isEmpty());assertTrue(cut.surfaceTriangles().stream().anyMatch(t->t.uses(3)&&t.uses(6)));
    }
    @Test void aClosedInnerLoopSplitsTheFaceWithoutRequiringRadialConstructionOrder() {
        var b=inset(false,false).toBuilder();for(int i=0;i<4;i++)b.disconnect(CUTS[i][0],CUTS[i][1]);
        var loop=b.build();assertTrue(loop.pendingConnections().isEmpty());assertTrue(loop.pendingVertices().isEmpty());assertEquals(20,loop.compile().geometry().triangles().size());
        assertFalse(lower(loop).compile().geometry().contains(8,12,8));
    }
    @Test void deletingInnerVerticesRestoresTheBaseSurfaceAndLeavesNoStaleReferences() {
        var lowered=lower(inset(false,false));var b=lowered.toBuilder();for(int id=8;id<12;id++)b.removeVertex(id);
        var restored=b.build();assertTrue(restored.connections().isEmpty());assertTrue(restored.compile().geometry().contains(8,15,8));assertEquals(12,restored.surfaceTriangles().size());
        assertEquals(Polyhedron.CUBE.collisionBoxes(),restored.compile().geometry().collisionBoxes());
        assertEquals(restored,PolyShape.fromPacked(restored.packed()));
    }
    @Test void movementRoundTripSerializationAndWholeTransformsKeepTheReferenceSurface() {
        var shape=lower(inset(false,false));assertEquals(shape,PolyShape.fromPacked(shape.packed()));
        assertEquals(shape,PolyShapeCodec.fromText(PolyShapeCodec.toText(shape)));
        var mirror=shape.transformPositions(p->VertexEditing.mirror(p,0));assertFalse(mirror.compile().geometry().contains(8,12,8));
        assertEquals(shape,mirror.transformPositions(p->VertexEditing.mirror(p,0)));
        var turned=shape.transformPositions(p->VertexEditing.rotate(p,2,1));assertFalse(turned.compile().geometry().contains(4,8,8));
    }
    @Test void newlyAddedVerticesCanBePositionedBeforeTheirFirstCompletedCuts() {
        var b=inset(false,false).toBuilder();int id=b.addCustomVertex(8,16,8);var shape=b.build().toBuilder().moveVertex(id,8,16,6).build();
        var linked=shape.toBuilder().connect(id,8).connect(id,9).connect(id,10).connect(id,11).build();
        assertFalse(linked.pendingVertices().containsKey(id));assertDoesNotThrow(linked::compile);
    }
    @Test void aCutAtAnExistingBoundaryEdgeHasNoTJunctionWhenItsPointMoves() {
        var b=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();int id=b.addCustomVertex(0,16,8);
        b.connect(2,id).connect(id,3);var shape=b.build();assertTrue(shape.pendingConnections().isEmpty());
        assertDoesNotThrow(shape::compile);assertDoesNotThrow(()->shape.toBuilder().moveVertex(id,2,14,8).build().compile());
    }
    @Test void crossingCutsWithoutAnExplicitIntersectionStayPendingInsteadOfAddingInvisiblePoints() {
        var shape=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder().connect(2,7).connect(3,6).build();
        assertEquals(8,shape.vertices().size());assertFalse(shape.pendingConnections().isEmpty());assertDoesNotThrow(shape::compile);
    }
    @Test void copiedOrdinaryCutsPreserveUndirectedLinksWithRemappedIds() {
        var shape=inset(true,false);var copy=PolyShapeCodec.readVertices(PolyShapeCodec.copyVertices(shape,List.of(8,9,10,11)));
        assertEquals(4,copy.vertices().size());assertEquals(4,copy.connections().size());assertTrue(copy.targets(0).contains(3));
    }
    @Test void manyClosedPolygonsRecoverEveryDrawnEdgeWithoutDependingOnTheOldDiagonal() {
        var random=new Random(0xC07FACE);
        for(int sample=0;sample<50;sample++) {
            int count=3+sample%10;double offset=random.nextDouble();var b=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();
            for(int i=0;i<count;i++){double angle=2*Math.PI*(i+offset)/count,radius=4+random.nextDouble();b.addCustomVertex(Math.round((8+radius*Math.cos(angle))*10)/10.,16,Math.round((8+radius*Math.sin(angle))*10)/10.);}
            for(int i=0;i<count;i++)b.connect(8+i,8+(i+1)%count);
            var shape=b.build();assertTrue(shape.pendingConnections().isEmpty(),"Unrecovered loop at sample "+sample);assertDoesNotThrow(shape::compile);
        }
    }
    @Test void aCoincidentPastedLoopStaysPendingWithoutErasingTheExistingDent() {
        var original=lower(inset(false,false));var b=original.toBuilder();
        for(int id=8;id<12;id++)b.addVertex(VertexType.CUSTOM,original.vertex(id).position());
        for(int id=12;id<16;id++)b.connect(id,12+(id-11)%4);
        var pasted=b.build();assertFalse(pasted.compile().geometry().contains(8,12,8));
        assertTrue(pasted.pendingVertices().keySet().containsAll(List.of(12,13,14,15)));
        assertTrue(original.connections().stream().noneMatch(pasted.pendingConnections()::contains));
    }
    @Test void drawnRegionBoundariesSurviveBakingAndSerializationForFaceSelection() {
        var lines=inset(true,false);assertTrue(lines.isDrawnEdge(8,9));assertTrue(lines.isDrawnEdge(9,8));
        var manual=lines.editableMesh();assertTrue(manual.manualMesh());assertTrue(manual.isDrawnEdge(8,9));
        assertEquals(manual,PolyShape.fromPacked(manual.packed()));assertTrue(PolyShape.fromPacked(manual.packed()).isDrawnEdge(9,8));
        assertFalse(lines.isDrawnEdge(8,10),"A hidden triangulation diagonal was incorrectly treated as a user cut");
    }
    @Test void legacyDirectedVersionOneDocumentsKeepTheirDentWhenMigratedToNormalCuts() {
        List<Integer> data=new ArrayList<>(List.of(1,0,9,9));
        for(int i=0;i<8;i++)data.addAll(List.of(i,Polyhedron.CUBE_POINTS.get(i).packed(),0));
        data.addAll(List.of(8,new Polyhedron.Point(8,8,8).packed(),1,0,4));for(int target:new int[]{2,3,6,7})data.addAll(List.of(8,target));
        var legacy=PolyShape.fromPacked(data.stream().mapToInt(Integer::intValue).toArray());assertFalse(legacy.lineMesh());assertFalse(legacy.compile().geometry().contains(8,12,8));
        var migrated=legacy.toBuilder().connect(2,3).build();assertTrue(migrated.lineMesh());assertFalse(migrated.compile().geometry().contains(8,12,8));
        assertEquals(migrated,PolyShape.fromPacked(migrated.packed()));
    }
}
