package com.caca.copypoly.client;

import com.caca.copypoly.*;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=Copypoly.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PolyClient {
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        PolyBlockEntity.setClientModelDataEnhancer(PolyModel::prepareBlockEntityModelData);
        event.getModels().replaceAll((id,model)->id.id().equals(Copypoly.id("copycat_polyhedron"))?new PolyModel(model):model);
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register(CopycatBlock.wrappedColor(),Copypoly.POLY.get());
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(Copypoly.MENU.get(),PolyScreen::new);
        PolyPackets.clientResult=r->{if(Minecraft.getInstance().screen instanceof PolyScreen s && s.getMenu().containerId==r.menu())s.result(r.error());};
    }
}
