package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeadHandIntelPayload(BlockPos pos, long[] pads, int[] board, long[] candidates) implements CustomPacketPayload {

    public static final Type<DeadHandIntelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "deadhand_intel"));

    public static final StreamCodec<FriendlyByteBuf, DeadHandIntelPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeLongArray(p.pads());
                buf.writeVarIntArray(p.board());
                buf.writeLongArray(p.candidates());
            },
            buf -> new DeadHandIntelPayload(buf.readBlockPos(), buf.readLongArray(null, 64), buf.readVarIntArray(1024), buf.readLongArray(null, 64)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
