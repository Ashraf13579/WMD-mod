package com.ashraf.wmd;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HandToolKeyPayload(int action) implements CustomPacketPayload {

    public static final int THROW = 0;

    public static final Type<HandToolKeyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hand_tool_key"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, HandToolKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HandToolKeyPayload::action, HandToolKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HandToolKeyPayload p, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp && p.action() == THROW) TractorBeam.fling(sp); });
    }
}
