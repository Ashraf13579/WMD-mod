package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LaunchCellPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<LaunchCellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_cell"));

    public static final StreamCodec<FriendlyByteBuf, LaunchCellPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchCellPayload::pos,
            ByteBufCodecs.VAR_INT, LaunchCellPayload::action,
            ByteBufCodecs.VAR_INT, LaunchCellPayload::value,
            LaunchCellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LaunchCellPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof LaunchCellBlockEntity c)
                    || !c.getBlockPos().equals(p.pos()) || c.isRemoved() || !Access.mayUse(c, context.player())) return;
            c.act(context.player(), p.action(), p.value());
        });
    }
}
