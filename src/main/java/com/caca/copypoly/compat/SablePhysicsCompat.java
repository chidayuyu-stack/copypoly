package com.caca.copypoly.compat;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

/** Loaded only when Sable is installed. A BE edit does not change its BlockState. */
final class SablePhysicsCompat {
    static void shapeChanged(ServerLevel level, BlockPos pos) {
        var system = SubLevelPhysicsSystem.get(level);
        if (system == null) return;
        var chunk = level.getChunkAt(pos);
        var state = level.getBlockState(pos);
        system.handleBlockChange(SectionPos.of(pos), chunk.getSection(chunk.getSectionIndex(pos.getY())),
                pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, state);
    }
}
