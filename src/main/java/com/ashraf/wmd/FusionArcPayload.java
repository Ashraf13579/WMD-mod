package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record FusionArcPayload(List<Arc> arcs) implements CustomPacketPayload {

    public record Arc(float x0, float y0, float z0, float x1, float y1, float z1, int seed, int kind) {}

    public static final Type<FusionArcPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_arc"));

    public static final StreamCodec<FriendlyByteBuf, FusionArcPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.arcs().size());
                for (Arc a : p.arcs()) {
                    buf.writeFloat(a.x0()); buf.writeFloat(a.y0()); buf.writeFloat(a.z0());
                    buf.writeFloat(a.x1()); buf.writeFloat(a.y1()); buf.writeFloat(a.z1());
                    buf.writeInt(a.seed()); buf.writeByte(a.kind());
                }
            },
            buf -> {
                int n = Math.min(32, buf.readVarInt());
                List<Arc> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    l.add(new Arc(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readByte()));
                return new FusionArcPayload(l);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> FusionArcs.add(p)));
        }
    }
}
