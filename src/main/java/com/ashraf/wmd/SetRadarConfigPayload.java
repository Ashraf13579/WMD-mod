package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetRadarConfigPayload(BlockPos pos, int mode, int frequency) implements CustomPacketPayload {

    public static final Type<SetRadarConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_radar_config"));

    public static final StreamCodec<FriendlyByteBuf, SetRadarConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetRadarConfigPayload::pos,
            ByteBufCodecs.VAR_INT, SetRadarConfigPayload::mode,
            ByteBufCodecs.VAR_INT, SetRadarConfigPayload::frequency,
            SetRadarConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetRadarConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof RadarMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof RadarBlockEntity be) {
                be.setMode(payload.mode());
                be.setFrequency(payload.frequency());
            }
        });
    }
}
