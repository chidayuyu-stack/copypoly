package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron.Point;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class VertexMarqueeTest {
    private static final List<Point> POINTS=List.of(new Point(2,2,0),new Point(6,6,0),new Point(12,12,0));
    private static final Function<Vec3,Vec3> PROJECT=p->new Vec3(p.x*160,p.y*160,0);
    @Test void boxReplacesEarlierSelectionInsteadOfAddingToIt() {
        var gizmo=new PolyGizmo();gizmo.select(2,false);
        var box=new VertexMarquee();box.begin(10,10);box.drag(80,80,3);
        gizmo.replaceSelection(box.find(POINTS,PROJECT,p->true));
        assertEquals(2,gizmo.selectedCount());assertTrue(gizmo.isSelected(0));assertTrue(gizmo.isSelected(1));assertFalse(gizmo.isSelected(2));
    }
    @Test void emptyBoxLeavesNoVerticesSelected() {
        var gizmo=new PolyGizmo();gizmo.select(0,false);gizmo.select(2,true);
        var box=new VertexMarquee();box.begin(70,10);box.drag(90,40,3);
        gizmo.replaceSelection(box.find(POINTS,PROJECT,p->true));
        assertEquals(0,gizmo.selectedCount());assertEquals(-1,gizmo.selected);
    }
    @Test void reverseDragIncludesBoundaryPointsAndExcludesHiddenOrCoveredVertices() {
        var box=new VertexMarquee();box.begin(120,120);box.drag(20,20,3);
        assertEquals(List.of(0,1,2),box.find(POINTS,PROJECT,p->true));
        assertEquals(List.of(1,2),box.find(POINTS,PROJECT,p->p.x>30));
        assertEquals(List.of(0,2),box.find(POINTS,p->p.x==6/16.0?null:PROJECT.apply(p),p->true));
    }
    @Test void clickJitterDoesNotStartMarqueeAndCancelClearsGesture() {
        var box=new VertexMarquee();box.begin(50,50);box.drag(51,51,3);
        assertTrue(box.pending());assertFalse(box.active());
        box.drag(70,70,3);assertTrue(box.active());box.drag(50,50,3);assertTrue(box.active());
        box.cancel();assertFalse(box.pending());assertFalse(box.active());
    }
}
