package com.ashraf.wmd;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExoModPayload(int action, int arg) implements CustomPacketPayload {

    public static final Type<ExoModPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_mod"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ExoModPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExoModPayload::action, ByteBufCodecs.INT, ExoModPayload::arg, ExoModPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ExoModPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp && p.action() >= ExoMods.THERMAL_ON && p.action() <= ExoMods.THRUST_OFF) ExoMods.action(sp, p.action(), p.arg());
        });
    }
}
