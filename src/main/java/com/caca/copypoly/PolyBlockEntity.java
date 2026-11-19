package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.compat.PhysicsCompat;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.function.BiFunction;

public class PolyBlockEntity extends CopycatBlockEntity {
    public static final ModelProperty<Polyhedron> GEOMETRY = new ModelProperty<>();
    private static volatile BiFunction<PolyBlockEntity, ModelData, ModelData> clientModelDataEnhancer;
    private Polyhedron poly = Polyhedron.CUBE;
    private VoxelShape shape;
    private int revision;
    private long lastEdit = Long.MIN_VALUE / 2;

    public PolyBlockEntity(BlockPos pos, BlockState state) { super(Copypoly.ENTITY.get(), pos, state); }
    public static void setClientModelDataEnhancer(BiFunction<PolyBlockEntity, ModelData, ModelData> enhancer) {
        clientModelDataEnhancer = enhancer;
    }
    public Polyhedron poly() { return poly; }
    public int revision() { return revision; }
    public boolean tryBeginEdit() {
        if (level == null || level.getGameTime() - lastEdit < 4) return false;
        lastEdit = level.getGameTime();
        return true;
    }
    public void setPoly(Polyhedron value) {
        poly = value; shape = null; revision++;
        if (level != null) {
            lastEdit = level.getGameTime();
            notifyUpdate();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            PhysicsCompat.shapeChanged(level, worldPosition);
        }
    }
    public VoxelShape shape() {
        if (shape == null) {
            VoxelShape built = Shapes.empty();
            for (Polyhedron.Box b : poly.collisionBoxes()) built = Shapes.joinUnoptimized(built,
                Shapes.box(b.x0()/16, b.y0()/16, b.z0()/16, b.x1()/16, b.y1()/16, b.z1()/16), BooleanOp.OR);
            shape = built.optimize();
        }
        return shape;
    }
    @Override public void onLoad() {
        super.onLoad();
        PhysicsCompat.shapeChanged(level, worldPosition);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putIntArray("Points", poly.packed()); tag.putInt("Revision", revision);
    }
    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries); tag.putIntArray("Points", poly.packed());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        Polyhedron previous = poly;
        try { poly = tag.contains("Points") ? Polyhedron.fromPacked(tag.getIntArray("Points")) : Polyhedron.CUBE; }
        catch (IllegalArgumentException e) { poly = Polyhedron.CUBE; }
        revision = tag.getInt("Revision");
        if (previous != poly) {
            shape = null;
            PhysicsCompat.shapeChanged(level, worldPosition);
            if (packet && level != null) {
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    @Override public ModelData getModelData() {
        ModelData data = super.getModelData().derive().with(GEOMETRY, poly).build();
        if (level == null || !level.isClientSide) return data;
        BiFunction<PolyBlockEntity, ModelData, ModelData> enhancer = clientModelDataEnhancer;
        return enhancer == null ? data : enhancer.apply(this, data);
    }
    @Override public void transform(BlockEntity be, StructureTransform transform) {
        super.transform(be, transform);
        setPoly(Polyhedron.of(poly.points().stream().map(p -> {
            Vec3 v = transform.applyWithoutOffset(new Vec3(p.x()/16.0, p.y()/16.0, p.z()/16.0));
            return new Polyhedron.Point((int)Math.round(v.x*16), (int)Math.round(v.y*16), (int)Math.round(v.z*16));
        }).toList()));
    }
}
