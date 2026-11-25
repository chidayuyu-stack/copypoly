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
    private static Polyhedron decimalShape() {return Polyhedron.of(Polyhedron.parse("0.001,0,0;15.999,0,0;0,8.125,0;0,0,16"));}
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void decimalsSurviveSaveSyncSafeWriteCloneDropAndPlacement(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        var be=(PolyBlockEntity)h.getBlockEntity(P);var shape=decimalShape();be.setPoly(shape);
        var registries=h.getLevel().registryAccess();
        var saved=be.saveWithoutMetadata(registries);
        h.assertTrue(saved.contains(PolyShapeData.KEY) && !saved.contains("Points"),"Save did not migrate to new format");
        var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
        restored.loadWithComponents(saved,registries);
        h.assertTrue(restored.poly()==shape,"Save/load rounded decimal points");
        var safe=new net.minecraft.nbt.CompoundTag();be.writeSafe(safe,registries);
        h.assertTrue(PolyShapeData.read(safe,"Points")==shape,"Create safe write lost decimals");
        restored.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),registries),registries);
        h.assertTrue(restored.poly()==shape,"Client update lost decimals");
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);
        var clone=Copypoly.POLY.get().getCloneItemStack(be.getBlockState(),hit,h.getLevel(),h.absolutePos(P),h.makeMockPlayer(GameType.CREATIVE));
        h.assertTrue(PolyItem.readShape(clone)==shape,"Clone lost decimals");
        var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);
        h.assertTrue(drops.size()==1 && PolyItem.readShape(drops.getFirst())==shape,"Drop lost decimals");
        var next=P.offset(1,0,0);h.setBlock(next,Copypoly.POLY.get());
        Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(next),be.getBlockState(),h.makeMockPlayer(GameType.SURVIVAL),drops.getFirst());
        h.assertTrue(((PolyBlockEntity)h.getBlockEntity(next)).poly()==shape,"Placement lost decimals");
        h.assertTrue(!restored.shape().isEmpty(),"Decimal collision is empty");
        PolyItem.saveShape(clone,Polyhedron.CUBE);
        h.assertTrue(PolyItem.readShape(clone)==Polyhedron.CUBE,"Reset retained old item geometry");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void legacyBlockAndItemMigrateOnShapeWrite(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());var be=(PolyBlockEntity)h.getBlockEntity(P);
        var registries=h.getLevel().registryAccess();
        var legacy=be.saveWithoutMetadata(registries);
        legacy.remove(PolyShapeData.KEY);legacy.putIntArray("Points",new int[]{0,16<<10,16<<5,16});
        be.loadWithComponents(legacy,registries);
        h.assertTrue(be.poly()==tetra(),"Old block changed shape");
        var migrated=be.saveWithoutMetadata(registries);
        h.assertTrue(migrated.contains(PolyShapeData.KEY) && !migrated.contains("Points"),"Block retained old format");
        var item=new ItemStack(Copypoly.ITEM.get());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,item,
            tag->tag.putIntArray("PolyPoints",new int[]{0,16<<10,16<<5,16}));
        h.assertTrue(PolyItem.readShape(item)==tetra(),"Old item changed shape");
        PolyItem.saveShape(item,PolyItem.readShape(item));
        var data=item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(data.contains(PolyShapeData.KEY) && !data.contains("PolyPoints"),"Item retained old format");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void createRotationsAndMirrorsPreserveThousandths(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());var be=(PolyBlockEntity)h.getBlockEntity(P);var shape=decimalShape();
        for(var axis:Direction.Axis.values()) {
            be.setPoly(shape);
            var turn=new com.simibubi.create.content.contraptions.StructureTransform(BlockPos.ZERO,axis,Rotation.CLOCKWISE_90,Mirror.NONE);
            for(int step=0;step<4;step++) {
                be.transform(be,turn);
                if(step==1) {
                    var expected=Polyhedron.of(shape.points().stream().map(p->new Polyhedron.Point(
                        axis==Direction.Axis.X?p.x():16-p.x(),axis==Direction.Axis.Y?p.y():16-p.y(),axis==Direction.Axis.Z?p.z():16-p.z())).toList());
                    h.assertTrue(be.poly()==expected,"Half turn rounded or incorrectly rotated decimals on "+axis);
                }
            }
            h.assertTrue(be.poly()==shape,"Full rotation lost thousandths on "+axis);
        }
        for(var mirror:new Mirror[]{Mirror.LEFT_RIGHT,Mirror.FRONT_BACK}) {
            be.setPoly(shape);
            var transform=new com.simibubi.create.content.contraptions.StructureTransform(BlockPos.ZERO,Direction.Axis.Y,Rotation.NONE,mirror);
            be.transform(be,transform);
            h.assertTrue(be.poly()!=shape,"Mirror did not transform geometry");
            be.transform(be,transform);
            h.assertTrue(be.poly()==shape,"Double mirror lost thousandths");
        }
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void decimalPacketsRoundTripAndApplyWithValidation(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());var be=(PolyBlockEntity)h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(10,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());player.containerMenu=menu;
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            buf.writeBlockPos(h.absolutePos(P));PolyShapeData.writePoints(buf,decimalShape().packed());buf.writeVarInt(menu.revision);
            var clientMenu=new PolyMenu(10,player.getInventory(),buf);
            h.assertTrue(clientMenu.initial==decimalShape(),"Menu transport rounded coordinates");
            buf.clear();
            var packet=new PolyPackets.Apply(10,menu.revision,decimalShape().packed());
            PolyPackets.Apply.CODEC.encode(buf,packet);
            var received=PolyPackets.Apply.CODEC.decode(buf);
            h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(10,menu.revision,new long[]{1L<<42,0,1,2})).equals("range"),"Invalid packed coordinate accepted");
            h.runAfterDelay(5,()->{
                h.assertTrue(PolyPackets.apply(player,received).isEmpty(),"Valid decimal packet rejected");
                h.assertTrue(be.poly()==decimalShape(),"Server lost submitted decimals");h.succeed();
            });
        } finally {buf.release();}
    }
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
        h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(8,menu.revision,new long[]{0})).equals("count"),"Invalid points accepted");
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
