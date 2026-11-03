package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DroneHiveTabletPayload(boolean mainHand) implements CustomPacketPayload {

    public static final Type<DroneHiveTabletPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_hive_tablet"));

    public static final StreamCodec<FriendlyByteBuf, DroneHiveTabletPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.mainHand()), buf -> new DroneHiveTabletPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DroneHiveTabletPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!(sp.getItemInHand(hand).getItem() instanceof LaserTabletItem)) return;
            BlockPos at = LaserTabletItem.bound(sp.getItemInHand(hand), "hive", sp.level());
            if (at == null) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.no_hive"), true); return; }

            if (sp.level().isLoaded(at) && sp.level().getBlockEntity(at) instanceof BigDroneSite s && (s instanceof DroneCatapultBlockEntity || s.isHangarModule())) {
                if (s.getOwner() != null && !Access.trusts(s, sp.getUUID())) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return; }
                if (SableBridge.worldPos(sp.level(), at).distanceToSqr(sp.position()) > (double) s.siteRange() * s.siteRange()) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.hive_far"), true); return; }
                BigDroneMenu.open(sp, s, true);
                return;
            }
            if (SableBridge.worldPos(sp.level(), at).distanceToSqr(sp.position()) > (double) Balance.DRONE_T2_RANGE * Balance.DRONE_T2_RANGE) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.hive_far"), true); return; }
            if (!sp.level().isLoaded(at) || !(sp.level().getBlockEntity(at) instanceof DroneHiveBlockEntity h)) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.hive_gone"), true); return; }
            if (h.getOwner() != null && !Access.trusts(h, sp.getUUID())) { sp.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return; }
            DroneHiveMenu.open(sp, h, true);
        });
    }
}
