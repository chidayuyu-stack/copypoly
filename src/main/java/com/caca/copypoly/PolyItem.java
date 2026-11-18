package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.api.PolyShape;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import java.util.List;

public class PolyItem extends BlockItem {
    public PolyItem(Block block) { super(block, new Item.Properties()); }
    public static void saveShape(ItemStack stack, Polyhedron poly) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if(poly==Polyhedron.CUBE)tag.remove("PolyPoints");else tag.putIntArray("PolyPoints", poly.packed());
            if(poly.freeform())tag.putIntArray("PolyFaces",poly.packedTriangles());else tag.remove("PolyFaces");
            tag.remove("PolyGraph");
        });
    }
    public static void saveDocument(ItemStack stack,PolyShape shape) {
        saveShape(stack,shape.compile().geometry());
        if(shape.managed())CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putIntArray("PolyGraph",shape.packed()));
    }
    public static PolyShape readDocument(ItemStack stack) {
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        try{if(tag.contains("PolyGraph")){var document=PolyShape.fromPacked(tag.getIntArray("PolyGraph"));document.compile();return document;}}
        catch(IllegalArgumentException invalid){return PolyShape.fromGeometry(Polyhedron.CUBE);}
        return PolyShape.fromGeometry(readLegacyShape(tag));
    }
    public static Polyhedron readShape(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if(tag.contains("PolyGraph"))return readDocument(stack).compile().geometry();
        return readLegacyShape(tag);
    }
    private static Polyhedron readLegacyShape(CompoundTag tag) {
        try { return tag.contains("PolyPoints") ? Polyhedron.fromPacked(tag.getIntArray("PolyPoints"),tag.getIntArray("PolyFaces")) : Polyhedron.CUBE; }
        catch (IllegalArgumentException e) { return Polyhedron.CUBE; }
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.copypoly.edit").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.copypoly.points", readShape(stack).points().size()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
