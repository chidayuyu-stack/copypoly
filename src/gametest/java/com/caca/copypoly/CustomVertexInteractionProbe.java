package com.caca.copypoly;

import com.caca.copypoly.api.*;
import com.caca.copypoly.client.PolyScreen;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

/** Opt-in real client interaction check. This source set is excluded from release jars. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class CustomVertexInteractionProbe {
    private static final BlockPos TARGET=new BlockPos(0,100,0);
    private static int stage,ticks,total;
    private static PolyShape saved;
    private static String clipboard;
    private static Vec3 body,camera;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("copypoly.customVertexSmoke"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(++total>6000)throw new IllegalStateException("Custom vertex probe timed out: "+stage);
        if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
            mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
            clipboard=mc.keyboardHandler.getClipboard();
            var settings=new LevelSettings("CopyPoly custom vertex check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("copypoly-custom-vertices-"+System.currentTimeMillis(),settings,new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
            next();return;
        }
        if(stage==1 && mc.level!=null && mc.player!=null && mc.screen==null) {
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
                    for(int y=100;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(TARGET,Copypoly.POLY.get().defaultBlockState());
                ((PolyBlockEntity)level.getBlockEntity(TARGET)).setMaterial(Blocks.STONE.defaultBlockState());level.setDayTime(6000);
                server.getPlayerList().getPlayers().getFirst().connection.teleport(2.8,100,3.8,145,20);
            });next();return;
        }
        if(stage==2 && ticks>60 && mc.level.getBlockEntity(TARGET) instanceof PolyBlockEntity) {open(mc);next();return;}
        if(stage==3 && ticks>30 && mc.screen instanceof PolyScreen s) {
            body=mc.player.position();camera=mc.getCameraEntity().position();
            check(mc.getCameraEntity()!=mc.player,"Camera is still attached to player");
            check(fields(s).isEmpty(),"Unselected coordinates are still listed");
            addAt(s,new Vec3(.5,1,.5),"add_custom");
            check(doc(s).vertex(8).type()==VertexType.CUSTOM && doc(s).vertex(8).position().equals(new Polyhedron.Point(8,16,8)),"Context menu added wrong vertex");
            check(doc(s).compile().pending().containsKey(8),"Unconnected custom vertex altered surface");
            for(int target:new int[]{2,3,6,7})link(s,8,target);
            check(doc(s).compile().activeCustomVertices().contains(8),"Four drag links did not split the top face");
            check(fields(s).size()==1,"Coordinate panel lists unselected vertices");
            fields(s).getFirst()[1].setValue("4");call(s,"validate");
            check(!preview(s).contains(8,12,8),"Coordinate edit did not make a dent");
            s.setFocused(null);s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(doc(s).vertices().size()==8 && preview(s).contains(8,12,8),"Del did not delete custom vertex and restore original face");
            shortcut(s,GLFW.GLFW_KEY_Z);
            check(doc(s).vertices().size()==9 && !preview(s).contains(8,12,8),"Undo did not restore custom point, links and dent");
            verifyAxisUndo(s);
            // A single custom vertex copy stays custom, without links to the old object.
            shortcut(s,GLFW.GLFW_KEY_C);shortcut(s,GLFW.GLFW_KEY_V);
            check(doc(s).vertices().size()==10 && doc(s).vertices().getLast().type()==VertexType.CUSTOM && doc(s).targets(9).isEmpty(),"Selected custom clipboard did not preserve type or isolate links");
            shortcut(s,GLFW.GLFW_KEY_Z);
            var before=doc(s);addAt(s,new Vec3(1,.5,.5),"add_normal");
            check(doc(s).vertices().getLast().type()==VertexType.NORMAL,"Normal context menu created a custom point");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Undo did not remove normal vertex creation");
            select(s,List.of(2,8));s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(doc(s).vertices().size()==7,"Del did not remove multiple selected vertices");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Undo did not restore a multi-delete atomically");
            select(s,List.of(8));saved=doc(s);next();return;
        }
        if(stage==4 && ticks>20 && mc.screen instanceof PolyScreen s) {
            Screenshot.grab(mc.gameDirectory,"custom-vertex-world.png",mc.getMainRenderTarget(),message->{});
            check(mc.player.position().distanceToSqr(body)<1e-8,"Editor moved player");
            check(mc.getCameraEntity().position().distanceToSqr(camera)<1e-8,"Popup or link gestures moved camera");
            button(s,"table").onPress();next();return;
        }
        if(stage==5 && ticks>20 && mc.screen instanceof PolyScreen s) {
            // Same context menu and linking work in the standalone model preview.
            var before=doc(s);addAt(s,new Vec3(.5,0,.5),"add_normal");
            check(doc(s).vertices().size()==10,"Preview context menu did not create a vertex");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Preview creation undo changed existing graph");
            link(s,8,2);check(!doc(s).targets(8).contains(2),"Repeated drag did not disconnect");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before),"Undo did not restore connection");
            button(s,"copy").onPress();button(s,"reset").onPress();button(s,"paste").onPress();
            check(doc(s).equals(before),"Whole-shape clipboard lost graph");
            verifyMarquee(s);select(s,List.of(8));next();return;
        }
        if(stage==6 && ticks>20 && mc.screen instanceof PolyScreen s) {
            Screenshot.grab(mc.gameDirectory,"custom-vertex-preview.png",mc.getMainRenderTarget(),message->{});
            button(s,"world").onPress();button(s,"apply").onPress();next();return;
        }
        if(stage==7 && ticks>30 && mc.screen==null) {
            check(((PolyBlockEntity)mc.level.getBlockEntity(TARGET)).document().equals(saved),"Server apply/client sync lost graph");open(mc);next();return;
        }
        if(stage==8 && ticks>25 && mc.screen instanceof PolyScreen s) {
            check(doc(s).equals(saved),"Reopen lost document");select(s,List.of(8));s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(preview(s).contains(8,12,8),"Reopened graph could not restore original surface");shortcut(s,GLFW.GLFW_KEY_Z);
            check(doc(s).equals(saved),"Reopened graph undo failed");s.onClose();
            check(mc.getCameraEntity()==mc.player,"Closing editor did not restore player camera");
            if(clipboard!=null && !clipboard.isEmpty())mc.keyboardHandler.setClipboard(clipboard);else GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");
            com.mojang.logging.LogUtils.getLogger().info("CopyPoly custom vertex probe passed: world/preview context menus, manual links/unlinks, automatic patch split and restoration, selected coordinates, axis drag, Ctrl clipboard, multi-Del, Ctrl+Z, marquee replacement, server apply and reopen");
            stage=9;mc.stop();
        }
    }
    private static void verifyAxisUndo(PolyScreen s) throws Exception {
        select(s,List.of(8));var original=doc(s);
        Vec3 handle=project(s,new Vec3(.5,.25+.4,.5));Vec3 mid=project(s,new Vec3(.5,.25+.4+1.2/16,.5)),end=project(s,new Vec3(.5,.25+.4+2.2/16,.5));
        s.mouseClicked(handle.x,handle.y,0);check((int)field(field(s,"gizmo"),"dragAxis")==1,"Custom Y handle could not be dragged");
        s.mouseDragged(mid.x,mid.y,0,mid.x-handle.x,mid.y-handle.y);s.mouseDragged(end.x,end.y,0,end.x-mid.x,end.y-mid.y);s.mouseReleased(end.x,end.y,0);
        check(doc(s).vertex(8).position().y()==6,"Axis drag did not snap custom vertex");
        shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(original),"Axis gesture required more than one undo");
    }
    private static void verifyMarquee(PolyScreen s) throws Exception {
        select(s,List.of(8));Object marquee=field(s,"marquee");
        var begin=marquee.getClass().getDeclaredMethod("begin",double.class,double.class);begin.setAccessible(true);
        var points=new ArrayList<Vec3>();for(var v:doc(s).vertices())points.add(project(s,new Vec3(v.position().x()/16,v.position().y()/16,v.position().z()/16)));
        double x0=points.stream().mapToDouble(v->v.x).min().orElseThrow()-3,x1=points.stream().mapToDouble(v->v.x).max().orElseThrow()+3;
        double y0=points.stream().mapToDouble(v->v.y).min().orElseThrow()-3,y1=points.stream().mapToDouble(v->v.y).sorted().toArray()[3];
        begin.invoke(marquee,x0,y0);s.mouseDragged(x1,y1,0,x1-x0,y1-y0);s.mouseReleased(x1,y1,0);
        check(!selected(s).contains(8) && !selected(s).isEmpty(),"Marquee did not replace old selection");
        double x=(int)field(s,"previewX")+3,y=(int)field(s,"topPos")+48;
        begin.invoke(marquee,x,y);s.mouseDragged(x+8,y+8,0,8,8);s.mouseReleased(x+8,y+8,0);
        check(selected(s).isEmpty() && fields(s).isEmpty(),"Empty marquee did not clear selection/coordinates");
    }
    private static void addAt(PolyScreen s,Vec3 local,String kind) throws Exception {
        Vec3 at=project(s,local);check(at!=null,"Context point is not projected");
        s.mouseClicked(at.x,at.y,1);s.mouseReleased(at.x,at.y,1);
        check((boolean)field(s,"popupOpen"),"Right click did not open add popup");
        Button b=button(s,kind);s.mouseClicked(b.getX()+3,b.getY()+3,0);s.mouseReleased(b.getX()+3,b.getY()+3,0);
    }
    private static void link(PolyScreen s,int source,int target) throws Exception {
        var a=doc(s).vertices().get(source).position();var b=doc(s).vertices().get(target).position();
        Vec3 p=project(s,new Vec3(a.x()/16,a.y()/16,a.z()/16)),q=project(s,new Vec3(b.x()/16,b.y()/16,b.z()/16));
        s.mouseClicked(p.x,p.y,0);check((int)field(s,"linkSource")==source,"Custom vertex did not start link gesture");
        s.mouseDragged(q.x,q.y,0,q.x-p.x,q.y-p.y);s.mouseReleased(q.x,q.y,0);
    }
    private static PolyShape doc(PolyScreen s) throws Exception{return (PolyShape)field(s,"document");}
    private static Polyhedron preview(PolyScreen s) throws Exception{return (Polyhedron)field(s,"preview");}
    @SuppressWarnings("unchecked") private static List<EditBox[]> fields(PolyScreen s) throws Exception{return (List<EditBox[]>)field(s,"fields");}
    @SuppressWarnings("unchecked") private static List<Integer> selected(PolyScreen s) throws Exception{return (List<Integer>)call(s,"selectedIds");}
    private static void select(PolyScreen s,List<Integer> indices) throws Exception {
        ((Set<?>)field(s,"selectedFaces")).clear();Field axes=PolyScreen.class.getDeclaredField("axesVisible");axes.setAccessible(true);axes.setBoolean(s,true);
        Object gizmo=field(s,"gizmo");var m=gizmo.getClass().getDeclaredMethod("replaceSelection",Collection.class);m.setAccessible(true);m.invoke(gizmo,indices);call(s,"rebuild");
    }
    private static void shortcut(PolyScreen s,int key){s.keyPressed(key,0,GLFW.GLFW_MOD_CONTROL);}
    private static Object field(Object o,String name) throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object call(PolyScreen s,String name) throws Exception {var m=PolyScreen.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(s);}
    private static Vec3 project(PolyScreen s,Vec3 p) throws Exception {var m=PolyScreen.class.getDeclaredMethod("editProject",Vec3.class);m.setAccessible(true);return (Vec3)m.invoke(s,p);}
    private static Button button(PolyScreen s,String key) {return s.children().stream().filter(c->c instanceof Button b && b.getMessage().equals(Component.translatable("screen.copypoly."+key))).map(c->(Button)c).findFirst().orElseThrow();}
    private static void open(Minecraft mc){mc.getSingleplayerServer().execute(()->Copypoly.POLY.get().openEditor(mc.getSingleplayerServer().overworld(),TARGET,mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));}
    private static void next(){stage++;ticks=0;}
    private static void check(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
