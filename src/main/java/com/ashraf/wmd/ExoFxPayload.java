package com.ashraf.wmd;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExoFxPayload(int entity, int action) implements CustomPacketPayload {

    public static final Type<ExoFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_fx"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ExoFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExoFxPayload::entity, ByteBufCodecs.VAR_INT, ExoFxPayload::action, ExoFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
