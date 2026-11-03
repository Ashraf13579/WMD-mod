package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TabletFirePayload(boolean mainHand, boolean fire) implements CustomPacketPayload {

    public static final Type<TabletFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tablet_fire"));

    public static final StreamCodec<FriendlyByteBuf, TabletFirePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.mainHand()); buf.writeBoolean(p.fire()); }, buf -> new TabletFirePayload(buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TabletFirePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!(sp.getItemInHand(hand).getItem() instanceof LaserTabletItem)) return;
            if (p.fire()) LaserTabletItem.fire(sp, hand);
            PacketDistributor.sendToPlayer(sp, LaserTabletItem.status(sp, sp.getItemInHand(hand), hand));
        });
    }
}
