package com.caca.copypoly;

import com.caca.copypoly.api.*;
import com.caca.copypoly.client.PolyScreen;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
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
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.*;
import java.util.*;

/** Drives the actual screen's face picking, F/Del/undo and apply paths. Not packaged. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class TopologyInteractionProbe {
    private static final BlockPos TARGET=new BlockPos(0,100,0);
    private static int stage,ticks,total;
    private static String clipboard;
    private static PolyShape saved;
    private static Vec3 body,camera;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("copypoly.topologySmoke"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(++total>6000)throw new IllegalStateException("Topology probe timed out at "+stage);
        if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
            mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);clipboard=mc.keyboardHandler.getClipboard();
            var settings=new LevelSettings("CopyPoly arbitrary mesh check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("copypoly-topology-"+System.currentTimeMillis(),settings,new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);next();return;
        }
        if(stage==1 && mc.level!=null && mc.player!=null && mc.screen==null) {
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
                    for(int y=100;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(TARGET,Copypoly.POLY.get().defaultBlockState());
                var be=(PolyBlockEntity)level.getBlockEntity(TARGET);be.setMaterial(Blocks.STONE.defaultBlockState());
                be.setConsumedItem(new net.minecraft.world.item.ItemStack(Blocks.STONE));level.setDayTime(6000);
                server.getPlayerList().getPlayers().getFirst().connection.teleport(2.8,100,3.8,145,20);
            });next();return;
        }
        if(stage==2 && ticks>60 && mc.level.getBlockEntity(TARGET) instanceof PolyBlockEntity){open(mc);next();return;}
        if(stage==3 && ticks>30 && mc.screen instanceof PolyScreen s) {
            body=mc.player.position();camera=mc.getCameraEntity().position();
            s.keyPressed(GLFW.GLFW_KEY_2,0,0);Vec3 p=project(s,new Vec3(.3,1,.4));
            s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
            check(((Set<?>)field(s,"selectedFaces")).size()==2,"Face picking did not group coplanar cube triangles");
            s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(doc(s).manualMesh() && doc(s).baseFaces().size()==10 && !(boolean)field(s,"valid"),"Deleting a face silently capped the open mesh");
            button(s,"apply").onPress();check((boolean)field(s,"showOpenings") && !((List<?>)field(s,"openingFaces")).isEmpty(),"Apply did not mark the missing face");
            check(!preview(s).closed() && preview(s).faces().size()==10,"Open draft preview retained the old cube");next();return;
        }
        if(stage==4 && ticks>20 && mc.screen instanceof PolyScreen s) {
            shot(mc,"topology-open-draft.png");s.keyPressed(GLFW.GLFW_KEY_1,0,0);
            select(s,List.of(2,3,7,6));s.keyPressed(GLFW.GLFW_KEY_F,0,0);
            check(doc(s).baseFaces().size()==12 && button(s,"apply").active,"F did not fill the perimeter hole with a quad");
            var before=doc(s);s.keyPressed(GLFW.GLFW_KEY_2,0,0);shortcut(s,GLFW.GLFW_KEY_A);s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(doc(s).baseFaces().isEmpty() && !(boolean)field(s,"valid"),"Face Ctrl+A/Del failed");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Undo did not restore deleted topology");
            s.keyPressed(GLFW.GLFW_KEY_1,0,0);button(s,"clear_faces").onPress();
            // Right-click a now-empty block volume still permits vertex creation.
            Vec3 p=project(s,new Vec3(.5,1,.5));s.mouseClicked(p.x,p.y,1);s.mouseReleased(p.x,p.y,1);
            check((boolean)field(s,"popupOpen"),"Empty surface lost its context menu");
            button(s,"add_custom").onPress();check(doc(s).vertices().size()==9,"Empty mesh could not create a vertex");shortcut(s,GLFW.GLFW_KEY_Z);
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Undo did not restore clearing faces");
            // Build every face of a genuine through-hole using the user's F action.
            var empty=TopologyFixtures.tunnel().toBuilder().meshFaces(List.of()).build();
            mc.keyboardHandler.setClipboard(PolyShapeCodec.toText(empty));button(s,"paste").onPress();
            for(var wall:TopologyFixtures.WALLS){select(s,Arrays.stream(wall).boxed().toList());s.keyPressed(GLFW.GLFW_KEY_F,0,0);}
            saved=doc(s);assertTunnel(s);select(s,List.of());next();return;
        }
        if(stage==5 && ticks>20 && mc.screen instanceof PolyScreen s) {
            shot(mc,"topology-tunnel-world.png");check(mc.player.position().distanceToSqr(body)<1e-8 && mc.getCameraEntity().position().distanceToSqr(camera)<1e-8,"Face gestures moved the camera or player");
            button(s,"table").onPress();next();return;
        }
        if(stage==6 && ticks>20 && mc.screen instanceof PolyScreen s) {
            s.keyPressed(GLFW.GLFW_KEY_2,0,0);Vec3 p=project(s,new Vec3(.1,.6,.1));
            s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
            check(!((Set<?>)field(s,"selectedFaces")).isEmpty(),"Preview face picking failed");
            s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);check(!(boolean)field(s,"valid"),"Preview open mesh could be applied");
            button(s,"apply").onPress();check((boolean)field(s,"showOpenings"),"Preview Apply did not diagnose open faces");
            shortcut(s,GLFW.GLFW_KEY_Z);assertTunnel(s);
            button(s,"copy").onPress();button(s,"reset").onPress();button(s,"paste").onPress();check(doc(s).equals(saved),"Clipboard lost explicit topology");
            s.keyPressed(GLFW.GLFW_KEY_1,0,0);Field pitch=PolyScreen.class.getDeclaredField("pitch");pitch.setAccessible(true);pitch.setDouble(s,1.12);next();return;
        }
        if(stage==7 && ticks>20 && mc.screen instanceof PolyScreen s) {
            shot(mc,"topology-tunnel-preview.png");button(s,"world").onPress();button(s,"apply").onPress();next();return;
        }
        if(stage==8 && ticks>25 && mc.screen==null) {
            var be=(PolyBlockEntity)mc.level.getBlockEntity(TARGET);check(be.document().equals(saved) && !be.poly().contains(8,8,8),"Server sync filled the through-hole");open(mc);next();return;
        }
        if(stage==9 && ticks>25 && mc.screen instanceof PolyScreen s) {
            check(doc(s).equals(saved),"Reopening lost topology");assertTunnel(s);
            // Move a surface vertex inward; topology stays fixed rather than rebuilding a hull.
            select(s,List.of(9));@SuppressWarnings("unchecked") var fields=(List<net.minecraft.client.gui.components.EditBox[]>)field(s,"fields");
            fields.getFirst()[0].setValue("14");call(s,"validate");check(doc(s).baseFaces().equals(saved.baseFaces()),"Moving a manual vertex rebuilt the convex hull");
            s.keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL);check(doc(s).equals(saved),"Coordinate undo changed explicit topology");s.onClose();
            if(clipboard!=null && !clipboard.isEmpty())mc.keyboardHandler.setClipboard(clipboard);else GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");
            com.mojang.logging.LogUtils.getLogger().info("CopyPoly topology probe passed: world/preview face picking, coplanar selection, open drafts, F perimeter triangulation, face Del/Ctrl+A, clear faces, undo, empty-volume vertex creation, 16-vertex tunnel built face by face, clipboard, server apply/reopen and topology-preserving movement");
            stage=10;mc.stop();
        }
    }
    private static void assertTunnel(PolyScreen s) throws Exception {check(doc(s).manualMesh() && doc(s).baseFaces().size()==32 && doc(s).vertices().size()==16 && !preview(s).contains(8,8,8) && preview(s).contains(2,8,8) && button(s,"apply").active,"Explicit mesh filled the tunnel or failed to close");}
    private static PolyShape doc(PolyScreen s) throws Exception{return (PolyShape)field(s,"document");}
    private static Polyhedron preview(PolyScreen s) throws Exception{return (Polyhedron)field(s,"preview");}
    private static void select(PolyScreen s,List<Integer> indices) throws Exception {((Set<?>)field(s,"selectedFaces")).clear();Object gizmo=field(s,"gizmo");var m=gizmo.getClass().getDeclaredMethod("replaceSelection",Collection.class);m.setAccessible(true);m.invoke(gizmo,indices);call(s,"rebuild");}
    private static void shortcut(PolyScreen s,int key){s.keyPressed(key,0,GLFW.GLFW_MOD_CONTROL);}
    private static Object field(Object o,String name) throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object call(PolyScreen s,String name) throws Exception{var m=PolyScreen.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(s);}
    private static Vec3 project(PolyScreen s,Vec3 p) throws Exception{var m=PolyScreen.class.getDeclaredMethod("editProject",Vec3.class);m.setAccessible(true);return (Vec3)m.invoke(s,p);}
    private static Button button(PolyScreen s,String key){return s.children().stream().filter(c->c instanceof Button b && b.getMessage().equals(Component.translatable("screen.copypoly."+key))).map(c->(Button)c).findFirst().orElseThrow();}
    private static void open(Minecraft mc){mc.getSingleplayerServer().execute(()->Copypoly.POLY.get().openEditor(mc.getSingleplayerServer().overworld(),TARGET,mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
    private static void next(){stage++;ticks=0;}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
