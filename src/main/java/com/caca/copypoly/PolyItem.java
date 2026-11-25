package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
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
            if (poly == Polyhedron.CUBE) PolyShapeData.clear(tag);
            else PolyShapeData.write(tag, poly);
        });
    }
    public static Polyhedron readShape(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try { return PolyShapeData.read(tag, "PolyPoints"); }
        catch (IllegalArgumentException e) { return Polyhedron.CUBE; }
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.copypoly.edit").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.copypoly.points", readShape(stack).points().size()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
