package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InterceptSteerPayload(long id, double tx, double ty, double tz) implements CustomPacketPayload {

    public static final Type<InterceptSteerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "intercept_steer"));

    public static final StreamCodec<FriendlyByteBuf, InterceptSteerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeLong(p.id()); buf.writeDouble(p.tx()); buf.writeDouble(p.ty()); buf.writeDouble(p.tz()); },
            buf -> new InterceptSteerPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
