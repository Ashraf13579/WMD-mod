package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetMachineFrequencyPayload(BlockPos pos, int frequency) implements CustomPacketPayload {

    public static final Type<SetMachineFrequencyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_machine_frequency"));

    public static final StreamCodec<FriendlyByteBuf, SetMachineFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetMachineFrequencyPayload::pos,
            ByteBufCodecs.VAR_INT, SetMachineFrequencyPayload::frequency,
            SetMachineFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetMachineFrequencyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof RadarLinked rl
                    && menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getBlockPos().equals(payload.pos())
                    && (!(be instanceof Owned o) || Access.mayUse(o, context.player()))) {
                rl.setFrequency(payload.frequency());
            }
        });
    }
}
