package com.caca.copypoly.client;

import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.world.phys.Vec3;

/** Orthographic editor math shared by rendering, picking and dragging. Coordinates are in 0..16 units. */
final class PreviewGeometry {
    static final Vec3[] AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    record View(double yaw, double pitch, double scale, double centerX, double centerY) {
        Vec3 rotate(Vec3 p) {
            double x = Math.cos(yaw) * p.x + Math.sin(yaw) * p.z;
            double z = -Math.sin(yaw) * p.x + Math.cos(yaw) * p.z;
            return new Vec3(x, Math.cos(pitch) * p.y - Math.sin(pitch) * z,
                Math.sin(pitch) * p.y + Math.cos(pitch) * z);
        }

        Vec3 inverse(Vec3 p) {
            double y = Math.cos(pitch) * p.y + Math.sin(pitch) * p.z;
            double z = -Math.sin(pitch) * p.y + Math.cos(pitch) * p.z;
            return new Vec3(Math.cos(yaw) * p.x - Math.sin(yaw) * z, y,
                Math.sin(yaw) * p.x + Math.cos(yaw) * z);
        }

        Vec3 project(Vec3 point) {
            Vec3 r = rotate(point.subtract(8, 8, 8));
            return new Vec3(centerX + r.x * scale, centerY - r.y * scale, r.z);
        }

        Vec3 axis(int axis) {
            Vec3 r = rotate(AXES[axis]);
            return new Vec3(r.x * scale, -r.y * scale, 0);
        }

        /** The renderer looks from positive camera Z toward negative Z. */
        Vec3 surface(Polyhedron hull, double x, double y) {
            Vec3 origin = inverse(new Vec3((x - centerX) / scale, (centerY - y) / scale, 64)).add(8, 8, 8);
            Vec3 direction = inverse(new Vec3(0, 0, -1));
            double near = 0, far = Double.POSITIVE_INFINITY;
            for (var face : hull.faces()) {
                var p = face.plane();
                double distance = p.distance(origin.x, origin.y, origin.z);
                double denominator = p.x() * direction.x + p.y() * direction.y + p.z() * direction.z;
                if (Math.abs(denominator) < 1e-9) {
                    if (distance > 1e-8) return null;
                } else if (denominator < 0) near = Math.max(near, -distance / denominator);
                else far = Math.min(far, -distance / denominator);
                if (near > far + 1e-8) return null;
            }
            return origin.add(direction.scale(near));
        }
    }

    static Polyhedron.Point snap(Vec3 p) {
        return new Polyhedron.Point(clamp(p.x), clamp(p.y), clamp(p.z));
    }

    private static int clamp(double value) { return (int)Math.max(0, Math.min(16, Math.round(value))); }

    static double segmentDistance(double x, double y, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dy = b.y - a.y, length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x) * dx + (y - a.y) * dy) / length));
        return Math.hypot(x - a.x - t * dx, y - a.y - t * dy);
    }

    static double draggedCoordinate(double start, double dx, double dy, Vec3 projectedAxis) {
        return draggedCoordinate(start, dx, dy, projectedAxis, false);
    }

    static double draggedCoordinate(double start, double dx, double dy, Vec3 projectedAxis, boolean fine) {
        double length = projectedAxis.x * projectedAxis.x + projectedAxis.y * projectedAxis.y;
        if (length < .01) return start;
        double delta = (dx * projectedAxis.x + dy * projectedAxis.y) / length;
        if (Math.abs(delta) < 1e-10) return start;
        double target = fine ? start + Math.round(delta * .1 / .01) * .01 : Math.round(start + delta);
        return Math.round(Math.max(0, Math.min(16, target)) * Polyhedron.SCALE) / (double)Polyhedron.SCALE;
    }

    /** Mouse deltas accumulate from a mode anchor, while Esc always uses the original point. */
    static final class Drag {
        final Polyhedron.Point original;
        private final int axis;
        private final Vec3 vector;
        private Polyhedron.Point anchor;
        private double anchorX, anchorY, lastX, lastY;
        private boolean fine;

        Drag(Polyhedron.Point point, int axis, Vec3 vector, double x, double y, boolean fine) {
            original = point; this.axis = axis; this.vector = vector;
            anchor = point; anchorX = lastX = x; anchorY = lastY = y; this.fine = fine;
        }

        void mode(boolean fine, Polyhedron.Point current) {
            if (this.fine == fine) return;
            this.fine = fine; anchor = current; anchorX = lastX; anchorY = lastY;
        }

        Polyhedron.Point move(double x, double y, boolean fine, Polyhedron.Point current) {
            mode(fine, current);
            lastX = x; lastY = y;
            double[] values = {anchor.x(), anchor.y(), anchor.z()};
            values[axis] = draggedCoordinate(values[axis], x-anchorX, y-anchorY, vector, fine);
            return new Polyhedron.Point(values[0], values[1], values[2]);
        }
    }
}
