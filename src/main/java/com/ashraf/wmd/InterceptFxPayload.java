package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InterceptFxPayload(double x, double y, double z, boolean kill, int fooled) implements CustomPacketPayload {

    public static final Type<InterceptFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "intercept_fx"));

    public static final StreamCodec<FriendlyByteBuf, InterceptFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, InterceptFxPayload::x,
            ByteBufCodecs.DOUBLE, InterceptFxPayload::y,
            ByteBufCodecs.DOUBLE, InterceptFxPayload::z,
            ByteBufCodecs.BOOL, InterceptFxPayload::kill,
            ByteBufCodecs.VAR_INT, InterceptFxPayload::fooled,
            InterceptFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
