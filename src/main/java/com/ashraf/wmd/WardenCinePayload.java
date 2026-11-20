package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record WardenCinePayload(int kind, int warden, double cx, double cy, double cz, float yaw, double ox, double oz, double floorY, long start, long end)
        implements CustomPacketPayload {

    public static final Type<WardenCinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "warden_cine"));

    public static final StreamCodec<FriendlyByteBuf, WardenCinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.kind); buf.writeVarInt(p.warden); buf.writeDouble(p.cx); buf.writeDouble(p.cy); buf.writeDouble(p.cz); buf.writeFloat(p.yaw); buf.writeDouble(p.ox); buf.writeDouble(p.oz); buf.writeDouble(p.floorY); buf.writeLong(p.start); buf.writeLong(p.end); },
            buf -> new WardenCinePayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final java.util.concurrent.ConcurrentLinkedQueue<WardenCinePayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
