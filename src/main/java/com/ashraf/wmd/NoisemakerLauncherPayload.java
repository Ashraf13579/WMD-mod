package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NoisemakerLauncherPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<NoisemakerLauncherPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "noisemaker_launcher"));

    public static final StreamCodec<FriendlyByteBuf, NoisemakerLauncherPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NoisemakerLauncherPayload::pos,
            ByteBufCodecs.VAR_INT, NoisemakerLauncherPayload::action,
            ByteBufCodecs.VAR_INT, NoisemakerLauncherPayload::value,
            NoisemakerLauncherPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(NoisemakerLauncherPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof NoisemakerLauncherBlockEntity d)
                    || !d.getBlockPos().equals(p.pos()) || d.isRemoved() || !Access.mayUse(d, context.player())) return;
            d.act(context.player(), p.action(), p.value());
        });
    }
}
