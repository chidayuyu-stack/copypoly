package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class PolyGizmoTest {
    @Test void perimeterOrderSurvivesSelectionAndToggleWithoutSortingIds() {
        var gizmo=new PolyGizmo();gizmo.select(6,false);gizmo.select(2,true);gizmo.select(3,true);gizmo.select(7,true);
        assertEquals(List.of(6,2,3,7),gizmo.selectionOrder());assertEquals(List.of(2,3,6,7),gizmo.selectedIndices());
        gizmo.select(3,true);gizmo.select(3,true);assertEquals(List.of(6,2,7,3),gizmo.selectionOrder());
    }
    private static final Function<Vec3,Vec3> PROJECT=p->new Vec3(100+p.x*160,200-p.y*160,p.z);
    private static final PolyGizmo.AxisCoordinate COORDINATE=(x,y,p,a)->a==0?(x-100)/10:(200-y)/10;

    @Test void selectsNearestVertexThenDragsOnlyChosenAxisInPixels() {
        var gizmo=new PolyGizmo();
        var points=List.of(new Polyhedron.Point(8,8,8));
        assertTrue(gizmo.click(180,120,points,PROJECT,COORDINATE));assertEquals(0,gizmo.selected);
        assertFalse(gizmo.dragging());
        assertTrue(gizmo.click(230,120,points,PROJECT,COORDINATE));assertTrue(gizmo.dragging());
        assertEquals(new Polyhedron.Point(9,8,8),gizmo.drag(242.6,170,false));
        assertEquals(new Polyhedron.Point(9.3,8,8),gizmo.drag(242.6,170,true));
        gizmo.release();assertFalse(gizmo.dragging());assertNull(gizmo.drag(0,0,false));
    }
    @Test void respectsPickRadiusDepthAndAxesThatPointAtCamera() {
        var gizmo=new PolyGizmo();
        var points=List.of(new Polyhedron.Point(8,8,9),new Polyhedron.Point(8,8,1));
        assertFalse(gizmo.click(190,120,points,PROJECT,COORDINATE));
        assertTrue(gizmo.click(180,120,points,PROJECT,COORDINATE));assertEquals(1,gizmo.selected);
        assertFalse(gizmo.click(180,155,points,PROJECT,COORDINATE));
        assertFalse(gizmo.click(0,0,points,p->null,COORDINATE));
    }
    @Test void controlClickBuildsGroupAndAxisDragMovesAllMembers() {
        var gizmo=new PolyGizmo();
        var points=List.of(new Polyhedron.Point(4,4,0),new Polyhedron.Point(12,8,0));
        assertTrue(gizmo.click(140,160,points,PROJECT,COORDINATE));
        assertTrue(gizmo.click(220,120,points,PROJECT,COORDINATE,true));
        assertEquals(2,gizmo.selectedCount());assertTrue(gizmo.isSelected(0));assertTrue(gizmo.isSelected(1));
        assertTrue(gizmo.click(230,140,points,PROJECT,COORDINATE));
        var coarse=gizmo.dragSelection(244,140,false);
        assertEquals(new Polyhedron.Point(5,4,0),coarse.get(0));
        assertEquals(new Polyhedron.Point(13,8,0),coarse.get(1));
        var fine=gizmo.dragSelection(244,140,true);
        assertEquals(new Polyhedron.Point(5.4,4,0),fine.get(0));
        assertEquals(new Polyhedron.Point(13.4,8,0),fine.get(1));
        gizmo.release();
        assertTrue(gizmo.click(220,120,points,PROJECT,COORDINATE,true));
        assertEquals(1,gizmo.selectedCount());assertFalse(gizmo.isSelected(1));
        assertTrue(gizmo.click(140,160,points,PROJECT,COORDINATE,true));
        assertEquals(0,gizmo.selectedCount());assertEquals(-1,gizmo.selected);
    }
    @Test void removingVerticesKeepsSelectionIndicesInSync() {
        var gizmo=new PolyGizmo();
        gizmo.select(1,false);gizmo.select(3,true);gizmo.select(5,true);
        gizmo.removePoint(3);
        assertEquals(2,gizmo.selectedCount());assertTrue(gizmo.isSelected(1));assertTrue(gizmo.isSelected(4));
        gizmo.removePoint(4);assertEquals(1,gizmo.selected);assertEquals(1,gizmo.selectedCount());
        gizmo.select(2,false);assertEquals(1,gizmo.selectedCount());assertFalse(gizmo.isSelected(1));
    }
}
