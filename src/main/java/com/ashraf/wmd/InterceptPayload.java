package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InterceptPayload(long id, double fx, double fy, double fz, double tx, double ty, double tz, long start, int duration, int kind) implements CustomPacketPayload {

    public static final Type<InterceptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "intercept"));

    public static final StreamCodec<FriendlyByteBuf, InterceptPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.id); buf.writeDouble(p.fx); buf.writeDouble(p.fy); buf.writeDouble(p.fz);
                buf.writeDouble(p.tx); buf.writeDouble(p.ty); buf.writeDouble(p.tz); buf.writeLong(p.start); buf.writeVarInt(p.duration); buf.writeVarInt(p.kind);
            },
            buf -> new InterceptPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readLong(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
