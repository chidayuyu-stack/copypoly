package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolyShapeDataTest {
    private static Polyhedron fractional() {
        return Polyhedron.of(Polyhedron.parse("0.001,0,0;15.999,0,0;0,8.125,0;0,0,16"));
    }

    @Test void readsBothLegacyContainersAndAlwaysWritesOnlyTheNewFormat() {
        for(String legacyKey:new String[]{"Points","PolyPoints"}) {
            var tag=new CompoundTag();
            tag.putIntArray(legacyKey,new int[]{0,16<<10,16<<5,16});
            tag.putString("OtherData","retained");
            var shape=PolyShapeData.read(tag,legacyKey);
            assertEquals(4,shape.points().size());
            assertFalse(tag.contains(PolyShapeData.KEY),"Reading must not mutate item data");
            PolyShapeData.write(tag,shape);
            assertFalse(tag.contains("Points"));assertFalse(tag.contains("PolyPoints"));
            assertEquals("retained",tag.getString("OtherData"));
            assertTrue(tag.getCompound(PolyShapeData.KEY).contains("Points",Tag.TAG_LONG_ARRAY));
            assertSame(shape,PolyShapeData.read(tag,legacyKey));
        }
    }
    @Test void decimalsRoundTripAndNewDataTakesPrecedence() {
        var tag=new CompoundTag();var shape=fractional();
        PolyShapeData.write(tag,shape);
        tag.putIntArray("Points",new int[]{0,16<<10,16<<5,16});
        for(int pass=0;pass<5;pass++) {
            assertSame(shape,PolyShapeData.read(tag,"Points"));
            PolyShapeData.write(tag,PolyShapeData.read(tag,"Points"));
        }
        assertEquals(4*Long.BYTES,tag.getCompound(PolyShapeData.KEY).getLongArray("Points").length*Long.BYTES);
        PolyShapeData.clear(tag);
        assertSame(Polyhedron.CUBE,PolyShapeData.read(tag,"Points"));
    }
    @Test void corruptOrUnknownNewDataCannotFallBackToStaleLegacyData() {
        var tag=new CompoundTag();
        tag.putIntArray("Points",new int[]{0,16<<10,16<<5,16});
        tag.putString(PolyShapeData.KEY,"bad type");
        assertThrows(IllegalArgumentException.class,()->PolyShapeData.read(tag,"Points"));
        PolyShapeData.write(tag,fractional());
        tag.getCompound(PolyShapeData.KEY).putInt("Version",99);
        assertEquals("version",assertThrows(IllegalArgumentException.class,()->PolyShapeData.read(tag,"Points")).getMessage());
        PolyShapeData.write(tag,fractional());
        tag.getCompound(PolyShapeData.KEY).putLongArray("Points",new long[]{1L<<42,0,1,2});
        assertThrows(IllegalArgumentException.class,()->PolyShapeData.read(tag,"Points"));
    }
    @Test void networkRoundTripUsesEightBytesPerPointAndRejectsUnboundedCounts() {
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var shape=fractional();
            PolyShapeData.writePoints(buf,shape.packed());
            assertEquals(1+4*8,buf.readableBytes());
            assertSame(shape,Polyhedron.fromPacked(PolyShapeData.readPoints(buf)));
            assertEquals(0,buf.readableBytes());
            for(int count:new int[]{-1,0,3,33,Integer.MAX_VALUE}) {
                buf.clear();buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class,()->PolyShapeData.readPoints(buf));
            }
            buf.clear();buf.writeVarInt(4);buf.writeLong(0);
            assertThrows(IndexOutOfBoundsException.class,()->PolyShapeData.readPoints(buf));
        } finally {buf.release();}
    }
    @Test void maximumShapeHasBoundedStorageAndNetworkSize() throws java.io.IOException {
        var points=new java.util.ArrayList<>(Polyhedron.CUBE_POINTS);
        for(int i=0;i<24;i++)points.add(new Polyhedron.Point(i%12+1.125,i/12*4+4.001,8));
        var shape=Polyhedron.of(points);
        assertEquals(32,shape.points().size());
        var tag=new CompoundTag();PolyShapeData.write(tag,shape);
        var bytes=new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(tag,new java.io.DataOutputStream(bytes));
        assertTrue(bytes.size()<=300,"Versioned shape NBT grew beyond its size budget: "+bytes.size());
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            PolyShapeData.writePoints(buf,shape.packed());
            assertEquals(257,buf.readableBytes());
            assertSame(shape,Polyhedron.fromPacked(PolyShapeData.readPoints(buf)));
        } finally {buf.release();}
    }
}
