package com.caca.copypoly;

import com.caca.copypoly.client.PolyModel;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

/** Opt-in client startup/model check; never present in the release jar. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class ClientCompatibilityProbe {
    private static boolean finished;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("copypoly.clientSmoke")) return;
        var client=Minecraft.getInstance();
        if (client.screen==null || client.getOverlay()!=null) return;
        finished=true;
        var model=client.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState());
        if (!(model instanceof PolyModel poly)) throw new IllegalStateException("CopyPoly model wrapper was not registered");
        if (poly.getQuads(null,null,RandomSource.create()).isEmpty()) throw new IllegalStateException("CopyPoly item mesh is empty");
        var fallbackTypes=poly.getRenderTypes(Copypoly.POLY.get().defaultBlockState(),RandomSource.create(),ModelData.EMPTY);
        for(var type:List.of(RenderType.solid(),RenderType.cutout(),RenderType.cutoutMipped(),RenderType.translucent()))
            if(!fallbackTypes.contains(type)) throw new IllegalStateException("CopyPoly is missing fallback render type: "+type);
        var tetra=Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16"));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.GLASS,Blocks.RED_STAINED_GLASS,
                Blocks.OAK_LEAVES,Blocks.GRASS_BLOCK,Blocks.OAK_LOG,com.simibubi.create.AllBlocks.ROSE_QUARTZ_TILES.get()}) {
            var quads=poly.preview(tetra,block.defaultBlockState());
            if(quads.isEmpty()) throw new IllegalStateException("CopyPoly preview mesh is empty for "+block);
            for(var quad:quads) {
                if(quad.getSprite().contents().name().getPath().equals("missingno"))
                    throw new IllegalStateException("CopyPoly preview has missing texture for "+block);
            }
        }
        var tiny=Polyhedron.of(Polyhedron.CUBE_POINTS.stream().map(p->new Polyhedron.Point(
            8.125+p.x()/16*.001,7.999+p.y()/16*.001,.001+p.z()/16*.001)).toList());
        var tinyQuads=poly.preview(tiny,Blocks.STONE.defaultBlockState());
        if(tinyQuads.size()!=12)throw new IllegalStateException("Thousandth-unit cube lost faces: "+tinyQuads.size());
        for(var quad:tinyQuads) {
            int[] data=quad.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++)
                if(!Float.isFinite(Float.intBitsToFloat(data[i*stride+axis])))throw new IllegalStateException("Decimal mesh has non-finite vertex");
        }
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly client compatibility probe passed: model registration, item mesh, render fallbacks, seven material meshes");
        client.stop();
    }
}
