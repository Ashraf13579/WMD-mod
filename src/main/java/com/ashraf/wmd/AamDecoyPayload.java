package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AamDecoyPayload(double x, double y, double z, float vx, float vy, float vz, int kind) implements CustomPacketPayload {

    public static final Type<AamDecoyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_decoy"));

    public static final StreamCodec<FriendlyByteBuf, AamDecoyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeFloat(p.vx()); buf.writeFloat(p.vy()); buf.writeFloat(p.vz()); buf.writeByte(p.kind()); },
            buf -> new AamDecoyPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
