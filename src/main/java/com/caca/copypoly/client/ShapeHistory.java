package com.caca.copypoly.client;

import com.caca.copypoly.api.PolyShape;
import java.util.*;

/** Immutable document snapshots: no camera movement or merely changing selection in undo. */
final class ShapeHistory {
    record State(PolyShape shape,List<Integer> selection,List<Integer> faces,boolean axes,boolean faceMode,PolyShape.Connection connection){State{selection=List.copyOf(selection);faces=List.copyOf(faces);}}
    private final ArrayDeque<State> past=new ArrayDeque<>();
    void remember(PolyShape shape,List<Integer> selection) {
        remember(shape,selection,List.of(),true,false);
    }
    void remember(PolyShape shape,List<Integer> selection,Collection<Integer> faces,boolean axes,boolean faceMode) {
        remember(shape,selection,faces,axes,faceMode,null);
    }
    void remember(PolyShape shape,List<Integer> selection,Collection<Integer> faces,boolean axes,boolean faceMode,PolyShape.Connection connection) {
        past.addLast(new State(shape,selection,List.copyOf(faces),axes,faceMode,connection));if(past.size()>100)past.removeFirst();
    }
    State undo(){return past.pollLast();}
}
