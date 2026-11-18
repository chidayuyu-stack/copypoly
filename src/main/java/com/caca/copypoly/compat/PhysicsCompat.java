package com.caca.copypoly.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class PhysicsCompat {
    private PhysicsCompat() {}
    public static void shapeChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server && ModList.get().isLoaded("sable"))
            SablePhysicsCompat.shapeChanged(server, pos);
    }
}
