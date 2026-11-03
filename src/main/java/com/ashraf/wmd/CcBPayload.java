package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CcBPayload(BlockPos pos, int action, long arg, int x, int z, String name, List<Long> pads) implements CustomPacketPayload {

    public static final int AIM = 0, LAUNCH = 1, RETARGET = 2, STRIKE = 3, CANCEL = 4, CAM = 5, ENGAGE = 6, ASAT_PICK = 7, COUNTER = 8, PACKAGE = 9;
    public static final int ART_FIRE = 10, ART_CEASE = 11, DOME_SET = 12, DOME_CLEAR = 13;

    public static final int PLAN_CYCLE = 14;

    public static final int PLAN_ADD = 15, PLAN_SET = 16, PLAN_DEL = 17;

    public static final int PLAN_RT = 18;

    public static long packXZ(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
    public static int unpackX(long v) { return (int) (v >> 32); }
    public static int unpackZ(long v) { return (int) v; }
    public static final int MAX_PADS = 8;

    public static final Type<CcBPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_b"));

    public static final StreamCodec<FriendlyByteBuf, CcBPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeLong(p.arg());
                buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeUtf(p.name(), 16);
                int n = Math.min(MAX_PADS, p.pads().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeLong(p.pads().get(i));
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int action = buf.readVarInt();
                long arg = buf.readLong();
                int x = buf.readInt(), z = buf.readInt();
                String name = buf.readUtf(16);
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_PADS) throw new io.netty.handler.codec.DecoderException("cc_b: bad pad count " + n);
                List<Long> pads = new ArrayList<>(n);
                for (int i = 0; i < n; i++) pads.add(buf.readLong());
                return new CcBPayload(pos, action, arg, x, z, name, List.copyOf(pads));
            });

    public static CcBPayload of(BlockPos pos, int action, long arg, int x, int z) { return new CcBPayload(pos, action, arg, x, z, "", List.of()); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CcBPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof CcConsoleBlockEntity be) || !be.getBlockPos().equals(p.pos())
                    || be.isRemoved() || be.getLevel() != sp.level()) return;
            if (!sp.getUUID().equals(be.getOwner())) { sp.displayClientMessage(Component.translatable("message.wmd.cc.owner_only"), true); return; }
            if (!be.isOnline()) { sp.displayClientMessage(Component.translatable("message.wmd.cc.offline"), true); return; }
            be.order(sp, p);
        });
    }
}
