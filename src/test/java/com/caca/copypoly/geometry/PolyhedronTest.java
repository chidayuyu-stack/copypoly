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
        assertThrows(IllegalArgumentException.class,()->Polyhedron.parse("0.0001,0,0"));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromPacked(new long[33]));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(1L<<42));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(16001));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.fromLegacy(32768));
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
    @Test void decimalParsingIsExactAndFormattingIsCanonical() {
        var p=Polyhedron.Point.parse(".001","8.125","16.0000");
        assertEquals(new Polyhedron.Point(.001,8.125,16),p);
        assertEquals("0.001, 8.125, 16",p.toString());
        assertEquals(p,Polyhedron.Point.unpack(p.packed()));
        assertEquals(Polyhedron.Point.parse("0","8","16"),Polyhedron.Point.parse("-0.000","8.0000","16."));
        assertEquals(List.of(p),Polyhedron.parse("(0.001，8.125，16)"));
        assertEquals("precision",assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.parse("0.0001","0","0")).getMessage());
        for(String bad:List.of("NaN","Infinity","1e0","0x1.0p0",".",""))
            assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.parse(bad,"0","0"));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.001,16.001})
            assertThrows(IllegalArgumentException.class,()->new Polyhedron.Point(bad,0,0));
    }
    @Test void legacyCoordinatesMigrateWithoutChangingShape() {
        int[] legacy={0,16<<10,16<<5,16};
        assertSame(hull("0,0,0;16,0,0;0,16,0;0,0,16"),Polyhedron.fromLegacy(legacy));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromLegacy(new int[]{-1,0,1,2}));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.fromLegacy(17<<10));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.fromPacked(new long[]{0,0,0,0}));
        for(long invalid:new long[]{-1,1L<<42,16001L<<28,16001L<<14,16001})
            assertThrows(IllegalArgumentException.class,()->Polyhedron.Point.unpack(invalid));
    }
    @Test void tinyDecimalCubeHasSixFacesAndExactCollisionBounds() {
        var points=Polyhedron.CUBE_POINTS.stream().map(p->new Polyhedron.Point(
            8.125+p.x()/16*.001,7.999+p.y()/16*.001,.001+p.z()/16*.001)).toList();
        var h=Polyhedron.of(points);
        assertEquals(6,h.faces().size());
        assertTrue(h.faces().stream().allMatch(f->f.vertices().size()==4));
        assertEquals(List.of(new Polyhedron.Box(8.125,7.999,.001,8.126,8,.002)),h.collisionBoxes());
        assertTrue(h.contains(8.1255,7.9995,.0015));
        assertFalse(h.contains(8.127,7.9995,.0015));
        assertSame(h,Polyhedron.fromPacked(h.packed()));
    }
    @Test void nearlyCoplanarDecimalHullRemainsClosedAndCovered() {
        var h=hull("0,0,0;16,16,15.999;16,15.999,16;15.999,16,16");
        assertEquals(4,h.faces().size());
        assertTrue(volume(h)>0);
        verifyCoverage(h,new Random(123));
        assertThrows(IllegalArgumentException.class,()->hull("0,0,.001;16,0,.001;0,16,.001;16,16,.001"));
    }
    @Test void transformsRoundOnlyNumericalNoiseAndKeepThousandths() {
        var p=Polyhedron.transformed(-1e-10,8.12500000001,16+1e-10);
        assertEquals(new Polyhedron.Point(0,8.125,16),p);
        assertThrows(IllegalArgumentException.class,()->Polyhedron.transformed(-.001,0,0));
        assertThrows(IllegalArgumentException.class,()->Polyhedron.transformed(16.001,0,0));
    }
    @Test void randomHullsAreClosedOutwardAndHaveConservativeBoundedCollision() {
        Random random=new Random(0xCA7);
        for(int trial=0;trial<200;trial++) {
            List<Polyhedron.Point> pts=new ArrayList<>();
            for(int i=0;i<32;i++)pts.add(trial<100
                ?new Polyhedron.Point(random.nextInt(17),random.nextInt(17),random.nextInt(17))
                :new Polyhedron.Point(random.nextInt(16001)/1000.0,random.nextInt(16001)/1000.0,random.nextInt(16001)/1000.0));
            var h=Polyhedron.of(pts);
            assertTrue(volume(h)>0);assertTrue(h.collisionBoxes().size()<=64);
            Map<String,Integer> edges=new HashMap<>();
            for(var f:h.faces()) {
                for(var p:pts)assertTrue(f.plane().distance(p.x(),p.y(),p.z())<=1e-10);
                for(int i=0;i<f.vertices().size();i++){
                    long a=f.vertices().get(i).packed(),b=f.vertices().get((i+1)%f.vertices().size()).packed();
                    edges.merge(Math.min(a,b)+":"+Math.max(a,b),1,Integer::sum);
                }
            }
            assertTrue(edges.values().stream().allMatch(n->n==2));
            verifyCoverage(h,random);
        }
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
