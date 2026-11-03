package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CounterPayload(long shot, long flight, int kind, boolean fooled, long arrive) implements CustomPacketPayload {

    public static final Type<CounterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "counter"));

    public static final StreamCodec<FriendlyByteBuf, CounterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CounterPayload::shot,
            ByteBufCodecs.VAR_LONG, CounterPayload::flight,
            ByteBufCodecs.VAR_INT, CounterPayload::kind,
            ByteBufCodecs.BOOL, CounterPayload::fooled,
            ByteBufCodecs.VAR_LONG, CounterPayload::arrive,
            CounterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
