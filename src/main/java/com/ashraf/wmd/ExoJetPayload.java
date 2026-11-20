package com.ashraf.wmd;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExoJetPayload(int action) implements CustomPacketPayload {

    public static final Type<ExoJetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_jet"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ExoJetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExoJetPayload::action, ExoJetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ExoJetPayload p, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp && p.action() >= ExoSuit.BOOST && p.action() <= ExoSuit.CROUCH_OFF) ExoSuit.action(sp, p.action()); });
    }
}
