package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.api.PolyShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PolyMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final Polyhedron initial;
    public final PolyShape document;
    public final int revision;
    private final net.minecraft.world.level.Level level;
    private final PolyBlockEntity target;
    public PolyMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), PolyShape.fromPacked(buf.readVarIntArray(PolyShape.MAX_PACKED)), buf.readVarInt());
    }
    public PolyMenu(int id, Inventory inv, BlockPos pos, Polyhedron initial, int revision) {
        this(id,inv,pos,PolyShape.fromGeometry(initial),revision);
    }
    public PolyMenu(int id,Inventory inv,BlockPos pos,PolyShape document,int revision) {
        super(Copypoly.MENU.get(), id); this.pos=pos.immutable(); this.document=document;this.initial=document.compile().geometry();this.revision=revision;
        level = inv.player.level();
        target = level.getBlockEntity(pos) instanceof PolyBlockEntity be ? be : null;
    }
    @Override public boolean stillValid(Player p) {
        return p.isAlive() && p.level() == level && level.getBlockState(pos).is(Copypoly.POLY.get())
            && (level.isClientSide || target != null && !target.isRemoved() && level.getBlockEntity(pos) == target)
            && p.canInteractWithBlock(pos,16-p.blockInteractionRange()) && p.mayBuild();
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
