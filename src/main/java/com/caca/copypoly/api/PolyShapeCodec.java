package com.caca.copypoly.api;

import com.caca.copypoly.geometry.PolyShapeText;
import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.VertexEditing;
import java.util.*;

/** Versioned document/selection clipboard interchange, plus legacy coordinate imports. */
public final class PolyShapeCodec {
    private static final String DOCUMENT="CopyPolyGraph 1\n",SELECTION="CopyPolyVertices 1\n";
    public static final int MAX_TEXT=128*1024;
    private static String data(PolyShape shape){return String.join(",",Arrays.stream(shape.packed()).mapToObj(Integer::toString).toList());}
    public static String toText(PolyShape shape){if(shape.manualMesh() || shape.lineMesh())shape.draftSurface();else shape.compile();return DOCUMENT+data(shape);}
    private static PolyShape packed(String text,String header) {
        if(text.length()>MAX_TEXT)throw new IllegalArgumentException("count");
        String[] parts=text.substring(header.length()).strip().split(",",-1);
        if(parts.length>PolyShape.MAX_PACKED)throw new IllegalArgumentException("graph");
        int[] data=new int[parts.length];
        try{for(int i=0;i<parts.length;i++)data[i]=Integer.parseInt(parts[i].strip());}
        catch(NumberFormatException bad){throw new IllegalArgumentException("format");}
        return PolyShape.fromPacked(data);
    }
    public static PolyShape fromText(String text) {
        text=text.replace("\r\n","\n").strip();
        var shape=text.startsWith(DOCUMENT)?packed(text,DOCUMENT):PolyShape.fromGeometry(PolyShapeText.read(text));if(shape.manualMesh() || shape.lineMesh())shape.draftSurface();else shape.compile();return shape;
    }
    public static String copyVertices(PolyShape shape,Collection<Integer> selected) {
        Set<Integer> wanted=new HashSet<>(selected);var vertices=shape.vertices().stream().filter(v->wanted.contains(v.id())).toList();
        if(vertices.isEmpty())throw new IllegalArgumentException("no_selection");
        boolean internalLinks=shape.connections().stream().anyMatch(c->wanted.contains(c.source()) && wanted.contains(c.target()));
        if(vertices.stream().allMatch(v->v.type()==VertexType.NORMAL) && !internalLinks)return String.join("\n",vertices.stream().map(v->VertexEditing.format(v.position().x())+", "+VertexEditing.format(v.position().y())+", "+VertexEditing.format(v.position().z())).toList());
        var builder=PolyShape.builder();Map<Integer,Integer> ids=new HashMap<>();
        for(var v:vertices)ids.put(v.id(),builder.addVertex(v.type(),v.position()));
        for(var c:shape.connections())if(ids.containsKey(c.source()) && ids.containsKey(c.target()))builder.connect(ids.get(c.source()),ids.get(c.target()));
        return SELECTION+data(builder.build());
    }
    public static PolyShape readVertices(String text) {
        text=text.replace("\r\n","\n").strip();if(text.length()>MAX_TEXT)throw new IllegalArgumentException("paste_limit");
        if(text.startsWith(SELECTION))return packed(text,SELECTION);
        var points=Polyhedron.parse(text);if(points.isEmpty())throw new IllegalArgumentException("format");
        var builder=PolyShape.builder();for(var p:points)builder.addVertex(VertexType.NORMAL,p);return builder.build();
    }
    private PolyShapeCodec(){}
}
