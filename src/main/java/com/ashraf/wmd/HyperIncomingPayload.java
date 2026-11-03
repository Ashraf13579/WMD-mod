package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HyperIncomingPayload(long flight, long warn, long dive, long impact, float[] pts) implements CustomPacketPayload {

    public static final int DIVE_POINTS = 10;

    public static final Type<HyperIncomingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hyper_incoming"));

    public static final StreamCodec<FriendlyByteBuf, HyperIncomingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.flight()); buf.writeLong(p.warn()); buf.writeLong(p.dive()); buf.writeLong(p.impact());
                buf.writeVarInt(p.pts().length);
                for (float f : p.pts()) buf.writeFloat(f);
            },
            buf -> {
                long id = buf.readLong(), w = buf.readLong(), d = buf.readLong(), i = buf.readLong();
                int n = buf.readVarInt();
                if (n < 0 || n > 3 * (DIVE_POINTS + 1)) throw new IllegalArgumentException("hyper incoming: bad size " + n);
                float[] pts = new float[n];
                for (int k = 0; k < n; k++) pts[k] = buf.readFloat();
                return new HyperIncomingPayload(id, w, d, i, pts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
