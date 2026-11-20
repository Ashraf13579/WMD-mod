package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record StormPayload(int kind, int id, Vec3 a, Vec3 b, float r, long t0, long t1, long t2) implements CustomPacketPayload {

    public static final int START = 0, STRIKE = 1, CUT = 2;

    public static final Type<StormPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "storm"));

    public static final StreamCodec<FriendlyByteBuf, StormPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind()); buf.writeVarInt(p.id());
                vec(buf, p.a()); vec(buf, p.b());
                buf.writeFloat(p.r()); buf.writeLong(p.t0()); buf.writeLong(p.t1()); buf.writeLong(p.t2());
            },
            buf -> new StormPayload(buf.readByte(), buf.readVarInt(), vec(buf), vec(buf), buf.readFloat(), buf.readLong(), buf.readLong(), buf.readLong()));

    private static void vec(FriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }

    private static Vec3 vec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientStorm.message(p)));
        }
    }
}
