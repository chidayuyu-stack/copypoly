package com.caca.copypoly.geometry;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeformMeshTest {
    private Polyhedron cube(){return Polyhedron.freeformFromHull(Polyhedron.CUBE_POINTS);}
    private Polyhedron dent(){var c=cube();var p=new ArrayList<>(c.points());p.set(7,new Polyhedron.Point(8,8,8));return c.editVertices(p);}
    @Test void freeCubeHasTwelveTrianglesButOnlyTwelveOutlineEdges() {
        var c=cube();assertTrue(c.freeform());assertFalse(Polyhedron.CUBE.freeform());
        assertEquals(12,c.triangles().size());assertEquals(12,c.edges().size());assertEquals(1,c.collisionBoxes().size());
        assertTrue(c.contains(8,8,8));assertSame(c,Polyhedron.fromPacked(c.packed(),c.packedTriangles()));
    }
    @Test void inwardVertexKeepsItsFacesAndMakesARealEmptyRecess() {
        var d=dent();var convex=Polyhedron.of(d.points());var point=new Polyhedron.Point(8,8,8);
        assertTrue(d.faces().stream().anyMatch(f->f.vertices().contains(point)));
        assertFalse(convex.faces().stream().anyMatch(f->f.vertices().contains(point)));
        assertTrue(convex.contains(7,7,13));assertFalse(d.contains(7,7,13));
        assertFalse(covered(d,7,7,13),"Concave collision filled the recess with its convex hull");
        assertTrue(d.contains(2,2,2));assertTrue(covered(d,2,2,2));
        assertEquals(cube().triangles(),d.triangles());assertTrue(d.collisionBoxes().size()<=64);
        assertSame(cube(),d.editVertices(Polyhedron.CUBE_POINTS));
    }
    @Test void mirroredAndRotatedDentsKeepOutwardWindingAndTheirCavities() {
        var d=dent();
        var mirrored=d.transformPoints(p->VertexEditing.mirror(p,0));
        assertFalse(mirrored.contains(9,7,13));assertTrue(mirrored.contains(14,2,2));
        var rotated=d.transformPoints(p->VertexEditing.rotate(p,1,1));
        assertFalse(rotated.contains(13,7,9));assertTrue(rotated.freeform());
        assertSame(d,mirrored.transformPoints(p->VertexEditing.mirror(p,0)));
    }
    @Test void pointAndTopologyStorageAndClipboardPreserveModeOrderAndDent() {
        var d=dent();assertSame(d,Polyhedron.fromPacked(d.packed(),d.packedTriangles()));
        assertSame(d,PolyShapeText.read(PolyShapeText.write(d)));
        assertSame(Polyhedron.CUBE,PolyShapeText.read(PolyShapeText.write(Polyhedron.CUBE)));
        assertFalse(Polyhedron.fromPacked(d.packed()).freeform());
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromPacked(d.packed(),new int[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromPacked(d.packed(),new int[Polyhedron.MAX_TRIANGLES*3+3]));
    }
    @Test void extraSurfacePointAndCopiedDuplicateCanBecomeNewControlVertices() {
        var c=cube();var points=new ArrayList<>(c.points());points.add(c.points().get(7));
        var copied=c.editVertices(points);assertEquals(9,copied.points().size());assertEquals(c.triangles(),copied.triangles());
        points.set(8,new Polyhedron.Point(8,16,8));var onFace=copied.editVertices(points);
        assertEquals(14,onFace.triangles().size());assertEquals(12,onFace.edges().size());
        points.set(8,new Polyhedron.Point(8,8,8));var dent=onFace.editVertices(points);
        assertFalse(dent.contains(8,14,8));assertTrue(dent.triangles().stream().anyMatch(t->t.uses(8)));
        var restored=onFace.removeVertex(8);
        assertEquals(c.points(),restored.points());assertEquals(c.edges(),restored.edges());assertEquals(c.collisionBoxes(),restored.collisionBoxes());
    }
    @Test void convertingLegacyInteriorPointsConnectsThemToTheSurface() {
        var points=new ArrayList<>(Polyhedron.CUBE_POINTS);points.add(new Polyhedron.Point(8,8,8));
        var free=Polyhedron.freeformFromHull(points);
        assertEquals(points,free.points());assertTrue(free.triangles().stream().anyMatch(t->t.uses(8)));
        assertTrue(free.freeform());
    }
    @Test void openInconsistentlyWoundCollapsedAndIntersectingSurfacesAreRejected() {
        var c=cube();var faces=new ArrayList<>(c.triangles());faces.removeLast();
        assertThrows(IllegalArgumentException.class,()->Polyhedron.mesh(c.points(),faces));
        var flipped=new ArrayList<>(c.triangles());flipped.set(0,flipped.getFirst().reversed());
        assertThrows(IllegalArgumentException.class,()->Polyhedron.mesh(c.points(),flipped));
        var collapsed=new ArrayList<>(c.points());collapsed.set(7,c.points().get(6));
        assertThrows(IllegalArgumentException.class,()->c.editVertices(collapsed));
        var touch=new ArrayList<>(c.points());touch.set(7,new Polyhedron.Point(0,8,0));
        assertThrows(IllegalArgumentException.class,()->c.editVertices(touch));
        assertEquals(7,Polyhedron.removeMeshVertex(touch,c.triangles(),7).points().size(),"Deleting the invalid vertex should repair the draft");
        var bad=new ArrayList<>(c.triangles());bad.set(0,new Polyhedron.Triangle(0,1,64));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.mesh(c.points(),bad));
    }
    @Test void concaveVoxelizationConservativelyCoversManyInteriorSamplesAndThinMeshes() {
        var shape=dent();var random=new Random(0xFEEE);
        for(int i=0;i<3000;i++) {
            double x=random.nextDouble()*16,y=random.nextDouble()*16,z=random.nextDouble()*16;
            if(shape.contains(x,y,z))assertTrue(covered(shape,x,y,z),"Voxelization lost an interior sample");
        }
        var thin=Polyhedron.of(Polyhedron.parse("0,0,0;16,16,15;16,15,16;15,16,16"));
        var mesh=Polyhedron.freeformFromHull(thin.points());assertFalse(mesh.collisionBoxes().isEmpty());
        assertTrue(covered(mesh,11.75,11.75,11.75));
    }
    private boolean covered(Polyhedron poly,double x,double y,double z) {
        return poly.collisionBoxes().stream().anyMatch(b->x>=b.x0()-1e-8&&x<=b.x1()+1e-8&&y>=b.y0()-1e-8&&y<=b.y1()+1e-8&&z>=b.z0()-1e-8&&z<=b.z1()+1e-8);
    }
}
