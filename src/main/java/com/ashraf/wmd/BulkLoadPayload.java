package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BulkLoadPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<BulkLoadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bulk_load"));

    public static final StreamCodec<FriendlyByteBuf, BulkLoadPayload> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, BulkLoadPayload::pos, BulkLoadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BulkLoadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp) BulkLoad.handle(sp, payload.pos()); });
    }
}
