package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DroneLasePayload(int id, int x, int y, int z) implements CustomPacketPayload {

    public static final Type<DroneLasePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_lase"));

    public static final StreamCodec<FriendlyByteBuf, DroneLasePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.id()); buf.writeInt(p.x()); buf.writeInt(p.y()); buf.writeInt(p.z()); },
            buf -> new DroneLasePayload(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DroneLasePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            int px = Math.max(-29_999_000, Math.min(29_999_000, p.x())), pz = Math.max(-29_999_000, Math.min(29_999_000, p.z()));
            int py = Math.max(sp.level().getMinBuildHeight(), Math.min(sp.level().getMaxBuildHeight(), p.y()));
            Drones.Drone d = Drones.get(sp.server).byId(p.id());
            if (d == null || d.pilot == null || !d.pilot.equals(sp.getUUID()) || d.kind != 4) return;
            if (d.phase != BigDrones.LOITER) { sp.displayClientMessage(Component.translatable("message.wmd.big_drone.lase_wait"), true); return; }
            if (Math.hypot(px + 0.5 - (d.tx + 0.5), pz + 0.5 - (d.tz + 0.5)) > Balance.T4_LASE_RANGE) {
                sp.displayClientMessage(Component.translatable("message.wmd.big_drone.lase_far", Balance.T4_LASE_RANGE).withStyle(ChatFormatting.RED), true);
                return;
            }
            d.lx = px + 0.5; d.ly = py + 1.0; d.lz = pz + 0.5; d.lased = true;
            sp.displayClientMessage(Component.translatable("message.wmd.big_drone.lased", px, py, pz).withStyle(ChatFormatting.GOLD), true);
        });
    }
}
