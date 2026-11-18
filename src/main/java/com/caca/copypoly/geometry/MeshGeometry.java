package com.caca.copypoly.geometry;

import java.util.*;
import static com.caca.copypoly.geometry.Polyhedron.*;

/** Closed triangle surfaces, with no dependency on game/client/native classes. */
final class MeshGeometry {
    private static final double EPS=1e-8;
    private record V(double x,double y,double z) {
        V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        V mul(double n){return new V(x*n,y*n,z*n);}
        double dot(V b){return x*b.x+y*b.y+z*b.z;}
        V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        double length(){return Math.sqrt(dot(this));}
        double axis(int i){return i==0?x:i==1?y:z;}
    }
    private static V v(Point p){return new V(p.x(),p.y(),p.z());}
    private static long edge(int a,int b){return (long)Math.min(a,b)<<32|Math.max(a,b)&0xffffffffL;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("mesh");}

    static List<Triangle> triangulateHull(Polyhedron hull,List<Point> ordered) {
        List<Triangle> result=new ArrayList<>();
        for(var face:hull.faces())for(int i=1;i<face.vertices().size()-1;i++)
            result.add(new Triangle(ordered.indexOf(face.vertices().getFirst()),ordered.indexOf(face.vertices().get(i)),ordered.indexOf(face.vertices().get(i+1))));
        return result;
    }
    static double signedVolume(List<Point> points,List<Triangle> triangles) {
        double volume=0;
        for(var t:triangles)volume+=v(points.get(t.a())).dot(v(points.get(t.b())).cross(v(points.get(t.c()))))/6;
        return volume;
    }
    static List<Face> surfaceFaces(List<Point> points,List<Triangle> triangles) {
        if(points.size()>MAX_POINTS || triangles.size()>MAX_TRIANGLES)throw invalid();
        List<Face> faces=new ArrayList<>();Set<List<Integer>> unique=new HashSet<>();
        for(var t:triangles) {
            for(int id:t.indices())if(id<0 || id>=points.size())throw invalid();
            if(!unique.add(Arrays.stream(t.indices()).sorted().boxed().toList()))throw invalid();
            var a=points.get(t.a());var b=points.get(t.b());var c=points.get(t.c());
            faces.add(new Face(Polyhedron.trianglePlane(a,b,c),List.of(a,b,c)));
        }
        return List.copyOf(faces);
    }
    private static Map<Long,List<Integer>> incidents(List<Triangle> triangles) {
        Map<Long,List<Integer>> edges=new HashMap<>();
        for(int i=0;i<triangles.size();i++){int[] p=triangles.get(i).indices();for(int j=0;j<3;j++)edges.computeIfAbsent(edge(p[j],p[(j+1)%3]),k->new ArrayList<>()).add(i);}
        return edges;
    }
    private static List<List<Integer>> components(List<Triangle> triangles,Map<Long,List<Integer>> edges) {
        Set<Integer> seen=new HashSet<>();List<List<Integer>> result=new ArrayList<>();
        for(int start=0;start<triangles.size();start++)if(!seen.contains(start)) {
            List<Integer> component=new ArrayList<>();var queue=new ArrayDeque<Integer>();queue.add(start);
            while(!queue.isEmpty()) {
                int i=queue.remove();if(!seen.add(i))continue;component.add(i);int[] p=triangles.get(i).indices();
                for(int j=0;j<3;j++)queue.addAll(edges.get(edge(p[j],p[(j+1)%3])));
            }
            result.add(component);
        }
        return result;
    }
    private static int direction(Triangle t,long e) {
        int[] p=t.indices();for(int i=0;i<3;i++)if(edge(p[i],p[(i+1)%3])==e)return p[i]<p[(i+1)%3]?1:-1;throw invalid();
    }
    static List<Triangle> orient(List<Point> points,List<Triangle> input) {
        surfaceFaces(points,input);var edges=incidents(input);
        if(input.size()<4 || edges.values().stream().anyMatch(l->l.size()!=2))throw invalid();
        List<Triangle> result=new ArrayList<>(input);Map<Integer,Boolean> flipped=new HashMap<>();
        var shells=components(input,edges);
        for(var shell:shells) {
            var queue=new ArrayDeque<Integer>();queue.add(shell.getFirst());flipped.put(shell.getFirst(),false);
            while(!queue.isEmpty()) {
                int i=queue.remove();int[] p=input.get(i).indices();
                for(int j=0;j<3;j++) {
                    long e=edge(p[j],p[(j+1)%3]);
                    for(int other:edges.get(e))if(other!=i) {
                        boolean flip=flipped.get(i)^(direction(input.get(i),e)==direction(input.get(other),e));
                        if(flipped.containsKey(other)){if(flipped.get(other)!=flip)throw invalid();}
                        else{flipped.put(other,flip);queue.add(other);}
                    }
                }
            }
            for(int i:shell)if(flipped.get(i))result.set(i,result.get(i).reversed());
            if(signedVolume(points,shell.stream().map(result::get).toList())<0)for(int i:shell)result.set(i,result.get(i).reversed());
        }
        // Nested closed shells describe cavities; an island inside a cavity is solid again.
        List<Face> faces=surfaceFaces(points,result);
        for(var shell:shells)if(nestingDepth(points,result,faces,shell,shells)%2==1)for(int i:shell)result.set(i,result.get(i).reversed());
        return List.copyOf(result);
    }
    private static int nestingDepth(List<Point> points,List<Triangle> triangles,List<Face> faces,List<Integer> shell,List<List<Integer>> shells) {
        var t=triangles.get(shell.getFirst());V sample=v(points.get(t.a())).add(v(points.get(t.b()))).add(v(points.get(t.c()))).mul(1/3.0);
        int depth=0;for(var other:shells)if(other!=shell && contains(other.stream().map(faces::get).toList(),sample.x,sample.y,sample.z))depth++;return depth;
    }
    static List<Triangle> polygon(List<Point> points,List<Integer> boundary) {
        if(boundary.size()<3 || new HashSet<>(boundary).size()!=boundary.size())throw new IllegalArgumentException("face_polygon");
        for(int id:boundary)if(id<0 || id>=points.size())throw invalid();
        List<Integer> ring=new ArrayList<>(boundary);V normal=new V(0,0,0);
        for(int i=0;i<ring.size();i++)normal=normal.add(v(points.get(ring.get(i))).cross(v(points.get(ring.get((i+1)%ring.size())))));
        if(normal.length()<EPS)throw new IllegalArgumentException("face_polygon");
        V first=v(points.get(ring.getFirst()));
        for(int id:ring)if(Math.abs(v(points.get(id)).sub(first).dot(normal))>EPS*normal.length())throw new IllegalArgumentException("face_nonplanar");
        int drop=dominant(normal);double sign=0;for(int i=0;i<ring.size();i++)sign+=cross2(v(points.get(ring.get(i))),v(points.get(ring.get((i+1)%ring.size()))),drop);sign=Math.signum(sign);
        List<Triangle> result=new ArrayList<>();
        while(ring.size()>3) {
            boolean clipped=false;
            for(int i=0;i<ring.size();i++) {
                int a=ring.get((i+ring.size()-1)%ring.size()),b=ring.get(i),c=ring.get((i+1)%ring.size());
                V av=v(points.get(a)),bv=v(points.get(b)),cv=v(points.get(c));
                if(sign*cross2(bv.sub(av),cv.sub(av),drop)<=EPS)continue;
                boolean inside=false;
                for(int other:ring)if(other!=a && other!=b && other!=c) {
                    V p=v(points.get(other));
                    if(sign*cross2(bv.sub(av),p.sub(av),drop)>=-EPS && sign*cross2(cv.sub(bv),p.sub(bv),drop)>=-EPS && sign*cross2(av.sub(cv),p.sub(cv),drop)>=-EPS){inside=true;break;}
                }
                if(!inside){result.add(new Triangle(a,b,c));ring.remove(i);clipped=true;break;}
            }
            if(!clipped)throw new IllegalArgumentException("face_polygon");
        }
        result.add(new Triangle(ring.get(0),ring.get(1),ring.get(2)));
        // Reject bow-ties and overlapping ears rather than silently constructing a different polygon.
        surfaceFaces(points,result);
        for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++)if(crosses(points,result.get(i),result.get(j)))throw new IllegalArgumentException("face_polygon");
        return List.copyOf(result);
    }
    static List<Face> validate(List<Point> points,List<Triangle> triangles) {
        if(points.size()<4 || points.size()>MAX_POINTS || triangles.size()<4 || triangles.size()>MAX_TRIANGLES)throw invalid();
        Map<Long,List<Integer>> incidents=new HashMap<>();Map<Long,Integer> directions=new HashMap<>();
        Set<List<Integer>> unique=new HashSet<>();Set<Integer> used=new HashSet<>();List<Face> faces=new ArrayList<>();
        for(int i=0;i<triangles.size();i++) {
            var t=triangles.get(i);int[] ids=t.indices();
            for(int id:ids)if(id<0 || id>=points.size())throw invalid();
            if(t.a()==t.b() || t.b()==t.c() || t.a()==t.c())throw invalid();
            var sorted=Arrays.stream(ids).sorted().boxed().toList();if(!unique.add(sorted))throw invalid();
            var a=points.get(t.a());var b=points.get(t.b());var c=points.get(t.c());
            faces.add(new Face(Polyhedron.trianglePlane(a,b,c),List.of(a,b,c)));
            for(int j=0;j<3;j++) {
                int from=ids[j],to=ids[(j+1)%3];long key=edge(from,to);
                incidents.computeIfAbsent(key,k->new ArrayList<>()).add(i);
                directions.merge(key,from<to?1:-1,Integer::sum);used.add(from);
            }
        }
        if(incidents.values().stream().anyMatch(l->l.size()!=2) || directions.values().stream().anyMatch(n->n!=0))throw invalid();
        if(used.stream().map(points::get).distinct().count()!=used.size())throw invalid();
        var shells=components(triangles,incidents);
        for(var shell:shells) {
            double volume=signedVolume(points,shell.stream().map(triangles::get).toList());
            int depth=nestingDepth(points,triangles,faces,shell,shells);
            if((depth%2==0?volume:-volume)<=EPS)throw invalid();
        }
        // Each vertex must have one circular link, not two surfaces pinched together.
        for(int vertex:used) {
            Map<Integer,Set<Integer>> link=new HashMap<>();
            for(var t:triangles)if(t.uses(vertex)) {
                int[] other=Arrays.stream(t.indices()).filter(id->id!=vertex).toArray();
                link.computeIfAbsent(other[0],k->new HashSet<>()).add(other[1]);
                link.computeIfAbsent(other[1],k->new HashSet<>()).add(other[0]);
            }
            if(link.values().stream().anyMatch(l->l.size()!=2))throw invalid();
            Set<Integer> reached=new HashSet<>();var pending=new ArrayDeque<Integer>();pending.add(link.keySet().iterator().next());
            while(!pending.isEmpty()){int id=pending.remove();if(reached.add(id))pending.addAll(link.get(id));}
            if(reached.size()!=link.size())throw invalid();
        }
        for(int a=0;a<triangles.size();a++)for(int b=a+1;b<triangles.size();b++)
            if(crosses(points,triangles.get(a),triangles.get(b)))throw invalid();
        return List.copyOf(faces);
    }

    private static boolean inTriangle(V p,V a,V b,V c) {
        V n=b.sub(a).cross(c.sub(a));double length=n.length();
        if(length<EPS || Math.abs(p.sub(a).dot(n))>EPS*length)return false;
        double tolerance=-EPS*length;
        return b.sub(a).cross(p.sub(a)).dot(n)>=tolerance && c.sub(b).cross(p.sub(b)).dot(n)>=tolerance && a.sub(c).cross(p.sub(c)).dot(n)>=tolerance;
    }
    private static boolean allowed(V p,List<V> shared) {
        for(V vertex:shared)if(p.sub(vertex).length()<EPS)return true;
        if(shared.size()==2) {
            V a=shared.getFirst(),d=shared.getLast().sub(a),r=p.sub(a);
            return d.cross(r).length()<EPS*d.length() && r.dot(d)>=-EPS && r.dot(d)<=d.dot(d)+EPS;
        }
        return false;
    }
    private static boolean badSegment(V a,V b,V[] triangle,List<V> shared) {
        V p=triangle[0],q=triangle[1],r=triangle[2],normal=q.sub(p).cross(r.sub(p));
        double da=a.sub(p).dot(normal),db=b.sub(p).dot(normal),tol=EPS*normal.length();
        if(da>tol && db>tol || da<-tol && db<-tol)return false;
        if(Math.abs(da)<=tol && Math.abs(db)<=tol) {
            // A segment in the triangle's plane: test its ends and intersections
            // with all triangle edges, including the interior of a collinear overlap.
            List<V> probes=new ArrayList<>(List.of(a,b,a.add(b).mul(.5)));
            int axis=dominant(normal);
            for(int i=0;i<3;i++) {
                V c=triangle[i],d=triangle[(i+1)%3];
                V ab=b.sub(a),cd=d.sub(c),ac=c.sub(a);
                double det=cross2(ab,cd,axis);
                if(Math.abs(det)>EPS) {
                    double t=cross2(ac,cd,axis)/det,u=cross2(ac,ab,axis)/det;
                    if(t>=-EPS && t<=1+EPS && u>=-EPS && u<=1+EPS)probes.add(a.add(ab.mul(t)));
                } else {
                    probes.add(c);probes.add(d);
                    double length=ab.dot(ab);
                    if(length>EPS)for(V endpoint:new V[]{c,d}) {
                        double t=endpoint.sub(a).dot(ab)/length;
                        if(t>=0 && t<=1)probes.add(a.add(ab.mul(t)));
                    }
                }
            }
            V ab=b.sub(a);double length=ab.dot(ab);
            for(V hit:probes)if(inTriangle(hit,p,q,r) && ab.cross(hit.sub(a)).length()<EPS*Math.max(1,ab.length())
                    && hit.sub(a).dot(ab)>=-EPS && hit.sub(a).dot(ab)<=length+EPS && !allowed(hit,shared))return true;
            return false;
        }
        double t=da/(da-db);if(t<-EPS || t>1+EPS)return false;
        V hit=a.add(b.sub(a).mul(t));
        return inTriangle(hit,p,q,r) && !allowed(hit,shared);
    }
    private static int dominant(V v){return Math.abs(v.x)>=Math.abs(v.y) && Math.abs(v.x)>=Math.abs(v.z)?0:Math.abs(v.y)>=Math.abs(v.z)?1:2;}
    private static double cross2(V a,V b,int drop){return drop==0?a.y*b.z-a.z*b.y:drop==1?a.x*b.z-a.z*b.x:a.x*b.y-a.y*b.x;}
    private static boolean crosses(List<Point> points,Triangle one,Triangle two) {
        V[] a=Arrays.stream(one.indices()).mapToObj(i->v(points.get(i))).toArray(V[]::new);
        V[] b=Arrays.stream(two.indices()).mapToObj(i->v(points.get(i))).toArray(V[]::new);
        for(int axis=0;axis<3;axis++) {
            final int k=axis;
            if(Arrays.stream(a).mapToDouble(p->p.axis(k)).max().orElseThrow()<Arrays.stream(b).mapToDouble(p->p.axis(k)).min().orElseThrow()-EPS
                    || Arrays.stream(b).mapToDouble(p->p.axis(k)).max().orElseThrow()<Arrays.stream(a).mapToDouble(p->p.axis(k)).min().orElseThrow()-EPS)return false;
        }
        List<V> shared=Arrays.stream(one.indices()).filter(two::uses).mapToObj(i->v(points.get(i))).toList();
        for(int i=0;i<3;i++)if(badSegment(a[i],a[(i+1)%3],b,shared) || badSegment(b[i],b[(i+1)%3],a,shared))return true;
        return false;
    }

    static boolean contains(List<Face> faces,double x,double y,double z) {
        V point=new V(x,y,z);double winding=0;
        for(var face:faces) {
            V a=v(face.vertices().get(0)),b=v(face.vertices().get(1)),c=v(face.vertices().get(2));
            if(inTriangle(point,a,b,c))return true;
            a=a.sub(point);b=b.sub(point);c=c.sub(point);
            double la=a.length(),lb=b.length(),lc=c.length();
            winding+=2*Math.atan2(a.dot(b.cross(c)),la*lb*lc+a.dot(b)*lc+b.dot(c)*la+c.dot(a)*lb);
        }
        return Math.abs(winding)>2*Math.PI;
    }
    static boolean intersectsCell(List<Face> faces,double x0,double y0,double z0,double x1,double y1,double z1) {
        V center=new V((x0+x1)/2,(y0+y1)/2,(z0+z1)/2);
        if(contains(faces,center.x,center.y,center.z))return true;
        V half=new V((x1-x0)/2-EPS,(y1-y0)/2-EPS,(z1-z0)/2-EPS);
        V[] axes={new V(1,0,0),new V(0,1,0),new V(0,0,1)};
        for(var face:faces) {
            V[] p=face.vertices().stream().map(MeshGeometry::v).map(v->v.sub(center)).toArray(V[]::new);
            List<V> tests=new ArrayList<>(List.of(axes));V[] edges={p[1].sub(p[0]),p[2].sub(p[1]),p[0].sub(p[2])};
            tests.add(edges[0].cross(edges[1]));for(V e:edges)for(V axis:axes)tests.add(e.cross(axis));
            boolean overlap=true;
            for(V axis:tests) {
                if(axis.dot(axis)<1e-20)continue;
                double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
                for(V vertex:p){double d=vertex.dot(axis);min=Math.min(min,d);max=Math.max(max,d);}
                double radius=Math.abs(axis.x)*half.x+Math.abs(axis.y)*half.y+Math.abs(axis.z)*half.z;
                if(min>radius || max<-radius){overlap=false;break;}
            }
            if(overlap)return true;
        }
        return false;
    }

    private record Closest(V point,double a,double b,double c) {}
    private static Closest closest(V p,V a,V b,V c) {
        // Voronoi regions of a triangle, retaining barycentrics for an edge split.
        V ab=b.sub(a),ac=c.sub(a),ap=p.sub(a);double d1=ab.dot(ap),d2=ac.dot(ap);
        if(d1<=0 && d2<=0)return new Closest(a,1,0,0);
        V bp=p.sub(b);double d3=ab.dot(bp),d4=ac.dot(bp);
        if(d3>=0 && d4<=d3)return new Closest(b,0,1,0);
        double vc=d1*d4-d3*d2;if(vc<=0 && d1>=0 && d3<=0){double t=d1/(d1-d3);return new Closest(a.add(ab.mul(t)),1-t,t,0);}
        V cp=p.sub(c);double d5=ab.dot(cp),d6=ac.dot(cp);
        if(d6>=0 && d5<=d6)return new Closest(c,0,0,1);
        double vb=d5*d2-d1*d6;if(vb<=0 && d2>=0 && d6<=0){double t=d2/(d2-d6);return new Closest(a.add(ac.mul(t)),1-t,0,t);}
        double va=d3*d6-d5*d4;if(va<=0 && d4-d3>=0 && d5-d6>=0){double t=(d4-d3)/((d4-d3)+(d5-d6));return new Closest(b.add(c.sub(b).mul(t)),0,1-t,t);}
        double inv=1/(va+vb+vc),v=vb*inv,w=vc*inv;return new Closest(a.add(ab.mul(v)).add(ac.mul(w)),1-v-w,v,w);
    }
    static List<Triangle> attachUnused(List<Point> points,List<Triangle> initial) {
        List<Triangle> faces=new ArrayList<>(initial);
        for(int index=0;index<points.size();index++) {
            final int vertex=index;
            if(faces.stream().anyMatch(t->t.uses(vertex)))continue;
            if(faces.stream().flatMapToInt(t->Arrays.stream(t.indices())).anyMatch(i->points.get(i).equals(points.get(vertex))))continue;
            int best=-1;double distance=Double.POSITIVE_INFINITY;Closest location=null;
            for(int i=0;i<faces.size();i++) {
                var t=faces.get(i);var hit=closest(v(points.get(index)),v(points.get(t.a())),v(points.get(t.b())),v(points.get(t.c())));
                double d=hit.point.sub(v(points.get(index))).length();
                if(d<distance-EPS){distance=d;best=i;location=hit;}
            }
            if(best<0)throw invalid();
            var selected=faces.get(best);int from=-1,to=-1;
            if(location.a<EPS){from=selected.b();to=selected.c();}
            else if(location.b<EPS){from=selected.c();to=selected.a();}
            else if(location.c<EPS){from=selected.a();to=selected.b();}
            if(from>=0) {
                List<Triangle> changed=new ArrayList<>();
                for(var t:faces) {
                    if(!t.uses(from) || !t.uses(to)){changed.add(t);continue;}
                    int[] ids=t.indices();
                    for(int j=0;j<3;j++)if(edge(ids[j],ids[(j+1)%3])==edge(from,to)) {
                        changed.add(new Triangle(ids[j],index,ids[(j+2)%3]));changed.add(new Triangle(index,ids[(j+1)%3],ids[(j+2)%3]));break;
                    }
                }
                faces=changed;
            } else {
                faces.remove(best);faces.add(new Triangle(selected.a(),selected.b(),index));faces.add(new Triangle(selected.b(),selected.c(),index));faces.add(new Triangle(selected.c(),selected.a(),index));
            }
        }
        return List.copyOf(faces);
    }
    static Polyhedron removeVertex(List<Point> vertices,List<Triangle> triangles,int index) {
        if(index<0 || index>=vertices.size())throw invalid();
        List<Triangle> result=new ArrayList<>();Map<Integer,Integer> next=new LinkedHashMap<>();
        for(var t:triangles) {
            if(!t.uses(index)){result.add(t);continue;}
            int[] ids=t.indices();for(int j=0;j<3;j++)if(ids[j]==index)next.put(ids[(j+1)%3],ids[(j+2)%3]);
        }
        if(!next.isEmpty()) {
            List<Integer> ring=new ArrayList<>();int start=next.keySet().iterator().next(),current=start;
            do{if(ring.contains(current) || !next.containsKey(current))throw invalid();ring.add(current);current=next.get(current);}while(current!=start);
            if(ring.size()!=next.size())throw invalid();
            // Ear clipping on the dominant projection of the hole boundary.
            V normal=new V(0,0,0);for(int i=0;i<ring.size();i++)normal=normal.add(v(vertices.get(ring.get(i))).cross(v(vertices.get(ring.get((i+1)%ring.size())))));
            int drop=dominant(normal);double sign=0;
            for(int i=0;i<ring.size();i++)sign+=cross2(v(vertices.get(ring.get(i))),v(vertices.get(ring.get((i+1)%ring.size()))),drop);
            sign=Math.signum(sign);if(sign==0)throw invalid();
            while(ring.size()>3) {
                boolean clipped=false;
                for(int i=0;i<ring.size();i++) {
                    int a=ring.get((i+ring.size()-1)%ring.size()),b=ring.get(i),c=ring.get((i+1)%ring.size());
                    V av=v(vertices.get(a)),bv=v(vertices.get(b)),cv=v(vertices.get(c));
                    if(sign*cross2(bv.sub(av),cv.sub(av),drop)<=EPS)continue;
                    boolean inside=false;
                    for(int other:ring)if(other!=a && other!=b && other!=c) {
                        V p=v(vertices.get(other));
                        if(sign*cross2(bv.sub(av),p.sub(av),drop)>=-EPS && sign*cross2(cv.sub(bv),p.sub(bv),drop)>=-EPS && sign*cross2(av.sub(cv),p.sub(cv),drop)>=-EPS){inside=true;break;}
                    }
                    if(!inside){result.add(new Triangle(a,b,c));ring.remove(i);clipped=true;break;}
                }
                if(!clipped)throw invalid();
            }
            result.add(new Triangle(ring.get(0),ring.get(1),ring.get(2)));
        }
        List<Point> points=new ArrayList<>(vertices);points.remove(index);
        return Polyhedron.mesh(points,result.stream().map(t->new Triangle(t.a()>index?t.a()-1:t.a(),t.b()>index?t.b()-1:t.b(),t.c()>index?t.c()-1:t.c())).toList());
    }
}
