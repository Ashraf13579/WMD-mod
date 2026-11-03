package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BigDronePayload(BlockPos pos, int[] data) implements CustomPacketPayload {

    public static final Type<BigDronePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "big_drone"));
    private static final int MAX = 3 + 2 * DroneMission.WAYPOINTS;

    public static final StreamCodec<FriendlyByteBuf, BigDronePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                int n = Math.min(MAX, p.data() == null ? 0 : p.data().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeInt(p.data()[i]);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("big drone payload: too many numbers " + n);
                int[] d = new int[n];
                for (int i = 0; i < n; i++) d[i] = buf.readInt();
                return new BigDronePayload(pos, d);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BigDronePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof BigDroneMenu menu && menu.getPos().equals(p.pos())
                    && sp.level().getBlockEntity(p.pos()) instanceof BigDroneSite s && Access.mayUse(s, sp))
                s.act(BigDroneSite.A_ROUTE, p.data(), sp);
        });
    }
}
