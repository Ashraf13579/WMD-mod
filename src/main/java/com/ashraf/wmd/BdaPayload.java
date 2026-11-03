package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BdaPayload(boolean has, int key, int x, int z, String warhead, long impactMs, long afterMs, boolean done, int cx0, int cz0, int step,
                         byte[] before, byte[] after, int[] beforeIcons, int[] lost, int standing, int pending) implements CustomPacketPayload {

    private static final int MAX_ICONS = 96;

    public static final BdaPayload NONE = new BdaPayload(false, 0, 0, 0, "", 0L, 0L, false, 0, 0, 1, new byte[0], new byte[0], new int[0], new int[0], 0, 0);

    public static BdaPayload of(CcBda.Report r, int pending, int key) {
        return new BdaPayload(true, key, r.x(), r.z(), r.warhead(), r.impactMs(), r.afterMs(), r.done(), r.cx0(), r.cz0(), r.step(), r.before(),
                r.done() ? r.after() : new byte[0], r.beforeIcons(), r.lost(), r.afterIcons().length / 3, pending);
    }

    public static final Type<BdaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_bda"));

    private static void ints(FriendlyByteBuf buf, int[] a) {
        int n = Math.min(MAX_ICONS * 3, a.length - a.length % 3);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeInt(a[i]);
    }

    private static int[] ints(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ICONS * 3 || n % 3 != 0) throw new IllegalArgumentException("bda: bad icon count " + n);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = buf.readInt();
        return a;
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n != 0 && n != CcBda.CROP * CcBda.CROP) throw new IllegalArgumentException("bda: bad picture size " + n);
        byte[] b = new byte[n];
        buf.readBytes(b);
        return b;
    }

    public static final StreamCodec<FriendlyByteBuf, BdaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.has()); buf.writeVarInt(p.key()); buf.writeInt(p.x()); buf.writeInt(p.z()); buf.writeUtf(p.warhead(), 32);
                buf.writeLong(p.impactMs()); buf.writeLong(p.afterMs()); buf.writeBoolean(p.done()); buf.writeInt(p.cx0()); buf.writeInt(p.cz0()); buf.writeVarInt(p.step());
                buf.writeVarInt(p.before().length); buf.writeBytes(p.before());
                buf.writeVarInt(p.after().length); buf.writeBytes(p.after());
                ints(buf, p.beforeIcons()); ints(buf, p.lost());
                buf.writeVarInt(p.standing()); buf.writeVarInt(p.pending());
            },
            buf -> {
                boolean has = buf.readBoolean();
                int key = buf.readVarInt(), x = buf.readInt(), z = buf.readInt();
                String w = buf.readUtf(32);
                long ims = buf.readLong(), ams = buf.readLong();
                boolean done = buf.readBoolean();
                int cx0 = buf.readInt(), cz0 = buf.readInt(), step = Math.max(1, buf.readVarInt());
                byte[] before = bytes(buf), after = bytes(buf);
                int[] bi = ints(buf), lost = ints(buf);
                int standing = buf.readVarInt(), pending = buf.readVarInt();
                return new BdaPayload(has, key, x, z, w, ims, ams, done, cx0, cz0, step, before, after, bi, lost, standing, pending);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
