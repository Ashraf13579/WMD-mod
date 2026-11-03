package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReconPayload(BlockPos pos, int action, int x, int z) implements CustomPacketPayload {

    public static final int MAP = 0, DOTS = 1, TARGET = 2, BDA = 3;

    private static final int LIMIT = 29_000_000;

    public static final int WALL_VIEW_RANGE = 24;

    public static final Type<ReconPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "recon"));

    public static final StreamCodec<FriendlyByteBuf, ReconPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeInt(p.x()); buf.writeInt(p.z()); },
            buf -> new ReconPayload(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReconPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            CcModuleBlockEntity gs;
            if (sp.containerMenu instanceof MachineMenu menu && menu.machine() instanceof CcReconWallBlockEntity w && w.getBlockPos().equals(p.pos())) gs = w;
            else if (p.action() != TARGET && p.pos() != null && sp.level().isLoaded(p.pos())
                    && SableBridge.distSqWithShips(sp.level(), sp.blockPosition(), p.pos()) <= WALL_VIEW_RANGE * WALL_VIEW_RANGE
                    && sp.level().getBlockEntity(p.pos()) instanceof CcModuleBlockEntity w
                    && (w instanceof CcReconWallBlockEntity || w instanceof CcMapTableBlockEntity) && sp.getUUID().equals(w.getOwner())) gs = w;
            else return;
            if (gs.isRemoved() || gs.getOwner() == null || !gs.isOnline()) return;
            if (p.action() == TARGET) {
                int x = Math.max(-LIMIT, Math.min(LIMIT, p.x())), z = Math.max(-LIMIT, Math.min(LIMIT, p.z()));
                Recon.cancel(gs.getOwner());
                ReconData.get(sp.server).setTarget(gs.getOwner(), x, z);
                Recon.answer(sp, gs.getOwner(), true, true);
            } else if (p.action() == BDA) CcBda.answer(sp, gs.getOwner(), p.x());
            else Recon.answer(sp, gs.getOwner(), p.action() == MAP, false);
        });
    }
}
