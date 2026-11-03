package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LaunchPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<LaunchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch"));

    public static final StreamCodec<FriendlyByteBuf, LaunchPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, LaunchPayload::pos, LaunchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LaunchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu && menu.getPos().equals(payload.pos())
                    && player.level().getBlockEntity(payload.pos()) instanceof LaunchTerminalBlockEntity be) {
                Component problem = be.tryLaunch(player);
                if (problem != null) player.displayClientMessage(problem, true);
            }
        });
    }
}
