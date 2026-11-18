package com.caca.copypoly.client;

import com.caca.copypoly.api.PolyShape;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class EditorLinePickingTest {
    private static final Function<Vec3,Vec3> PROJECT=p->new Vec3(100+160*p.x,200-160*p.y,p.z);
    @Test void picksDrawnSegmentsWithinToleranceButNotOtherMeshEdgesOrTheirExtensions() {
        var shape=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder().connect(0,4).build();
        var link=new PolyShape.Connection(0,4);
        assertEquals(link,EditorLinePicking.pick(shape,180,202,PROJECT,3));
        assertNull(EditorLinePicking.pick(shape,180,204,PROJECT,3));
        assertNull(EditorLinePicking.pick(shape,270,200,PROJECT,3));
        assertNull(EditorLinePicking.pick(shape,180,40,PROJECT,3));
    }
    @Test void overlappingWiresUseInterpolatedDepthAndNullOrCollapsedProjectionsAreIgnored() {
        var shape=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder().connect(1,5).connect(0,4).build();
        assertEquals(new PolyShape.Connection(0,4),EditorLinePicking.pick(shape,180,200,PROJECT,3));
        assertNull(EditorLinePicking.pick(shape,180,200,p->null,3));
        assertNull(EditorLinePicking.pick(shape,0,0,p->Vec3.ZERO,3));
        assertNull(EditorLinePicking.pick(shape,0,0,p->new Vec3(Double.NaN,0,0),3));
    }
    @Test void shortPendingWireCanBePickedBetweenVisibleVertexMarkers() {
        var builder=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();
        int first=builder.addCustomVertex(4,16,8),second=builder.addCustomVertex(6,16,8);
        var shape=builder.connect(first,second).build();
        Function<Vec3,Vec3> project=p->new Vec3(100+96*p.x,200-96*p.y,p.z);
        var link=new PolyShape.Connection(first,second);
        assertTrue(shape.pendingConnections().contains(link));
        assertEquals(link,EditorLinePicking.pick(shape,130,104,project,3));
        var points=shape.vertices().stream().map(PolyShape.Vertex::position).toList();
        assertEquals(-1,PolyGizmo.pickVertex(130,104,points,project,-1,5));
        assertEquals(first,PolyGizmo.pickVertex(124,104,points,project,-1,5));
    }
}
