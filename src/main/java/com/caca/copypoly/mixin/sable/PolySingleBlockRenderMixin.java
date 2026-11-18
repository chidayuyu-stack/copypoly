package com.caca.copypoly.mixin.sable;

import com.caca.copypoly.PolyBlockEntity;
import com.caca.copypoly.client.PolyModel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** The single-block wrapper otherwise discovers layers with empty model data. */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.neoforge.platform.SableSubLevelRenderPlatformImpl",remap=false)
public abstract class PolySingleBlockRenderMixin {
    @Inject(method="getRenderLayers",at=@At("HEAD"),cancellable=true)
    private void copypoly$layers(SingleBlockSubLevelWrapper world,BakedModel model,BlockState state,BlockPos pos,RandomSource random,
            CallbackInfoReturnable<List<RenderType>> cir) {
        if(model instanceof PolyModel && world.getBlockEntity(pos) instanceof PolyBlockEntity entity)
            cir.setReturnValue(model.getRenderTypes(state,random,entity.getModelData()).asList());
    }
    @WrapOperation(method="tesselateBlock",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getModelData(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/client/model/data/ModelData;"))
    private ModelData copypoly$liveDraft(ClientLevel level,BlockPos pos,Operation<ModelData> original) {
        return level.getBlockEntity(pos) instanceof PolyBlockEntity entity?entity.getModelData():original.call(level,pos);
    }
}
