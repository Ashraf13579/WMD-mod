package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DepthChargePayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<DepthChargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "depth_charge"));

    public static final StreamCodec<FriendlyByteBuf, DepthChargePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DepthChargePayload::pos,
            ByteBufCodecs.VAR_INT, DepthChargePayload::action,
            ByteBufCodecs.VAR_INT, DepthChargePayload::value,
            DepthChargePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DepthChargePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof DepthChargeRackBlockEntity r)
                    || !r.getBlockPos().equals(p.pos()) || r.isRemoved() || !Access.mayUse(r, context.player())) return;
            r.act(context.player(), p.action(), p.value());
        });
    }
}
