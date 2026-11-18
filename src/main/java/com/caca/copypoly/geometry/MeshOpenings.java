package com.caca.copypoly.geometry;

import java.util.*;
import static com.caca.copypoly.geometry.Polyhedron.*;

/** Boundary diagnostics and display-only caps. A closed ring has no boundary edges. */
public final class MeshOpenings {
    private record P(double x,double y) {}
    private static long edge(int a,int b){return (long)Math.min(a,b)<<32|Math.max(a,b)&0xffffffffL;}
    public static List<List<Integer>> boundaryLoops(List<Triangle> faces) {
        Map<Long,Integer> counts=new LinkedHashMap<>();for(var t:faces){int[] p=t.indices();for(int i=0;i<3;i++)counts.merge(edge(p[i],p[(i+1)%3]),1,Integer::sum);}
        Map<Integer,List<Integer>> graph=new LinkedHashMap<>();Set<Long> unseen=new LinkedHashSet<>();
        counts.forEach((e,n)->{if(n==1){int a=(int)(e>>32),b=(int)(long)e;graph.computeIfAbsent(a,k->new ArrayList<>()).add(b);graph.computeIfAbsent(b,k->new ArrayList<>()).add(a);unseen.add(e);}});
        List<List<Integer>> result=new ArrayList<>();
        while(!unseen.isEmpty()) {
            long first=unseen.iterator().next();int start=(int)(first>>32),previous=start,current=(int)first;
            List<Integer> loop=new ArrayList<>();loop.add(start);unseen.remove(first);boolean complete=true;
            while(current!=start) {
                if(loop.contains(current) || graph.get(current).size()!=2){complete=false;break;}loop.add(current);
                int prev=previous;var next=graph.get(current).stream().filter(id->id!=prev).findFirst();
                if(next.isEmpty() || !unseen.remove(edge(current,next.get()))){complete=false;break;}previous=current;current=next.get();
            }
            if(complete && loop.size()>=3)result.add(List.copyOf(loop));
        }
        return List.copyOf(result);
    }
    private static int drop(List<Point> points,List<Integer> loop) {
        double x=0,y=0,z=0;for(int i=0;i<loop.size();i++){Point a=points.get(loop.get(i)),b=points.get(loop.get((i+1)%loop.size()));x+=(a.y()-b.y())*(a.z()+b.z());y+=(a.z()-b.z())*(a.x()+b.x());z+=(a.x()-b.x())*(a.y()+b.y());}
        return Math.abs(x)>=Math.abs(y)&&Math.abs(x)>=Math.abs(z)?0:Math.abs(y)>=Math.abs(z)?1:2;
    }
    private static P p(Point v,int drop){return drop==0?new P(v.y(),v.z()):drop==1?new P(v.x(),v.z()):new P(v.x(),v.y());}
    private static double cross(P a,P b,P c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static double area(List<Integer> loop,List<Point> points,int drop){double a=0;for(int i=0;i<loop.size();i++){P p=p(points.get(loop.get(i)),drop),q=p(points.get(loop.get((i+1)%loop.size())),drop);a+=p.x*q.y-p.y*q.x;}return a/2;}
    private static boolean inside(P point,List<Integer> loop,List<Point> points,int drop){boolean inside=false;for(int i=0,j=loop.size()-1;i<loop.size();j=i++){P a=p(points.get(loop.get(i)),drop),b=p(points.get(loop.get(j)),drop);if((a.y>point.y)!=(b.y>point.y) && point.x<(b.x-a.x)*(point.y-a.y)/(b.y-a.y)+a.x)inside=!inside;}return inside;}
    private static boolean coplanar(List<Point> points,List<Integer> a,List<Integer> b) {
        Point first=points.get(a.getFirst());double[] normal=null;
        for(int i=1;i<a.size()-1;i++){Point p=points.get(a.get(i)),q=points.get(a.get(i+1));double ux=p.x()-first.x(),uy=p.y()-first.y(),uz=p.z()-first.z(),vx=q.x()-first.x(),vy=q.y()-first.y(),vz=q.z()-first.z();double[] n={uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx};if(Math.abs(n[0])+Math.abs(n[1])+Math.abs(n[2])>1e-8){normal=n;break;}}
        if(normal==null)return false;double length=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);
        for(var loop:List.of(a,b))for(int id:loop){Point p=points.get(id);if(Math.abs(normal[0]*(p.x()-first.x())+normal[1]*(p.y()-first.y())+normal[2]*(p.z()-first.z()))>1e-7*length)return false;}return true;
    }
    /** Triangles which mark only actual openings, with nested coplanar loops treated as holes. */
    public static List<Triangle> previewCaps(List<Point> points,List<Triangle> faces) {
        var loops=boundaryLoops(faces);List<Triangle> result=new ArrayList<>();int[] parent=new int[loops.size()];Arrays.fill(parent,-1);
        for(int i=0;i<loops.size();i++) {
            double best=Double.POSITIVE_INFINITY;
            for(int j=0;j<loops.size();j++)if(i!=j && coplanar(points,loops.get(i),loops.get(j))) {
                int drop=drop(points,loops.get(j));double outer=Math.abs(area(loops.get(j),points,drop)),inner=Math.abs(area(loops.get(i),points,drop));
                if(outer>inner+1e-8 && outer<best && inside(p(points.get(loops.get(i).getFirst()),drop),loops.get(j),points,drop)){best=outer;parent[i]=j;}
            }
        }
        for(int i=0;i<loops.size();i++) {
            int depth=0;for(int j=parent[i];j>=0;j=parent[j])depth++;
            if(depth%2!=0)continue;int drop=drop(points,loops.get(i));List<Integer> outer=new ArrayList<>(loops.get(i));if(area(outer,points,drop)<0)Collections.reverse(outer);
            List<List<Integer>> holes=new ArrayList<>();for(int j=0;j<loops.size();j++)if(parent[j]==i){var hole=new ArrayList<>(loops.get(j));if(area(hole,points,drop)>0)Collections.reverse(hole);holes.add(hole);}
            try {
                for(var hole:holes)outer=bridge(outer,hole,holes,points,drop);
                result.addAll(ears(outer,points,drop));
            }catch(IllegalArgumentException unclear){/* Boundary edges still identify non-planar/branched openings. */}
        }
        return List.copyOf(result);
    }
    private static boolean crosses(P a,P b,P c,P d){return cross(a,b,c)*cross(a,b,d)<-1e-9 && cross(c,d,a)*cross(c,d,b)<-1e-9;}
    private static List<Integer> bridge(List<Integer> outer,List<Integer> hole,List<List<Integer>> holes,List<Point> points,int drop) {
        int hi=0;for(int i=1;i<hole.size();i++)if(p(points.get(hole.get(i)),drop).x>p(points.get(hole.get(hi)),drop).x)hi=i;
        P h=p(points.get(hole.get(hi)),drop);int oi=-1;double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<outer.size();i++) {
            P o=p(points.get(outer.get(i)),drop),mid=new P((h.x+o.x)/2,(h.y+o.y)/2);double distance=Math.pow(h.x-o.x,2)+Math.pow(h.y-o.y,2);
            if(distance>=best || !inside(mid,outer,points,drop) || holes.stream().anyMatch(loop->inside(mid,loop,points,drop)))continue;
            boolean blocked=false;List<List<Integer>> boundaries=new ArrayList<>(holes);boundaries.add(outer);
            for(var boundary:boundaries)for(int j=0;j<boundary.size();j++)if(crosses(h,o,p(points.get(boundary.get(j)),drop),p(points.get(boundary.get((j+1)%boundary.size())),drop)))blocked=true;
            if(!blocked){oi=i;best=distance;}
        }
        if(oi<0)throw new IllegalArgumentException("mesh");List<Integer> ring=new ArrayList<>(outer.subList(0,oi+1));
        for(int i=0;i<=hole.size();i++)ring.add(hole.get((hi+i)%hole.size()));ring.add(outer.get(oi));ring.addAll(outer.subList(oi+1,outer.size()));return ring;
    }
    private static List<Triangle> ears(List<Integer> source,List<Point> points,int drop) {
        List<Integer> ring=new ArrayList<>(source);List<Triangle> result=new ArrayList<>();
        while(ring.size()>3) {
            boolean clipped=false;
            for(int i=0;i<ring.size();i++) {
                int a=ring.get((i+ring.size()-1)%ring.size()),b=ring.get(i),c=ring.get((i+1)%ring.size());P av=p(points.get(a),drop),bv=p(points.get(b),drop),cv=p(points.get(c),drop);
                if(cross(av,bv,cv)<=1e-8)continue;boolean blocked=false;
                for(int id:ring)if(id!=a&&id!=b&&id!=c){P v=p(points.get(id),drop);if(cross(av,bv,v)>=-1e-8&&cross(bv,cv,v)>=-1e-8&&cross(cv,av,v)>=-1e-8){blocked=true;break;}}
                if(!blocked){result.add(new Triangle(a,b,c));ring.remove(i);clipped=true;break;}
            }
            if(!clipped)throw new IllegalArgumentException("mesh");
        }
        if(ring.size()==3 && cross(p(points.get(ring.get(0)),drop),p(points.get(ring.get(1)),drop),p(points.get(ring.get(2)),drop))>1e-8)result.add(new Triangle(ring.get(0),ring.get(1),ring.get(2)));
        return result;
    }
    /** Prefer an existing hole perimeter; otherwise sort planar vertices around their center. */
    public static List<Integer> orderFace(List<Point> points,List<Triangle> faces,Collection<Integer> selected) {
        var wanted=new LinkedHashSet<>(selected);if(wanted.size()<3)throw new IllegalArgumentException("face_polygon");
        for(var loop:boundaryLoops(faces))if(new HashSet<>(loop).equals(wanted))return loop;
        List<Integer> result=new ArrayList<>(wanted);if(result.size()==3)return result;
        final int drop=projectionAxis(points,result);
        double x=0,y=0;for(int id:result){P p=p(points.get(id),drop);x+=p.x;y+=p.y;}final double cx=x/result.size(),cy=y/result.size();
        result.sort(Comparator.comparingDouble(id->{P p=p(points.get(id),drop);return Math.atan2(p.y-cy,p.x-cx);}));return result;
    }
    private static int projectionAxis(List<Point> points,List<Integer> result) {
        double best=0;int axis=-1;
        for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++)for(int k=j+1;k<result.size();k++) {
            Point a=points.get(result.get(i)),b=points.get(result.get(j)),c=points.get(result.get(k));
            double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=nx*nx+ny*ny+nz*nz;
            if(length>best){best=length;axis=Math.abs(nx)>=Math.abs(ny)&&Math.abs(nx)>=Math.abs(nz)?0:Math.abs(ny)>=Math.abs(nz)?1:2;}
        }
        if(axis<0)throw new IllegalArgumentException("face_polygon");return axis;
    }
    /** A warped polygon is represented by triangles; a planar polygon remains coplanar. */
    public static List<Triangle> triangulateFace(List<Point> points,List<Triangle> surface,Collection<Integer> selected) {
        var order=new ArrayList<>(orderFace(points,surface,selected));int drop=projectionAxis(points,order);
        if(area(order,points,drop)<0)Collections.reverse(order);return List.copyOf(ears(order,points,drop));
    }
    private MeshOpenings(){}
}
