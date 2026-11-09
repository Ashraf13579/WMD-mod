package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UnderwaterBlastPayload(double x, double y, double z, int kind, float power, float depth, float surface) implements CustomPacketPayload {

    public static final Type<UnderwaterBlastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "underwater_blast"));

    public static final StreamCodec<FriendlyByteBuf, UnderwaterBlastPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeVarInt(p.kind); b.writeFloat(p.power); b.writeFloat(p.depth); b.writeFloat(p.surface); },
            b -> new UnderwaterBlastPayload(b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
