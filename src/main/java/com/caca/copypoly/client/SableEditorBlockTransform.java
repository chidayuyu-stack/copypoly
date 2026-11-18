package com.caca.copypoly.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4d;

/** Optional client adapter; no Sable types are exposed by the editor itself. */
final class SableEditorBlockTransform {
    static Matrix4d capture(Level level, BlockPos pos, float partialTick) {
        var body = Sable.HELPER.getContaining(level,pos);
        if (body == null) return new Matrix4d().translation(pos.getX(),pos.getY(),pos.getZ());
        var pose = body instanceof ClientSubLevel client ? client.renderPose(partialTick) : body.logicalPose();
        return pose.bakeIntoMatrix(new Matrix4d()).translate(pos.getX(),pos.getY(),pos.getZ());
    }
}
