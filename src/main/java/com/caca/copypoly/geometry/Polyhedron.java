package com.caca.copypoly.geometry;

import java.util.*;

/** Immutable convex hull or closed editable mesh on an exact 0.1-pixel lattice. */
public final class Polyhedron {
    public static final int MAX_POINTS = 64;
    public static final int MAX_BOXES = 64;
    // For a closed simplicial surface, 3F=2E and E<=V(V-1)/2.
    // This admits every possible manifold triangulation of the allowed vertices.
    public static final int MAX_TRIANGLES = MAX_POINTS*(MAX_POINTS-1)/3;
    public record Point(double x, double y, double z) implements Comparable<Point> {
        public Point {
            x = coordinate(x); y = coordinate(y); z = coordinate(z);
        }
        private static double coordinate(double value) {
            if (!Double.isFinite(value) || value < 0 || value > 16 || Math.abs(value * 10 - Math.rint(value * 10)) > 1e-7)
                throw new IllegalArgumentException("range");
            return Math.round(value * 10) / 10.0;
        }
        public int tx() { return (int)Math.round(x * 10); }
        public int ty() { return (int)Math.round(y * 10); }
        public int tz() { return (int)Math.round(z * 10); }
        public int packed() {
            // Keep the original representation for integer points, including existing worlds and items.
            if (tx()%10 == 0 && ty()%10 == 0 && tz()%10 == 0) return (int)x << 10 | (int)y << 5 | (int)z;
            return 1 << 24 | tx() << 16 | ty() << 8 | tz();
        }
        public static Point unpack(int n) {
            if ((n & 1 << 24) != 0) {
                if (n < 0 || n >>> 25 != 0) throw new IllegalArgumentException("range");
                return new Point((n >> 16 & 255) / 10.0, (n >> 8 & 255) / 10.0, (n & 255) / 10.0);
            }
            if (n < 0 || (n >>> 15) != 0) throw new IllegalArgumentException("range");
            return new Point(n >> 10, n >> 5 & 31, n & 31);
        }
        public int compareTo(Point p) {
            int c = Integer.compare(tx(), p.tx());
            if (c == 0) c = Integer.compare(ty(), p.ty());
            return c == 0 ? Integer.compare(tz(), p.tz()) : c;
        }
        public double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        public Point withAxis(int axis, double value) { return new Point(axis == 0 ? value : x, axis == 1 ? value : y, axis == 2 ? value : z); }
    }
    public record Plane(int x, int y, int z, int d) {
        public double distance(double a, double b, double c) { return x * a + y * b + z * c - d / 10.0; }
        private int side(Point p) { return x * p.tx() + y * p.ty() + z * p.tz() - d; }
    }
    public record Face(Plane plane, List<Point> vertices) {}
    public record Triangle(int a, int b, int c) {
        public Triangle reversed() { return new Triangle(a,c,b); }
        public boolean uses(int i) { return a==i || b==i || c==i; }
        public int[] indices() { return new int[]{a,b,c}; }
    }
    public record Edge(Point a, Point b) {}
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
    private final Box bounds;
    private final List<Triangle> topology;
    private final boolean closed;
    private volatile List<Edge> edges;
    private volatile List<Box> boxes;
    private record MeshKey(List<Point> points,List<Triangle> topology) {}
    private static final Map<MeshKey,Polyhedron> MESH_CACHE = new LinkedHashMap<>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<MeshKey,Polyhedron> e) { return size()>256; }
    };

    public List<Point> points() { return points; }
    public List<Face> faces() { return faces; }
    public boolean freeform() { return topology != null; }
    public List<Triangle> triangles() { return topology == null ? MeshGeometry.triangulateHull(this,points) : topology; }
    public int[] packedTriangles() { return topology == null ? new int[0] : topology.stream().flatMapToInt(t->Arrays.stream(t.indices())).toArray(); }
    public int[] packed() { return points.stream().mapToInt(Point::packed).toArray(); }
    public static Polyhedron fromPacked(int[] data) {
        if (data.length > MAX_POINTS) throw new IllegalArgumentException("count");
        return of(Arrays.stream(data).mapToObj(Point::unpack).toList());
    }
    public static Polyhedron fromPacked(int[] data,int[] triangles) {
        if(triangles.length==0)return fromPacked(data);
        if(data.length>MAX_POINTS || triangles.length>MAX_TRIANGLES*3 || triangles.length%3!=0)throw new IllegalArgumentException("mesh");
        List<Triangle> faces=new ArrayList<>();
        for(int i=0;i<triangles.length;i+=3)faces.add(new Triangle(triangles[i],triangles[i+1],triangles[i+2]));
        return mesh(Arrays.stream(data).mapToObj(Point::unpack).toList(),faces);
    }
    public static Polyhedron mesh(List<Point> points,List<Triangle> triangles) {
        var key=new MeshKey(List.copyOf(points),List.copyOf(triangles));
        synchronized(MESH_CACHE){var known=MESH_CACHE.get(key);if(known!=null)return known;}
        var built=new Polyhedron(key.points,key.topology);
        synchronized(MESH_CACHE){MESH_CACHE.put(key,built);}
        return built;
    }
    /** Closed arbitrary mesh with winding inferred consistently, including nested cavities. */
    public static Polyhedron orientedMesh(List<Point> points,List<Triangle> triangles) {
        return mesh(points,MeshGeometry.orient(points,triangles));
    }
    /** Display-only open surface. Never use an unfinished surface for solid collision. */
    public static Polyhedron surface(List<Point> points,List<Triangle> triangles) {
        List<Triangle> drawable=new ArrayList<>();
        for(var t:triangles){
            for(int id:t.indices())if(id<0 || id>=points.size())throw new IllegalArgumentException("mesh");
            try{trianglePlane(points.get(t.a()),points.get(t.b()),points.get(t.c()));drawable.add(t);}catch(IllegalArgumentException collapsed){/* A collapsed draft face remains in its document for repair. */}
        }
        return new Polyhedron(List.copyOf(points),List.copyOf(drawable),false);
    }
    public boolean closed(){return closed;}
    /** Triangulates a simple planar polygon in the supplied perimeter order. */
    public static List<Triangle> triangulatePolygon(List<Point> points,List<Integer> perimeter) {
        return MeshGeometry.polygon(points,perimeter);
    }
    public static Polyhedron freeformFromHull(List<Point> points) {
        var hull=of(points);
        return mesh(points,MeshGeometry.attachUnused(points,MeshGeometry.triangulateHull(hull,points)));
    }
    public static Polyhedron editedMesh(List<Point> points,List<Triangle> topology) {
        return mesh(points,MeshGeometry.attachUnused(points,topology));
    }
    public Polyhedron editVertices(List<Point> points) {
        if(!freeform())return of(points);
        if(points.size()<this.points.size())throw new IllegalArgumentException("mesh");
        return mesh(points,MeshGeometry.attachUnused(points,topology));
    }
    public Polyhedron removeVertex(int index) {
        return freeform()?MeshGeometry.removeVertex(points,topology,index):of(java.util.stream.IntStream.range(0,points.size()).filter(i->i!=index).mapToObj(points::get).toList());
    }
    public static Polyhedron removeMeshVertex(List<Point> points,List<Triangle> topology,int index) {
        return MeshGeometry.removeVertex(points,topology,index);
    }
    public Polyhedron transformPoints(java.util.function.UnaryOperator<Point> transform) {
        List<Point> mapped=points.stream().map(transform).toList();
        if(!freeform())return of(mapped);
        var faces=MeshGeometry.signedVolume(mapped,topology)<0?topology.stream().map(Triangle::reversed).toList():topology;
        return mesh(mapped,faces);
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
        topology = null;
        closed=true;
        Set<Plane> planes = new LinkedHashSet<>();
        boolean volume = false;
        // Exact integer half-space tests avoid epsilon cracks and ambiguous coplanar triangles.
        for (int i=0;i<pts.size()-2;i++) for(int j=i+1;j<pts.size()-1;j++) for(int k=j+1;k<pts.size();k++) {
            Point a=pts.get(i), b=pts.get(j), c=pts.get(k);
            int ux=b.tx()-a.tx(), uy=b.ty()-a.ty(), uz=b.tz()-a.tz(), vx=c.tx()-a.tx(), vy=c.ty()-a.ty(), vz=c.tz()-a.tz();
            int nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            int g=gcd(gcd(Math.abs(nx),Math.abs(ny)),Math.abs(nz));
            if(g==0) continue;
            nx/=g; ny/=g; nz/=g;
            int d=nx*a.tx()+ny*a.ty()+nz*a.tz();
            boolean positive=false, negative=false;
            for(Point p:pts) {
                int side=nx*p.tx()+ny*p.ty()+nz*p.tz()-d;
                positive |= side>0; negative |= side<0;
                if(positive && negative) break;
            }
            volume |= positive || negative;
            if(positive && negative || !positive && !negative) continue;
            planes.add(positive ? new Plane(-nx,-ny,-nz,-d) : new Plane(nx,ny,nz,d));
        }
        if(!volume || planes.size()<4) throw new IllegalArgumentException("flat");
        List<Face> result=new ArrayList<>();
        for(Plane plane:planes) result.add(new Face(plane, polygon(pts,plane)));
        faces=List.copyOf(result);
        bounds=new Box(pts.stream().mapToDouble(Point::x).min().orElseThrow(), pts.stream().mapToDouble(Point::y).min().orElseThrow(),
            pts.stream().mapToDouble(Point::z).min().orElseThrow(), pts.stream().mapToDouble(Point::x).max().orElseThrow(),
            pts.stream().mapToDouble(Point::y).max().orElseThrow(), pts.stream().mapToDouble(Point::z).max().orElseThrow());
    }
    private Polyhedron(List<Point> pts,List<Triangle> topology) {
        this(pts,topology,true);
    }
    private Polyhedron(List<Point> pts,List<Triangle> topology,boolean closed) {
        points=pts;this.topology=topology;
        this.closed=closed;
        faces=closed?MeshGeometry.validate(pts,topology):MeshGeometry.surfaceFaces(pts,topology);
        List<Point> used=faces.stream().flatMap(f->f.vertices.stream()).toList();
        if(used.isEmpty()){bounds=new Box(0,0,0,0,0,0);return;}
        bounds=new Box(used.stream().mapToDouble(Point::x).min().orElseThrow(),used.stream().mapToDouble(Point::y).min().orElseThrow(),used.stream().mapToDouble(Point::z).min().orElseThrow(),
            used.stream().mapToDouble(Point::x).max().orElseThrow(),used.stream().mapToDouble(Point::y).max().orElseThrow(),used.stream().mapToDouble(Point::z).max().orElseThrow());
    }
    static Plane trianglePlane(Point a,Point b,Point c) {
        int ux=b.tx()-a.tx(),uy=b.ty()-a.ty(),uz=b.tz()-a.tz(),vx=c.tx()-a.tx(),vy=c.ty()-a.ty(),vz=c.tz()-a.tz();
        int x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,g=gcd(gcd(Math.abs(x),Math.abs(y)),Math.abs(z));
        if(g==0)throw new IllegalArgumentException("mesh");
        x/=g;y/=g;z/=g;return new Plane(x,y,z,x*a.tx()+y*a.ty()+z*a.tz());
    }
    public List<Edge> edges() {
        if(edges!=null)return edges;
        record Pair(Point a,Point b) {}
        Map<Pair,List<Plane>> planes=new LinkedHashMap<>();
        for(var face:faces)for(int i=0;i<face.vertices.size();i++) {
            var a=face.vertices.get(i);var b=face.vertices.get((i+1)%face.vertices.size());
            var key=a.compareTo(b)<0?new Pair(a,b):new Pair(b,a);
            planes.computeIfAbsent(key,k->new ArrayList<>()).add(face.plane);
        }
        List<Edge> result=new ArrayList<>();
        for(var entry:planes.entrySet()) {
            var adjacent=entry.getValue();
            if(adjacent.size()==2 && adjacent.getFirst().equals(adjacent.getLast()))continue;
            result.add(new Edge(entry.getKey().a,entry.getKey().b));
        }
        return edges=List.copyOf(result);
    }
    private static int gcd(int a,int b) { while(b!=0) {int t=a%b;a=b;b=t;} return a; }
    public static int dominant(Plane p) {
        int x=Math.abs(p.x),y=Math.abs(p.y),z=Math.abs(p.z);
        return y>=x && y>=z ? 1 : x>=z ? 0 : 2;
    }
    private static int u(Point p,int axis) { return axis==0 ? p.tz() : p.tx(); }
    private static int v(Point p,int axis) { return axis==1 ? p.tz() : p.ty(); }
    private static long cross(Point a,Point b,Point c,int axis) {
        return (long)(u(b,axis)-u(a,axis))*(v(c,axis)-v(a,axis))-(long)(v(b,axis)-v(a,axis))*(u(c,axis)-u(a,axis));
    }
    private static List<Point> polygon(List<Point> points,Plane plane) {
        int axis=dominant(plane);
        List<Point> p=points.stream().filter(t->plane.side(t)==0)
            .sorted(Comparator.comparingInt((Point t)->u(t,axis)).thenComparingInt(t->v(t,axis))).toList();
        List<Point> h=new ArrayList<>();
        for(Point t:p) { while(h.size()>=2 && cross(h.get(h.size()-2),h.getLast(),t,axis)<=0) h.removeLast(); h.add(t); }
        int lower=h.size();
        for(int i=p.size()-2;i>=0;i--) { Point t=p.get(i); while(h.size()>lower && cross(h.get(h.size()-2),h.getLast(),t,axis)<=0) h.removeLast(); h.add(t); }
        h.removeLast();
        Point a=h.get(0),b=h.get(1),c=h.get(2);
        long nx=(long)(b.ty()-a.ty())*(c.tz()-a.tz())-(long)(b.tz()-a.tz())*(c.ty()-a.ty());
        long ny=(long)(b.tz()-a.tz())*(c.tx()-a.tx())-(long)(b.tx()-a.tx())*(c.tz()-a.tz());
        long nz=(long)(b.tx()-a.tx())*(c.ty()-a.ty())-(long)(b.ty()-a.ty())*(c.tx()-a.tx());
        if(nx*plane.x+ny*plane.y+nz*plane.z<0) Collections.reverse(h);
        return List.copyOf(h);
    }
    public boolean contains(double x,double y,double z) {
        if(!closed)return false;
        if(freeform())return MeshGeometry.contains(faces,x,y,z);
        return faces.stream().allMatch(f->f.plane.distance(x,y,z)<=1e-8);
    }
    public List<Box> collisionBoxes() {
        if(!closed)return List.of();
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
        boolean[][][] cells=new boolean[n][n][n];
        for(int x=0;x<n;x++) for(int y=0;y<n;y++) for(int z=0;z<n;z++) {
            double x0=Math.max(x*step,bounds.x0), y0=Math.max(y*step,bounds.y0), z0=Math.max(z*step,bounds.z0);
            double x1=Math.min((x+1)*step,bounds.x1), y1=Math.min((y+1)*step,bounds.y1), z1=Math.min((z+1)*step,bounds.z1);
            if(x0>=x1 || y0>=y1 || z0>=z1) continue;
            boolean hit;
            if(freeform())hit=MeshGeometry.intersectsCell(faces,x0,y0,z0,x1,y1,z1);
            else {
            hit=true;
            // Conservative plane rejection: never loses thin valid hulls (unlike center sampling).
            for(Face f:faces) {Plane p=f.plane; if(p.distance(p.x>=0?x0:x1,p.y>=0?y0:y1,p.z>=0?z0:z1)>=0) {hit=false;break;}}
            }
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
            try {result.add(new Point(Double.parseDouble(xyz[0]),Double.parseDouble(xyz[1]),Double.parseDouble(xyz[2])));}
            catch(NumberFormatException e) {throw new IllegalArgumentException("format");}
            if(result.size()>MAX_POINTS) throw new IllegalArgumentException("count");
        }
        return result;
    }
}
