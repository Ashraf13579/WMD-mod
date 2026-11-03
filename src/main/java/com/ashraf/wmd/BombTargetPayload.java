package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BombTargetPayload(BlockPos pos, boolean set, int x, int z) implements CustomPacketPayload {

    public static final Type<BombTargetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomb_target"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, BombTargetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BombTargetPayload::pos,
            ByteBufCodecs.BOOL, BombTargetPayload::set,
            ByteBufCodecs.VAR_INT, BombTargetPayload::x,
            ByteBufCodecs.VAR_INT, BombTargetPayload::z,
            BombTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BombTargetPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof BombRackBlockEntity be) || !be.getBlockPos().equals(p.pos())) return;
            if (!Access.mayUse(be, player)) { Access.deny(be, player); return; }
            be.setTarget(p.set(), p.x(), p.z());
        });
    }
}
