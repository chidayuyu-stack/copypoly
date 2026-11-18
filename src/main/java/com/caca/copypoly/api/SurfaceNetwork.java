package com.caca.copypoly.api;

import com.caca.copypoly.geometry.Polyhedron;
import java.util.*;

/** Undirected cuts in the reference surface. Coordinates may later deform freely. */
final class SurfaceNetwork {
    record Result(List<Polyhedron.Triangle> triangles,Set<Integer> active,Map<Integer,PolyShape.PendingReason> pending,Set<PolyShape.Connection> pendingLinks,Set<Long> cutSegments) {}
    private record P(double x,double y) {}
    private record Plane(long x,long y,long z,long d) {
        int drop(){return Math.abs(x)>=Math.abs(y)&&Math.abs(x)>=Math.abs(z)?0:Math.abs(y)>=Math.abs(z)?1:2;}
        double distance(Polyhedron.Point p){return Math.abs(x*p.tx()+y*p.ty()+z*p.tz()-d)/Math.sqrt(x*x+y*y+z*z);}
    }
    private record Region(Plane plane,List<Polyhedron.Triangle> faces,Set<Integer> used) {}
    private record Group(Set<Integer> free,Set<Integer> anchors,List<PolyShape.Connection> links) {}
    private static long edge(int a,int b){return (long)Math.min(a,b)<<32|Math.max(a,b)&0xffffffffL;}
    static PolyShape.Connection connection(int a,int b){return new PolyShape.Connection(Math.min(a,b),Math.max(a,b));}
    private static long gcd(long a,long b){while(b!=0){long t=a%b;a=b;b=t;}return a;}
    private static Plane plane(Polyhedron.Point a,Polyhedron.Point b,Polyhedron.Point c) {
        long ux=b.tx()-a.tx(),uy=b.ty()-a.ty(),uz=b.tz()-a.tz(),vx=c.tx()-a.tx(),vy=c.ty()-a.ty(),vz=c.tz()-a.tz();
        long x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,g=gcd(gcd(Math.abs(x),Math.abs(y)),Math.abs(z));
        if(g==0)throw new IllegalArgumentException("mesh");x/=g;y/=g;z/=g;
        if(x<0 || x==0&&y<0 || x==0&&y==0&&z<0){x=-x;y=-y;z=-z;}
        return new Plane(x,y,z,x*a.tx()+y*a.ty()+z*a.tz());
    }
    private static P project(Polyhedron.Point p,int drop){return drop==0?new P(p.ty(),p.tz()):drop==1?new P(p.tx(),p.tz()):new P(p.tx(),p.ty());}
    private static double cross(P a,P b,P c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean on(P p,P a,P b){return Math.abs(cross(a,b,p))<1e-7 && p.x>=Math.min(a.x,b.x)-1e-7 && p.x<=Math.max(a.x,b.x)+1e-7 && p.y>=Math.min(a.y,b.y)-1e-7 && p.y<=Math.max(a.y,b.y)+1e-7;}
    private static boolean inside(P p,P a,P b,P c){double x=cross(a,b,p),y=cross(b,c,p),z=cross(c,a,p);return x>=-1e-7&&y>=-1e-7&&z>=-1e-7 || x<=1e-7&&y<=1e-7&&z<=1e-7;}
    private static boolean crosses(P a,P b,P c,P d){return cross(a,b,c)*cross(a,b,d)<-1e-7 && cross(c,d,a)*cross(c,d,b)<-1e-7;}
    private static boolean contains(Region region,P p,Map<Integer,P> uv){return region.faces.stream().anyMatch(t->inside(p,uv.get(t.a()),uv.get(t.b()),uv.get(t.c())));}
    private static List<Region> regions(List<Polyhedron.Triangle> faces,Map<Integer,Polyhedron.Point> rest) {
        Map<Long,List<Integer>> neighbors=new HashMap<>();List<Plane> planes=new ArrayList<>();
        for(int i=0;i<faces.size();i++){var t=faces.get(i);planes.add(plane(rest.get(t.a()),rest.get(t.b()),rest.get(t.c())));int[] p=t.indices();for(int j=0;j<3;j++)neighbors.computeIfAbsent(edge(p[j],p[(j+1)%3]),k->new ArrayList<>()).add(i);}
        Set<Integer> seen=new HashSet<>();List<Region> result=new ArrayList<>();
        for(int start=0;start<faces.size();start++)if(!seen.contains(start)) {
            var queue=new ArrayDeque<Integer>();queue.add(start);List<Polyhedron.Triangle> patch=new ArrayList<>();Set<Integer> used=new LinkedHashSet<>();
            while(!queue.isEmpty()) {
                int i=queue.remove();if(!planes.get(i).equals(planes.get(start)) || !seen.add(i))continue;
                var t=faces.get(i);patch.add(t);int[] p=t.indices();for(int j=0;j<3;j++){used.add(p[j]);queue.addAll(neighbors.get(edge(p[j],p[(j+1)%3])));}
            }
            result.add(new Region(planes.get(start),List.copyOf(patch),Set.copyOf(used)));
        }
        return result;
    }
    static Result compile(List<PolyShape.Vertex> vertices,List<Polyhedron.Triangle> base,Map<Integer,Polyhedron.Point> rest,List<PolyShape.Connection> connections) {
        Set<Integer> pins=new HashSet<>();for(var t:base)for(int id:t.indices())pins.add(id);
        Map<Integer,Set<Integer>> graph=new LinkedHashMap<>();for(var c:connections){graph.computeIfAbsent(c.source(),k->new LinkedHashSet<>()).add(c.target());graph.computeIfAbsent(c.target(),k->new LinkedHashSet<>()).add(c.source());}
        // A half-finished branch cannot partition a surface. Keep it as a visible draft line.
        boolean pruned=true;while(pruned){pruned=false;for(int id:new ArrayList<>(graph.keySet()))if(!pins.contains(id)&&graph.get(id).size()<2){for(int target:graph.remove(id))graph.get(target).remove(id);pruned=true;}}
        List<Group> groups=new ArrayList<>();Set<Integer> visited=new HashSet<>();
        for(int id:graph.keySet())if(!pins.contains(id)&&visited.add(id)) {
            Set<Integer> free=new LinkedHashSet<>(),anchors=new LinkedHashSet<>();var queue=new ArrayDeque<Integer>();queue.add(id);
            while(!queue.isEmpty()) {int p=queue.remove();free.add(p);for(int target:graph.get(p))if(pins.contains(target))anchors.add(target);else if(visited.add(target))queue.add(target);}
            groups.add(new Group(free,anchors,connections.stream().filter(c->free.contains(c.source())||free.contains(c.target())).filter(c->graph.containsKey(c.source())&&graph.get(c.source()).contains(c.target())).toList()));
        }
        for(var c:connections)if(pins.contains(c.source())&&pins.contains(c.target()))groups.add(new Group(Set.of(),Set.of(c.source(),c.target()),List.of(c)));
        List<Region> regions=regions(base,rest);Map<Region,List<PolyShape.Connection>> assigned=new LinkedHashMap<>();
        Map<Region,Map<P,Integer>> occupied=new HashMap<>();
        Map<Integer,PolyShape.PendingReason> pending=new LinkedHashMap<>();Set<PolyShape.Connection> pendingLinks=new LinkedHashSet<>(connections);
        for(var v:vertices)if(!pins.contains(v.id()))pending.put(v.id(),PolyShape.PendingReason.NEEDS_CONNECTIONS);
        for(var group:groups) {
            Region best=null;double score=Double.POSITIVE_INFINITY;
            for(var region:regions) {
                if(!region.used.containsAll(group.anchors))continue;
                Map<Integer,P> uv=new HashMap<>();rest.forEach((id,p)->uv.put(id,project(p,region.plane.drop())));
                if(group.free.stream().anyMatch(id->!contains(region,uv.get(id),uv)))continue;
                double distance=group.free.stream().mapToDouble(id->region.plane.distance(rest.get(id))).sum();
                if(distance<score){score=distance;best=region;}
            }
            if(best==null){for(int id:group.free)pending.put(id,PolyShape.PendingReason.INVALID_BOUNDARY);continue;}
            var region=best;Map<P,Integer> taken=occupied.computeIfAbsent(region,k->{Map<P,Integer> p=new HashMap<>();for(int id:region.used)p.put(project(rest.get(id),region.plane.drop()),id);return p;});
            Set<P> unique=new HashSet<>();boolean duplicate=false;
            for(int id:group.free){P p=project(rest.get(id),region.plane.drop());if(!unique.add(p) || taken.containsKey(p))duplicate=true;}
            if(duplicate){for(int id:group.free)pending.put(id,PolyShape.PendingReason.INVALID_BOUNDARY);continue;}
            for(int id:group.free)taken.put(project(rest.get(id),region.plane.drop()),id);
            assigned.computeIfAbsent(best,k->new ArrayList<>()).addAll(group.links);
        }
        List<Polyhedron.Triangle> result=new ArrayList<>();Set<Integer> active=new LinkedHashSet<>(pins);Set<Long> cutSegments=new LinkedHashSet<>();
        for(var region:regions) {
            var links=assigned.getOrDefault(region,List.of());if(links.isEmpty()){result.addAll(region.faces);continue;}
            Map<Integer,P> uv=new HashMap<>();rest.forEach((id,p)->uv.put(id,project(p,region.plane.drop())));
            try {
                Set<Integer> inserted=new TreeSet<>();for(var c:links){inserted.add(c.source());inserted.add(c.target());}inserted.removeAll(region.used);
                List<Polyhedron.Triangle> mesh=new ArrayList<>(region.faces);
                for(int id:inserted)insert(mesh,id,uv);
                Set<Long> locked=new HashSet<>();Map<Long,Integer> counts=counts(region.faces);
                counts.forEach((e,n)->{if(n==1)locked.add(e);});
                // Split a long drawn edge at existing vertices that lie on it.
                List<int[]> constraints=new ArrayList<>();Set<Integer> used=new LinkedHashSet<>(region.used);used.addAll(inserted);
                for(var c:links) {
                    P a=uv.get(c.source()),b=uv.get(c.target());
                    var chain=used.stream().filter(id->on(uv.get(id),a,b)).sorted(Comparator.comparingDouble(id->Math.pow(uv.get(id).x-a.x,2)+Math.pow(uv.get(id).y-a.y,2))).toList();
                    for(int i=1;i<chain.size();i++)constraints.add(new int[]{chain.get(i-1),chain.get(i)});
                }
                constraints.sort(Comparator.comparingLong(c->edge(c[0],c[1])));
                for(var c:constraints)recover(mesh,c[0],c[1],uv,locked);
                for(var c:constraints)cutSegments.add(edge(c[0],c[1]));
                result.addAll(mesh);active.addAll(inserted);for(int id:inserted)pending.remove(id);pendingLinks.removeAll(links);
            }catch(IllegalArgumentException invalid){result.addAll(region.faces);for(var c:links)for(int id:new int[]{c.source(),c.target()})if(!pins.contains(id))pending.put(id,PolyShape.PendingReason.INVALID_BOUNDARY);}
        }
        // A point on a boundary edge belongs to both neighboring surface patches.
        // Split the other patch too, avoiding a T-junction when the point moves.
        for(int id:active)if(!pins.contains(id)) {
            var p=rest.get(id);List<Polyhedron.Triangle> joined=new ArrayList<>();
            for(var t:result) {
                int[] v=t.indices();boolean split=false;
                if(!t.uses(id))for(int i=0;i<3;i++)if(on3(p,rest.get(v[i]),rest.get(v[(i+1)%3]))) {
                    if(cutSegments.remove(edge(v[i],v[(i+1)%3]))){cutSegments.add(edge(v[i],id));cutSegments.add(edge(id,v[(i+1)%3]));}
                    joined.add(new Polyhedron.Triangle(v[i],id,v[(i+2)%3]));joined.add(new Polyhedron.Triangle(id,v[(i+1)%3],v[(i+2)%3]));split=true;break;
                }
                if(!split)joined.add(t);
            }
            result=joined;
        }
        return new Result(List.copyOf(result),Set.copyOf(active),Map.copyOf(pending),Set.copyOf(pendingLinks),Set.copyOf(cutSegments));
    }
    private static boolean on3(Polyhedron.Point p,Polyhedron.Point a,Polyhedron.Point b) {
        double x=p.tx()-a.tx(),y=p.ty()-a.ty(),z=p.tz()-a.tz(),u=b.tx()-a.tx(),v=b.ty()-a.ty(),w=b.tz()-a.tz();
        double dot=x*u+y*v+z*w,length=u*u+v*v+w*w;
        return Math.abs(y*w-z*v)+Math.abs(z*u-x*w)+Math.abs(x*v-y*u)<1e-7 && dot>1e-7 && dot<length-1e-7;
    }
    private static Map<Long,Integer> counts(List<Polyhedron.Triangle> mesh){Map<Long,Integer> result=new HashMap<>();for(var t:mesh){int[] p=t.indices();for(int i=0;i<3;i++)result.merge(edge(p[i],p[(i+1)%3]),1,Integer::sum);}return result;}
    private static Polyhedron.Triangle triangle(int a,int b,int c,Map<Integer,P> uv){double cross=cross(uv.get(a),uv.get(b),uv.get(c));if(Math.abs(cross)<1e-7)throw new IllegalArgumentException("line_boundary");return cross>0?new Polyhedron.Triangle(a,b,c):new Polyhedron.Triangle(a,c,b);}
    private static void insert(List<Polyhedron.Triangle> mesh,int id,Map<Integer,P> uv) {
        P p=uv.get(id);for(var t:mesh)for(int used:t.indices())if(uv.get(used).equals(p))throw new IllegalArgumentException("line_boundary");
        List<Polyhedron.Triangle> result=new ArrayList<>();boolean found=false;
        for(var t:mesh) {
            int[] v=t.indices();boolean split=false;
            for(int i=0;i<3;i++)if(on(p,uv.get(v[i]),uv.get(v[(i+1)%3]))) {
                result.add(triangle(v[i],id,v[(i+2)%3],uv));result.add(triangle(id,v[(i+1)%3],v[(i+2)%3],uv));split=true;found=true;break;
            }
            if(split)continue;
            if(!found && inside(p,uv.get(t.a()),uv.get(t.b()),uv.get(t.c()))) {
                result.add(triangle(t.a(),t.b(),id,uv));result.add(triangle(t.b(),t.c(),id,uv));result.add(triangle(t.c(),t.a(),id,uv));found=true;
            }else result.add(t);
        }
        if(!found)throw new IllegalArgumentException("line_boundary");mesh.clear();mesh.addAll(result);
    }
    private static void recover(List<Polyhedron.Triangle> mesh,int a,int b,Map<Integer,P> uv,Set<Long> locked) {
        long target=edge(a,b);int budget=mesh.size()*mesh.size()+16;Set<List<Polyhedron.Triangle>> states=new HashSet<>();
        while(!counts(mesh).containsKey(target)) {
            if(--budget<0 || !states.add(List.copyOf(mesh)))throw new IllegalArgumentException("line_boundary");
            boolean flipped=false;Map<Long,List<Integer>> adjacent=new LinkedHashMap<>();
            for(int i=0;i<mesh.size();i++){int[] p=mesh.get(i).indices();for(int j=0;j<3;j++)adjacent.computeIfAbsent(edge(p[j],p[(j+1)%3]),k->new ArrayList<>()).add(i);}
            for(var entry:adjacent.entrySet()) {
                long e=entry.getKey();int u=(int)(e>>32),v=(int)e;
                if(u==a||u==b||v==a||v==b || !crosses(uv.get(a),uv.get(b),uv.get(u),uv.get(v)))continue;
                if(locked.contains(e))throw new IllegalArgumentException("line_crossing");
                if(entry.getValue().size()!=2)continue;
                int i=entry.getValue().get(0),j=entry.getValue().get(1);
                int x=Arrays.stream(mesh.get(i).indices()).filter(p->p!=u&&p!=v).findFirst().orElseThrow(),y=Arrays.stream(mesh.get(j).indices()).filter(p->p!=u&&p!=v).findFirst().orElseThrow();
                if(!crosses(uv.get(u),uv.get(v),uv.get(x),uv.get(y)))continue;
                // Prefer a flip which removes an intersection with the requested edge.
                if(crosses(uv.get(a),uv.get(b),uv.get(x),uv.get(y)))continue;
                mesh.set(i,triangle(x,y,u,uv));mesh.set(j,triangle(y,x,v,uv));flipped=true;break;
            }
            if(!flipped)throw new IllegalArgumentException("line_boundary");
        }
        locked.add(target);
    }
    private SurfaceNetwork(){}
}
