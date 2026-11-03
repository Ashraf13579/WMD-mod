package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BombSightKeyPayload(BlockPos pos, int key) implements CustomPacketPayload {

    public static final Type<BombSightKeyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomb_sight_key"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, BombSightKeyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BombSightKeyPayload::pos,
            ByteBufCodecs.VAR_INT, BombSightKeyPayload::key,
            BombSightKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BombSightKeyPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp) || !sp.serverLevel().isLoaded(p.pos())) return;
            if (sp.serverLevel().getBlockEntity(p.pos()) instanceof BombSightBlockEntity s) s.key(sp, p.key());
        });
    }
}
