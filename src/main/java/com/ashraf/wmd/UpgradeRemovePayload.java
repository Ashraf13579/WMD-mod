package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpgradeRemovePayload(BlockPos pos, int slot) implements CustomPacketPayload {

    public static final Type<UpgradeRemovePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "upgrade_remove"));

    public static final StreamCodec<FriendlyByteBuf, UpgradeRemovePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpgradeRemovePayload::pos, ByteBufCodecs.VAR_INT, UpgradeRemovePayload::slot, UpgradeRemovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UpgradeRemovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof MachineMenu mm) || !mm.stillValid(sp) || !(mm.machine() instanceof ProcessUnitBlockEntity be)) return;
            if (be.isRemoved() || !be.getBlockPos().equals(payload.pos())) return;
            if (be instanceof Owned o && !Access.mayUse(o, sp)) { Access.deny(o, sp); return; }
            ItemStack s = be.removeUpgrade(payload.slot());
            if (!s.isEmpty() && !sp.getInventory().add(s)) sp.drop(s, false);
        });
    }
}
