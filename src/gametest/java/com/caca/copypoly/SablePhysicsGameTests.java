package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Real Rapier native contacts, run only with the optional Sable distribution. */
@PrefixGameTestTemplate(false)
public final class SablePhysicsGameTests {
    private static Polyhedron slab(double height) {
        return Polyhedron.of(Polyhedron.CUBE_POINTS.stream()
                .map(p -> new Polyhedron.Point(p.x(), p.y() == 0 ? 0 : height, p.z())).toList());
    }
    private static Polyhedron recessedTop() {
        var points=new java.util.ArrayList<>(Polyhedron.CUBE_POINTS);points.add(new Polyhedron.Point(8,16,8));
        var connected=Polyhedron.freeformFromHull(points);points.set(8,new Polyhedron.Point(8,4,8));
        return connected.editVertices(points);
    }
    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_freeform_terrain",timeoutTicks=200)
    public static void concaveNativeTerrainChangesWhenOnlyModeAndFacesChange(GameTestHelper h) {
        var level=h.getLevel();var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();var p=new BlockPos(352,200,256);
        level.setBlockAndUpdate(p,Copypoly.POLY.get().defaultBlockState());var be=(PolyBlockEntity)level.getBlockEntity(p);
        var cup=recessedTop();be.setPoly(cup);upload(h,pipeline,p);
        height(h,.5,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Sable filled a freeform recess");
        be.setPoly(Polyhedron.of(cup.points()));
        height(h,1,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Switching mode with identical vertices left a stale native collider");
        be.setPoly(cup);
        height(h,.5,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Restoring free deformation did not restore its cavity");h.succeed();
    }
    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_freeform_body",timeoutTicks=200)
    public static void assembledFreeformStructureHasAnActualNativeRecess(GameTestHelper h) {
        var level=h.getLevel();var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();var ground=new BlockPos(368,199,256);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var pos=ground.offset(x,0,z);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());upload(h,pipeline,pos);
        }
        var source=new BlockPos(2,4,2);h.setBlock(source,Copypoly.POLY.get());((PolyBlockEntity)h.getBlockEntity(source)).setPoly(recessedTop());
        var original=h.absolutePos(source);
        var body=SubLevelAssemblyHelper.assembleBlocks(level,original,java.util.List.of(original),new BoundingBox3i(original,original));
        h.runAfterDelay(3,()->{
            try {
                var be=(PolyBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
                h.assertTrue(be.poly().freeform() && be.poly()==recessedTop(),"Sable assembly lost mesh topology");
                pipeline.teleport(body,new Vector3d(ground.getX()+.5,200.5,ground.getZ()+.5),new Quaterniond());pipeline.resetVelocity(body);
                height(h,.5,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Native object landed on a filled hull instead of the structure's recess");
                h.succeed();
            } finally {body.markRemoved();SubLevelContainer.getContainer(level).processSubLevelRemovals();}
        });
    }
    private static void upload(GameTestHelper h, PhysicsPipeline pipeline, BlockPos pos) {
        var section = SectionPos.of(pos);
        var chunk = h.getLevel().getChunkAt(pos);
        pipeline.handleChunkSectionAddition(chunk.getSection(chunk.getSectionIndex(pos.getY())), section.x(), section.y(), section.z(), true);
    }
    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_custom_vertex_body",timeoutTicks=200)
    public static void customVertexGraphSurvivesAssemblyAndRemovalRestoresNativeSurface(GameTestHelper h) {
        var level=h.getLevel();var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();var ground=new BlockPos(384,199,256);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var p=ground.offset(x,0,z);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());upload(h,pipeline,p);
        }
        var b=com.caca.copypoly.api.PolyShape.fromGeometry(Polyhedron.CUBE).toBuilder();int id=b.addCustomVertex(8,4,8);
        for(int t:new int[]{2,3,6,7})b.connect(id,t);var graph=b.build();
        var source=new BlockPos(2,4,2);h.setBlock(source,Copypoly.POLY.get());
        com.caca.copypoly.api.CopypolyApi.setShape(level,h.absolutePos(source),graph);
        var original=h.absolutePos(source);
        var body=SubLevelAssemblyHelper.assembleBlocks(level,original,java.util.List.of(original),new BoundingBox3i(original,original));
        h.runAfterDelay(3,()->{
            try {
                var target=body.getPlot().getCenterBlock();
                h.assertTrue(com.caca.copypoly.api.CopypolyApi.getShape(level,target).equals(graph),"Sable assembly discarded base surface or custom links");
                pipeline.teleport(body,new Vector3d(ground.getX()+.5,200.5,ground.getZ()+.5),new Quaterniond());pipeline.resetVelocity(body);
                height(h,.5,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Native body did not use the custom vertex dent");
                com.caca.copypoly.api.CopypolyApi.setShape(level,target,graph.toBuilder().removeVertex(id).build());
                height(h,1,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Deleting custom vertex did not restore native collision");
                com.caca.copypoly.api.CopypolyApi.setShape(level,target,graph);
                height(h,.5,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Restoring graph did not refresh native cavity");h.succeed();
            } finally {body.markRemoved();SubLevelContainer.getContainer(level).processSubLevelRemovals();}
        });
    }
    private static double drop(GameTestHelper h, PhysicsPipeline pipeline, double x, double y, double z) {
        var pose = new Pose3d(); pose.position().set(x, y + 2, z);
        var handle = pipeline.addBox(new BoxPhysicsObject(pose, new Vector3d(.08), 1));
        try {
            for (int tick = 0; tick < 100; tick++) {
                pipeline.prePhysicsTicks();
                for (int step = 0; step < 5; step++) pipeline.physicsTick(.01);
            }
            handle.readPose(pose);
            return pose.position().y - y - .08;
        } finally { handle.remove(); }
    }
    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_tunnel",timeoutTicks=200)
    public static void explicitThroughHoleSurvivesAssemblyAndNativeObjectsFallThroughIt(GameTestHelper h) {
        var level=h.getLevel();var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();var ground=new BlockPos(400,199,256);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var p=ground.offset(x,0,z);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());upload(h,pipeline,p);
        }
        var source=new BlockPos(2,4,2);h.setBlock(source,Copypoly.POLY.get());var graph=TopologyFixtures.tunnel();
        com.caca.copypoly.api.CopypolyApi.setShape(level,h.absolutePos(source),graph);var original=h.absolutePos(source);
        var body=SubLevelAssemblyHelper.assembleBlocks(level,original,java.util.List.of(original),new BoundingBox3i(original,original));
        h.runAfterDelay(3,()->{
            try {
                var target=body.getPlot().getCenterBlock();h.assertTrue(com.caca.copypoly.api.CopypolyApi.getShape(level,target).equals(graph),"Assembly lost tunnel topology");
                pipeline.teleport(body,new Vector3d(ground.getX()+.5,200.5,ground.getZ()+.5),new Quaterniond());pipeline.resetVelocity(body);
                height(h,0,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Native object could not pass through the tunnel");
                height(h,1,drop(h,pipeline,ground.getX()+.125,200,ground.getZ()+.5),"Native object fell through the solid rim");h.succeed();
            } finally {body.markRemoved();SubLevelContainer.getContainer(level).processSubLevelRemovals();}
        });
    }
    private static void height(GameTestHelper h, double expected, double actual, String message) {
        h.assertTrue(Math.abs(expected - actual) < .04, message + ": expected " + expected + ", got " + actual);
    }
    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_line_inset",timeoutTicks=200)
    public static void normalVertexLineCutsHaveAFlatNativeBottomAndRestoreAfterDeletion(GameTestHelper h) {
        var level=h.getLevel();var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();var ground=new BlockPos(416,199,256);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=ground.offset(x,0,z);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());upload(h,pipeline,p);}
        var source=new BlockPos(2,4,2);h.setBlock(source,Copypoly.POLY.get());var shape=TopologyFixtures.lineInset();
        com.caca.copypoly.api.CopypolyApi.setShape(level,h.absolutePos(source),shape);var original=h.absolutePos(source);
        var body=SubLevelAssemblyHelper.assembleBlocks(level,original,java.util.List.of(original),new BoundingBox3i(original,original));
        h.runAfterDelay(3,()->{
            try {
                var target=body.getPlot().getCenterBlock();h.assertTrue(com.caca.copypoly.api.CopypolyApi.getShape(level,target).equals(shape),"Assembly lost line reference surface");
                pipeline.teleport(body,new Vector3d(ground.getX()+.5,200.5,ground.getZ()+.5),new Quaterniond());pipeline.resetVelocity(body);
                height(h,.5,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Native collider filled the four-point inset");
                var b=shape.toBuilder();for(int id=8;id<12;id++)b.removeVertex(id);com.caca.copypoly.api.CopypolyApi.setShape(level,target,b.build());
                height(h,1,drop(h,pipeline,ground.getX()+.5,200,ground.getZ()+.5),"Deleting inset points did not restore native top surface");h.succeed();
            }finally{body.markRemoved();SubLevelContainer.getContainer(level).processSubLevelRemovals();}
        });
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Cannot inspect collider cache: " + name, e); }
    }

    private static ExplosionDamageCalculator onlyDestroy(BlockPos target) {
        return new ExplosionDamageCalculator() {
            @Override public java.util.Optional<Float> getBlockExplosionResistance(Explosion explosion,
                    BlockGetter world, BlockPos pos, BlockState state, FluidState fluid) {
                return java.util.Optional.of(0F);
            }
            @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter world,
                    BlockPos pos, BlockState state, float power) { return pos.equals(target); }
            @Override public boolean shouldDamageEntity(Explosion explosion, net.minecraft.world.entity.Entity entity) { return false; }
        };
    }

    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_explosion_cache",timeoutTicks=200)
    public static void repeatedExplosionsReuseSurvivingCollidersAndRecycleDestroyedHandles(GameTestHelper h) {
        var level = h.getLevel();
        var pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        var pos = new BlockPos(304,200,256);
        var neighbor = pos.east();
        var state = Copypoly.POLY.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var be = (PolyBlockEntity)level.getBlockEntity(pos);
        be.setPoly(slab(6));
        upload(h,pipeline,pos);
        var boxes = be.poly().collisionBoxes();
        var shape = be.shape();
        var cache = field(pipeline,"copypoly$colliders");
        var positions = (java.util.Map<?,?>)field(cache,"positions");
        var entry = positions.get(pos);
        h.assertTrue(entry != null,"Target has no cached native collider");
        var handles = ((Object[])field(entry,"handles")).clone();
        var current = field(entry,"current");
        for (int blast=0;blast<4;blast++) {
            level.setBlockAndUpdate(neighbor,Blocks.DIRT.defaultBlockState());
            level.explode(null,null,onlyDestroy(neighbor),Vec3.atCenterOf(neighbor),2F,false,Level.ExplosionInteraction.TNT);
            h.assertTrue(level.getBlockState(neighbor).isAir(),"Explosion did not destroy the neighboring block");
            h.assertTrue(level.getBlockEntity(pos) == be,"Explosion replaced the surviving block entity");
            h.assertTrue(be.poly().collisionBoxes() == boxes && be.shape() == shape,"Explosion rebuilt surviving geometry");
            h.assertTrue(positions.get(pos) == entry && current.equals(field(entry,"current"))
                    && java.util.Arrays.equals(handles,(Object[])field(entry,"handles")),"Explosion rebuilt surviving native handles");
        }
        height(h,.375,drop(h,pipeline,pos.getX()+.5,pos.getY(),pos.getZ()+.5),"Explosions changed surviving native collision");
        for (int blast=0;blast<4;blast++) {
            level.explode(null,null,onlyDestroy(pos),Vec3.atCenterOf(pos),2F,false,Level.ExplosionInteraction.TNT);
            h.assertTrue(level.getBlockState(pos).isAir() && !positions.containsKey(pos),"Destroyed collider was not released");
            var available = (java.util.Map<?,?>)field(cache,"available");
            var pooled = new java.util.HashSet<Object>();
            for (var pool : available.values()) pooled.addAll((java.util.Collection<?>)pool);
            level.setBlockAndUpdate(pos,state);
            be = (PolyBlockEntity)level.getBlockEntity(pos);
            be.setPoly(slab(6));
            for (var handle : (Object[])field(positions.get(pos),"handles"))
                h.assertTrue(handle == null || pooled.contains(handle),"Replacement allocated a new native handle despite the pool");
            height(h,.375,drop(h,pipeline,pos.getX()+.5,pos.getY(),pos.getZ()+.5),"Replacement lost its native shape");
        }
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly explosion cache probe passed: four neighboring blasts retained geometry and handles; four destructive blasts recycled handles with correct native contacts");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_contacts",timeoutTicks=200)
    public static void nativeTerrainUsesPerPositionShapesAndRefreshesAfterEdits(GameTestHelper h) {
        var level = h.getLevel();
        var pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        var left = new BlockPos(240,200,256); var right = new BlockPos(242,200,256);
        level.setBlockAndUpdate(left, Copypoly.POLY.get().defaultBlockState());
        level.setBlockAndUpdate(right, Copypoly.POLY.get().defaultBlockState());
        var a = (PolyBlockEntity)level.getBlockEntity(left); var b = (PolyBlockEntity)level.getBlockEntity(right);
        a.setPoly(slab(4)); b.setPoly(slab(12));
        var p = left; var q = right;
        upload(h,pipeline,p); if (!SectionPos.of(p).equals(SectionPos.of(q))) upload(h,pipeline,q);
        height(h,.25,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Thin terrain collider was replaced by a cube");
        height(h,.75,drop(h,pipeline,q.getX()+.5,q.getY(),q.getZ()+.5),"Same-state neighboring shape used the wrong collider");
        for (int pixels : new int[]{8, 2, 10, 4}) {
            a.setPoly(slab(pixels)); // Only the production BE hook refreshes native geometry.
            height(h,pixels/16.0,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"BE edit left stale native collision");
            height(h,.75,drop(h,pipeline,q.getX()+.5,q.getY(),q.getZ()+.5),"Editing another position corrupted this collider");
        }
        // Removal and placement must not revive an old native shape.
        level.setBlockAndUpdate(left, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(left, Copypoly.POLY.get().defaultBlockState());
        a = (PolyBlockEntity)level.getBlockEntity(left); a.setPoly(slab(6));
        height(h,.375,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Replaced block retained old collision");
        pipeline.handleChunkSectionRemoval(p.getX()>>4,p.getY()>>4,p.getZ()>>4);
        upload(h,pipeline,p);
        height(h,.375,drop(h,pipeline,p.getX()+.5,p.getY(),p.getZ()+.5),"Section reload lost custom collision");
        h.succeed();
    }

    @GameTest(template="empty",templateNamespace="copypoly",batch="sable_sublevel_contacts",timeoutTicks=200)
    public static void assembledPolyhedronCollidesWithCustomTerrainAndUpdatesInFlight(GameTestHelper h) {
        var level = h.getLevel();
        var pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        var ground = new BlockPos(272,200,256);
        for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) {
            var pos = ground.offset(x,0,z);
            level.setBlockAndUpdate(pos,Copypoly.POLY.get().defaultBlockState());
            ((PolyBlockEntity)level.getBlockEntity(pos)).setPoly(slab(6));
            upload(h,pipeline,pos);
        }
        var source = new BlockPos(2,4,2);
        h.setBlock(source,Copypoly.POLY.get());
        ((PolyBlockEntity)h.getBlockEntity(source)).setPoly(slab(4));
        var original = h.absolutePos(source);
        ServerSubLevel body = SubLevelAssemblyHelper.assembleBlocks(level,original,
                java.util.List.of(original),new BoundingBox3i(original,original));
        h.runAfterDelay(3, () -> {
            try {
                var plotPos = body.getPlot().getCenterBlock();
                h.assertTrue(level.getBlockEntity(plotPos) instanceof PolyBlockEntity,"Assembly lost the custom block entity");
                var be = (PolyBlockEntity)level.getBlockEntity(plotPos);
                h.assertTrue(be.poly() == slab(4),"Assembly lost saved vertices");
                // Upside down: a cached cube and a real quarter-height slab have
                // different bottom surfaces even though their mass centers match.
                for (int pixels : new int[]{4, 12, 2, 8}) {
                    if (pixels == 8) {
                        var tag = be.saveWithoutMetadata(level.registryAccess());
                        tag.putIntArray("Points",slab(pixels).packed());
                        be.loadWithComponents(tag,level.registryAccess());
                    } else if (pixels != 4) be.setPoly(slab(pixels));
                    pipeline.teleport(body,new Vector3d(ground.getX()+.5,ground.getY()+3,ground.getZ()+.5),new Quaterniond().rotateX(java.lang.Math.PI));
                    pipeline.resetVelocity(body);
                    for(int tick=0;tick<100;tick++) {
                        pipeline.prePhysicsTicks();
                        for(int step=0;step<5;step++) pipeline.physicsTick(.01);
                    }
                    var pose = new Pose3d(body.logicalPose()); pipeline.readPose(body,pose);
                    var bottom = new Vector3d(plotPos.getX()+.5,plotPos.getY()+pixels/16.0,plotPos.getZ()+.5);
                    pose.transformPosition(bottom);
                    height(h,.375,bottom.y-ground.getY(),"Sable structure used stale or missing polyhedron collision at "+pixels+" pixels");
                }
                h.succeed();
            } finally {
                body.markRemoved();
                SubLevelContainer.getContainer(level).processSubLevelRemovals();
            }
        });
    }

}
