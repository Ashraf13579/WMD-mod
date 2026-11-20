package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TabletFirePayload(boolean mainHand, int action, int index, long flight, int x, int z, int arg) implements CustomPacketPayload {

    public TabletFirePayload(boolean mainHand, int action, int index, long flight) { this(mainHand, action, index, flight, 0, 0, 0); }

    public static final Type<TabletFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tablet_fire"));

    public static final StreamCodec<FriendlyByteBuf, TabletFirePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBoolean(p.mainHand()); buf.writeByte(p.action()); buf.writeByte(p.index()); buf.writeLong(p.flight()); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeVarInt(p.arg()); },
            buf -> new TabletFirePayload(buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readLong(), buf.readInt(), buf.readInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TabletFirePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!(sp.getItemInHand(hand).getItem() instanceof LaserTabletItem)) return;
            String ans = LaserTabletItem.act(sp, hand, p.action(), p.index(), p.flight(), p.x(), p.z(), p.arg());
            if (p.action() == LaserTabletItem.A_OPEN && ans.isEmpty()) return;
            PacketDistributor.sendToPlayer(sp, LaserTabletItem.status(sp, sp.getItemInHand(hand), hand, ans));
        });
    }
}
