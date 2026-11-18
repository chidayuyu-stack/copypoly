package com.caca.copypoly;

import com.caca.copypoly.client.PolyScreen;
import com.caca.copypoly.geometry.Polyhedron;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.lang.reflect.*;

/** Optional real client/sublevel regression check, excluded from the release jar. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class SableEditorInteractionProbe {
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(Boolean.getBoolean("copypoly.sableEditorSmoke") && net.neoforged.fml.ModList.get().isLoaded("sable")) Session.tick();
    }
    private static final class Session {
        private static final BlockPos SOURCE=new BlockPos(0,100,0);
        private static volatile BlockPos target;
        private static ServerSubLevel serverBody;
        private static int stage,ticks,total;
        private static PolyScreen editor;
        private static Vec3 previousPoint;

        static void tick() throws Exception {
            var mc=Minecraft.getInstance();ticks++;
            if(++total>3600)throw new IllegalStateException("Sable editor timed out at stage "+stage);
            if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);
                var settings=new LevelSettings("CopyPoly Sable editor check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("copypoly-sable-editor-"+System.currentTimeMillis(),settings,
                        new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
                next();return;
            }
            if(stage==1 && mc.level!=null && mc.player!=null && mc.screen==null) {
                mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();var level=server.overworld();
                    for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
                        for(int y=100;y<107;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    var system=SubLevelPhysicsSystem.require(level);system.setPaused(true);
                    level.setBlockAndUpdate(SOURCE,Copypoly.POLY.get().defaultBlockState());
                    ((PolyBlockEntity)level.getBlockEntity(SOURCE)).setMaterial(Blocks.STONE.defaultBlockState());
                    ((PolyBlockEntity)level.getBlockEntity(SOURCE)).setConsumedItem(new net.minecraft.world.item.ItemStack(Blocks.STONE));
                    serverBody=SubLevelAssemblyHelper.assembleBlocks(level,SOURCE,java.util.List.of(SOURCE),new BoundingBox3i(SOURCE,SOURCE));
                    SubLevelContainer.getContainer(level).addForceLoadTicket(serverBody,SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
                    system.getPipeline().teleport(serverBody,new Vector3d(.5,100.5,.5),new Quaterniond().rotateY(.4).rotateX(.15));
                    serverBody.updateLastPose();serverBody.forceUpdateGlobalBounds();
                    target=serverBody.getPlot().getCenterBlock().immutable();
                    level.setDayTime(6000);
                    var player=server.getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(2.8,100,3.8,145,25);
                });next();return;
            }
            if(stage==2 && ticks>100 && target!=null && mc.level.getBlockEntity(target) instanceof PolyBlockEntity
                    && Sable.HELPER.getContaining(mc.level,target) instanceof ClientSubLevel body
                    && body.renderPose().position().distanceSquared(new Vector3d(.5,100.5,.5))<1) {
                mc.getSingleplayerServer().execute(()->Copypoly.POLY.get().openEditor(mc.getSingleplayerServer().overworld(),target,
                        mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
                next();return;
            }
            if(stage==3 && ticks>30 && mc.screen instanceof PolyScreen s) {
                editor=s;assertAlignment(mc,s);
                previousPoint=project(s,new Vec3(1,1,1));
                s.mouseClicked(previousPoint.x,previousPoint.y,0);s.mouseReleased(previousPoint.x,previousPoint.y,0);
                check((int)field(field(s,"gizmo"),"selected")==7,"Physical structure vertex picking missed the visible point");
                shot(mc,"sable-editor-rotated.png");
                com.mojang.logging.LogUtils.getLogger().info("Sable editor initial render pose: {} / vertices: {} / finalized {} / sections {}",((ClientSubLevel)Sable.HELPER.getContaining(mc.level,target)).renderPose(),previousPoint,((ClientSubLevel)Sable.HELPER.getContaining(mc.level,target)).isFinalized(),((ClientSubLevel)Sable.HELPER.getContaining(mc.level,target)).getRenderData().getVisibleSectionCount());
                mc.getSingleplayerServer().execute(()->{
                    var system=SubLevelPhysicsSystem.require(mc.getSingleplayerServer().overworld());
                    system.getPipeline().teleport(serverBody,new Vector3d(1.4,100.9,.6),new Quaterniond().rotateY(.9).rotateZ(.18));
                    serverBody.updateLastPose();serverBody.forceUpdateGlobalBounds();
                });next();return;
            }
            if(stage==4 && ticks<50) {
                final double t=ticks/50.0;
                mc.getSingleplayerServer().execute(()->{
                    var system=SubLevelPhysicsSystem.require(mc.getSingleplayerServer().overworld());
                    system.getPipeline().teleport(serverBody,new Vector3d(.5+.9*t,100.5+.4*t,.5+.1*t),new Quaterniond().rotateY(.4+.5*t).rotateZ(.18*t));
                    serverBody.updateLastPose();serverBody.forceUpdateGlobalBounds();
                });return;
            }
            if(stage==4 && ticks>100) {
                check(mc.screen==editor,"Moving physical structure closed the editor");assertAlignment(mc,editor);
                com.mojang.logging.LogUtils.getLogger().info("Sable editor moved render pose: {} / vertices: {} / delta {}",((ClientSubLevel)Sable.HELPER.getContaining(mc.level,target)).renderPose(),project(editor,new Vec3(1,1,1)),project(editor,new Vec3(1,1,1)).distanceTo(previousPoint));
                check(project(editor,new Vec3(1,1,1)).distanceTo(previousPoint)>5,"Overlay did not follow moving/rotating structure");
                Vec3 handle=project(editor,new Vec3(1.4,1,1)),destination=project(editor,new Vec3(1.4-2.2/16,1,1));
                editor.mouseClicked(handle.x,handle.y,0);
                check((int)field(field(editor,"gizmo"),"dragAxis")==0,"Rotated local X arrow could not be picked");
                editor.mouseDragged(destination.x,destination.y,0,destination.x-handle.x,destination.y-handle.y);
                editor.mouseReleased(destination.x,destination.y,0);
                check(((Polyhedron)field(editor,"preview")).points().contains(new Polyhedron.Point(14,16,16)),"Physical structure drag used world axes instead of local pixel coordinates");
                check(((PolyBlockEntity)mc.level.getBlockEntity(target)).getModelData().get(PolyBlockEntity.GEOMETRY)
                        ==field(editor,"preview"),"Sublevel visual draft did not update");
                verifyCustomVertex(editor);
                verifyExplicitTopology(editor);
                verifyLineInset(editor);
                next();return;
            }
            if(stage==5 && ticks>20) {
                shot(mc,"sable-editor-moved-and-edited.png");
                editor.children().stream().filter(c->c instanceof Button b && b.getMessage().equals(Component.translatable("screen.copypoly.apply")))
                        .map(c->(Button)c).findFirst().orElseThrow().onPress();
                next();return;
            }
            if(stage==6 && ticks>40 && mc.screen==null) {
                check(((PolyBlockEntity)mc.level.getBlockEntity(target)).poly().points().contains(new Polyhedron.Point(14,16,16)),"Apply failed on a physical structure");
                check(((PolyBlockEntity)mc.level.getBlockEntity(target)).document().compile().activeCustomVertices().contains(8),"Apply lost custom graph on physical structure");
                check(mc.getCameraEntity()==mc.player,"Apply did not restore player camera");
                com.mojang.logging.LogUtils.getLogger().info("CopyPoly Sable editor probe passed: projected vertices aligned with render pose, rotated picking, follow translation/rotation, local-axis drag, right-click raycast/menu, custom drag links, automatic dent, Del restoration, undo and server graph save");
                next();mc.stop();
            }
        }
        private static void verifyCustomVertex(PolyScreen s) throws Exception {
            Vec3 p=project(s,new Vec3(.5,1,.5));s.mouseClicked(p.x,p.y,1);s.mouseReleased(p.x,p.y,1);
            check((boolean)field(s,"popupOpen"),"Rotated structure raycast did not open add menu");
            Button add=s.children().stream().filter(c->c instanceof Button b && b.getMessage().equals(Component.translatable("screen.copypoly.add_custom"))).map(c->(Button)c).findFirst().orElseThrow();
            s.mouseClicked(add.getX()+3,add.getY()+3,0);s.mouseReleased(add.getX()+3,add.getY()+3,0);
            check(document(s).vertex(8).position().equals(new Polyhedron.Point(8,16,8)),"Structure raycast used plot/world position instead of local coordinates");
            for(int id:new int[]{2,3,6,7}) {
                var source=document(s).vertex(8).position();var targetPoint=document(s).vertex(id).position();
                Vec3 a=project(s,new Vec3(source.x()/16,source.y()/16,source.z()/16)),b=project(s,new Vec3(targetPoint.x()/16,targetPoint.y()/16,targetPoint.z()/16));
                s.mouseClicked(a.x,a.y,0);check((int)field(s,"linkSource")==8,"Structure custom point could not be selected");
                s.mouseDragged(b.x,b.y,0,b.x-a.x,b.y-a.y);s.mouseReleased(b.x,b.y,0);
            }
            @SuppressWarnings("unchecked") var fields=(java.util.List<net.minecraft.client.gui.components.EditBox[]>)field(s,"fields");
            fields.getFirst()[1].setValue("4");var validate=PolyScreen.class.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(s);
            var expected=document(s);check(expected.compile().activeCustomVertices().contains(8) && !expected.compile().geometry().contains(8,12,8),"Structure links did not create a dent");
            // Sable single-block rendering bypasses BakedModel.getModelData().
            // Verify its exact raw-data path uses nonempty material layers and
            // the edited geometry instead of the inventory cube fallback.
            var mc=Minecraft.getInstance();var be=(PolyBlockEntity)mc.level.getBlockEntity(target);
            var model=mc.getBlockRenderer().getBlockModel(be.getBlockState());var data=be.getModelData();
            var random=net.minecraft.util.RandomSource.create(42);int quads=0;boolean foundDent=false;
            var wrapper=new dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper();
            wrapper.setup(mc.level,1.4,100.9,.6,target,be.getBlockState());
            var layers=dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform.INSTANCE.getRenderLayers(wrapper,model,be.getBlockState(),target,random);wrapper.clear();
            check(new java.util.HashSet<>(layers).equals(new java.util.HashSet<>(model.getRenderTypes(be.getBlockState(),random,data).asList())),"Sable wrapper discovered wrong material layers");
            for(var layer:layers) {
                var rendered=model.getQuads(be.getBlockState(),null,random,data,layer);quads+=rendered.size();
                for(var quad:rendered){int[] v=quad.getVertices();int stride=v.length/4;for(int n=0;n<4;n++)if(Math.abs(Float.intBitsToFloat(v[n*stride])-.5)<1e-6 && Math.abs(Float.intBitsToFloat(v[n*stride+1])-.25)<1e-6 && Math.abs(Float.intBitsToFloat(v[n*stride+2])-.5)<1e-6)foundDent=true;}
                check(rendered.stream().allMatch(q->q.getSprite().contents().name().toString().equals("minecraft:block/stone")),"Raw Sable model data lost the copied material");
            }
            check(quads>0 && foundDent,"Raw Sable model path omitted the actual custom geometry");
            s.setFocused(null);s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
            check(document(s).compile().geometry().contains(8,12,8),"Structure deletion did not restore surface");
            s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
            check(document(s).equals(expected),"Structure undo lost custom links");
        }
        private static com.caca.copypoly.api.PolyShape document(PolyScreen s) throws Exception{return (com.caca.copypoly.api.PolyShape)field(s,"document");}
        private static void verifyLineInset(PolyScreen s) throws Exception {
            var mc=Minecraft.getInstance();String clipboard=mc.keyboardHandler.getClipboard();var before=document(s);
            try {
                mc.keyboardHandler.setClipboard(com.caca.copypoly.api.PolyShapeCodec.toText(TopologyFixtures.lineInset()));
                var paste=PolyScreen.class.getDeclaredMethod("pasteWholeShape");paste.setAccessible(true);paste.invoke(s);
                check(document(s).lineMesh() && !((Polyhedron)field(s,"preview")).contains(8,12,8),"Structure lost the connected inset");
                var a=document(s).vertex(8).position();var b=document(s).vertex(2).position();Vec3 p=project(s,new Vec3(a.x()/16,a.y()/16,a.z()/16)),q=project(s,new Vec3(b.x()/16,b.y()/16,b.z()/16));
                var visible=PolyScreen.class.getDeclaredMethod("handlesVisible");visible.setAccessible(true);
                Vec3 midpoint=p.lerp(q,.5);var linked=document(s);
                s.mouseClicked(midpoint.x,midpoint.y,0);s.mouseReleased(midpoint.x,midpoint.y,0);
                check(field(s,"selectedConnection")!=null && ((java.util.Set<?>)field(s,"selectedFaces")).isEmpty() && !(boolean)visible.invoke(s),"Rotated structure line could not be selected");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
                check(!document(s).targets(2).contains(8) && document(s).vertices().equals(linked.vertices()),"Structure line Del did not preserve endpoints");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                check(document(s).equals(linked) && field(s,"selectedConnection")!=null,"Structure line undo lost selection or topology");
                s.mouseClicked(p.x,p.y,0);check(!(boolean)visible.invoke(s),"Structure vertex press displayed axes");
                s.mouseDragged(q.x,q.y,0,q.x-p.x,q.y-p.y);check(!(boolean)visible.invoke(s),"Structure line drag displayed axes");s.mouseReleased(q.x,q.y,0);
                check(!document(s).targets(2).contains(8),"Normal-to-normal reverse line did not disconnect on structure");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                check(document(s).equals(TopologyFixtures.lineInset()),"Structure undo lost cut references");
                p=project(s,new Vec3(.3,.5,1));s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
                check(!((java.util.Set<?>)field(s,"selectedFaces")).isEmpty(),"Default face selection failed on rotated structure");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
                s.children().stream().filter(c->c instanceof Button button && button.getMessage().equals(Component.translatable("screen.copypoly.apply"))).map(c->(Button)c).findFirst().orElseThrow().onPress();
                check((boolean)field(s,"showOpenings") && !((java.util.List<?>)field(s,"openingFaces")).isEmpty(),"Rotated structure did not mark an open face");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                check(document(s).equals(before),"Structure face undo did not restore prior document");s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1,0,0);
            }finally{if(clipboard.isEmpty())org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");else mc.keyboardHandler.setClipboard(clipboard);}
        }
        private static void verifyExplicitTopology(PolyScreen s) throws Exception {
            var mc=Minecraft.getInstance();String clipboard=mc.keyboardHandler.getClipboard();var before=document(s);
            try {
                mc.keyboardHandler.setClipboard(com.caca.copypoly.api.PolyShapeCodec.toText(TopologyFixtures.tunnel()));
                var paste=PolyScreen.class.getDeclaredMethod("pasteWholeShape");paste.setAccessible(true);paste.invoke(s);
                check(document(s).manualMesh() && !((Polyhedron)field(s,"preview")).contains(8,8,8),"Physical structure draft filled explicit tunnel");
                var be=(PolyBlockEntity)mc.level.getBlockEntity(target);var model=mc.getBlockRenderer().getBlockModel(be.getBlockState());
                var data=be.getModelData();var random=net.minecraft.util.RandomSource.create(42);boolean innerVertex=false;
                for(var layer:model.getRenderTypes(be.getBlockState(),random,data))for(var q:model.getQuads(be.getBlockState(),null,random,data,layer)) {
                    int[] v=q.getVertices();int stride=v.length/4;
                    for(int i=0;i<4;i++)if(Float.intBitsToFloat(v[i*stride])==.25f && Float.intBitsToFloat(v[i*stride+1])==1 && Float.intBitsToFloat(v[i*stride+2])==.25f)innerVertex=true;
                }
                check(innerVertex,"Sable renderer lost tunnel rim topology");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2,0,0);Vec3 p=project(s,new Vec3(.125,1,.5));
                s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
                check(!((java.util.Set<?>)field(s,"selectedFaces")).isEmpty(),"Rotated structure face selection missed its rim");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
                check(!(boolean)field(s,"valid") && !((Polyhedron)field(s,"preview")).closed(),"Physical structure did not show open draft after face deletion");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                check(document(s).equals(TopologyFixtures.tunnel()),"Undo lost explicit mesh on structure");
                s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                check(document(s).equals(before),"Undo did not restore prior automatic document");s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1,0,0);
            } finally {
                if(clipboard.isEmpty())org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");else mc.keyboardHandler.setClipboard(clipboard);
            }
        }
        private static void assertAlignment(Minecraft mc,PolyScreen screen) throws Exception {
            var body=(ClientSubLevel)Sable.HELPER.getContaining(mc.level,target);
            float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);
            var pose=body.renderPose(partial);
            for(var p:Polyhedron.CUBE_POINTS) {
                var local=new Vec3(p.x()/16,p.y()/16,p.z()/16);var screenPoint=project(screen,local);
                check(screenPoint!=null && screenPoint.x>190 && screenPoint.x<screen.width && screenPoint.y>0 && screenPoint.y<screen.height,"Physical structure vertex disappeared or projected outside canvas");
                // Independent world-point projection using the actual Sable pose.
                var expected=pose.transformPosition(Vec3.atLowerCornerOf(target).add(local));
                var camera=mc.gameRenderer.getMainCamera().getPosition();
                var frame=field(screen,"worldView");var matrix=(org.joml.Matrix4f)field(frame,"clipFromCamera");
                var clip=new org.joml.Vector4f((float)(expected.x-camera.x),(float)(expected.y-camera.y),(float)(expected.z-camera.z),1).mul(matrix);
                double x=(clip.x/clip.w+1)*screen.width/2,y=(1-clip.y/clip.w)*screen.height/2;
                check(Math.hypot(screenPoint.x-x,screenPoint.y-y)<.5,"Overlay does not align with rendered Sable block");
            }
        }
        private static Vec3 project(PolyScreen s,Vec3 point) throws Exception {
            var method=PolyScreen.class.getDeclaredMethod("editProject",Vec3.class);method.setAccessible(true);return (Vec3)method.invoke(s,point);
        }
        private static Object field(Object object,String name) throws Exception {
            Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
        }
        private static void shot(Minecraft mc,String name) {
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->com.mojang.logging.LogUtils.getLogger().info("Sable editor screenshot: {}",m.getString()));
        }
        private static void check(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
        private static void next(){stage++;ticks=0;}
    }
}
