package com.caca.copypoly.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

/** A local-only camera entity. It is never spawned, synchronized, or used to move the player. */
final class WorldEditorCamera {
    private final Set<KeyMapping> held=new HashSet<>();
    private CameraEntity camera;
    private Entity previousCamera;
    private CameraType previousType;
    private ClientLevel level;
    private Vec3 initialPosition;
    private float initialYaw,initialPitch;
    private double speed=.4;
    private Vec3 velocity=Vec3.ZERO;

    void start() {
        var mc=Minecraft.getInstance();
        if(camera!=null || mc.level==null || mc.player==null)return;
        level=mc.level;previousCamera=mc.getCameraEntity();previousType=mc.options.getCameraType();
        var view=mc.gameRenderer.getMainCamera();
        float eyeHeight=previousCamera==null?mc.player.getEyeHeight():previousCamera.getEyeHeight();
        camera=new CameraEntity(level,eyeHeight);
        initialPosition=mc.player.getEyePosition().add(0,1,0).subtract(0,eyeHeight,0);
        initialYaw=view.getYRot();initialPitch=view.getXRot();
        resetView();
        suppressPlayerInput(mc.player.input);
        mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setCameraEntity(camera);
    }

    boolean active() {
        var mc=Minecraft.getInstance();
        return camera!=null && mc.level==level && mc.getCameraEntity()==camera;
    }

    void stop() {
        clear();
        if(camera==null)return;
        var mc=Minecraft.getInstance();
        if(mc.getCameraEntity()==camera) {
            Entity restore=previousCamera!=null && !previousCamera.isRemoved() && previousCamera.level()==mc.level?previousCamera:mc.player;
            mc.setCameraEntity(restore);
        }
        mc.options.setCameraType(previousType);
        camera=null;previousCamera=null;level=null;
    }

    void resetView() {
        if(camera==null)return;
        clear();camera.absMoveTo(initialPosition.x,initialPosition.y,initialPosition.z,initialYaw,initialPitch);
        camera.setOldPosAndRot();
    }

    void look(double dx,double dy) {
        if(!active())return;
        var mc=Minecraft.getInstance();var window=mc.getWindow();
        double sensitivity=mouseSensitivity();
        double rawX=CameraMouseInput.rawDelta(dx,window.getScreenWidth(),window.getGuiScaledWidth());
        double rawY=CameraMouseInput.rawDelta(dy,window.getScreenHeight(),window.getGuiScaledHeight());
        camera.turn(rawX*sensitivity,rawY*sensitivity*(mc.options.invertYMouse().get()?-1:1));
        camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();
    }

    void pan(double dx,double dy) {
        if(!active())return;
        var window=Minecraft.getInstance().getWindow();
        dx=CameraMouseInput.rawDelta(dx,window.getScreenWidth(),window.getGuiScaledWidth());
        dy=CameraMouseInput.rawDelta(dy,window.getScreenHeight(),window.getGuiScaledHeight());
        Vec3 forward=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
        Vec3 right=Vec3.directionFromRotation(0,camera.getYRot()+90),up=right.cross(forward).normalize();
        camera.setPos(camera.position().add(right.scale(dx*.025*mouseSensitivity())).add(up.scale(-dy*.025*mouseSensitivity())));
        velocity=Vec3.ZERO;
        camera.setOldPosAndRot();
    }
    void dolly(double amount) {
        if(!active())return;
        camera.setPos(camera.position().add(Vec3.directionFromRotation(camera.getXRot(),camera.getYRot()).scale(amount*1.6)));
        velocity=Vec3.ZERO;
        camera.setOldPosAndRot();
    }
    private double mouseSensitivity() {
        var options=Minecraft.getInstance().options;
        var event=net.neoforged.neoforge.client.ClientHooks.getTurnPlayerValues(options.sensitivity().get(),options.smoothCamera);
        return CameraMouseInput.multiplier(event.getMouseSensitivity());
    }

    void tick(boolean enabled) {
        if(!active())return;
        var mc=Minecraft.getInstance();
        camera.setOldPosAndRot();
        if(!mc.isWindowActive()){clear();enabled=false;}
        if(!enabled){velocity=Vec3.ZERO;return;}
        var options=mc.options;
        double forward=(down(options.keyUp)?1:0)-(down(options.keyDown)?1:0);
        double side=(down(options.keyRight)?1:0)-(down(options.keyLeft)?1:0);
        double vertical=(down(options.keyJump)?1:0)-(down(options.keyShift)?1:0);
        Vec3 step=step(camera.getYRot(),camera.getXRot(),forward,side,vertical,speed*(down(options.keySprint)?3:1));
        velocity=velocity.lerp(step,1-Math.exp(-14*.05));
        camera.setPos(camera.position().add(velocity));
    }

    static Vec3 step(float yaw,float pitch,double forward,double side,double vertical,double speed) {
        Vec3 direction=Vec3.directionFromRotation(pitch,yaw).scale(forward)
            .add(Vec3.directionFromRotation(0,yaw+90).scale(side)).add(0,vertical,0);
        return direction.lengthSqr()<1e-10?Vec3.ZERO:direction.normalize().scale(speed);
    }

    /** Editor navigation must never reach the real player's movement, crouch, or jump input. */
    static void suppressPlayerInput(Input input) {
        input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
        input.forwardImpulse=input.leftImpulse=0;
        var player=Minecraft.getInstance().player;
        if(player!=null){player.xxa=player.zza=0;player.setJumping(false);}
    }

    private KeyMapping[] bindings() {
        var o=Minecraft.getInstance().options;
        return new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint};
    }
    boolean key(int key,int scan,boolean down) {
        boolean handled=false;
        for(KeyMapping binding:bindings())if(binding.matches(key,scan)) {
            if(down)held.add(binding);else held.remove(binding);
            handled=true;
        }
        return handled;
    }
    void clear(){held.clear();velocity=Vec3.ZERO;}
    private boolean down(KeyMapping binding) {
        if(held.contains(binding))return true;
        var key=binding.getKey();long window=Minecraft.getInstance().getWindow().getWindow();
        if(key.getType()==InputConstants.Type.KEYSYM && key.getValue()>=0)return InputConstants.isKeyDown(window,key.getValue());
        if(key.getType()==InputConstants.Type.MOUSE && key.getValue()>=0)return GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
        return false;
    }

    private static final class CameraEntity extends Marker {
        private final EntityDimensions dimensions;
        CameraEntity(ClientLevel level,float eyeHeight) {
            super(EntityType.MARKER,level);
            dimensions=EntityDimensions.fixed(.01f,.01f).withEyeHeight(eyeHeight);refreshDimensions();
            setNoGravity(true);setSilent(true);
        }
        @Override public EntityDimensions getDimensions(Pose pose) { return dimensions==null?super.getDimensions(pose):dimensions; }
    }
}
