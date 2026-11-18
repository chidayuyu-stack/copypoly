package com.caca.copypoly.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

/** One render-frame snapshot shared by the overlay, picking, marquee and axis rays. */
final class EditorWorldProjection {
    private final Matrix4f clipFromCamera, cameraFromClip;
    private final Matrix4d worldFromBlock, blockFromWorld;
    private final Vec3 camera;
    private final int width, height;

    EditorWorldProjection(Matrix4f view, Matrix4f projection, Vec3 camera, int width, int height, Matrix4d worldFromBlock) {
        clipFromCamera = new Matrix4f(projection).mul(view);
        cameraFromClip = new Matrix4f(clipFromCamera).invert();
        this.worldFromBlock = new Matrix4d(worldFromBlock);
        blockFromWorld = new Matrix4d(worldFromBlock).invert();
        this.camera = camera; this.width = width; this.height = height;
    }
    record Ray(Vec3 origin,Vec3 direction) {}
    Ray ray(double x,double y) {
        float nx=(float)(2*x/width-1),ny=(float)(1-2*y/height);
        var near=new Vector4f(nx,ny,-1,1).mul(cameraFromClip);var far=new Vector4f(nx,ny,1,1).mul(cameraFromClip);
        near.div(near.w);far.div(far.w);
        var a=blockFromWorld.transformPosition(new Vector3d(near.x+camera.x,near.y+camera.y,near.z+camera.z));
        var b=blockFromWorld.transformPosition(new Vector3d(far.x+camera.x,far.y+camera.y,far.z+camera.z));
        return new Ray(new Vec3(a.x,a.y,a.z),new Vec3(b.x-a.x,b.y-a.y,b.z-a.z).normalize());
    }

    Vec3 project(Vec3 local) {
        // Cancel the distant storage-plot origin in double precision before using
        // Minecraft's float projection, preserving the editor's 0.1-pixel lattice.
        var world = worldFromBlock.transformPosition(new Vector3d(local.x,local.y,local.z));
        var clip = new Vector4f((float)(world.x-camera.x),(float)(world.y-camera.y),(float)(world.z-camera.z),1).mul(clipFromCamera);
        if (clip.w <= .001 || clip.z/clip.w < -1 || clip.z/clip.w > 1) return null;
        return new Vec3((clip.x/clip.w+1)*width/2, (1-clip.y/clip.w)*height/2, clip.z/clip.w);
    }

    double axisCoordinate(double x, double y, Vec3 origin, int axis) {
        float nx=(float)(2*x/width-1), ny=(float)(1-2*y/height);
        var near=new Vector4f(nx,ny,-1,1).mul(cameraFromClip);
        var far=new Vector4f(nx,ny,1,1).mul(cameraFromClip);
        near.div(near.w); far.div(far.w);
        var localNear=blockFromWorld.transformPosition(new Vector3d(near.x+camera.x,near.y+camera.y,near.z+camera.z));
        var localFar=blockFromWorld.transformPosition(new Vector3d(far.x+camera.x,far.y+camera.y,far.z+camera.z));
        Vec3 rayOrigin=new Vec3(localNear.x,localNear.y,localNear.z);
        Vec3 ray=new Vec3(localFar.x-localNear.x,localFar.y-localNear.y,localFar.z-localNear.z).normalize();
        Vec3 direction=PolyGizmo.axis(axis), offset=rayOrigin.subtract(origin);
        double b=direction.dot(ray), denominator=1-b*b;
        return denominator<1e-5 ? Double.NaN : 16*(direction.dot(offset)-b*ray.dot(offset))/denominator;
    }
}
