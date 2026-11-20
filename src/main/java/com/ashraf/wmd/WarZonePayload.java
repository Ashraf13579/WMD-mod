package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WarZonePayload(byte state) implements CustomPacketPayload {

    public static final Type<WarZonePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "war_zone"));

    public static final StreamCodec<FriendlyByteBuf, WarZonePayload> STREAM_CODEC = StreamCodec.of((b, p) -> b.writeByte(p.state()), b -> new WarZonePayload(b.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
