package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AsatFxPayload(OrbitMath.Sat sat, long serverMs, boolean hit) implements CustomPacketPayload {

    public static final Type<AsatFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asat_fx"));

    public static final StreamCodec<FriendlyByteBuf, AsatFxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { p.sat().write(buf); buf.writeLong(p.serverMs()); buf.writeBoolean(p.hit()); },
            buf -> new AsatFxPayload(OrbitMath.Sat.read(buf), buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
