package com.caca.copypoly.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.joml.Matrix4d;

final class EditorBlockTransform {
    static Matrix4d capture(Level level, BlockPos pos, float partialTick) {
        if (ModList.get().isLoaded("sable")) return SableEditorBlockTransform.capture(level,pos,partialTick);
        return new Matrix4d().translation(pos.getX(),pos.getY(),pos.getZ());
    }
}
