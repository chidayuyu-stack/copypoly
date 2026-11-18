package com.caca.copypoly.geometry;

import java.util.*;

/** Human-readable whole-shape clipboard format; legacy coordinate lists still work. */
public final class PolyShapeText {
    public static final int MAX_LENGTH=8192;
    private static final String HEADER="CopyPolyMesh 1\n",FACES="\nFaces\n";
    public static String write(Polyhedron shape) {
        String coordinates=String.join("\n",shape.points().stream().map(p->VertexEditing.format(p.x())+", "+VertexEditing.format(p.y())+", "+VertexEditing.format(p.z())).toList());
        return shape.freeform()?HEADER+coordinates+FACES+String.join("\n",shape.triangles().stream().map(t->t.a()+", "+t.b()+", "+t.c()).toList()):coordinates;
    }
    public static Polyhedron read(String text) {
        if(text.length()>MAX_LENGTH)throw new IllegalArgumentException("count");
        text=text.replace("\r\n","\n").strip();
        if(!text.startsWith(HEADER))return Polyhedron.of(Polyhedron.parse(text));
        String[] parts=text.substring(HEADER.length()).split(FACES,-1);
        if(parts.length!=2)throw new IllegalArgumentException("format");
        var points=Polyhedron.parse(parts[0]);List<Polyhedron.Triangle> triangles=new ArrayList<>();
        for(String line:parts[1].split("\n")) {
            String[] xyz=line.strip().split("[\\s,，]+");if(xyz.length!=3)throw new IllegalArgumentException("format");
            try{triangles.add(new Polyhedron.Triangle(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2])));}
            catch(NumberFormatException e){throw new IllegalArgumentException("format");}
            if(triangles.size()>Polyhedron.MAX_TRIANGLES)throw new IllegalArgumentException("mesh");
        }
        return Polyhedron.mesh(points,triangles);
    }
    private PolyShapeText() {}
}
