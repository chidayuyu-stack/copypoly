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
    public static void lineCutReferencePositionsSurvivePacketSyncPersistenceDropAndFurtherEdits(GameTestHelper h) {
        var shape=TopologyFixtures.lineInset();h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(83,player.getInventory(),h.absolutePos(P),be.document(),be.revision());player.containerMenu=menu;
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        PolyPackets.Apply.CODEC.encode(buf,new PolyPackets.Apply(83,menu.revision,shape));var decoded=PolyPackets.Apply.CODEC.decode(buf);buf.release();
        h.runAfterDelay(5,()->{
            h.assertTrue(PolyPackets.apply(player,decoded).isEmpty(),"Server rejected automatic normal-vertex cuts");
            h.assertTrue(be.document().equals(shape) && !be.poly().contains(8,12,8),"Server filled the inset");
            var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.document().equals(shape),"Persistence lost reference positions");
            var client=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());client.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(client.document().equals(shape),"Sync lost line document");
            var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);h.assertTrue(drops.size()==1 && PolyItem.readDocument(drops.getFirst()).equals(shape),"Drop lost line document");
            h.setBlock(P.east(),Copypoly.POLY.get());Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(P.east()),be.getBlockState(),player,drops.getFirst());
            PolyBlockEntity placed=h.getBlockEntity(P.east());
            var edited=placed.document().toBuilder().moveVertex(8,4,10,4).build();placed.setDocument(edited);
            h.assertTrue(edited.pendingConnections().isEmpty() && !placed.poly().contains(8,12,8),"Further edit after placement lost the reference plane");
            var remove=shape.toBuilder();for(int id=8;id<12;id++)remove.removeVertex(id);placed.setDocument(remove.build());
            h.assertTrue(placed.poly().contains(8,15,8),"Deleting inset points did not restore base after reload");h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void arbitraryTunnelSurvivesGraphPacketPersistenceDropAndPlacement(GameTestHelper h) {
        var graph=TopologyFixtures.tunnel();h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(78,player.getInventory(),h.absolutePos(P),be.document(),be.revision());player.containerMenu=menu;
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        PolyPackets.Apply.CODEC.encode(buf,new PolyPackets.Apply(78,menu.revision,graph));var packet=PolyPackets.Apply.CODEC.decode(buf);buf.release();
        h.runAfterDelay(5,()->{
            h.assertTrue(PolyPackets.apply(player,packet).isEmpty(),"Tunnel packet was rejected");
            h.assertTrue(be.document().manualMesh() && !be.poly().contains(8,8,8),"Server filled the tunnel with a convex hull");
            var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.document().equals(graph) && !restored.poly().contains(8,8,8),"Persistence lost explicit topology");
            var client=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());client.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(client.document().equals(graph),"Client sync lost explicit topology");
            var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);h.assertTrue(drops.size()==1 && PolyItem.readDocument(drops.getFirst()).equals(graph),"Drop lost tunnel");
            h.setBlock(P.east(),Copypoly.POLY.get());Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(P.east()),be.getBlockState(),player,drops.getFirst());
            PolyBlockEntity placed=h.getBlockEntity(P.east());h.assertTrue(placed.document().equals(graph),"Placement lost mesh document");
            var hole=new AABB(.45,.1,.45,.55,.9,.55);h.assertTrue(placed.shape().toAabbs().stream().noneMatch(box->box.intersects(hole)),"Collision filled through hole");h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void unfinishedExplicitSurfaceCannotReplaceTheAuthoritativeSolid(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(79,player.getInventory(),h.absolutePos(P),be.document(),be.revision());player.containerMenu=menu;
        var open=TopologyFixtures.tunnel().toBuilder().removeFaces(java.util.List.of(0,1)).build();
        h.runAfterDelay(5,()->{
            var error=PolyPackets.apply(player,new PolyPackets.Apply(79,menu.revision,open));
            h.assertTrue(!error.isEmpty() && be.poly()==Polyhedron.CUBE && !be.document().manualMesh(),"Open draft changed server collision");h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void vertexGraphSurvivesPacketSyncSaveDropPlacementAndApiRemoval(GameTestHelper h) {
        var b=com.caca.copypoly.api.PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();
        int custom=b.addCustomVertex(8,4,8),pending=b.addCustomVertex(4,12,4);
        for(int target:new int[]{2,3,6,7})b.connect(custom,target);
        b.connect(pending,2);var graph=b.build();
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(64,player.getInventory(),h.absolutePos(P),be.document(),be.revision());player.containerMenu=menu;
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        PolyPackets.Apply.CODEC.encode(buf,new PolyPackets.Apply(64,menu.revision,graph));var packet=PolyPackets.Apply.CODEC.decode(buf);buf.release();
        h.runAfterDelay(5,()->{
            h.assertTrue(PolyPackets.apply(player,packet).isEmpty(),"Graph apply rejected");
            h.assertTrue(be.document().equals(graph) && !be.poly().contains(8,12,8),"Graph lost custom dent");
            var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
            restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.document().equals(graph),"NBT lost vertex graph");
            var client=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
            client.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(client.document().equals(graph),"Client sync lost links/types/pending point");
            var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);
            h.assertTrue(drops.size()==1 && PolyItem.readDocument(drops.getFirst()).equals(graph),"Drop lost editing document");
            var other=P.east();h.setBlock(other,Copypoly.POLY.get());
            Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(other),be.getBlockState(),player,drops.getFirst());
            h.assertTrue(com.caca.copypoly.api.CopypolyApi.getShape(h.getLevel(),h.absolutePos(other)).equals(graph),"Placement/API lost graph");
            var removed=graph.toBuilder().removeVertex(custom).build();
            com.caca.copypoly.api.CopypolyApi.setShape(h.getLevel(),h.absolutePos(other),removed);
            PolyBlockEntity placed=h.getBlockEntity(other);
            h.assertTrue(placed.poly().contains(8,12,8),"Deleting custom point did not restore top surface");
            h.assertTrue(placed.document().vertex(pending).type()==com.caca.copypoly.api.VertexType.CUSTOM,"API discarded unrelated draft point");
            var reset=drops.getFirst().copy();PolyItem.saveShape(reset,Polyhedron.CUBE);
            h.assertTrue(!PolyItem.readDocument(reset).managed(),"Legacy save retained stale graph");
            h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void invalidGraphPacketCannotModifyAuthoritativeDocument(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(65,player.getInventory(),h.absolutePos(P),be.document(),be.revision());player.containerMenu=menu;
        h.runAfterDelay(5,()->{
            var error=PolyPackets.apply(player,new PolyPackets.Apply(65,menu.revision,new int[0],new int[0],new int[]{1,0,8,65}));
            h.assertTrue(error.equals("graph") && be.poly()==Polyhedron.CUBE,"Malformed graph changed the block");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            buf.writeVarInt(65);buf.writeVarInt(menu.revision);buf.writeVarIntArray(new int[0]);buf.writeVarIntArray(new int[0]);
            buf.writeVarInt(com.caca.copypoly.api.PolyShape.MAX_PACKED+1);
            boolean rejected=false;try{PolyPackets.Apply.CODEC.decode(buf);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}finally{buf.release();}
            h.assertTrue(rejected,"Packet decoder accepted an unbounded graph");h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void freeformPacketPreservesFacesAcrossSyncSaveDropAndPlacement(GameTestHelper h) {
        var base=Polyhedron.freeformFromHull(Polyhedron.CUBE_POINTS);
        var points=new java.util.ArrayList<>(base.points());points.set(7,new Polyhedron.Point(8,8,8));
        var dent=base.editVertices(points);
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(42,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());player.containerMenu=menu;
        var packet=new PolyPackets.Apply(42,menu.revision,dent.packed(),dent.packedTriangles());
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        PolyPackets.Apply.CODEC.encode(buffer,packet);var decoded=PolyPackets.Apply.CODEC.decode(buffer);buffer.release();
        h.assertTrue(java.util.Arrays.equals(packet.faces(),decoded.faces()),"Network codec lost mesh faces");
        h.runAfterDelay(5,()->{
            h.assertTrue(PolyPackets.apply(player,decoded).isEmpty(),"Server rejected a valid concave mesh");
            h.assertTrue(be.poly()==dent,"Server rebuilt the free mesh as a convex hull");
            var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
            restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.poly()==dent && restored.poly().freeform(),"NBT lost mesh mode or topology");
            var client=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
            client.readClient(be.writeClient(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(client.poly()==dent,"Client sync lost the concave mesh");
            var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);
            h.assertTrue(drops.size()==1 && PolyItem.readShape(drops.getFirst())==dent,"Dropped item lost mesh faces");
            var other=P.east();h.setBlock(other,Copypoly.POLY.get());
            Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(other),be.getBlockState(),player,drops.getFirst());
            h.assertTrue(((PolyBlockEntity)h.getBlockEntity(other)).poly()==dent,"Placement lost free deformation");
            var cavity=new AABB(7/16.0-.01,7/16.0-.01,13/16.0-.01,7/16.0+.01,7/16.0+.01,13/16.0+.01);
            h.assertTrue(be.shape().toAabbs().stream().noneMatch(box->box.intersects(cavity)),"Player collision filled the dent");
            var reset=drops.getFirst().copy();PolyItem.saveShape(reset,Polyhedron.CUBE);
            h.assertTrue(PolyItem.readShape(reset)==Polyhedron.CUBE,"Reset item retained old mesh topology");
            h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void invalidMeshPacketCannotModifyTheBlock(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(43,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());player.containerMenu=menu;
        h.runAfterDelay(5,()->{
            var error=PolyPackets.apply(player,new PolyPackets.Apply(43,menu.revision,be.poly().packed(),new int[]{0,1,2}));
            h.assertTrue(error.equals("mesh") && be.poly()==Polyhedron.CUBE,"Open mesh packet changed authoritative geometry");h.succeed();
        });
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void slopeOutlineUsesNineHullEdgesAndRetainsSteppedCollision(GameTestHelper h) {
        var slope = Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,0,16;16,0,16;0,16,16;16,16,16"));
        h.setBlock(P, Copypoly.POLY.get());
        PolyBlockEntity be = h.getBlockEntity(P);
        be.setPoly(slope);
        var outline = be.getBlockState().getShape(h.getLevel(), h.absolutePos(P));
        var collision = be.getBlockState().getCollisionShape(h.getLevel(), h.absolutePos(P));
        var edges = new java.util.ArrayList<double[]>();
        outline.forAllEdges((x0,y0,z0,x1,y1,z1) -> edges.add(new double[]{x0,y0,z0,x1,y1,z1}));
        h.assertTrue(edges.size() == 9, "Slope outline contained duplicate or stair-step edges: " + edges.size());
        h.assertTrue(edges.stream().filter(e -> e[1] != e[4] && e[2] != e[5]).count() == 2, "Missing the two diagonal slope edges");
        h.assertTrue(collision.toAabbs().size() == 8, "Slope no longer uses eight solid steps");
        h.assertTrue(outline.toAabbs().equals(collision.toAabbs()), "Outline wrapper changed the collision voxel grid");
        var box = new AABB(.4,2,.4,.6,2.4,.6);
        h.assertTrue(collision.collide(Direction.Axis.Y,box,-3) == outline.collide(Direction.Axis.Y,box,-3), "Wrapper changed physical collision");
        be.setPoly(tetra());
        var changed = new java.util.ArrayList<double[]>();
        be.outline().forAllEdges((a,b,c,d,e,f) -> changed.add(new double[]{a,b,c,d,e,f}));
        h.assertTrue(changed.size() == 6 && outline != be.outline(), "Saved edit retained the old selection outline");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void editorAllowsWalkingAroundTargetButClosesBeyond16Blocks(GameTestHelper h) {
        h.setBlock(P,Copypoly.POLY.get());
        PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var menu=new PolyMenu(25,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());
        Vec3 center=Vec3.atCenterOf(h.absolutePos(P));
        player.setPos(center.add(10,0,0));
        h.assertTrue(menu.stillValid(player),"Walking around the target closed the editor");
        player.setPos(center.add(17,0,0));
        h.assertTrue(!menu.stillValid(player),"Editor accepted edits beyond its range");
        h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly")
    public static void decimal64PointPacketSurvivesSaveDropAndPlacement(GameTestHelper h) {
        var points=new java.util.ArrayList<Polyhedron.Point>();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)
            points.add(new Polyhedron.Point(x*5.3,y*5.3,z*5.3));
        var poly=Polyhedron.of(points);
        h.setBlock(P,Copypoly.POLY.get());
        PolyBlockEntity be=h.getBlockEntity(P);
        var player=h.makeMockPlayer(GameType.CREATIVE);
        player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var menu=new PolyMenu(19,player.getInventory(),h.absolutePos(P),be.poly(),be.revision());
        player.containerMenu=menu;
        h.assertTrue(PolyPackets.apply(player,new PolyPackets.Apply(19,menu.revision,poly.packed())).isEmpty(),"64-point decimal packet rejected");
        var restored=new PolyBlockEntity(h.absolutePos(P),be.getBlockState());
        restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.poly()==poly,"Decimal shape lost during save/reload");
        var drops=Block.getDrops(be.getBlockState(),h.getLevel(),h.absolutePos(P),be);
        h.assertTrue(drops.size()==1 && PolyItem.readShape(drops.getFirst())==poly,"Decimal points lost in drop");
        BlockPos next=P.offset(1,0,0);h.setBlock(next,Copypoly.POLY.get());
        Copypoly.POLY.get().setPlacedBy(h.getLevel(),h.absolutePos(next),be.getBlockState(),player,drops.getFirst());
        PolyBlockEntity placed=h.getBlockEntity(next);
        h.assertTrue(placed.poly()==poly && !placed.shape().isEmpty(),"Decimal placement/collision failed");
        h.succeed();
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
