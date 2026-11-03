package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TripodScopePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<TripodScopePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tripod_scope"));

    public static final StreamCodec<FriendlyByteBuf, TripodScopePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBlockPos(p.pos()), buf -> new TripodScopePayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
