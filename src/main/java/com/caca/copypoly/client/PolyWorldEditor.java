package com.caca.copypoly.client;

import com.caca.copypoly.Copypoly;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class PolyWorldEditor {
    @SubscribeEvent public static void player(RenderPlayerEvent.Pre event) {
        var mc=Minecraft.getInstance();
        if(event.getEntity()==mc.player && mc.screen instanceof PolyScreen screen && screen.worldCameraActive()
                && mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(mc.player.getEyePosition())<1)
            event.setCanceled(true);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        if(Minecraft.getInstance().screen instanceof PolyScreen screen && screen.worldCameraActive())event.setCanceled(true);
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if(Minecraft.getInstance().screen instanceof PolyScreen screen)screen.updateMovement(event.getInput());
    }
    @SubscribeEvent public static void captureView(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL && Minecraft.getInstance().screen instanceof PolyScreen screen)
            screen.captureWorldView(event.getModelViewMatrix(),event.getProjectionMatrix(),event.getCamera().getPosition(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }
}
