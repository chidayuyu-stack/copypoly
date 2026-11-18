package com.caca.copypoly.compat;

import com.caca.copypoly.PolyBlockEntity;
import com.caca.copypoly.geometry.Polyhedron;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Per pipeline and block position: never mutate Sable's shared BlockState cache. */
public final class PolyRapierColliders {
    private record Surface(double friction, double volume, double restitution) {}
    private static final class Entry {
        final Surface surface;
        final RapierVoxelColliderData[] handles = new RapierVoxelColliderData[2];
        int current;
        Polyhedron geometry;
        Entry(Surface surface) { this.surface = surface; }
    }
    private final Map<BlockPos, Entry> positions = new HashMap<>();
    private final Map<Surface, ArrayDeque<RapierVoxelColliderData>> available = new HashMap<>();

    public RapierVoxelColliderData get(BlockGetter world, BlockPos pos, BlockState state) {
        var geometry = world.getBlockEntity(pos) instanceof PolyBlockEntity be ? be.poly() : Polyhedron.CUBE;
        var surface = new Surface(PhysicsBlockPropertyHelper.getFriction(state),
                PhysicsBlockPropertyHelper.getVolume(state), PhysicsBlockPropertyHelper.getRestitution(state));
        var entry = positions.get(pos);
        if (entry != null && !entry.surface.equals(surface)) { release(pos); entry = null; }
        if (entry == null) { entry = new Entry(surface); positions.put(pos.immutable(), entry); }
        if (entry.geometry == geometry) return entry.handles[entry.current];

        // Native voxel replacement compares handles. Alternate two entries after edits,
        // otherwise changing boxes under the same handle leaves stale native geometry.
        entry.current ^= 1;
        var data = entry.handles[entry.current];
        if (data == null) {
            data = available.computeIfAbsent(surface, ignored -> new ArrayDeque<>()).pollFirst();
            if (data == null) data = Rapier3D.createVoxelColliderEntry(surface.friction, surface.volume,
                    surface.restitution, false, null);
            entry.handles[entry.current] = data;
        }
        data.clearBoxes();
        for (var box : geometry.collisionBoxes())
            data.addBox(new Vector3d(box.x0()/16, box.y0()/16, box.z0()/16),
                    new Vector3d(box.x1()/16, box.y1()/16, box.z1()/16));
        entry.geometry = geometry;
        return data;
    }

    public void release(BlockPos pos) {
        var entry = positions.remove(pos);
        if (entry == null) return;
        var pool = available.computeIfAbsent(entry.surface, ignored -> new ArrayDeque<>());
        for (var data : entry.handles) if (data != null) pool.addLast(data);
    }
    public void releaseSection(int x, int y, int z) {
        for (var pos : new ArrayList<>(positions.keySet()))
            if (pos.getX() >> 4 == x && pos.getY() >> 4 == y && pos.getZ() >> 4 == z) release(pos);
    }
}
