package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetMachineModePayload(BlockPos pos, int mode) implements CustomPacketPayload {

    public static final Type<SetMachineModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "set_machine_mode"));

    public static final StreamCodec<FriendlyByteBuf, SetMachineModePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetMachineModePayload::pos,
            ByteBufCodecs.VAR_INT, SetMachineModePayload::mode,
            SetMachineModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetMachineModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof ModeMachine mm
                    && menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getBlockPos().equals(payload.pos())
                    && payload.mode() >= 0 && payload.mode() < mm.modeCount()
                    && (!(be instanceof Owned o) || Access.mayUse(o, context.player()))) {
                mm.setMode(payload.mode());
            }
        });
    }
}
