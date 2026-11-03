package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CruisePayload(BlockPos pos, int action, int[] data) implements CustomPacketPayload {

    public static final Type<CruisePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cruise"));
    private static final int MAX = 3 + 2 * Balance.CRUISE_WAYPOINTS;

    public static final StreamCodec<FriendlyByteBuf, CruisePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.action());
                int n = Math.min(MAX, p.data() == null ? 0 : p.data().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeInt(p.data()[i]);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int action = buf.readVarInt(), n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("cruise payload: too many numbers " + n);
                int[] d = new int[n];
                for (int i = 0; i < n; i++) d[i] = buf.readInt();
                return new CruisePayload(pos, action, d);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CruisePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MachineMenu menu && menu.machine() instanceof CruiseLauncherBlockEntity l
                    && l.getBlockPos().equals(p.pos()) && !l.isRemoved() && Access.mayUse(l, context.player())
                    && p.action() >= CruiseLauncherBlockEntity.A_ROUTE && p.action() <= CruiseLauncherBlockEntity.A_CLEAR_WP) {
                l.act(p.action(), p.data(), context.player());
            }
        });
    }
}
