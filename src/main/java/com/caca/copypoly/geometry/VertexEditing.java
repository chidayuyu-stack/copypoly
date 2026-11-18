package com.caca.copypoly.geometry;

/** Pixel-space snapping, relative to the position where the drag began. */
public final class VertexEditing {
    private VertexEditing() {}

    public static double snap(double start, double delta, boolean fine) {
        int units = fine ? 1 : 10;
        long ticks = Math.round(start * 10) + Math.round(delta * 10 / units) * units;
        return Math.clamp(ticks, 0, 160) / 10.0;
    }

    /** All selected points receive one identical offset; the whole group stops at the block boundary. */
    public static java.util.List<Polyhedron.Point> translate(java.util.List<Polyhedron.Point> points,int axis,double delta,boolean fine) {
        if(points.isEmpty())return java.util.List.of();
        if(axis<0 || axis>2 || !Double.isFinite(delta))throw new IllegalArgumentException("axis");
        int min=160,max=0;
        for(var point:points){int ticks=(int)Math.round(point.axis(axis)*10);min=Math.min(min,ticks);max=Math.max(max,ticks);}
        int units=fine?1:10;
        long offset=Math.clamp(Math.round(delta*10/units)*units,-min,160-max);
        return points.stream().map(p->p.withAxis(axis,(Math.round(p.axis(axis)*10)+offset)/10.0)).toList();
    }

    public static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int)value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /** Reflect across the block-center plane, retaining the exact tenth-pixel lattice. */
    public static Polyhedron.Point mirror(Polyhedron.Point point, int axis) {
        if(axis<0 || axis>2)throw new IllegalArgumentException("axis");
        return point.withAxis(axis,(160-Math.round(point.axis(axis)*10))/10.0);
    }

    /** Right-handed quarter turns around (8,8,8), without rounding or cropping the shape. */
    public static Polyhedron.Point rotate(Polyhedron.Point point, int axis, int quarterTurns) {
        if(axis<0 || axis>2)throw new IllegalArgumentException("axis");
        int x=point.tx(),y=point.ty(),z=point.tz();
        for(int turn=0;turn<Math.floorMod(quarterTurns,4);turn++) {
            int a=x,b=y,c=z;
            if(axis==0){y=160-c;z=b;}
            else if(axis==1){x=c;z=160-a;}
            else{x=160-b;y=a;}
        }
        return new Polyhedron.Point(x/10.0,y/10.0,z/10.0);
    }
}
