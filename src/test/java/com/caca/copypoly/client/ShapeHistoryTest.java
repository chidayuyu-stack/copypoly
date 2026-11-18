package com.caca.copypoly.client;

import com.caca.copypoly.api.PolyShape;
import com.caca.copypoly.geometry.Polyhedron;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapeHistoryTest {
    @Test void undoRestoresDeletedConnectionSelectionWithoutSelectingItsEndpoints() {
        var history=new ShapeHistory();var shape=PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder().connect(3,6).build();
        var link=shape.connections().getFirst();history.remember(shape,List.of(),List.of(),false,false,link);
        var restored=history.undo();assertEquals(shape,restored.shape());assertEquals(link,restored.connection());
        assertTrue(restored.selection().isEmpty());assertTrue(restored.faces().isEmpty());assertFalse(restored.axes());
    }
    @Test void undoRestoresFaceSelectionSoDeleteCannotAccidentallySwitchToDeletingItsVertices() {
        var history=new ShapeHistory();var cube=PolyShape.fromGeometry(Polyhedron.CUBE);
        history.remember(cube,List.of(2,3,7,6),List.of(4,5),false,false);
        var restored=history.undo();assertEquals(List.of(4,5),restored.faces());assertFalse(restored.axes());assertFalse(restored.faceMode());
    }
    @Test void undoRestoresDocumentAndSelectionAcrossCreationConnectionMovementAndDeletion() {
        var history=new ShapeHistory();var cube=PolyShape.fromGeometry(Polyhedron.CUBE);
        var b=cube.toBuilder();int id=b.addCustomVertex(8,16,8);var added=b.build();history.remember(cube,List.of(2));
        history.remember(added,List.of(id));for(int t:new int[]{2,3,6,7})b.connect(id,t);var linked=b.build();
        history.remember(linked,List.of(id));var moved=linked.toBuilder().moveVertex(id,8,4,8).build();
        history.remember(moved,List.of(id));var deleted=moved.toBuilder().removeVertex(id).build();
        assertTrue(deleted.compile().geometry().contains(8,12,8));
        var state=history.undo();assertEquals(moved,state.shape());assertEquals(List.of(id),state.selection());
        assertFalse(state.shape().compile().geometry().contains(8,12,8));
        assertEquals(linked,history.undo().shape());assertEquals(added,history.undo().shape());assertEquals(cube,history.undo().shape());assertNull(history.undo());
    }
}
