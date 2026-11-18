package com.caca.copypoly;

import com.caca.copypoly.geometry.Polyhedron;
import com.caca.copypoly.api.PolyShape;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PolyPackets {
    /** Client callback is installed only by the client entry point, so dedicated servers never link GUI classes. */
    public static java.util.function.Consumer<Result> clientResult = r -> {};
    public record Apply(int menu, int revision, int[] points, int[] faces,int[] graph) implements CustomPacketPayload {
        public Apply(int menu,int revision,int[] points,int[] faces){this(menu,revision,points,faces,new int[0]);}
        public Apply(int menu,int revision,int[] points){this(menu,revision,points,new int[0]);}
        public Apply(int menu,int revision,PolyShape shape){this(menu,revision,new int[0],new int[0],shape.packed());}
        public static final Type<Apply> TYPE = new Type<>(Copypoly.id("apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Apply> CODEC = StreamCodec.of(
            (b,p)->{b.writeVarInt(p.menu);b.writeVarInt(p.revision);b.writeVarIntArray(p.points);b.writeVarIntArray(p.faces);b.writeVarIntArray(p.graph);},
            b->new Apply(b.readVarInt(),b.readVarInt(),b.readVarIntArray(Polyhedron.MAX_POINTS),b.readVarIntArray(Polyhedron.MAX_TRIANGLES*3),b.readVarIntArray(PolyShape.MAX_PACKED)));
        public Type<Apply> type() { return TYPE; }
    }
    public record Result(int menu, String error) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(Copypoly.id("result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of(
            (b,p)->{b.writeVarInt(p.menu);b.writeUtf(p.error,64);}, b->new Result(b.readVarInt(),b.readUtf(64)));
        public Type<Result> type() {return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("6");
        r.playToClient(Result.TYPE,Result.CODEC,(p,c)->clientResult.accept(p));
        r.playToServer(Apply.TYPE,Apply.CODEC,(p,c)->{
            if (!(c.player() instanceof ServerPlayer sp)) return;
            String error=apply(sp,p);
            PacketDistributor.sendToPlayer(sp,new Result(p.menu,error));
        });
    }
    static String apply(net.minecraft.world.entity.player.Player sp, Apply p) {
        if (!(sp.containerMenu instanceof PolyMenu menu) || menu.containerId!=p.menu || !menu.stillValid(sp)
                || !sp.level().mayInteract(sp,menu.pos)) return "permission";
        if (!(sp.level().getBlockEntity(menu.pos) instanceof PolyBlockEntity be)) return "missing";
        if (be.revision()!=p.revision || menu.revision!=p.revision) return "changed";
        if (!be.tryBeginEdit()) return "busy";
        try {
            if(p.graph.length>0)be.setDocument(PolyShape.fromPacked(p.graph));
            else be.setPoly(Polyhedron.fromPacked(p.points,p.faces));
            return "";
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }
}
