package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AftermathPayload(double x, double y, double z, float radius, byte kind, long start, int duration) implements CustomPacketPayload {

    public static final Type<AftermathPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aftermath"));

    public static final StreamCodec<FriendlyByteBuf, AftermathPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeFloat(p.radius()); b.writeByte(p.kind()); b.writeLong(p.start()); b.writeVarInt(p.duration()); },
            b -> new AftermathPayload(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readByte(), b.readLong(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
