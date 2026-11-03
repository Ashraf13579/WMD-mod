package com.ashraf.wmd;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ExoticFxPayload(int id, int kind, float x, float y, float z, float r, int left, int total, long[] eat) implements CustomPacketPayload {

    public static final int NANITE_STATE = 0, NANITE_END = 1, GRAV_STATE = 2, GRAV_THROW = 3;
    static final int MAX_EAT = 256;

    public static final Type<ExoticFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exotic_fx"));

    public static final StreamCodec<FriendlyByteBuf, ExoticFxPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.id()); b.writeVarInt(p.kind()); b.writeFloat(p.x()); b.writeFloat(p.y()); b.writeFloat(p.z()); b.writeFloat(p.r());
        b.writeVarInt(p.left()); b.writeVarInt(p.total());
        int n = Math.min(MAX_EAT, p.eat().length);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeLong(p.eat()[i]);
    }, b -> {
        int id = b.readVarInt(), kind = b.readVarInt();
        float x = b.readFloat(), y = b.readFloat(), z = b.readFloat(), r = b.readFloat();
        int left = b.readVarInt(), total = b.readVarInt(), n = b.readVarInt();
        if (n < 0 || n > MAX_EAT) throw new io.netty.handler.codec.DecoderException("exotic_fx: bad block count " + n);
        long[] eat = new long[n];
        for (int i = 0; i < n; i++) eat[i] = b.readLong();
        return new ExoticFxPayload(id, kind, x, y, z, r, left, total, eat);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final ConcurrentLinkedQueue<ExoticFxPayload> PENDING = Statics.client(new ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { WarheadFx.clearBites(); }

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
