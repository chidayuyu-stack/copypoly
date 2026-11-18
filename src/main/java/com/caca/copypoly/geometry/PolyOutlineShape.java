package com.caca.copypoly.geometry;

import com.caca.copypoly.mixin.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The solid voxel grid stays intact; only the selection wireframe follows the hull. */
public final class PolyOutlineShape extends VoxelShape {
    private final VoxelShape collision;
    private final Polyhedron poly;

    public PolyOutlineShape(VoxelShape collision, Polyhedron poly) {
        super(((VoxelShapeAccessor) collision).copypoly$voxels());
        this.collision = collision;
        this.poly = poly;
    }

    @Override public DoubleList getCoords(Direction.Axis axis) { return collision.getCoords(axis); }

    @Override public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        for(var edge:poly.edges()) {
            var a=edge.a();var b=edge.b();
            consumer.consume(a.x()/16,a.y()/16,a.z()/16,b.x()/16,b.y()/16,b.z()/16);
        }
    }
}
