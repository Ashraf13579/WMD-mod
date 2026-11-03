package com.ashraf.wmd;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ReconMapPayload(int status, int satId, long passMs, boolean hasTarget, int tx, int tz, long snapTime, long snapAgeMs,
                              int x0, int z0, int step, int n, int loadedPct, byte[] col, int[] icons, int[] dots, int[] flights, boolean hasMap,
                              int fill, short[] hts, java.util.Map<Long, String> ships) implements CustomPacketPayload {

    public ReconMapPayload(int status, int satId, long passMs, boolean hasTarget, int tx, int tz, long snapTime, long snapAgeMs,
                           int x0, int z0, int step, int n, int loadedPct, byte[] col, int[] icons, int[] dots, int[] flights, boolean hasMap,
                           int fill, short[] hts) {
        this(status, satId, passMs, hasTarget, tx, tz, snapTime, snapAgeMs, x0, z0, step, n, loadedPct, col, icons, dots, flights, hasMap, fill, hts, java.util.Map.of());
    }

    public ReconMapPayload {
        ships = ships == null ? java.util.Map.of() : ships;
    }

    static final int SHIP_LABEL_MAX = 48;

    public String[] shipLabel(int x, int z) {
        String l = ships.get(Recon.shipKey(x, z));
        if (l == null) return null;
        int i = l.lastIndexOf('|');
        return i < 0 ? new String[] { l, "?" } : new String[] { l.substring(0, i), l.substring(i + 1) };
    }

    public static final Type<ReconMapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "recon_map"));

    private static void ints(FriendlyByteBuf buf, int[] a) { buf.writeVarInt(a.length); for (int v : a) buf.writeInt(v); }

    private static int[] ints(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("recon map: too many entries " + n);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = buf.readInt();
        return a;
    }

    private static void writeHeights(FriendlyByteBuf buf, short[] h, int n) {
        if (h == null || n <= 0 || h.length != n * n) { buf.writeVarInt(0); return; }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (short v : h) if (v != ReconData.NO_H) { min = Math.min(min, v); max = Math.max(max, v); }
        if (min == Integer.MAX_VALUE) { buf.writeVarInt(0); return; }
        if (max - min < 255) {
            buf.writeVarInt(1);
            buf.writeInt(min);
            byte[] b = new byte[h.length];
            for (int i = 0; i < h.length; i++) b[i] = (byte) (h[i] == ReconData.NO_H ? 255 : h[i] - min);
            buf.writeByteArray(b);
        } else {
            buf.writeVarInt(2);
            byte[] b = new byte[h.length * 2];
            for (int i = 0; i < h.length; i++) { b[2 * i] = (byte) (h[i] >> 8); b[2 * i + 1] = (byte) h[i]; }
            buf.writeByteArray(b);
        }
    }

    private static short[] readHeights(FriendlyByteBuf buf, int n) {
        int mode = buf.readVarInt();
        if (mode == 1) {
            int min = buf.readInt();
            byte[] b = buf.readByteArray(256 * 256);
            if (b.length != n * n) return null;
            short[] h = new short[b.length];
            for (int i = 0; i < b.length; i++) h[i] = (b[i] & 0xFF) == 255 ? ReconData.NO_H : (short) (min + (b[i] & 0xFF));
            return h;
        }
        if (mode == 2) {
            byte[] b = buf.readByteArray(2 * 256 * 256);
            if (b.length != 2 * n * n) return null;
            short[] h = new short[n * n];
            for (int i = 0; i < h.length; i++) h[i] = (short) (((b[2 * i] & 0xFF) << 8) | (b[2 * i + 1] & 0xFF));
            return h;
        }
        return null;
    }

    public static final StreamCodec<FriendlyByteBuf, ReconMapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.status()); buf.writeVarInt(p.satId()); buf.writeLong(p.passMs());
                buf.writeBoolean(p.hasTarget()); buf.writeInt(p.tx()); buf.writeInt(p.tz());
                buf.writeLong(p.snapTime()); buf.writeLong(p.snapAgeMs());
                buf.writeBoolean(p.hasMap());
                if (p.hasMap()) {
                    buf.writeInt(p.x0()); buf.writeInt(p.z0()); buf.writeVarInt(p.step()); buf.writeVarInt(p.n()); buf.writeVarInt(p.loadedPct());
                    buf.writeByteArray(p.col());
                    ints(buf, p.icons());
                    writeHeights(buf, p.hts(), p.n());
                    int sn = Math.min(Recon.MAX_ICONS, p.ships().size());
                    buf.writeVarInt(sn);
                    int k = 0;
                    for (java.util.Map.Entry<Long, String> e : p.ships().entrySet()) {
                        if (k++ >= sn) break;
                        String l = e.getValue() == null ? "" : e.getValue();
                        buf.writeLong(e.getKey());
                        buf.writeUtf(l.length() > SHIP_LABEL_MAX ? l.substring(0, SHIP_LABEL_MAX) : l, SHIP_LABEL_MAX * 4);
                    }
                }
                ints(buf, p.dots());
                ints(buf, p.flights());
                buf.writeInt(p.fill());
            },
            buf -> {
                int status = buf.readVarInt(), sat = buf.readVarInt();
                long pass = buf.readLong();
                boolean ht = buf.readBoolean();
                int tx = buf.readInt(), tz = buf.readInt();
                long time = buf.readLong(), age = buf.readLong();
                boolean hm = buf.readBoolean();
                int x0 = 0, z0 = 0, step = 0, n = 0, pct = 0;
                byte[] col = new byte[0];
                int[] icons = new int[0];
                short[] hts = null;
                java.util.Map<Long, String> ships = new java.util.HashMap<>();
                if (hm) {
                    x0 = buf.readInt(); z0 = buf.readInt(); step = buf.readVarInt(); n = buf.readVarInt(); pct = buf.readVarInt();
                    col = buf.readByteArray(256 * 256);
                    icons = ints(buf, Recon.MAX_ICONS * 3);
                    hts = readHeights(buf, n);
                    int sn = buf.readVarInt();
                    if (sn < 0 || sn > Recon.MAX_ICONS) throw new IllegalArgumentException("recon map: too many airship labels " + sn);
                    for (int i = 0; i < sn; i++) { long key = buf.readLong(); ships.put(key, buf.readUtf(SHIP_LABEL_MAX * 4)); }
                }
                int[] dots = ints(buf, Recon.MAX_DOTS * 2);
                int[] flights = ints(buf, Recon.MAX_FLIGHTS * 3);
                int fill = buf.readInt();
                return new ReconMapPayload(status, sat, pass, ht, tx, tz, time, age, x0, z0, step, n, pct, col, icons, dots, flights, hm, fill, hts, ships);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
