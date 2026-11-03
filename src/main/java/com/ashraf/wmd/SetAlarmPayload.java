package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetAlarmPayload(BlockPos pos, int frequency, boolean test) implements CustomPacketPayload {

    public static final Type<SetAlarmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_alarm"));

    public static final StreamCodec<FriendlyByteBuf, SetAlarmPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetAlarmPayload::pos,
            ByteBufCodecs.VAR_INT, SetAlarmPayload::frequency,
            ByteBufCodecs.BOOL, SetAlarmPayload::test,
            SetAlarmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetAlarmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof AlarmMenu menu && menu.getPos().equals(payload.pos())
                    && context.player().level().getBlockEntity(payload.pos()) instanceof AlarmBlockEntity be) {
                be.setFrequency(payload.frequency());
                if (payload.test()) be.startTest();
            }
        });
    }
}
