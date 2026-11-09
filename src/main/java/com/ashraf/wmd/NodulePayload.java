package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NodulePayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<NodulePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nodule_machine"));

    public static final StreamCodec<FriendlyByteBuf, NodulePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NodulePayload::pos,
            ByteBufCodecs.VAR_INT, NodulePayload::action,
            ByteBufCodecs.VAR_INT, NodulePayload::value,
            NodulePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(NodulePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || menu.machine() == null) return;
            if (menu.machine() instanceof DredgeWinchBlockEntity d && d.getBlockPos().equals(p.pos()) && !d.isRemoved() && Access.mayUse(d, context.player()))
                d.act(context.player(), p.action(), p.value());
            else if (menu.machine() instanceof CollectorRigBlockEntity r && r.getBlockPos().equals(p.pos()) && !r.isRemoved() && Access.mayUse(r, context.player()))
                r.act(context.player(), p.action(), p.value());
        });
    }
}
