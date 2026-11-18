package com.caca.copypoly;

import com.caca.copypoly.api.PolyShape;
import java.util.List;

final class TopologyFixtures {
    static final int[][] WALLS={{0,1,9,8},{4,5,13,12},{0,1,5,4},{8,9,13,12},
        {1,2,10,9},{5,6,14,13},{1,2,6,5},{9,10,14,13},
        {2,3,11,10},{6,7,15,14},{2,3,7,6},{10,11,15,14},
        {3,0,8,11},{7,4,12,15},{3,0,4,7},{11,8,12,15}};
    static PolyShape tunnel() {
        double[][] ring={{0,0},{16,0},{16,16},{0,16},{4,4},{12,4},{12,12},{4,12}};
        var b=PolyShape.builder().meshFaces(List.of());
        for(int y:new int[]{0,16})for(var p:ring)b.addNormalVertex(p[0],y,p[1]);
        for(var wall:WALLS)b.face(java.util.Arrays.stream(wall).boxed().toList());return b.build();
    }
    static PolyShape lineInset() {
        var b=PolyShape.fromGeometry(com.caca.copypoly.geometry.Polyhedron.CUBE).toBuilder();
        for(var p:new double[][]{{4,16,4},{12,16,4},{12,16,12},{4,16,12}})b.addNormalVertex(p[0],p[1],p[2]);
        for(var edge:new int[][]{{2,8},{9,6},{7,10},{11,3},{8,9},{10,9},{10,11},{8,11}})b.connect(edge[0],edge[1]);
        var shape=b.build();var positions=new java.util.LinkedHashMap<Integer,com.caca.copypoly.geometry.Polyhedron.Point>();
        for(int id=8;id<12;id++)positions.put(id,shape.vertex(id).position().withAxis(1,8));return shape.withPositions(positions);
    }
    private TopologyFixtures(){}
}
