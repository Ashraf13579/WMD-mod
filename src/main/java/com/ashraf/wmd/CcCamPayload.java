package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CcCamPayload(long flight) implements CustomPacketPayload {

    public static final Type<CcCamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_cam"));

    public static final StreamCodec<FriendlyByteBuf, CcCamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeLong(p.flight()), buf -> new CcCamPayload(buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
