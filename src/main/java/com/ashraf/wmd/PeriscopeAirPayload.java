package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PeriscopeAirPayload(float[] marks) implements CustomPacketPayload {

    public static final int MAX = 32;

    public static final Type<PeriscopeAirPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "periscope_air"));

    public static final StreamCodec<FriendlyByteBuf, PeriscopeAirPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX * 4, p.marks().length / 4 * 4);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeFloat(p.marks()[i]);
            },
            buf -> {
                int n = Math.min(MAX * 4, buf.readVarInt());
                float[] m = new float[n];
                for (int i = 0; i < n; i++) m[i] = buf.readFloat();
                return new PeriscopeAirPayload(m);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
