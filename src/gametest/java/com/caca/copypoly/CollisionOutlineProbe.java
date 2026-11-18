package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client render verification only; excluded from the published jar. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class CollisionOutlineProbe {
    private static final BlockPos TARGET = new BlockPos(0,100,0);
    private static int stage, ticks, total;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("copypoly.collisionSmoke")) return;
        var mc = Minecraft.getInstance(); ticks++;
        if (++total > 3600) throw new IllegalStateException("Collision render check timed out at stage " + stage);
        if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
            mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.options.pauseOnLostFocus=false; mc.resizeDisplay();
            var settings = new LevelSettings("CopyPoly collision check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("copypoly-collision-"+System.currentTimeMillis(),settings,
                    new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            next(); return;
        }
        if(stage==1 && mc.level!=null && mc.player!=null && mc.screen==null) {
            mc.getSingleplayerServer().execute(() -> {
                var server=mc.getSingleplayerServer();var level=server.overworld();
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
                    for(int y=100;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                if(Boolean.getBoolean("copypoly.collisionControl")) level.setBlockAndUpdate(TARGET,Blocks.STONE.defaultBlockState());
                else {
                    level.setBlockAndUpdate(TARGET,Copypoly.POLY.get().defaultBlockState());
                    var be=(PolyBlockEntity)level.getBlockEntity(TARGET);
                    be.setPoly(Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,0,16;16,0,16;0,16,16;16,16,16")));
                    be.setMaterial(Blocks.STONE.defaultBlockState());
                }
                level.setDayTime(6000);
                var player=server.getPlayerList().getPlayers().getFirst();
                player.connection.teleport(2.5,100.7,-2.5,33.69f,24.86f);
            }); next(); return;
        }
        if(stage==2 && ticks>100 && mc.level!=null) {
            if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(TARGET))
                throw new IllegalStateException("Render check did not target the slope: " + mc.hitResult);
            if(!Boolean.getBoolean("copypoly.collisionControl")) {
                var be=(PolyBlockEntity)mc.level.getBlockEntity(TARGET);
                int[] count={0}; be.outline().forAllEdges((a,b,c,d,e,f)->count[0]++);
                if(count[0]!=9 || be.shape().toAabbs().size()!=8)throw new IllegalStateException("Client slope geometry mismatch");
            }
            Screenshot.grab(mc.gameDirectory,Boolean.getBoolean("copypoly.collisionControl") ? "vanilla-control.png" : "slope-outline-1.5.0.png",mc.getMainRenderTarget(),message ->
                    com.mojang.logging.LogUtils.getLogger().info("CopyPoly collision render screenshot: {}",message.getString()));
            next(); return;
        }
        if(stage==3 && ticks>30) {
            com.mojang.logging.LogUtils.getLogger().info(Boolean.getBoolean("copypoly.collisionControl")
                    ? "CopyPoly vanilla control completed without placing or editing a CopyPoly block"
                    : "CopyPoly collision client probe passed: optional mixins, nine-edge diagonal outline, eight-step solid collision");
            mc.stop(); next();
        }
    }
    private static void next() { stage++; ticks=0; }
}
