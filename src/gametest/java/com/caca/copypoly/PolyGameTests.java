package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.simibubi.create.AllItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered only by NeoForge's development GameTest lifecycle, never by a normal game launch. */
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid=Copypoly.MODID, bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class PolyGameTests {
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        event.register(PolyGameTests.class);
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) event.register(SablePhysicsGameTests.class);
        if (!System.getProperty("copypoly.persistencePhase", "").isEmpty()) event.register(MaterialPersistenceProbe.class);
    }
    private static final BlockPos P=new BlockPos(2,2,2);
    private static Polyhedron tetra() {return Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16"));}
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void appliedMaterialsSurviveRepeatedSaveAndReload(GameTestHelper h) {
        var block=Copypoly.POLY.get();
        for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
            var player=h.makeMockPlayer(mode);
            player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
            var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
            for(Item item:new Item[]{Items.STONE,Items.GLASS,Items.RED_STAINED_GLASS,Items.OAK_LEAVES,Items.GRASS_BLOCK,Items.OAK_LOG,
                    com.simibubi.create.AllBlocks.ROSE_QUARTZ_TILES.asItem()}) {
                h.setBlock(P,Blocks.AIR);h.setBlock(P,block);
                var be=(PolyBlockEntity)h.getBlockEntity(P);be.setPoly(tetra());
                var stack=new ItemStack(item,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                block.useItemOn(stack,be.getBlockState(),h.getLevel(),h.absolutePos(P),player,InteractionHand.MAIN_HAND,hit);
                if(item==Items.OAK_LOG)be.cycleMaterial();
                var material=be.getMaterial();
                h.assertTrue(be.hasCustomMaterial(),"Material not applied: "+item);
                for(int pass=0;pass<3;pass++) {
                    var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());
                    // Chunk loading constructs and reads the entity before assigning its level.
                    var restored=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(P),be.getBlockState(),tag,h.getLevel().registryAccess());
                    h.assertTrue(restored instanceof PolyBlockEntity,"Wrong entity type after reload");
                    be=(PolyBlockEntity)restored;
                    h.assertTrue(be.getMaterial()==material,"Reload lost material/state: "+item+" / "+mode);
                    h.assertTrue(be.getConsumedItem().is(item) && be.getConsumedItem().getCount()==1,"Reload lost material item: "+item);
                    h.assertTrue(be.poly()==tetra(),"Reload lost shape");
                    var clientCopy=new PolyBlockEntity(h.absolutePos(P),block.defaultBlockState());
                    clientCopy.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
                    h.assertTrue(clientCopy.getMaterial()==material && clientCopy.poly()==tetra(),"Client sync lost material or shape");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void shapeSurvivesSaveDropAndPlacement(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);be.setPoly(tetra());
        var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());
        PolyBlockEntity restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
        restored.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.poly().points().equals(tetra().points()),"Shape did not survive NBT");
        var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);
        h.assertTrue(drops.size()==1,"Expected one shape item");
        h.assertTrue(PolyItem.readShape(drops.getFirst()).points().equals(tetra().points()),"Dropped shape lost points");
        BlockPos next=P.offset(1,0,0);h.setBlock(next,Copypoly.POLY.get());
        Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(next),Copypoly.POLY.get().defaultBlockState(),h.makeMockPlayer(GameType.SURVIVAL),drops.getFirst());
        PolyBlockEntity placed=h.getBlockEntity(next);
        h.assertTrue(placed.poly().points().equals(tetra().points()),"Placed item lost shape");
        h.assertTrue(!placed.shape().isEmpty(),"Missing collision");h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void materialsConsumeCycleAndRefundExactlyOnce(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());var be=(PolyBlockEntity)h.getBlockEntity(P);var block=Copypoly.POLY.get();
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var logs=new ItemStack(Items.OAK_LOG,3);player.setItemInHand(InteractionHand.MAIN_HAND,logs);
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
        block.useItemOn(logs,be.getBlockState(),h.getLevel(),h.absolutePos(P),player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(logs.getCount()==2 && be.getMaterial().is(Blocks.OAK_LOG),"Material was not applied/consumed");
        var axis=be.getMaterial().getValue(BlockStateProperties.AXIS);
        block.useItemOn(logs,be.getBlockState(),h.getLevel(),h.absolutePos(P),player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(logs.getCount()==2 && be.getMaterial().getValue(BlockStateProperties.AXIS)!=axis,"Cycling consumed material or did not rotate");
        var stone=new ItemStack(Items.STONE,2);
        block.useItemOn(stone,be.getBlockState(),h.getLevel(),h.absolutePos(P),player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(stone.getCount()==2 && be.getMaterial().is(Blocks.OAK_LOG),"Existing material was silently replaced");
        player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        block.onWrenched(be.getBlockState(),context);block.onWrenched(be.getBlockState(),context);
        h.assertTrue(!be.hasCustomMaterial() && player.getInventory().countItem(Items.OAK_LOG)==1,"Material refund was lost or duplicated");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void normalEmptyHandDoesNothingAndMaterialsValidate(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());var block=Copypoly.POLY.get();var player=h.makeMockPlayer(GameType.SURVIVAL);
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
        h.assertTrue(block.useWithoutItem(block.defaultBlockState(),h.getLevel(),h.absolutePos(P),player,hit)==InteractionResult.PASS,"Empty hand should do nothing");
        for(Item item:new Item[]{Items.GLASS,Items.RED_STAINED_GLASS,Items.OAK_LEAVES,Items.GRASS_BLOCK})
            h.assertTrue(block.getAcceptedBlockState(h.getLevel(),h.absolutePos(P),new ItemStack(item),Direction.UP)!=null,"Required material rejected: "+item);
        h.assertTrue(block.getAcceptedBlockState(h.getLevel(),h.absolutePos(P),new ItemStack(Copypoly.ITEM.get()),Direction.UP)==null,"Recursive copycat accepted");h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void staleEditorCannotModifyReplacement(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var old=(PolyBlockEntity)h.getBlockEntity(P);
        var menu=new PolyMenu(7,player.getInventory(),h.absolutePos(P),old.poly(),old.revision());
        player.containerMenu=menu;
        h.assertTrue(menu.stillValid(player),"Fresh menu should be valid");
        h.setBlock(P,Blocks.AIR);h.setBlock(P,Copypoly.POLY.get());
        h.assertTrue(!menu.stillValid(player),"Replacement incorrectly retained old editor");
        String result=PolyPackets.apply(player,new PolyPackets.Apply(7,menu.revision,tetra().packed()));
        h.assertTrue(result.equals("permission"),"Stale edit packet was accepted");
        h.assertTrue(((PolyBlockEntity)h.getBlockEntity(P)).poly()==Polyhedron.CUBE,"Replacement was modified");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void packetsValidateThrottleAndRejectStaleRevision(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        var be=(PolyBlockEntity)h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(8,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());
        player.containerMenu=menu;
        h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(9,menu.revision,tetra().packed())).equals("permission"),"Wrong menu accepted");
        h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(8,menu.revision,new int[]{0})).equals("count"),"Invalid points accepted");
        h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(8,menu.revision,tetra().packed())).equals("busy"),"Invalid request bypassed throttle");
        h.assertTrue(be.poly()==Polyhedron.CUBE,"Rejected packet changed shape");
        h.runAfterDelay(5,()->{
            h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(8,menu.revision,tetra().packed())).isEmpty(),"Valid update rejected");
            h.assertTrue(be.poly()==tetra(),"Valid update lost shape");
            h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(8,menu.revision,Polyhedron.CUBE.packed())).equals("changed"),"Stale revision accepted");
            h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void canceledDismantlePreservesBlockAndMaterial(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        var be=(PolyBlockEntity)h.getBlockEntity(P);be.setPoly(tetra());
        var block=Copypoly.POLY.get();var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
        var log=new ItemStack(Items.OAK_LOG);
        player.setItemInHand(InteractionHand.MAIN_HAND,log);
        block.useItemOn(log,be.getBlockState(),h.getLevel(),h.absolutePos(P),player,InteractionHand.MAIN_HAND,hit);
        player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> deny=e->{
            if(e.getLevel()==h.getLevel() && e.getPos().equals(h.absolutePos(P)))e.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try { block.onSneakWrenched(be.getBlockState(),context); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(P))==be && be.hasCustomMaterial(),"Canceled removal changed block/material");
        h.assertTrue(player.getInventory().countItem(Items.OAK_LOG)==0,"Canceled removal refunded material");
        block.onSneakWrenched(be.getBlockState(),context);
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(P)).isAir(),"Dismantle failed");
        h.assertTrue(player.getInventory().countItem(Items.OAK_LOG)==1,"Dismantle did not refund exactly one material");
        h.assertTrue(player.getInventory().countItem(Copypoly.ITEM.get())==1,"Dismantle did not refund exactly one block");
        for(var stack:player.getInventory().items) if(stack.is(Copypoly.ITEM.get()))
            h.assertTrue(PolyItem.readShape(stack)==tetra(),"Dismantle lost shape");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void deniedInteractionDoesNotOpenEditor(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(true);
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,h.absolutePos(P),hit);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        Copypoly.rightClick(event);
        h.assertTrue(!event.isCanceled(),"Denied block interaction was intercepted");
        h.succeed();
    }
}
