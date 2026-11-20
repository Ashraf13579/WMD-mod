package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record WardenFxPayload(int kind, float x, float y, float z, float tx, float ty, float tz, int dur) implements CustomPacketPayload {

    public static final int RING = 0, SHELL = 1, SLAM = 2, FLASH = 3, RAIL = 4, PURGE = 5, EMP = 6, EYE = 7;

    public static final Type<WardenFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "warden_fx"));

    public static final StreamCodec<FriendlyByteBuf, WardenFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.kind); buf.writeFloat(p.x); buf.writeFloat(p.y); buf.writeFloat(p.z); buf.writeFloat(p.tx); buf.writeFloat(p.ty); buf.writeFloat(p.tz); buf.writeVarInt(p.dur); },
            buf -> new WardenFxPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final java.util.concurrent.ConcurrentLinkedQueue<WardenFxPayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
