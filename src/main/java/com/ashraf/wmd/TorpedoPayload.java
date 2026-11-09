package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TorpedoPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final Type<TorpedoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "torpedo"));

    public static final StreamCodec<FriendlyByteBuf, TorpedoPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TorpedoPayload::pos,
            ByteBufCodecs.VAR_INT, TorpedoPayload::action,
            ByteBufCodecs.VAR_INT, TorpedoPayload::value,
            TorpedoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TorpedoPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof TorpedoLauncherBlockEntity t)
                    || !t.getBlockPos().equals(p.pos()) || t.isRemoved() || !Access.mayUse(t, context.player())) return;
            switch (p.action()) {
                case TorpedoLauncherBlockEntity.A_FIRE -> {
                    int r = t.fire(context.player());
                    if (r == TorpedoLauncherBlockEntity.S_BLOCKED) {
                        net.minecraft.core.BlockPos f = t.muzzleFront();
                        context.player().displayClientMessage(Component.translatable("gui.wmd.torp.blocked_by", t.getLevel().getBlockState(f).getBlock().getName(), f.getX(), f.getY(), f.getZ()), true);
                    } else if (r != TorpedoLauncherBlockEntity.S_FIRED) context.player().displayClientMessage(Component.translatable("gui.wmd.torp.st." + r), true);
                }
                case TorpedoLauncherBlockEntity.A_DEPTH -> t.setDepth(p.value());
                case TorpedoLauncherBlockEntity.A_TRAIN -> { if (t instanceof TorpedoLauncherDeckBlockEntity d) d.setTrain(p.value()); }
                case TorpedoLauncherBlockEntity.A_FREQ -> t.setFrequency(p.value());
                case TorpedoLauncherBlockEntity.A_GYRO -> t.setGyro(p.value());
                default -> {}
            }
        });
    }
}
