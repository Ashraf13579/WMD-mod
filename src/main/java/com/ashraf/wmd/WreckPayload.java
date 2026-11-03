package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WreckPayload(double x, double y, double z, float[] v) implements CustomPacketPayload {

    public static final Type<WreckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wreck"));

    public static final StreamCodec<FriendlyByteBuf, WreckPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeVarInt(p.v().length); for (float f : p.v()) b.writeFloat(f); },
            b -> {
                double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
                int n = Math.min(b.readVarInt(), 3 * 16);
                float[] v = new float[n];
                for (int i = 0; i < n; i++) v[i] = b.readFloat();
                return new WreckPayload(x, y, z, v);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
