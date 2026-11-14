package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArtImpactPayload(long id, int gun, int kind, double x, double y, double z, int stage) implements CustomPacketPayload {

    public static final int IMPACT = 0, SHOT_DOWN = 1;

    public static final int GONE = 2;

    public static final Type<ArtImpactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "art_impact"));

    public static final StreamCodec<FriendlyByteBuf, ArtImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarLong(p.id()); buf.writeByte(p.gun()); buf.writeByte(p.kind());
                buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeByte(p.stage());
            },
            buf -> new ArtImpactPayload(buf.readVarLong(), buf.readByte(), Artillery.knownType(buf.readByte()),       buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
