package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BombSightPayload(BlockPos pos, boolean set, int x, int z) implements CustomPacketPayload {

    public static final Type<BombSightPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomb_sight"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, BombSightPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BombSightPayload::pos,
            ByteBufCodecs.BOOL, BombSightPayload::set,
            ByteBufCodecs.VAR_INT, BombSightPayload::x,
            ByteBufCodecs.VAR_INT, BombSightPayload::z,
            BombSightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BombSightPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof BombSightBlockEntity be) || !be.getBlockPos().equals(p.pos())) return;
            if (!Access.mayUse(be, player)) { Access.deny(be, player); return; }
            be.setTarget(p.set(), p.x(), p.z());
        });
    }
}
