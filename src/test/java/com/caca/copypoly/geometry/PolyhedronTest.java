package com.caca.copypoly.geometry;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolyhedronTest {
    private Polyhedron hull(String text) {return Polyhedron.of(Polyhedron.parse(text));}
    @Test void cubeHasSixOutwardQuadsAndOneCollisionBox() {
        var h=Polyhedron.CUBE;
        assertEquals(6,h.faces().size());
        assertTrue(h.faces().stream().allMatch(f->f.vertices().size()==4));
        assertEquals(List.of(new Polyhedron.Box(0,0,0,16,16,16)),h.collisionBoxes());
        assertEquals(4096,volume(h),1e-8);
        assertTrue(h.contains(0,0,0));assertFalse(h.contains(17,8,8));
    }
    @Test void tetrahedronUsesFourFacesAndExactVolume() {
        var h=hull("0,0,0\n16,0,0\n0,16,0\n0,0,16");
        assertEquals(4,h.faces().size());assertEquals(4096./6,volume(h),1e-8);
        assertFalse(h.contains(10,10,10));assertTrue(h.contains(2,2,2));
    }
    @Test void rejectsFlatCollinearOutOfRangeAndOversizedInputs() {
        assertThrows(IllegalArgumentException.class,()->hull("0,0,0;16,0,0;0,0,16;16,0,16"));
        assertThrows(IllegalArgumentException.class,()->hull("0,0,0;1,1,1;2,2,2;3,3,3"));
        assertThrows(IllegalArgumentException.class,()->hull("17,0,0"));
        assertThrows(IllegalArgumentException.class,()->hull("0.55,0,0"));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromPacked(new int[65]));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(32768));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(-1));
    }
    @Test void duplicatesInteriorAndCoplanarPointsDoNotCreateExtraFaces() {
        List<Polyhedron.Point> points=new ArrayList<>(Polyhedron.CUBE_POINTS);
        points.add(new Polyhedron.Point(8,8,8));points.add(new Polyhedron.Point(8,0,8));points.add(new Polyhedron.Point(0,0,0));
        var h=Polyhedron.of(points);
        assertEquals(10,h.points().size());assertEquals(6,h.faces().size());
        assertTrue(h.faces().stream().allMatch(f->f.vertices().size()==4));assertEquals(4096,volume(h),1e-8);
        Collections.reverse(points);assertSame(h,Polyhedron.of(points));assertSame(h,Polyhedron.fromPacked(h.packed()));
    }
    @Test void collisionPreservesVeryThinShapes() {
        var h=hull("0,0,0;16,16,15;16,15,16;15,16,16");
        assertFalse(h.collisionBoxes().isEmpty());assertTrue(volume(h)>0);
        verifyCoverage(h,new Random(43));
    }
    @Test void randomHullsAreClosedOutwardAndHaveConservativeBoundedCollision() {
        Random random=new Random(0xCA7);
        for(int trial=0;trial<100;trial++) {
            List<Polyhedron.Point> pts=new ArrayList<>();
            for(int i=0;i<64;i++)pts.add(new Polyhedron.Point(random.nextInt(161)/10.0,random.nextInt(161)/10.0,random.nextInt(161)/10.0));
            var h=Polyhedron.of(pts);
            assertTrue(volume(h)>0);assertTrue(h.collisionBoxes().size()<=64);
            Map<String,Integer> edges=new HashMap<>();
            for(var f:h.faces()) {
                for(var p:pts)assertTrue(f.plane().distance(p.x(),p.y(),p.z())<=1e-7);
                for(int i=0;i<f.vertices().size();i++){
                    int a=f.vertices().get(i).packed(),b=f.vertices().get((i+1)%f.vertices().size()).packed();
                    edges.merge(Math.min(a,b)+":"+Math.max(a,b),1,Integer::sum);
                }
            }
            assertTrue(edges.values().stream().allMatch(n->n==2));
            verifyCoverage(h,random);
        }
    }
    @Test void accepts64PointsAndKeepsDecimalFacesExact() {
        List<Polyhedron.Point> points=new ArrayList<>();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)
            points.add(new Polyhedron.Point(.1+x*.1,.2+y*.1,.3+z*.1));
        var h=Polyhedron.of(points);
        assertEquals(64,h.points().size());assertEquals(6,h.faces().size());
        assertEquals(.027,volume(h),1e-9);
        assertSame(h,Polyhedron.fromPacked(h.packed()));
        assertFalse(h.collisionBoxes().isEmpty());
        verifyCoverage(h,new Random(123));
        points.add(new Polyhedron.Point(16,16,16));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.of(points));
    }
    @Test void oldIntegerDataAndAllDecimalCoordinatesRoundTrip() {
        for(int x=0;x<=16;x++)for(int y=0;y<=16;y++)for(int z=0;z<=16;z++) {
            int old=x<<10|y<<5|z;
            var p=Polyhedron.Point.unpack(old);
            assertEquals(new Polyhedron.Point(x,y,z),p);assertEquals(old,p.packed());
        }
        for(int t=0;t<=160;t++) {
            var p=new Polyhedron.Point(t/10.0,(160-t)/10.0,.1);
            assertEquals(p,Polyhedron.Point.unpack(p.packed()));
        }
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(1<<24|161<<16));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(1<<25));
        assertThrows(IllegalArgumentException.class,()->new Polyhedron.Point(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new Polyhedron.Point(Double.POSITIVE_INFINITY,0,0));
    }
    @Test void pixelAndControlDragsSnapClampAndPreserveOtherAxes() {
        assertEquals(9,VertexEditing.snap(8,1.26,false));
        assertEquals(9.3,VertexEditing.snap(8,1.26,true));
        assertEquals(7,VertexEditing.snap(8,-1.26,false));
        assertEquals(6.7,VertexEditing.snap(8,-1.26,true));
        assertEquals(9.3,VertexEditing.snap(8.3,1.26,false));
        assertEquals(0,VertexEditing.snap(.1,-20,true));
        assertEquals(16,VertexEditing.snap(15.9,20,false));
        var p=new Polyhedron.Point(1.1,2.2,3.3).withAxis(1,4.4);
        assertEquals(new Polyhedron.Point(1.1,4.4,3.3),p);
    }
    private static void verifyCoverage(Polyhedron h,Random r) {
        // Positive convex combinations are known interior points, independent of the plane implementation.
        for(int j=0;j<60;j++) {
            double x=0,y=0,z=0,sum=0;
            for(var p:h.points()){double w=r.nextDouble();x+=p.x()*w;y+=p.y()*w;z+=p.z()*w;sum+=w;}
            final double a=x/sum,b=y/sum,c=z/sum;
            assertTrue(h.collisionBoxes().stream().anyMatch(box->a>=box.x0()-1e-8&&a<=box.x1()+1e-8&&b>=box.y0()-1e-8&&b<=box.y1()+1e-8&&c>=box.z0()-1e-8&&c<=box.z1()+1e-8));
        }
    }
    private static double volume(Polyhedron h) {
        double v=0;
        for(var f:h.faces()) {
            var a=f.vertices().getFirst();
            for(int i=1;i<f.vertices().size()-1;i++) {
                var b=f.vertices().get(i);var c=f.vertices().get(i+1);
                v+=a.x()*(b.y()*c.z()-b.z()*c.y())+a.y()*(b.z()*c.x()-b.x()*c.z())+a.z()*(b.x()*c.y()-b.y()*c.x());
            }
        }
        return v/6;
    }
}
