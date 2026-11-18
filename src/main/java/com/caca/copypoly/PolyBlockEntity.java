package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.geometry.PolyOutlineShape;
import com.caca.copypoly.compat.PhysicsCompat;
import com.caca.copypoly.api.PolyShape;
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

public class PolyBlockEntity extends CopycatBlockEntity {
    public static final ModelProperty<Polyhedron> GEOMETRY = new ModelProperty<>();
    private Polyhedron poly = Polyhedron.CUBE;
    private PolyShape document=PolyShape.fromGeometry(Polyhedron.CUBE);
    private Polyhedron clientPreview;
    private VoxelShape shape;
    private VoxelShape outline;
    private int revision;
    private long lastEdit = Long.MIN_VALUE / 2;

    public PolyBlockEntity(BlockPos pos, BlockState state) { super(Copypoly.ENTITY.get(), pos, state); }
    public Polyhedron poly() { return poly; }
    public PolyShape document(){return document;}
    public int revision() { return revision; }
    public boolean tryBeginEdit() {
        if (level == null || level.getGameTime() - lastEdit < 4) return false;
        lastEdit = level.getGameTime();
        return true;
    }
    public void setPoly(Polyhedron value) {
        document=PolyShape.fromGeometry(value);
        updateGeometry(value);
    }
    public void setDocument(PolyShape value) {
        var geometry=value.compile().geometry();document=value;updateGeometry(geometry);
    }
    private void updateGeometry(Polyhedron value) {
        poly = value; shape = null; outline = null; revision++;
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
    public VoxelShape outline() {
        if (outline == null) outline = new PolyOutlineShape(shape(), poly);
        return outline;
    }
    @Override public void onLoad() {
        super.onLoad();
        PhysicsCompat.shapeChanged(level, worldPosition);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putIntArray("Points", poly.packed()); tag.putInt("Revision", revision);
        writeFaces(tag);
    }
    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries); tag.putIntArray("Points", poly.packed()); writeFaces(tag);
    }
    private void writeFaces(CompoundTag tag) {
        if(poly.freeform())tag.putIntArray("Faces",poly.packedTriangles());else tag.remove("Faces");
        if(document.managed())tag.putIntArray("VertexGraph",document.packed());else tag.remove("VertexGraph");
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        Polyhedron previous = poly;
        try {
            if(tag.contains("VertexGraph")){document=PolyShape.fromPacked(tag.getIntArray("VertexGraph"));poly=document.compile().geometry();}
            else{poly = tag.contains("Points") ? Polyhedron.fromPacked(tag.getIntArray("Points"),tag.getIntArray("Faces")) : Polyhedron.CUBE;document=PolyShape.fromGeometry(poly);}
        } catch (IllegalArgumentException e) { poly = Polyhedron.CUBE;document=PolyShape.fromGeometry(poly); }
        revision = tag.getInt("Revision");
        if (previous != poly) {
            shape = null; outline = null;
            PhysicsCompat.shapeChanged(level, worldPosition);
            if (packet && level != null) {
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    /** Local visual draft only; collision, persistence and server state continue to use poly. */
    public void setClientPreview(Polyhedron value) {
        if (level == null || !level.isClientSide || clientPreview == value) return;
        clientPreview = value;
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public ModelData getModelData() { return super.getModelData().derive().with(GEOMETRY, clientPreview == null ? poly : clientPreview).build(); }
    @Override public void transform(BlockEntity be, StructureTransform transform) {
        super.transform(be, transform);
        setDocument(document.transformPositions(p -> {
            Vec3 v = transform.applyWithoutOffset(new Vec3(p.x()/16.0, p.y()/16.0, p.z()/16.0));
            return new Polyhedron.Point(Math.clamp(Math.round(v.x*160),0,160)/10.0,
                Math.clamp(Math.round(v.y*160),0,160)/10.0, Math.clamp(Math.round(v.z*160),0,160)/10.0);
        }));
    }
}
