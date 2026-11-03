package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record GasCloudPayload(int id, float x, float y, float z, float r, float fade, float wx, float wz) implements CustomPacketPayload {

    public static final Type<GasCloudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gas_cloud"));

    public static final StreamCodec<FriendlyByteBuf, GasCloudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.id()); buf.writeFloat(p.x()); buf.writeFloat(p.y()); buf.writeFloat(p.z()); buf.writeFloat(p.r()); buf.writeFloat(p.fade()); buf.writeFloat(p.wx()); buf.writeFloat(p.wz()); },
            buf -> new GasCloudPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final Map<Integer, Object[]> SEEN = Statics.client(new HashMap<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> SEEN.put(p.id(), new Object[] { p, -1L })));
        }
    }
}
