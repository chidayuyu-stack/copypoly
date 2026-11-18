package com.caca.copypoly.geometry;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class VertexEditingTest {
    @Test void groupTranslationClampsOneCommonOffsetAndPreservesSpacing() {
        var points=java.util.List.of(new Polyhedron.Point(14,2.3,4.5),new Polyhedron.Point(15.8,3.2,6.7));
        var moved=VertexEditing.translate(points,0,10,true);
        assertEquals(new Polyhedron.Point(14.2,2.3,4.5),moved.get(0));
        assertEquals(new Polyhedron.Point(16,3.2,6.7),moved.get(1));
        var back=VertexEditing.translate(points,0,-100,false);
        assertEquals(0,back.get(0).x());assertEquals(1.8,back.get(1).x());
        assertEquals(java.util.List.of(),VertexEditing.translate(java.util.List.of(),0,1,true));
    }
    @Test void quarterTurnsUseBlockCenterAndRightHandedAxes() {
        var p=new Polyhedron.Point(1.1,2.2,3.3);
        assertEquals(new Polyhedron.Point(1.1,12.7,2.2),VertexEditing.rotate(p,0,1));
        assertEquals(new Polyhedron.Point(3.3,2.2,14.9),VertexEditing.rotate(p,1,1));
        assertEquals(new Polyhedron.Point(13.8,1.1,3.3),VertexEditing.rotate(p,2,1));
        assertEquals(new Polyhedron.Point(14.9,2.2,3.3),VertexEditing.mirror(p,0));
        assertEquals(new Polyhedron.Point(1.1,13.8,3.3),VertexEditing.mirror(p,1));
        assertEquals(new Polyhedron.Point(1.1,2.2,12.7),VertexEditing.mirror(p,2));
    }
    @Test void transformsAreReversibleAcrossBoundaryAndDecimalPoints() {
        var random=new Random(172);
        for(int i=0;i<1000;i++) {
            var p=new Polyhedron.Point(random.nextInt(161)/10.0,random.nextInt(161)/10.0,random.nextInt(161)/10.0);
            for(int axis=0;axis<3;axis++) {
                assertEquals(p,VertexEditing.mirror(VertexEditing.mirror(p,axis),axis));
                assertEquals(p,VertexEditing.rotate(p,axis,4));
                assertEquals(p,VertexEditing.rotate(VertexEditing.rotate(p,axis,1),axis,-1));
                assertEquals(VertexEditing.rotate(p,axis,3),VertexEditing.rotate(p,axis,-1));
            }
        }
        for(var p:Polyhedron.CUBE_POINTS)for(int axis=0;axis<3;axis++)
            assertTrue(Polyhedron.CUBE_POINTS.contains(VertexEditing.rotate(p,axis,1)));
    }
    @Test void wholeShapeTransformsKeep64PointsAndSurvivePacking() {
        var source=new ArrayList<Polyhedron.Point>();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)
            source.add(new Polyhedron.Point(x*5.3,y*5.3,z*5.3));
        for(int axis=0;axis<3;axis++) {
            final int a=axis;
            var mirrored=Polyhedron.of(source.stream().map(p->VertexEditing.mirror(p,a)).toList());
            var rotated=Polyhedron.of(source.stream().map(p->VertexEditing.rotate(p,a,1)).toList());
            assertEquals(64,mirrored.points().size());assertEquals(64,rotated.points().size());
            assertSame(mirrored,Polyhedron.fromPacked(mirrored.packed()));
            assertSame(rotated,Polyhedron.fromPacked(rotated.packed()));
            assertEquals(6,mirrored.faces().size());assertEquals(6,rotated.faces().size());
            assertFalse(mirrored.collisionBoxes().isEmpty());assertFalse(rotated.collisionBoxes().isEmpty());
        }
    }
}
