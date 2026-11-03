package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetRadarBearingPayload(BlockPos pos, int bearing) implements CustomPacketPayload {

    public static final Type<SetRadarBearingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_radar_bearing"));

    public static final StreamCodec<FriendlyByteBuf, SetRadarBearingPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetRadarBearingPayload::pos,
            ByteBufCodecs.VAR_INT, SetRadarBearingPayload::bearing,
            SetRadarBearingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetRadarBearingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof RadarMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof RadarBlockEntity be) {
                be.setTargetBearing(Math.floorMod(payload.bearing(), 360));
            }
        });
    }
}
