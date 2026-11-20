package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record WardenDronesPayload(int warden, int[] ids, float[] xyz) implements CustomPacketPayload {

    public static final Type<WardenDronesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "warden_drones"));

    public static final StreamCodec<FriendlyByteBuf, WardenDronesPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.warden);
        buf.writeVarInt(p.ids.length);
        for (int i = 0; i < p.ids.length; i++) { buf.writeVarInt(p.ids[i]); buf.writeFloat(p.xyz[i * 3]); buf.writeFloat(p.xyz[i * 3 + 1]); buf.writeFloat(p.xyz[i * 3 + 2]); }
    }, buf -> {
        int w = buf.readVarInt(), n = Math.min(64, buf.readVarInt());
        int[] ids = new int[n];
        float[] xyz = new float[n * 3];
        for (int i = 0; i < n; i++) { ids[i] = buf.readVarInt(); xyz[i * 3] = buf.readFloat(); xyz[i * 3 + 1] = buf.readFloat(); xyz[i * 3 + 2] = buf.readFloat(); }
        return new WardenDronesPayload(w, ids, xyz);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final java.util.concurrent.ConcurrentLinkedQueue<WardenDronesPayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
