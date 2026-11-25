package com.caca.copypoly.geometry;

import java.math.BigDecimal;
import java.util.*;

/** Immutable fixed-point convex hull. Construction is bounded by MAX_POINTS, never performed per tick. */
public final class Polyhedron {
    public static final int MAX_POINTS = 32;
    public static final int MAX_BOXES = 64;
    public static final int SCALE = 1000;
    public static final int MAX_COORDINATE = 16 * SCALE;

    public static final class Point implements Comparable<Point> {
        private final int x, y, z;
        /** Coordinates are in editor units; generated positions round to the nearest thousandth. */
        public Point(double x, double y, double z) {
            this.x = fixed(x); this.y = fixed(y); this.z = fixed(z);
        }
        private Point(long packed) {
            if (packed < 0 || (packed >>> 42) != 0) throw new IllegalArgumentException("range");
            x = (int)(packed >>> 28); y = (int)(packed >>> 14 & 16383); z = (int)(packed & 16383);
            if (x > MAX_COORDINATE || y > MAX_COORDINATE || z > MAX_COORDINATE) throw new IllegalArgumentException("range");
        }
        public double x() { return x / (double)SCALE; }
        public double y() { return y / (double)SCALE; }
        public double z() { return z / (double)SCALE; }
        public long packed() { return (long)x << 28 | (long)y << 14 | z; }
        public static Point unpack(long n) { return new Point(n); }
        public static Point fromLegacy(int n) {
            if (n < 0 || (n >>> 15) != 0) throw new IllegalArgumentException("range");
            return new Point(n >> 10, n >> 5 & 31, n & 31);
        }
        public static Point parse(String x, String y, String z) {
            return unpack((long)parseCoordinate(x) << 28 | (long)parseCoordinate(y) << 14 | parseCoordinate(z));
        }
        public String[] coordinates() { return new String[]{format(x), format(y), format(z)}; }
        public int compareTo(Point p) { return Long.compare(packed(), p.packed()); }
        @Override public boolean equals(Object other) { return other instanceof Point p && x == p.x && y == p.y && z == p.z; }
        @Override public int hashCode() { return Long.hashCode(packed()); }
        @Override public String toString() { return String.join(", ", coordinates()); }
    }
    private static int fixed(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 16) throw new IllegalArgumentException("range");
        return (int)Math.round(value * SCALE);
    }
    private static int parseCoordinate(String text) {
        text = text.strip();
        if (text.length() > 64 || !text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
            throw new IllegalArgumentException("format");
        BigDecimal value = new BigDecimal(text);
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(16)) > 0) throw new IllegalArgumentException("range");
        try { return value.movePointRight(3).intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("precision"); }
    }
    private static String format(int fixed) {
        return BigDecimal.valueOf(fixed, 3).stripTrailingZeros().toPlainString();
    }
    /** Only transform roundoff may cross a boundary; genuine out-of-range positions are rejected. */
    public static Point transformed(double x, double y, double z) {
        return new Point(transformCoordinate(x), transformCoordinate(y), transformCoordinate(z));
    }
    private static double transformCoordinate(double value) {
        if (!Double.isFinite(value) || value < -1e-7 || value > 16 + 1e-7) throw new IllegalArgumentException("range");
        return Math.max(0, Math.min(16, value));
    }
    public record Plane(double x, double y, double z, double d) {
        public double distance(double a, double b, double c) { return x * a + y * b + z * c - d; }
    }
    /** Exact topology uses grid units; normalized planes are only for rendering and ray queries. */
    private record ExactPlane(long x, long y, long z, long d) {
        long distance(int a, int b, int c) { return x * a + y * b + z * c - d; }
        Plane normalized() {
            double length = Math.sqrt((double)x*x + (double)y*y + (double)z*z);
            return new Plane(x/length, y/length, z/length, d/(SCALE*length));
        }
    }
    public record Face(Plane plane, List<Point> vertices) {}
    /** Box coordinates use the same 0..16 units as points. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

    private static final Map<List<Point>, Polyhedron> CACHE = new LinkedHashMap<>(256, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<List<Point>, Polyhedron> e) { return size() > 256; }
    };
    public static final List<Point> CUBE_POINTS = List.of(new Point(0,0,0), new Point(0,0,16), new Point(0,16,0),
        new Point(0,16,16), new Point(16,0,0), new Point(16,0,16), new Point(16,16,0), new Point(16,16,16));
    public static final Polyhedron CUBE = of(CUBE_POINTS);
    private final List<Point> points;
    private final List<Face> faces;
    private final List<ExactPlane> exactPlanes;
    private final Box bounds;
    private final Point min, max;
    private volatile List<Box> boxes;

    public List<Point> points() { return points; }
    public List<Face> faces() { return faces; }
    public long[] packed() { return points.stream().mapToLong(Point::packed).toArray(); }
    public static Polyhedron fromPacked(long[] data) {
        if (data.length > MAX_POINTS) throw new IllegalArgumentException("count");
        List<Point> points=Arrays.stream(data).mapToObj(Point::unpack).toList();
        if(new HashSet<>(points).size()!=points.size())throw new IllegalArgumentException("duplicate");
        return of(points);
    }
    public static Polyhedron fromLegacy(int[] data) {
        if (data.length > MAX_POINTS) throw new IllegalArgumentException("count");
        return of(Arrays.stream(data).mapToObj(Point::fromLegacy).toList());
    }
    public static Polyhedron of(Collection<Point> input) {
        if (input.size() > MAX_POINTS) throw new IllegalArgumentException("count");
        List<Point> key = List.copyOf(new TreeSet<>(input));
        if (key.size() < 4) throw new IllegalArgumentException("count");
        synchronized (CACHE) {
            Polyhedron known = CACHE.get(key);
            if (known != null) return known;
        }
        Polyhedron built = new Polyhedron(key);
        synchronized (CACHE) { CACHE.put(key, built); }
        return built;
    }
    private Polyhedron(List<Point> pts) {
        points = pts;
        Set<ExactPlane> planes = new LinkedHashSet<>();
        boolean volume = false;
        // Exact integer half-space tests avoid epsilon cracks and ambiguous coplanar triangles.
        for (int i=0;i<pts.size()-2;i++) for(int j=i+1;j<pts.size()-1;j++) for(int k=j+1;k<pts.size();k++) {
            Point a=pts.get(i), b=pts.get(j), c=pts.get(k);
            long ux=b.x-a.x, uy=b.y-a.y, uz=b.z-a.z, vx=c.x-a.x, vy=c.y-a.y, vz=c.z-a.z;
            long nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            long g=gcd(gcd(Math.abs(nx),Math.abs(ny)),Math.abs(nz));
            if(g==0) continue;
            nx/=g; ny/=g; nz/=g;
            long d=nx*a.x+ny*a.y+nz*a.z;
            boolean positive=false, negative=false;
            for(Point p:pts) {
                long side=nx*p.x+ny*p.y+nz*p.z-d;
                positive |= side>0; negative |= side<0;
                if(positive && negative) break;
            }
            volume |= positive || negative;
            if(positive && negative || !positive && !negative) continue;
            planes.add(positive ? new ExactPlane(-nx,-ny,-nz,-d) : new ExactPlane(nx,ny,nz,d));
        }
        if(!volume || planes.size()<4) throw new IllegalArgumentException("flat");
        List<Face> result=new ArrayList<>();
        exactPlanes=List.copyOf(planes);
        for(ExactPlane plane:planes) result.add(new Face(plane.normalized(), polygon(pts,plane)));
        faces=List.copyOf(result);
        min=new Point(pts.stream().mapToDouble(Point::x).min().orElseThrow(), pts.stream().mapToDouble(Point::y).min().orElseThrow(), pts.stream().mapToDouble(Point::z).min().orElseThrow());
        max=new Point(pts.stream().mapToDouble(Point::x).max().orElseThrow(), pts.stream().mapToDouble(Point::y).max().orElseThrow(), pts.stream().mapToDouble(Point::z).max().orElseThrow());
        bounds=new Box(min.x(),min.y(),min.z(),max.x(),max.y(),max.z());
    }
    private static long gcd(long a,long b) { while(b!=0) {long t=a%b;a=b;b=t;} return a; }
    public static int dominant(Plane p) {
        return dominant(p.x,p.y,p.z);
    }
    private static int dominant(double a,double b,double c) {
        double x=Math.abs(a),y=Math.abs(b),z=Math.abs(c);
        return y>=x && y>=z ? 1 : x>=z ? 0 : 2;
    }
    private static int u(Point p,int axis) { return axis==0 ? p.z : p.x; }
    private static int v(Point p,int axis) { return axis==1 ? p.z : p.y; }
    private static long cross(Point a,Point b,Point c,int axis) {
        return (long)(u(b,axis)-u(a,axis))*(v(c,axis)-v(a,axis))-(long)(v(b,axis)-v(a,axis))*(u(c,axis)-u(a,axis));
    }
    private static List<Point> polygon(List<Point> points,ExactPlane plane) {
        int axis=dominant(plane.x,plane.y,plane.z);
        List<Point> p=points.stream().filter(t->plane.distance(t.x,t.y,t.z)==0)
            .sorted(Comparator.comparingInt((Point t)->u(t,axis)).thenComparingInt(t->v(t,axis))).toList();
        List<Point> h=new ArrayList<>();
        for(Point t:p) { while(h.size()>=2 && cross(h.get(h.size()-2),h.getLast(),t,axis)<=0) h.removeLast(); h.add(t); }
        int lower=h.size();
        for(int i=p.size()-2;i>=0;i--) { Point t=p.get(i); while(h.size()>lower && cross(h.get(h.size()-2),h.getLast(),t,axis)<=0) h.removeLast(); h.add(t); }
        h.removeLast();
        Point a=h.get(0),b=h.get(1),c=h.get(2);
        // Each normal is at most 2*16000^2; even their dot product fits in a long.
        long nx=(long)(b.y-a.y)*(c.z-a.z)-(long)(b.z-a.z)*(c.y-a.y);
        long ny=(long)(b.z-a.z)*(c.x-a.x)-(long)(b.x-a.x)*(c.z-a.z);
        long nz=(long)(b.x-a.x)*(c.y-a.y)-(long)(b.y-a.y)*(c.x-a.x);
        if(nx*plane.x+ny*plane.y+nz*plane.z<0) Collections.reverse(h);
        return List.copyOf(h);
    }
    public boolean contains(double x,double y,double z) {
        return faces.stream().allMatch(f->f.plane.distance(x,y,z)<=1e-8);
    }
    public List<Box> collisionBoxes() {
        List<Box> b=boxes;
        if(b!=null) return b;
        for(int resolution:new int[]{8,4,2}) {
            b=voxelize(resolution);
            if(b.size()<=MAX_BOXES) break;
        }
        boxes=List.copyOf(b);
        return boxes;
    }
    private List<Box> voxelize(int n) {
        double step=16.0/n;
        int gridStep=MAX_COORDINATE/n;
        boolean[][][] cells=new boolean[n][n][n];
        for(int x=0;x<n;x++) for(int y=0;y<n;y++) for(int z=0;z<n;z++) {
            int x0=Math.max(x*gridStep,min.x), y0=Math.max(y*gridStep,min.y), z0=Math.max(z*gridStep,min.z);
            int x1=Math.min((x+1)*gridStep,max.x), y1=Math.min((y+1)*gridStep,max.y), z1=Math.min((z+1)*gridStep,max.z);
            if(x0>=x1 || y0>=y1 || z0>=z1) continue;
            boolean hit=true;
            // Conservative plane rejection: never loses thin valid hulls (unlike center sampling).
            for(ExactPlane p:exactPlanes) {if(p.distance(p.x>=0?x0:x1,p.y>=0?y0:y1,p.z>=0?z0:z1)>=0) {hit=false;break;}}
            cells[x][y][z]=hit;
        }
        List<Box> result=new ArrayList<>();
        for(int x=0;x<n;x++) for(int y=0;y<n;y++) for(int z=0;z<n;z++) {
            if(!cells[x][y][z]) continue;
            int xe=x+1,ye=y+1,ze=z+1;
            while(xe<n && cells[xe][y][z]) xe++;
            outer: while(ye<n) {for(int a=x;a<xe;a++) if(!cells[a][ye][z]) break outer; ye++;}
            outer: while(ze<n) {for(int a=x;a<xe;a++) for(int b=y;b<ye;b++) if(!cells[a][b][ze]) break outer; ze++;}
            for(int a=x;a<xe;a++) for(int b=y;b<ye;b++) for(int c=z;c<ze;c++) cells[a][b][c]=false;
            result.add(new Box(Math.max(x*step,bounds.x0),Math.max(y*step,bounds.y0),Math.max(z*step,bounds.z0),
                Math.min(xe*step,bounds.x1),Math.min(ye*step,bounds.y1),Math.min(ze*step,bounds.z1)));
        }
        return result;
    }
    public static List<Point> parse(String text) {
        List<Point> result=new ArrayList<>();
        for(String line:text.strip().split("\\R|;")) {
            if(line.isBlank()) continue;
            String[] xyz=line.strip().replace("(","").replace(")","").split("[\\s,，]+");
            if(xyz.length!=3) throw new IllegalArgumentException("format");
            result.add(Point.parse(xyz[0],xyz[1],xyz[2]));
            if(result.size()>MAX_POINTS) throw new IllegalArgumentException("count");
        }
        return result;
    }
}
