package com.ashraf.wmd;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EvaThrustPayload(boolean on) implements CustomPacketPayload {

    public static final Type<EvaThrustPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "eva_thrust"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, EvaThrustPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EvaThrustPayload::on, EvaThrustPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EvaThrustPayload p, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer sp) OrbitSurvival.thrust(sp, p.on() && Orbit.isOrbit(sp.level())); });
    }
}
