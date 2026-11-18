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

/** End-to-end click/release versus drag, and the user's four-point inset example. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class LineInteractionProbe {
    private static final BlockPos TARGET=new BlockPos(0,100,0);
    private static final int[][] CUTS={{2,8},{9,6},{7,10},{11,3},{8,9},{10,9},{10,11},{8,11}};
    private static int stage,ticks,total;
    private static String clipboard;
    private static PolyShape saved;
    private static Vec3 body,camera,target;
    private static long openingStarted;
    private static PolyShape openDraft;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("copypoly.lineSmoke"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(++total>6000)throw new IllegalStateException("Line probe timed out at "+stage);
        if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
            clipboard=mc.keyboardHandler.getClipboard();
            var settings=new LevelSettings("CopyPoly automatic line cuts",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("copypoly-line-cuts-"+System.currentTimeMillis(),settings,new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);next();return;
        }
        if(stage==1 && mc.level!=null && mc.player!=null && mc.screen==null) {
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
                    for(int y=100;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(TARGET,Copypoly.POLY.get().defaultBlockState());var be=(PolyBlockEntity)level.getBlockEntity(TARGET);
                be.setMaterial(Blocks.STONE.defaultBlockState());be.setConsumedItem(new net.minecraft.world.item.ItemStack(Blocks.STONE));level.setDayTime(6000);
                server.getPlayerList().getPlayers().getFirst().connection.teleport(2.8,100,3.8,145,20);
            });next();return;
        }
        if(stage==2 && ticks>60 && mc.level.getBlockEntity(TARGET) instanceof PolyBlockEntity){open(mc);next();return;}
        if(stage==3 && ticks>30 && mc.screen instanceof PolyScreen s) {
            body=mc.player.position();camera=mc.getCameraEntity().position();
            check(!(boolean)field(s,"advanced"),"Advanced mesh controls are still the default workflow");
            Vec3 p=point(s,7);s.mouseClicked(p.x,p.y,0);
            check(!handles(s) && (int)field(field(s,"gizmo"),"selected")==-1,"Mouse down selected a vertex or displayed arrows before click release");
            s.mouseReleased(p.x,p.y,0);check(handles(s) && (int)field(field(s,"gizmo"),"selected")==7,"Click release did not select/show arrows");
            // Already-selected arrows must disappear even before a link drag starts.
            p=point(s,3);s.mouseClicked(p.x,p.y,0);check(!handles(s) && (int)field(field(s,"gizmo"),"selected")==7,"Pressing a normal vertex changed the old selection");
            target=point(s,6);s.mouseDragged(target.x,target.y,0,target.x-p.x,target.y-p.y);
            check(!handles(s) && (boolean)field(s,"linkDragging"),"Normal vertex drag did not become an arrow-free line gesture");next();return;
        }
        if(stage==4 && ticks>5 && mc.screen instanceof PolyScreen s) {
            check(!handles(s),"Arrows appeared during a held line drag");shot(mc,"line-drag-without-arrows.png");
            s.mouseReleased(target.x,target.y,0);check(!handles(s),"Line release automatically displayed move arrows");
            check(doc(s).targets(3).contains(6) && doc(s).pendingConnections().isEmpty(),"Normal-to-normal line did not cut the face");
            verifyLineDelete(s,3,6);stage=41;ticks=0;return;
        }
        if(stage==41 && ticks>5 && mc.screen instanceof PolyScreen s) {
            shot(mc,"line-selected-world.png");
            click(s,3);check(handles(s),"Click after linking did not restore move arrows");shortcut(s,GLFW.GLFW_KEY_Z);
            check(doc(s).connections().isEmpty(),"Undo did not remove normal cut");
            double[][] positions={{4,16,4},{12,16,4},{12,16,12},{4,16,12}};
            for(int i=0;i<4;i++) {
                Vec3 p=project(s,new Vec3(positions[i][0]/16,1,positions[i][2]/16));s.mouseClicked(p.x,p.y,1);s.mouseReleased(p.x,p.y,1);
                check((boolean)field(s,"popupOpen"),"Right-click add menu did not open");button(s,i%2==0?"add_custom":"add_normal").onPress();
                check(doc(s).vertices().getLast().position().equals(new Polyhedron.Point(positions[i][0],16,positions[i][2])),"Added vertex did not use clicked surface coordinates");
            }
            for(var cut:CUTS)link(s,cut[0],cut[1]);
            check(doc(s).vertices().size()==12 && doc(s).pendingConnections().isEmpty() && doc(s).surfaceTriangles().size()==20,"Diagram did not partition into an inset and four outer regions");
            check(!handles(s),"Completing an inner loop showed axes without a click");stage=5;ticks=0;return;
        }
        if(stage==5 && ticks>15 && mc.screen instanceof PolyScreen s) {
            shot(mc,"line-inset-connected.png");
            Vec3 center=project(s,new Vec3(.5,1,.5));s.mouseClicked(center.x,center.y,0);s.mouseReleased(center.x,center.y,0);
            check(((Set<?>)field(s,"selectedFaces")).size()==2,"Face selection crossed the user's cut lines into neighboring coplanar regions");
            click(s,7);ctrlClick(s,7);for(int id=8;id<12;id++)ctrlClick(s,id);
            check(selectedCount(s)==4 && handles(s),"Delayed Ctrl click did not build a movable four-point selection");
            Vec3 a=project(s,new Vec3(.5,1.4,.5)),mid=project(s,new Vec3(.5,1.4-4.2/16,.5)),end=project(s,new Vec3(.5,1.4-8.2/16,.5));
            s.mouseClicked(a.x,a.y,0);check((int)field(field(s,"gizmo"),"dragAxis")==1,"Multi-point Y handle could not be dragged");
            s.mouseDragged(mid.x,mid.y,0,mid.x-a.x,mid.y-a.y);s.mouseDragged(end.x,end.y,0,end.x-mid.x,end.y-mid.y);s.mouseReleased(end.x,end.y,0);
            for(int id=8;id<12;id++)check(doc(s).vertex(id).position().y()==8,"Inner points did not move together by eight pixels");
            saved=doc(s);assertDent(s);
            shortcut(s,GLFW.GLFW_KEY_Z);for(int id=8;id<12;id++)check(doc(s).vertex(id).position().y()==16,"One drag required more than one undo");
            dragDown(s);check(doc(s).equals(saved),"Repeated group drag changed reference topology");next();return;
        }
        if(stage==6 && ticks>15 && mc.screen instanceof PolyScreen s) {
            shot(mc,"line-inset-world-dent.png");
            check(mc.player.position().distanceToSqr(body)<1e-8 && mc.getCameraEntity().position().distanceToSqr(camera)<1e-8,"Point/link gestures moved the camera or player");
            s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);check(doc(s).vertices().size()==8 && preview(s).contains(8,15,8),"Deleting inner points did not restore original face");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"Delete undo lost inner points/cuts/reference positions");
            link(s,9,8);check(!doc(s).targets(8).contains(9),"Reverse-direction drag did not disconnect");shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"Disconnect undo failed");
            Vec3 p=project(s,new Vec3(.3,.5,1));s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
            check(!((Set<?>)field(s,"selectedFaces")).isEmpty(),"Default left-click did not select a face without switching tools");
            int vertices=doc(s).vertices().size();s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);
            check(doc(s).vertices().size()==vertices && !(boolean)field(s,"valid"),"Face Del deleted vertices or silently filled the opening");
            button(s,"apply").onPress();
            check((boolean)field(s,"showOpenings") && !((List<?>)field(s,"openingFaces")).isEmpty() && !(boolean)field(s,"waiting"),"Apply did not mark missing faces without sending an invalid save");
            stage=61;ticks=0;return;
        }
        if(stage==61 && ticks>15 && mc.screen instanceof PolyScreen s) {
            if(openingStarted==0) {
                shot(mc,"line-missing-face-world-red.png");openDraft=doc(s);long first=(long)field(s,"openingsUntil");
                button(s,"apply").onPress();check((long)field(s,"openingsUntil")>first,"Apply did not renew the five-second opening hint");
                openingStarted=net.minecraft.Util.getMillis();return;
            }
            if(net.minecraft.Util.getMillis()-openingStarted<5200) {
                if(net.minecraft.Util.getMillis()-openingStarted<4500)check((boolean)field(s,"showOpenings"),"World red hint disappeared before five seconds");return;
            }
            verifyExpired(s);shot(mc,"line-missing-face-world-expired.png");
            button(s,"apply").onPress();check((boolean)field(s,"showOpenings"),"Expired world hint could not be displayed again");
            click(s,7);for(int id:new int[]{1,5,3})ctrlClick(s,id);
            check(button(s,"create_face").active,"New face is not available for a four-point selection");button(s,"create_face").onPress();
            check((boolean)field(s,"valid") && ((List<?>)field(s,"openingFaces")).isEmpty(),"Unordered New face did not close the opening and clear red highlighting");
            shortcut(s,GLFW.GLFW_KEY_Z);shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"Face creation/deletion undo did not restore line document");
            mc.keyboardHandler.setClipboard(PolyShapeCodec.toText(TopologyFixtures.tunnel()));button(s,"paste").onPress();
            check((boolean)field(s,"valid") && ((List<?>)field(s,"openingFaces")).isEmpty(),"Closed O-shaped structure was mistaken for a missing face");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"O-shape validation changed the previous draft");
            button(s,"table").onPress();stage=7;ticks=0;return;
        }
        if(stage==7 && ticks>15 && mc.screen instanceof PolyScreen s) {
            link(s,8,9);check(!doc(s).targets(9).contains(8) && !handles(s),"Preview reverse cut gesture displayed arrows or failed");
            shortcut(s,GLFW.GLFW_KEY_Z);assertDent(s);
            verifyLineDelete(s,8,9);stage=70;ticks=0;return;
        }
        if(stage==70 && ticks>5 && mc.screen instanceof PolyScreen s) {
            shot(mc,"line-selected-preview.png");
            button(s,"copy").onPress();button(s,"reset").onPress();button(s,"paste").onPress();check(doc(s).equals(saved),"Whole document clipboard lost cut references");
            Vec3 p=project(s,new Vec3(.3,.5,1));s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);button(s,"apply").onPress();
            check((boolean)field(s,"showOpenings") && !((List<?>)field(s,"openingFaces")).isEmpty(),"Preview Apply did not mark its missing face");
            openingStarted=0;stage=71;ticks=0;return;
        }
        if(stage==71 && ticks>15 && mc.screen instanceof PolyScreen s) {
            if(openingStarted==0){shot(mc,"line-missing-face-preview-red.png");openingStarted=net.minecraft.Util.getMillis();openDraft=doc(s);return;}
            if(net.minecraft.Util.getMillis()-openingStarted<5200)return;
            verifyExpired(s);shot(mc,"line-missing-face-preview-expired.png");shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"Preview face delete undo failed");
            Field pitch=PolyScreen.class.getDeclaredField("pitch");pitch.setAccessible(true);pitch.setDouble(s,1.1);click(s,8);stage=8;ticks=0;return;
        }
        if(stage==8 && ticks>15 && mc.screen instanceof PolyScreen s) {
            shot(mc,"line-inset-preview-dent.png");button(s,"world").onPress();button(s,"apply").onPress();next();return;
        }
        if(stage==9 && ticks>25 && mc.screen==null) {
            check(((PolyBlockEntity)mc.level.getBlockEntity(TARGET)).document().equals(saved),"Server save/client sync lost line topology");open(mc);next();return;
        }
        if(stage==10 && ticks>25 && mc.screen instanceof PolyScreen s) {
            check(doc(s).equals(saved),"Reopening lost reference surface");assertDent(s);click(s,8);
            var fields=fields(s);fields.getFirst()[1].setValue("10");call(s,"validate");check(doc(s).pendingConnections().isEmpty(),"Reopened edit lost reference embedding");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(saved),"Coordinate undo lost reference positions");s.onClose();
            if(clipboard!=null && !clipboard.isEmpty())mc.keyboardHandler.setClipboard(clipboard);else GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");
            com.mojang.logging.LogUtils.getLogger().info("CopyPoly line probe passed: delayed click/arrows, arrow-free line drag, any-type bidirectional cuts, line click/Del/undo in world/preview, four-point inset, Ctrl multi-select, group undo, default face click/Del, unordered New face, five-second red openings and renewal, valid closed O shape, clipboard, save/reopen");
            stage=11;mc.stop();
        }
    }
    private static void verifyExpired(PolyScreen s) throws Exception {
        check(!(boolean)field(s,"showOpenings") && ((List<?>)field(s,"openingFaces")).isEmpty() && ((List<?>)field(s,"openingPoints")).isEmpty(),"Red opening hint did not clear after five seconds");
        check(doc(s).equals(openDraft) && !(boolean)field(s,"valid") && !(boolean)field(s,"waiting"),"Hint expiry changed or saved the invalid draft");
    }
    private static void verifyLineDelete(PolyScreen s,int a,int b) throws Exception {
        var before=doc(s);var edge=before.connections().stream().filter(c->c.source()==Math.min(a,b) && c.target()==Math.max(a,b)).findFirst().orElseThrow();
        var p=point(s,a).lerp(point(s,b),.5);s.mouseClicked(p.x,p.y,0);s.mouseReleased(p.x,p.y,0);
        check(edge.equals(field(s,"selectedConnection")) && selectedCount(s)==0 && ((Set<?>)field(s,"selectedFaces")).isEmpty() && !handles(s),"Line click selected an endpoint/face or displayed axes");
        for(int i=0;i<2;i++) {
            s.keyPressed(GLFW.GLFW_KEY_DELETE,0,0);check(!doc(s).connections().contains(edge) && doc(s).vertices().equals(before.vertices()),"Line Del deleted endpoints or left the connection");
            shortcut(s,GLFW.GLFW_KEY_Z);check(doc(s).equals(before) && edge.equals(field(s,"selectedConnection")),"Line undo lost geometry or line selection");
        }
    }
    private static void dragDown(PolyScreen s) throws Exception {
        Vec3 a=project(s,new Vec3(.5,1.4,.5)),b=project(s,new Vec3(.5,1.4-8.2/16,.5));s.mouseClicked(a.x,a.y,0);s.mouseDragged(b.x,b.y,0,b.x-a.x,b.y-a.y);s.mouseReleased(b.x,b.y,0);
    }
    private static void assertDent(PolyScreen s) throws Exception {check(doc(s).lineMesh() && !preview(s).contains(8,12,8) && preview(s).contains(8,2,8) && doc(s).pendingConnections().isEmpty() && button(s,"apply").active,"Connected inset was filled or invalid");}
    private static void click(PolyScreen s,int index) throws Exception {Vec3 p=point(s,index);s.mouseClicked(p.x,p.y,0);check(!handles(s),"Vertex press immediately displayed axes");s.mouseReleased(p.x,p.y,0);}
    private static void ctrlClick(PolyScreen s,int index) throws Exception {
        Vec3 p=point(s,index);s.mouseClicked(p.x,p.y,0);Field control=PolyScreen.class.getDeclaredField("linkControl");control.setAccessible(true);control.setBoolean(s,true);
        Object marquee=field(s,"marquee");var begin=marquee.getClass().getDeclaredMethod("begin",double.class,double.class);begin.setAccessible(true);begin.invoke(marquee,p.x,p.y);s.mouseReleased(p.x,p.y,0);
    }
    private static void link(PolyScreen s,int first,int second) throws Exception {
        Vec3 a=point(s,first),b=point(s,second);s.mouseClicked(a.x,a.y,0);check((int)field(s,"linkSource")==first && !handles(s),"Vertex did not enter a pending arrow-free gesture");
        s.mouseDragged(b.x,b.y,0,b.x-a.x,b.y-a.y);check(!handles(s),"Line drag displayed axes");s.mouseReleased(b.x,b.y,0);check(!handles(s),"Link completion displayed axes");
    }
    private static boolean handles(PolyScreen s) throws Exception{return (boolean)call(s,"handlesVisible");}
    private static int selectedCount(PolyScreen s) throws Exception {Object gizmo=field(s,"gizmo");var m=gizmo.getClass().getDeclaredMethod("selectedCount");m.setAccessible(true);return (int)m.invoke(gizmo);}
    private static Vec3 point(PolyScreen s,int index) throws Exception {var p=doc(s).vertices().get(index).position();return project(s,new Vec3(p.x()/16,p.y()/16,p.z()/16));}
    private static PolyShape doc(PolyScreen s) throws Exception{return (PolyShape)field(s,"document");}
    private static Polyhedron preview(PolyScreen s) throws Exception{return (Polyhedron)field(s,"preview");}
    @SuppressWarnings("unchecked") private static List<EditBox[]> fields(PolyScreen s) throws Exception{return (List<EditBox[]>)field(s,"fields");}
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
