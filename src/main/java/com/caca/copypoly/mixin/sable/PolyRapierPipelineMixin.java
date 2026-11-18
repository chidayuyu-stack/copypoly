package com.caca.copypoly.mixin.sable;

import com.caca.copypoly.PolyBlock;
import com.caca.copypoly.compat.PolyRapierColliders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.physics.impl.rapier.collider.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable 2.0.6: world terrain and moving sublevel plots. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class PolyRapierPipelineMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private final PolyRapierColliders copypoly$colliders = new PolyRapierColliders();
    @Unique private static final String COPYPOLY_BAKERY = "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery;getPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;";

    @WrapOperation(method = "handleChunkSectionAddition", at = @At(value = "INVOKE", target = COPYPOLY_BAKERY))
    private RapierVoxelColliderData copypoly$upload(RapierVoxelColliderBakery bakery, BlockState state,
            Operation<RapierVoxelColliderData> original, @Local(ordinal = 0) BlockPos pos) {
        return state.getBlock() instanceof PolyBlock ? copypoly$colliders.get(level, pos, state) : original.call(bakery, state);
    }
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE", target = COPYPOLY_BAKERY, ordinal = 0))
    private RapierVoxelColliderData copypoly$neighbor(RapierVoxelColliderBakery bakery, BlockState state,
            Operation<RapierVoxelColliderData> original, @Local(ordinal = 1) BlockPos pos) {
        return state.getBlock() instanceof PolyBlock ? copypoly$colliders.get(level, pos, state) : original.call(bakery, state);
    }
    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE", target = COPYPOLY_BAKERY, ordinal = 1))
    private RapierVoxelColliderData copypoly$changed(RapierVoxelColliderBakery bakery, BlockState state,
            Operation<RapierVoxelColliderData> original, @Local(ordinal = 0) BlockPos pos) {
        return state.getBlock() instanceof PolyBlock ? copypoly$colliders.get(level, pos, state) : original.call(bakery, state);
    }
    @Inject(method = "handleBlockChange", at = @At("RETURN"))
    private void copypoly$removed(SectionPos section, LevelChunkSection chunk, int x, int y, int z,
            BlockState before, BlockState after, CallbackInfo ci) {
        if (!(after.getBlock() instanceof PolyBlock)) copypoly$colliders.release(new BlockPos(
                section.minBlockX() + (x & 15), section.minBlockY() + (y & 15), section.minBlockZ() + (z & 15)));
    }
    @Inject(method = "handleChunkSectionRemoval", at = @At("RETURN"))
    private void copypoly$unloaded(int x, int y, int z, CallbackInfo ci) { copypoly$colliders.releaseSection(x, y, z); }
}
