package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class ReconData extends SavedData {

    private static final String NAME = "wmd_recon";

    public static final short NO_H = Short.MIN_VALUE;

    public record Snapshot(int x0, int z0, int step, int n, byte[] col, long timeMs, int[] icons, int loadedPct, short[] hts, Map<Long, String> ships) {

        public Snapshot(int x0, int z0, int step, int n, byte[] col, long timeMs, int[] icons, int loadedPct, short[] hts) {
            this(x0, z0, step, n, col, timeMs, icons, loadedPct, hts, Map.of());
        }

        public Snapshot {
            ships = ships == null ? Map.of() : Map.copyOf(ships);
        }

        public boolean sameArea(int ox0, int oz0, int ostep, int on) { return x0 == ox0 && z0 == oz0 && step == ostep && n == on && col.length == on * on; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x0", x0); t.putInt("z0", z0); t.putInt("step", step); t.putInt("n", n);
            t.putByteArray("col", col); t.putLong("time", timeMs); t.putIntArray("icons", icons); t.putInt("loaded", loadedPct);
            if (hts != null && hts.length == n * n) {
                byte[] b = new byte[hts.length * 2];
                for (int i = 0; i < hts.length; i++) { b[2 * i] = (byte) (hts[i] >> 8); b[2 * i + 1] = (byte) hts[i]; }
                t.putByteArray("hts", b);
            }
            if (!ships.isEmpty()) {
                ListTag sl = new ListTag();
                for (Map.Entry<Long, String> e : ships.entrySet()) {
                    CompoundTag c = new CompoundTag();
                    c.putLong("k", e.getKey());
                    c.putString("l", e.getValue());
                    sl.add(c);
                }
                t.put("ships", sl);
            }
            return t;
        }

        static Snapshot load(CompoundTag t) {
            int n = t.getInt("n");
            byte[] col = t.getByteArray("col");
            if (n <= 0 || col.length != n * n) return null;
            int[] ic = t.getIntArray("icons");
            if (ic.length % 3 != 0) ic = new int[0];
            short[] hts = null;
            byte[] b = t.getByteArray("hts");
            if (b.length == 2 * n * n) {
                hts = new short[n * n];
                for (int i = 0; i < hts.length; i++) hts[i] = (short) (((b[2 * i] & 0xFF) << 8) | (b[2 * i + 1] & 0xFF));
            }
            Map<Long, String> ships = new HashMap<>();
            for (Tag x : t.getList("ships", Tag.TAG_COMPOUND)) if (x instanceof CompoundTag c && ships.size() < Recon.MAX_ICONS) ships.put(c.getLong("k"), c.getString("l"));
            return new Snapshot(t.getInt("x0"), t.getInt("z0"), Math.max(1, t.getInt("step")), n, col, t.getLong("time"), ic, t.getInt("loaded"), hts, ships);
        }
    }

    public static final class Entry {
        public boolean hasTarget;
        public int tx, tz;

        public long lastPassEnd;
        long lastShotMs;
        public Snapshot snap;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static ReconData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ReconData::new, ReconData::load, null), NAME);
    }

    public Entry entry(UUID owner) { return owner == null ? null : entries.get(owner); }

    public Entry entryOrNew(UUID owner) { return entries.computeIfAbsent(owner, k -> new Entry()); }

    public void setTarget(UUID owner, int x, int z) {
        Entry e = entryOrNew(owner);
        e.hasTarget = true;
        e.tx = x;
        e.tz = z;
        e.lastShotMs = 0L;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", me.getKey());
            t.putBoolean("hasTarget", e.hasTarget);
            t.putInt("tx", e.tx); t.putInt("tz", e.tz);
            t.putLong("lastPass", e.lastPassEnd);
            if (e.snap != null) t.put("snap", e.snap.save());
            l.add(t);
        }
        tag.put("entries", l);
        return tag;
    }

    public static ReconData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReconData d = new ReconData();
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("owner")) continue;
            Entry e = new Entry();
            e.hasTarget = t.getBoolean("hasTarget");
            e.tx = t.getInt("tx"); e.tz = t.getInt("tz");
            e.lastPassEnd = t.getLong("lastPass");
            if (t.contains("snap", Tag.TAG_COMPOUND)) e.snap = Snapshot.load(t.getCompound("snap"));
            d.entries.put(t.getUUID("owner"), e);
        }
        return d;
    }
}
