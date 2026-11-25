package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** One writer for all shape data; legacy formats are read-only migration paths. */
public final class PolyShapeData {
    public static final String KEY = "PolyShape";
    public static final int VERSION = 2;

    private PolyShapeData() {}

    public static Polyhedron read(CompoundTag tag, String legacyKey) {
        if (tag.contains(KEY)) {
            if (!tag.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("format");
            CompoundTag shape = tag.getCompound(KEY);
            if (!shape.contains("Version", Tag.TAG_INT) || shape.getInt("Version") != VERSION)
                throw new IllegalArgumentException("version");
            if (!shape.contains("Points", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("format");
            return Polyhedron.fromPacked(shape.getLongArray("Points"));
        }
        if (!tag.contains(legacyKey)) return Polyhedron.CUBE;
        if (!tag.contains(legacyKey, Tag.TAG_INT_ARRAY)) throw new IllegalArgumentException("format");
        return Polyhedron.fromLegacy(tag.getIntArray(legacyKey));
    }

    public static void write(CompoundTag tag, Polyhedron shape) {
        clear(tag);
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putLongArray("Points", shape.packed());
        tag.put(KEY, data);
    }

    public static void clear(CompoundTag tag) {
        tag.remove(KEY);
        tag.remove("Points");
        tag.remove("PolyPoints");
    }

    public static void writePoints(FriendlyByteBuf buf, long[] points) {
        checkCount(points.length);
        buf.writeVarInt(points.length);
        for (long point : points) buf.writeLong(point);
    }

    public static long[] readPoints(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkCount(count);
        long[] points = new long[count];
        for (int i = 0; i < count; i++) points[i] = buf.readLong();
        return points;
    }

    private static void checkCount(int count) {
        if (count < 4 || count > Polyhedron.MAX_POINTS) throw new IllegalArgumentException("count");
    }
}
