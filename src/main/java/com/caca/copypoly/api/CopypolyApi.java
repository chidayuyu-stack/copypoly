package com.caca.copypoly.api;

import com.caca.copypoly.PolyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;

/** Server-side integration entry points. Calls must run on the owning server thread. */
public final class CopypolyApi {
    public static PolyShape getShape(BlockGetter level,BlockPos pos) {
        if(level.getBlockEntity(pos) instanceof PolyBlockEntity entity)return entity.document();
        throw new IllegalArgumentException("Not a CopyPoly block at "+pos);
    }
    public static void setShape(ServerLevel level,BlockPos pos,PolyShape shape) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Apply shapes on the server thread");
        if(!level.hasChunkAt(pos))throw new IllegalArgumentException("Chunk is not loaded");
        shape.compile();
        if(level.getBlockEntity(pos) instanceof PolyBlockEntity entity){entity.setDocument(shape);return;}
        throw new IllegalArgumentException("Not a CopyPoly block at "+pos);
    }
    private CopypolyApi(){}
}
