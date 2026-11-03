package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CcRoutePayload(BlockPos table, BlockPos site, int[] data) implements CustomPacketPayload {

    public static final Type<CcRoutePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_route"));
    private static final int MAX = 3 + 2 * DroneMission.WAYPOINTS;

    public static final StreamCodec<FriendlyByteBuf, CcRoutePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.table()); buf.writeBlockPos(p.site());
                int n = Math.min(MAX, p.data() == null ? 0 : p.data().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeInt(p.data()[i]);
            },
            buf -> {
                BlockPos t = buf.readBlockPos(), s = buf.readBlockPos();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("cc route: too many numbers " + n);
                int[] d = new int[n];
                for (int i = 0; i < n; i++) d[i] = buf.readInt();
                return new CcRoutePayload(t, s, d);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CcRoutePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof CcMapTableBlockEntity t) || t.isRemoved() || !t.getBlockPos().equals(p.table())) return;
            if (t.getOwner() == null || !Access.mayUse(t, sp)) return;
            if (!sp.level().isLoaded(p.site()) || !(sp.level().getBlockEntity(p.site()) instanceof BigDroneSite s) || !t.getOwner().equals(s.getOwner())) return;
            s.act(BigDroneSite.A_ROUTE, p.data(), sp);
            PacketDistributor.sendToPlayer(sp, CcDronesPayload.of(sp.serverLevel(), t.getBlockPos(), t.getOwner()));
        });
    }
}
