package com.caca.copypoly;

import com.caca.copypoly.client.PolyScreen;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import java.lang.reflect.Field;
import com.caca.copypoly.geometry.VertexEditing;
import net.minecraft.world.item.ItemStack;

/** Opt-in end-to-end world/editor probe, excluded from the release jar. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class EditorInteractionProbe {
    private static final BlockPos TARGET=new BlockPos(0,100,0);
    private static int stage, ticks, total;
    private static PolyScreen editor;
    private static Vec3 handle, destination;
    private static Polyhedron saved;
    private static Vec3 beforeMovement;
    private static Vec3 beforeCamera;
    private static float playerYaw,playerPitch;
    private static boolean playerFlying;
    private static Polyhedron groupSource;
    private static double boxEndX,boxEndY;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        boolean clipboardOnly=Boolean.getBoolean("copypoly.vertexClipboardSmoke");
        boolean freeOnly=Boolean.getBoolean("copypoly.freeformSmoke");
        if(!Boolean.getBoolean("copypoly.editorSmoke") && !clipboardOnly && !freeOnly)return;
        var mc=Minecraft.getInstance();
        if(++total>12000)throw new IllegalStateException("Editor smoke timed out at stage "+stage);
        ticks++;
        if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) && mc.getOverlay()==null) {
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.options.pauseOnLostFocus=false;
            if(clipboardOnly || freeOnly){mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);}
            var settings=new LevelSettings("CopyPoly editor smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("copypoly-editor-smoke-"+System.currentTimeMillis(),settings,
                new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);
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
                var be=(PolyBlockEntity)level.getBlockEntity(TARGET);be.setMaterial(Blocks.STONE.defaultBlockState());
                level.setDayTime(6000);
                var player=server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(2.8,100,3.8);player.setYRot(145);player.setXRot(20);
                player.connection.teleport(2.8,100,3.8,145,20);
            });next();return;
        }
        if(stage==2 && ticks>60 && mc.level.getBlockEntity(TARGET) instanceof PolyBlockEntity) {
            if(net.neoforged.fml.ModList.get().isLoaded("jei")) {
                mc.setScreen(new InventoryScreen(mc.player));stage=20;ticks=0;return;
            }
            open(mc);next();return;
        }
        if(stage==20 && ticks>60) {
            check(jeiVisible(),"JEI positive control failed: normal inventory did not show ingredients");
            shot(mc,"jei-inventory-control.png");mc.player.closeContainer();stage=21;ticks=0;return;
        }
        if(stage==21 && ticks>10 && mc.screen==null){open(mc);stage=3;ticks=0;return;}
        if(stage==3 && mc.screen instanceof PolyScreen s && ticks>30) {
            editor=s;
            if(freeOnly) {
                button(s,"mode_convex").onPress();check(((Polyhedron)field(s,"preview")).freeform(),"Free deformation mode did not activate");
                selectAndStartDrag(s);destination=project(s,new Vec3(.9,1,1));
                s.mouseDragged(destination.x,destination.y,0,destination.x-handle.x,destination.y-handle.y);s.mouseReleased(destination.x,destination.y,0);
                assertDraft(s,8);setVertex(s,7,new Polyhedron.Point(8,8,8));
                var shape=(Polyhedron)field(s,"preview");check(shape.freeform() && !shape.contains(7,7,13),"Free drag rebuilt the convex hull instead of a dent");
                saved=shape;stage=101;ticks=0;return;
            }
            if(clipboardOnly) {
                verifyVertexClipboard(s,mc);
                button(s,"table").onPress();verifyVertexClipboard(s,mc);
                s.onClose();
                check(mc.getCameraEntity()==mc.player,"Clipboard check did not restore camera");
                com.mojang.logging.LogUtils.getLogger().info("CopyPoly vertex clipboard probe passed: world and preview shortcuts, selected group copy, append/select, move copies without originals, decimal single point, empty selection, invalid/empty paste, exact 64-point limit, text-field focus and preserved system clipboard");
                stage=100;mc.stop();return;
            }
            if(!mc.isWindowActive())return;
            check(!(mc.screen instanceof AbstractContainerScreen),"Editor still uses a container screen");assertJeiHidden();
            beforeMovement=mc.player.position();beforeCamera=mc.getCameraEntity().position();
            playerYaw=mc.player.getYRot();playerPitch=mc.player.getXRot();playerFlying=mc.player.getAbilities().flying;
            check(mc.getCameraEntity()!=mc.player,"Editor did not detach the camera");
            check(mc.options.getCameraType()==CameraType.FIRST_PERSON,"Free camera did not use its own perspective");
            verifyMouseSensitivity(s,mc);
            for(var entity:mc.level.entitiesForRendering())check(entity!=mc.getCameraEntity(),"Camera was spawned into the world");
            double x=mc.getWindow().getGuiScaledWidth()-2;
            s.mouseClicked(x,20,1);s.mouseDragged(x+12,12,1,12,-8);s.mouseReleased(x+12,12,1);
            check(mc.getCameraEntity().getYRot()!=playerYaw,"Right drag did not turn the camera");
            s.keyPressed(mc.options.keyDown.getKey().getValue(),0,0);
            s.keyPressed(mc.options.keyRight.getKey().getValue(),0,0);
            stage=30;ticks=0;return;
        }
        if(stage==30 && ticks>8) {
            editor.keyReleased(mc.options.keyDown.getKey().getValue(),0,0);
            editor.keyReleased(mc.options.keyRight.getKey().getValue(),0,0);
            check(mc.getCameraEntity().position().distanceToSqr(beforeCamera)>.01,"Camera did not move");
            assertBodyUnchanged(mc);
            check(mc.screen==editor,"Moving closed the editor");
            beforeCamera=mc.getCameraEntity().position();
            editor.keyPressed(mc.options.keyDown.getKey().getValue(),0,0);
            editor.setFocused(editor.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow());
            stage=31;ticks=0;return;
        }
        if(stage==31 && ticks>4) {
            check(mc.getCameraEntity().position().distanceToSqr(beforeCamera)<1e-10,"Typing focus did not suppress camera movement");
            assertBodyUnchanged(mc);shot(mc,"world-editor-free-camera.png");
            editor.keyReleased(mc.options.keyDown.getKey().getValue(),0,0);
            editor.mouseClicked(mc.getWindow().getGuiScaledWidth()-2,20,0);
            check(editor.getFocused()==null,"Clicking the world did not leave the coordinate field");
            editor.keyPressed(mc.options.keyDown.getKey().getValue(),0,0);
            stage=32;ticks=0;return;
        }
        if(stage==32 && ticks>100) {
            editor.keyReleased(mc.options.keyDown.getKey().getValue(),0,0);
            check(mc.getCameraEntity().position().distanceTo(beforeMovement)>17,"Camera did not fly beyond player interaction range");
            check(mc.screen==editor,"Independent camera distance closed the editor");assertBodyUnchanged(mc);
            beforeCamera=mc.getCameraEntity().position();editor.keyPressed(mc.options.keyJump.getKey().getValue(),0,0);
            stage=34;ticks=0;return;
        }
        if(stage==34 && ticks>8) {
            editor.keyReleased(mc.options.keyJump.getKey().getValue(),0,0);
            check(mc.getCameraEntity().getY()>beforeCamera.y+.5,"Camera did not ascend");assertBodyUnchanged(mc);
            beforeCamera=mc.getCameraEntity().position();editor.keyPressed(mc.options.keyShift.getKey().getValue(),0,0);
            stage=35;ticks=0;return;
        }
        if(stage==35 && ticks>8) {
            editor.keyReleased(mc.options.keyShift.getKey().getValue(),0,0);
            check(mc.getCameraEntity().getY()<beforeCamera.y-.5,"Camera did not descend");assertBodyUnchanged(mc);
            button(editor,"reset_view").onPress();stage=33;ticks=0;return;
        }
        if(stage==33 && ticks>8) {
            assertBodyUnchanged(mc);
            check(mc.getCameraEntity()!=mc.player,"Reset view reattached the camera");
            verifyCameraAndPanelGestures(editor,mc);beginMarquee(editor,true);
            stage=40;ticks=0;return;
        }
        if(stage==40 && ticks>10) {
            check(selectionCount(editor)>0 && selectionCount(editor)<8,"Box selection did not replace the old full selection");
            assertJeiHidden();shot(mc,"world-editor-box-selection.png");
            editor.mouseReleased(boxEndX,boxEndY,0);
            emptyMarquee(editor);
            check(selectionCount(editor)==0,"Empty marquee did not clear all selected vertices");
            selectAndStartDrag(editor);stage=4;ticks=0;return;
        }
        if(stage==4 && ticks>20) {
            shot(mc,"world-editor-selected.png");
            editor.mouseDragged(destination.x,destination.y,0,destination.x-handle.x,destination.y-handle.y);
            editor.mouseReleased(destination.x,destination.y,0);
            assertDraft(editor,14);next();return;
        }
        if(stage==5 && ticks>30) {
            shot(mc,"world-editor-dragged.png");
            var be=(PolyBlockEntity)mc.level.getBlockEntity(TARGET);
            check(be.poly()==Polyhedron.CUBE,"Draft changed authoritative shape before Apply");
            check(be.getModelData().get(PolyBlockEntity.GEOMETRY)!=Polyhedron.CUBE,"World preview did not change model data");
            groupSource=(Polyhedron)field(editor,"preview");
            selectGroup(editor,1,3);dragGroup(editor,0,1.26);assertGroupOffset(editor,1);
            stage=51;ticks=0;return;
        }
        if(stage==51 && ticks>20) {
            shot(mc,"world-editor-multi-selection.png");
            dragGroup(editor,1,-1.26);assertGroupOffset(editor,0);
            check(field(editor,"preview")==groupSource,"Group round trip changed the shape");
            verifyTransforms(editor);
            button(editor,"rotate_axis","Y").onPress();button(editor,"mirror_axis","X").onPress();
            stage=50;ticks=0;return;
        }
        if(stage==50 && ticks>20) {
            shot(mc,"world-editor-transformed.png");
            button(editor,"table").onPress();stage=6;ticks=0;return;
        }
        if(stage==6 && ticks>30) {
            check(mc.getCameraEntity()==mc.player,"Preview mode did not restore the player camera");
            assertJeiHidden();beginMarquee(editor,true);editor.mouseReleased(boxEndX,boxEndY,0);
            check(selectionCount(editor)>0 && selectionCount(editor)<8,"Preview box selection failed");
            emptyMarquee(editor);check(selectionCount(editor)==0,"Empty preview marquee retained old selection");
            check(((PolyBlockEntity)mc.level.getBlockEntity(TARGET)).getModelData().get(PolyBlockEntity.GEOMETRY)==Polyhedron.CUBE,"Mode switch did not restore world model");
            shot(mc,"preview-editor.png");button(editor,"world").onPress();
            check(mc.getCameraEntity()!=mc.player,"Re-entering world mode did not detach camera");
            editor.onClose();next();return;
        }
        if(stage==7 && ticks>20 && mc.screen==null) {
            check(((PolyBlockEntity)mc.level.getBlockEntity(TARGET)).poly()==Polyhedron.CUBE,"Cancel changed shape");
            check(mc.getCameraEntity()==mc.player,"Cancel did not restore camera");assertBodyUnchanged(mc);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            open(mc);next();return;
        }
        if(stage==8 && mc.screen instanceof PolyScreen s && ticks>30) {
            editor=s;selectAndStartDrag(s);
            s.mouseDragged(destination.x,destination.y,0,destination.x-handle.x,destination.y-handle.y);
            s.mouseReleased(destination.x,destination.y,0);
            assertDraft(s,14);button(s,"apply").onPress();next();return;
        }
        if(stage==9 && ticks>40 && mc.screen==null) {
            var be=(PolyBlockEntity)mc.level.getBlockEntity(TARGET);
            saved=be.poly();check(saved.points().contains(new Polyhedron.Point(14,16,16)),"Apply did not reach server/client shape");
            check(be.getModelData().get(PolyBlockEntity.GEOMETRY)==saved,"Closing editor left a draft model behind");
            check(mc.getCameraEntity()==mc.player && mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK,"Apply did not restore original camera and perspective");
            assertBodyUnchanged(mc);open(mc);next();return;
        }
        if(stage==10 && mc.screen instanceof PolyScreen && ticks>30) {
            check(mc.getCameraEntity()!=mc.player,"Camera was not detached for forced-close test");
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().removeBlock(TARGET,false));
            next();return;
        }
        if(stage==11 && ticks>30 && mc.screen==null) {
            check(mc.getCameraEntity()==mc.player && mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK,"Target removal did not restore camera");
            assertBodyUnchanged(mc);
            com.mojang.logging.LogUtils.getLogger().info("CopyPoly editor probe passed: plain editor screen, JEI inventory/hidden-editor controls, pan/look/dolly and draggable panel, replacement and empty marquee in world/preview, independent camera and unchanged player, multi-point drag, transforms and all close paths");
            mc.setScreen(new IconInspectionScreen());next();return;
        }
        if(stage==12 && ticks>30) {
            shot(mc,"item-icons.png");stage=13;mc.stop();
        }
        if(stage==101 && ticks>25) {
            shot(mc,"freeform-world-dent.png");verifyFreeformMaterialMeshes(mc,saved);
            verifyTransforms(editor);
            String previousClipboard=mc.keyboardHandler.getClipboard();
            try {
                button(editor,"copy").onPress();String exported=mc.keyboardHandler.getClipboard();
                check(com.caca.copypoly.geometry.PolyShapeText.read(exported)==saved,"Whole-shape clipboard lost the concave mesh");
                button(editor,"mode_free").onPress();check(!((Polyhedron)field(editor,"preview")).freeform(),"Convex mode did not activate");
                check(((Polyhedron)field(editor,"preview")).contains(7,7,13),"Convex mode did not rebuild the outer hull");
                mc.keyboardHandler.setClipboard(exported);button(editor,"paste").onPress();
                check(field(editor,"preview")==saved,"Mesh clipboard paste did not restore mode and faces");
            } finally { if(previousClipboard.isEmpty())org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");else mc.keyboardHandler.setClipboard(previousClipboard); }
            button(editor,"table").onPress();stage=102;ticks=0;return;
        }
        if(stage==102 && ticks>25) {
            check(((Polyhedron)field(editor,"preview")).freeform(),"Preview switch lost free mode");
            shot(mc,"freeform-preview-dent.png");
            // Moving the same point outward again restores the cube without changing its connections.
            setVertex(editor,7,new Polyhedron.Point(16,16,16));check(((Polyhedron)field(editor,"preview")).collisionBoxes().size()==1,"Outward movement did not restore the convex surface");
            setVertex(editor,7,new Polyhedron.Point(8,8,8));
            check(field(editor,"preview")==saved,"Inward/outward round trip lost topology");
            button(editor,"apply").onPress();stage=103;ticks=0;return;
        }
        if(stage==103 && ticks>30 && mc.screen==null) {
            check(((PolyBlockEntity)mc.level.getBlockEntity(TARGET)).poly()==saved,"Server/client save lost the concave topology");
            open(mc);stage=104;ticks=0;return;
        }
        if(stage==104 && ticks>25 && mc.screen instanceof PolyScreen s) {
            check(((Polyhedron)field(s,"preview")).freeform() && field(s,"preview")==saved,"Reopened editor did not restore free mode");
            check(mc.getCameraEntity()!=mc.player,"Reopening free mode did not detach camera");
            s.onClose();
            com.mojang.logging.LogUtils.getLogger().info("CopyPoly freeform client probe passed: world local-axis drag, inward dent and outward bulge, convex/free switching, mesh clipboard, seven material meshes, mirror/rotate winding, preview, save and reopen");
            stage=105;mc.stop();
        }
    }
    private static void next(){stage++;ticks=0;}
    @SuppressWarnings("unchecked")
    private static void setVertex(PolyScreen screen,int index,Polyhedron.Point point) throws Exception {
        var page=PolyScreen.class.getDeclaredField("page");page.setAccessible(true);page.setInt(screen,index/(int)field(screen,"visibleRows"));
        var rebuild=PolyScreen.class.getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(screen);
        var fields=(java.util.List<EditBox[]>)field(screen,"fields");var row=fields.get(index%(int)field(screen,"visibleRows"));
        for(int axis=0;axis<3;axis++)row[axis].setValue(VertexEditing.format(point.axis(axis)));
        screen.setFocused(null);var validate=PolyScreen.class.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(screen);
        check((boolean)field(screen,"valid"),"Mesh edit was rejected: "+field(screen,"error"));
    }
    private static void verifyFreeformMaterialMeshes(Minecraft mc,Polyhedron mesh) {
        var model=(com.caca.copypoly.client.PolyModel)mc.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState());
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.GLASS,Blocks.RED_STAINED_GLASS,Blocks.OAK_LEAVES,Blocks.GRASS_BLOCK,Blocks.OAK_LOG,com.simibubi.create.AllBlocks.ROSE_QUARTZ_TILES.get()}) {
            var quads=model.preview(mesh,block.defaultBlockState());check(!quads.isEmpty(),"Freeform material mesh was empty");
            for(var quad:quads)check(!quad.getSprite().contents().name().getPath().equals("missingno"),"Freeform mesh used missing texture");
        }
    }
    @SuppressWarnings("unchecked")
    private static java.util.List<Polyhedron.Point> clipboardDraft(PolyScreen screen) throws Exception {
        var method=PolyScreen.class.getDeclaredMethod("draftPoints");method.setAccessible(true);
        return (java.util.List<Polyhedron.Point>)method.invoke(screen);
    }
    private static void shortcut(PolyScreen screen,int key) {
        check(screen.keyPressed(key,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL),"Clipboard shortcut was not consumed");
        screen.keyReleased(key,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
    }
    private static void verifyVertexClipboard(PolyScreen screen,Minecraft mc) throws Exception {
        String originalClipboard=mc.keyboardHandler.getClipboard();
        int copy=org.lwjgl.glfw.GLFW.GLFW_KEY_C,paste=org.lwjgl.glfw.GLFW.GLFW_KEY_V;
        try {
            selectGroup(screen,1,3);
            var source=clipboardDraft(screen);
            shortcut(screen,copy);
            check(Polyhedron.parse(mc.keyboardHandler.getClipboard()).equals(java.util.List.of(source.get(1),source.get(3))),"Copy included unselected vertices");
            shortcut(screen,paste);
            var appended=clipboardDraft(screen);
            check(appended.size()==10 && appended.subList(0,8).equals(source),"Paste replaced existing vertices");
            check(appended.subList(8,10).equals(java.util.List.of(source.get(1),source.get(3))),"Paste changed copied coordinates");
            var gizmo=field(screen,"gizmo");
            var selected=gizmo.getClass().getDeclaredMethod("isSelected",int.class);selected.setAccessible(true);
            check(selectionCount(screen)==2 && (boolean)selected.invoke(gizmo,8) && (boolean)selected.invoke(gizmo,9)
                    && !(boolean)selected.invoke(gizmo,1),"Paste did not select only the new vertices");
            if((boolean)field(screen,"worldMode")) {
                dragGroup(screen,0,1.26);
                var moved=clipboardDraft(screen);
                check(moved.subList(0,8).equals(source),"Moving pasted group moved its source vertices");
                check(moved.get(8).equals(new Polyhedron.Point(1,0,16)) && moved.get(9).equals(new Polyhedron.Point(1,16,16)),"Pasted group could not be dragged together");
            }
            for(String invalid:new String[]{"", "not coordinates", "17,0,0", "0.55,0,0"}) {
                var before=clipboardDraft(screen);int count=selectionCount(screen);
                if(invalid.isEmpty())org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");
                else mc.keyboardHandler.setClipboard(invalid);
                shortcut(screen,paste);
                check(clipboardDraft(screen).equals(before) && selectionCount(screen)==count,"Invalid paste changed draft or selection: '"+invalid+"', count "+before.size()+" -> "+clipboardDraft(screen).size());
            }
            var replace=gizmo.getClass().getDeclaredMethod("replaceSelection",java.util.Collection.class);replace.setAccessible(true);
            replace.invoke(gizmo,java.util.List.of());mc.keyboardHandler.setClipboard("leave this clipboard unchanged");shortcut(screen,copy);
            check(mc.keyboardHandler.getClipboard().equals("leave this clipboard unchanged"),"Copy without selection overwrote clipboard");

            button(screen,"reset").onPress();mc.keyboardHandler.setClipboard("1.2, 2.3, 3.4");shortcut(screen,paste);
            check(clipboardDraft(screen).size()==9 && clipboardDraft(screen).get(8).equals(new Polyhedron.Point(1.2,2.3,3.4))
                    && selectionCount(screen)==1,"Single decimal vertex paste failed");
            shortcut(screen,copy);check(Polyhedron.parse(mc.keyboardHandler.getClipboard()).equals(java.util.List.of(new Polyhedron.Point(1.2,2.3,3.4))),"Single-point copy lost decimal precision");

            var box=screen.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();
            screen.setFocused(box);var beforeText=clipboardDraft(screen);
            screen.keyPressed(paste,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
            check(clipboardDraft(screen).size()==beforeText.size(),"Text-field paste appended vertices");screen.setFocused(null);
            mc.keyboardHandler.setClipboard("8,8,8\n".repeat(64-clipboardDraft(screen).size()));shortcut(screen,paste);
            check(clipboardDraft(screen).size()==64,"Paste rejected an exact 64-point draft");
            var full=clipboardDraft(screen);int selectedCount=selectionCount(screen);
            mc.keyboardHandler.setClipboard("2,2,2;3,3,3");shortcut(screen,paste);
            check(clipboardDraft(screen).equals(full) && selectionCount(screen)==selectedCount,"Overflow paste was applied partially");
        } finally {
            if(originalClipboard.isEmpty())org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(),"");
            else mc.keyboardHandler.setClipboard(originalClipboard);
            button(screen,"reset").onPress();
            var validate=PolyScreen.class.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(screen);
        }
    }
    private static void verifyMouseSensitivity(PolyScreen screen,Minecraft mc) {
        double oldSensitivity=mc.options.sensitivity().get();boolean oldInvert=mc.options.invertYMouse().get(),oldSmooth=mc.options.smoothCamera;
        int oldScale=mc.options.guiScale().get();var camera=mc.getCameraEntity();
        float yaw=camera.getYRot(),pitch=camera.getXRot();
        var vanilla=new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER,mc.level);
        try {
            mc.options.smoothCamera=false;
            for(int scale:new int[]{1,2,3})for(double setting:new double[]{0,.25,.5,1})for(boolean invert:new boolean[]{false,true}) {
                mc.options.guiScale().set(scale);mc.resizeDisplay();mc.options.sensitivity().set(setting);mc.options.invertYMouse().set(invert);
                camera.absRotateTo(yaw,pitch);vanilla.absRotateTo(yaw,pitch);
                double base=setting*.6F+.2F,gain=base*base*base*8;
                vanilla.turn(48*gain,-24*gain*(invert?-1:1));
                var window=mc.getWindow();double dx=48.0*window.getGuiScaledWidth()/window.getScreenWidth(),dy=-24.0*window.getGuiScaledHeight()/window.getScreenHeight();
                double x=window.getGuiScaledWidth()-2;
                screen.mouseClicked(x,20,1);screen.mouseDragged(x+dx,20+dy,1,dx,dy);screen.mouseReleased(x+dx,20+dy,1);
                check(Math.abs(camera.getYRot()-vanilla.getYRot())<1e-4 && Math.abs(camera.getXRot()-vanilla.getXRot())<1e-4,
                    "Mouse sensitivity differs from vanilla at GUI scale "+scale+", setting "+setting+", invert="+invert);
                camera.absRotateTo(yaw,pitch);
            }
        } finally {
            mc.options.sensitivity().set(oldSensitivity);mc.options.invertYMouse().set(oldInvert);mc.options.smoothCamera=oldSmooth;
            mc.options.guiScale().set(oldScale);mc.resizeDisplay();camera.absRotateTo(yaw,pitch);
        }
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly mouse sensitivity probe passed: 24 combinations of GUI scale, sensitivity and inverted Y match vanilla Entity.turn");
    }
    private static boolean jeiVisible() throws Exception {
        Object runtime=Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        Object overlay=Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getIngredientListOverlay").invoke(runtime);
        return (boolean)Class.forName("mezz.jei.api.runtime.IIngredientListOverlay").getMethod("isListDisplayed").invoke(overlay);
    }
    private static void assertJeiHidden() throws Exception {
        if(net.neoforged.fml.ModList.get().isLoaded("jei"))check(!jeiVisible(),"JEI inventory overlay appeared on the editor");
    }
    private static int selectionCount(PolyScreen screen) throws Exception {
        Object gizmo=field(screen,"gizmo");var method=gizmo.getClass().getDeclaredMethod("selectedCount");method.setAccessible(true);
        return (int)method.invoke(gizmo);
    }
    private static void beginMarquee(PolyScreen screen,boolean topHalf) throws Exception {
        // Drive the same gesture state used by Ctrl+left-down without synthesizing a native Ctrl key.
        Object gizmo=field(screen,"gizmo");var replace=gizmo.getClass().getDeclaredMethod("replaceSelection",java.util.Collection.class);replace.setAccessible(true);
        replace.invoke(gizmo,java.util.stream.IntStream.range(0,8).boxed().toList());
        var draft=PolyScreen.class.getDeclaredMethod("draftPoints");draft.setAccessible(true);
        @SuppressWarnings("unchecked")var points=(java.util.List<Polyhedron.Point>)draft.invoke(screen);
        var projected=new java.util.ArrayList<Vec3>();
        for(var p:points){Vec3 v=project(screen,new Vec3(p.x()/16,p.y()/16,p.z()/16));check(v!=null,"Box test point not projected");projected.add(v);}
        double minX=projected.stream().mapToDouble(v->v.x).min().orElseThrow()-5;
        double minY=projected.stream().mapToDouble(v->v.y).min().orElseThrow()-5;
        boxEndX=projected.stream().mapToDouble(v->v.x).max().orElseThrow()+5;
        var ys=projected.stream().mapToDouble(v->v.y).sorted().toArray();boxEndY=(ys[3]+ys[4])/2;
        Object marquee=field(screen,"marquee");var begin=marquee.getClass().getDeclaredMethod("begin",double.class,double.class);begin.setAccessible(true);begin.invoke(marquee,minX,minY);
        screen.mouseDragged(boxEndX,boxEndY,0,boxEndX-minX,boxEndY-minY);
    }
    private static void emptyMarquee(PolyScreen screen) throws Exception {
        Object marquee=field(screen,"marquee");var begin=marquee.getClass().getDeclaredMethod("begin",double.class,double.class);begin.setAccessible(true);
        // This rectangle lies above the model in both editor modes.
        double x=(double)(int)field(screen,"previewX")+10;
        if((boolean)field(screen,"worldMode"))x=Minecraft.getInstance().getWindow().getGuiScaledWidth()-50;
        double y=(double)(int)field(screen,"topPos")+48;
        begin.invoke(marquee,x,y);screen.mouseDragged(x+12,y+8,0,12,8);screen.mouseReleased(x+12,y+8,0);
    }
    private static void verifyCameraAndPanelGestures(PolyScreen screen,Minecraft mc) throws Exception {
        double x=mc.getWindow().getGuiScaledWidth()-20,y=mc.getWindow().getGuiScaledHeight()/2.0;
        Vec3 before=mc.getCameraEntity().position();
        screen.mouseClicked(x,y,0);screen.mouseDragged(x+10,y+6,0,10,6);screen.mouseReleased(x+10,y+6,0);
        check(mc.getCameraEntity().position().distanceToSqr(before)>.001,"Blank left drag did not pan the camera");
        check(org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_CURSOR)==org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL,"Camera drag did not restore cursor");
        screen.mouseClicked(x,y,0);screen.mouseDragged(x-10,y-6,0,-10,-6);screen.mouseReleased(x-10,y-6,0);
        check(mc.getCameraEntity().position().distanceToSqr(before)<1e-10,"Camera pan round trip drifted");
        screen.mouseScrolled(x,y,0,1);check(mc.getCameraEntity().position().distanceTo(before)>1,"Scroll did not dolly camera");
        screen.mouseScrolled(x,y,0,-1);check(mc.getCameraEntity().position().distanceToSqr(before)<1e-10,"Dolly round trip drifted");
        int left=(int)field(screen,"leftPos"),top=(int)field(screen,"topPos");
        screen.mouseClicked(left+35,top+10,0);screen.mouseDragged(left+55,top+20,0,20,10);screen.mouseReleased(left+55,top+20,0);
        check((int)field(screen,"leftPos")==left+20 && (int)field(screen,"topPos")==top+10,"Title drag did not move panel");
        screen.mouseClicked(left+55,top+20,0);screen.mouseDragged(left+35,top+10,0,-20,-10);screen.mouseReleased(left+35,top+10,0);
        assertBodyUnchanged(mc);
    }
    private static void assertBodyUnchanged(Minecraft mc) {
        check(mc.player.position().distanceToSqr(beforeMovement)<1e-8,"Camera controls moved the real player");
        check(mc.player.getYRot()==playerYaw && mc.player.getXRot()==playerPitch,"Camera controls rotated the real player");
        check(mc.player.getAbilities().flying==playerFlying,"Camera changed player flight ability");
        check(mc.player.input.forwardImpulse==0 && mc.player.input.leftImpulse==0 && !mc.player.input.jumping && !mc.player.input.shiftKeyDown,"Navigation reached player input");
        var serverPosition=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().position()).join();
        check(serverPosition.distanceToSqr(beforeMovement)<1e-8,"Camera controls moved server-side player");
    }
    private static void selectGroup(PolyScreen screen,int first,int second) throws Exception {
        Object gizmo=field(screen,"gizmo");
        var select=gizmo.getClass().getDeclaredMethod("select",int.class,boolean.class);select.setAccessible(true);
        select.invoke(gizmo,first,false);select.invoke(gizmo,second,true);
        Field page=PolyScreen.class.getDeclaredField("page");page.setAccessible(true);page.setInt(screen,0);
        var rebuild=PolyScreen.class.getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(screen);
    }
    private static void dragGroup(PolyScreen screen,double xPixels,double delta) throws Exception {
        Vec3 handle=project(screen,new Vec3(xPixels/16+.4,.5,1));
        Vec3 destination=project(screen,new Vec3(xPixels/16+.4+delta/16,.5,1));
        screen.mouseClicked(handle.x,handle.y,0);
        check((int)field(field(screen,"gizmo"),"dragAxis")==0,"Group X handle did not start drag");
        screen.mouseDragged(destination.x,destination.y,0,destination.x-handle.x,destination.y-handle.y);
        screen.mouseReleased(destination.x,destination.y,0);
    }
    private static void assertGroupOffset(PolyScreen screen,double offset) throws Exception {
        var draft=PolyScreen.class.getDeclaredMethod("draftPoints");draft.setAccessible(true);
        @SuppressWarnings("unchecked") var points=(java.util.List<Polyhedron.Point>)draft.invoke(screen);
        check(points.get(1).equals(new Polyhedron.Point(offset,0,16)) && points.get(3).equals(new Polyhedron.Point(offset,16,16)),"Selected vertices did not move together");
        check(points.get(7).equals(new Polyhedron.Point(14,16,16)),"Group drag moved an unselected vertex");
    }
    private static void open(Minecraft mc){mc.getSingleplayerServer().execute(()->Copypoly.POLY.get().openEditor(mc.getSingleplayerServer().overworld(),TARGET,mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));}
    private static void selectAndStartDrag(PolyScreen s) throws Exception {
        Vec3 point=project(s,new Vec3(1,1,1));check(point!=null,"Target outside view");
        s.mouseClicked(point.x,point.y,0);s.mouseReleased(point.x,point.y,0);
        Object gizmo=field(s,"gizmo");check((int)field(gizmo,"selected")==7,"Wrong world vertex selected");
        handle=project(s,new Vec3(1.4,1,1));destination=project(s,new Vec3(1.4-2.2/16,1,1));
        s.mouseClicked(handle.x,handle.y,0);
        check((int)field(gizmo,"dragAxis")==0,"X handle did not start drag");
    }
    private static Vec3 project(PolyScreen s,Vec3 local) throws Exception {
        var method=PolyScreen.class.getDeclaredMethod("editProject",Vec3.class);method.setAccessible(true);return (Vec3)method.invoke(s,local);
    }
    private static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static void assertDraft(PolyScreen s,double x) throws Exception {
        Polyhedron draft=(Polyhedron)field(s,"preview");
        check(draft.points().contains(new Polyhedron.Point(x,16,16)),"World drag did not snap to expected pixel coordinate");
    }
    private static Button button(PolyScreen screen,String key,Object...args) {
        return screen.children().stream().filter(c->c instanceof Button b && b.getMessage().equals(Component.translatable("screen.copypoly."+key,args)))
            .map(c->(Button)c).findFirst().orElseThrow();
    }
    private static void verifyTransforms(PolyScreen screen) throws Exception {
        Polyhedron source=(Polyhedron)field(screen,"preview");
        for(int axis=0;axis<3;axis++) {
            final int a=axis;String label=""+"XYZ".charAt(axis);
            button(screen,"mirror_axis",label).onPress();
            check(((Polyhedron)field(screen,"preview")).points().equals(source.transformPoints(p->VertexEditing.mirror(p,a)).points()),"Mirror failed to transform entire draft");
            button(screen,"mirror_axis",label).onPress();
            check(field(screen,"preview")==source,"Double mirror changed shape");
            for(int turn=0;turn<4;turn++)button(screen,"rotate_axis",label).onPress();
            check(field(screen,"preview")==source,"Four rotations changed shape");
        }
    }
    private static final class IconInspectionScreen extends Screen {
        private final ItemStack[] items;
        IconInspectionScreen() {
            super(Component.literal("CopyPoly inventory icon inspection"));
            items=new ItemStack[]{new ItemStack(Copypoly.ITEM.get()),new ItemStack(Copypoly.ITEM.get()),new ItemStack(Copypoly.ITEM.get())};
            PolyItem.saveShape(items[1],Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16")));
            PolyItem.saveShape(items[2],saved);
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int x,int y,float partial) {
            g.fill(0,0,width,height,0xff202a35);
            g.drawCenteredString(font,title,width/2,20,0xffffffff);
            for(int i=0;i<items.length;i++) {
                int left=width/2-150+i*110,top=height/2-45;
                g.fill(left-8,top-8,left+88,top+88,0xffb4b4b4);
                g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(5,5,1);
                g.renderFakeItem(items[i],0,0);g.pose().popPose();
                g.drawString(font,new String[]{"Cube / alpha cutout","Tetrahedron","Edited shape"}[i],left-6,top+96,0xffffffff,false);
                g.renderFakeItem(items[i],left+32,top+120);
            }
        }
    }
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
