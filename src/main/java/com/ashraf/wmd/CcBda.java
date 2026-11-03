package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CcBda extends SavedData {

    private static final String NAME = "wmd_cc_bda";
    public static final int CROP = 32, MAX = 8, MATCH = 8;
    public static final long DELAY_MS = 30_000L, STALE_MS = 2L * 3_600_000L;

    public static final class Report {
        long id, impactMs, afterMs;
        int x, z;
        String warhead = "";

        int sx0, sz0, step, n, cx0, cz0;
        byte[] before, after;
        int[] beforeIcons = new int[0], afterIcons = new int[0], lost = new int[0];

        public long id() { return id; }
        public boolean done() { return after != null; }
        public int x() { return x; }
        public int z() { return z; }
        public String warhead() { return warhead; }
        public long impactMs() { return impactMs; }
        public long afterMs() { return afterMs; }
        public int cx0() { return cx0; }
        public int cz0() { return cz0; }
        public int step() { return step; }
        public byte[] before() { return before; }
        public byte[] after() { return after; }
        public int[] beforeIcons() { return beforeIcons; }
        public int[] afterIcons() { return afterIcons; }

        public int[] lost() { return lost; }
        public int destroyed() { return lost.length / 3; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id); t.putLong("ms", impactMs); t.putLong("after", afterMs); t.putInt("x", x); t.putInt("z", z); t.putString("w", warhead);
            t.putInt("sx0", sx0); t.putInt("sz0", sz0); t.putInt("step", step); t.putInt("n", n); t.putInt("cx0", cx0); t.putInt("cz0", cz0);
            t.putByteArray("b", before);
            if (after != null) t.putByteArray("a", after);
            t.putIntArray("bi", beforeIcons); t.putIntArray("ai", afterIcons); t.putIntArray("lost", lost);
            return t;
        }

        static Report load(CompoundTag t) {
            Report r = new Report();
            r.id = t.getLong("id"); r.impactMs = t.getLong("ms"); r.afterMs = t.getLong("after"); r.x = t.getInt("x"); r.z = t.getInt("z"); r.warhead = t.getString("w");
            r.sx0 = t.getInt("sx0"); r.sz0 = t.getInt("sz0"); r.step = Math.max(1, t.getInt("step")); r.n = t.getInt("n"); r.cx0 = t.getInt("cx0"); r.cz0 = t.getInt("cz0");
            r.before = t.getByteArray("b");
            if (r.before.length != CROP * CROP) return null;
            r.after = t.contains("a") ? t.getByteArray("a") : null;
            if (r.after != null && r.after.length != CROP * CROP) r.after = null;
            r.beforeIcons = triples(t.getIntArray("bi")); r.afterIcons = triples(t.getIntArray("ai")); r.lost = triples(t.getIntArray("lost"));
            return r;
        }

        private static int[] triples(int[] a) { return a.length % 3 == 0 ? a : new int[0]; }
    }

    private final Map<UUID, List<Report>> reports = new HashMap<>();
    private long nextId = 1;

    public static CcBda get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CcBda::new, CcBda::load, null), NAME);
    }

    public static Report latest(MinecraftServer server, UUID owner) {
        List<Report> l = owner == null ? null : get(server).reports.get(owner);
        if (l == null || l.isEmpty()) return null;
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).done()) return l.get(i);
        return l.get(l.size() - 1);
    }

    public static int pending(MinecraftServer server, UUID owner) {
        List<Report> l = owner == null ? null : get(server).reports.get(owner);
        int c = 0;
        if (l != null) for (Report r : l) if (!r.done()) c++;
        return c;
    }

    public static int openedSince(MinecraftServer server, UUID owner, long sinceMs) {
        List<Report> l = owner == null ? null : get(server).reports.get(owner);
        int c = 0;
        if (l != null) for (Report r : l) if (r.impactMs >= sinceMs) c++;
        return c;
    }

    public static void onImpact(ServerLevel level, Flight f, int gy) {
        if (level.dimension() != Level.OVERWORLD || f == null || f.build() == null || f.build().isInterceptor() || Satellites.toOrbit(f.build()) || Asat.isAsat(f.build())) return;
        MinecraftServer server = level.getServer();
        UUID owner = CcBattleLog.ownerOf(server, f);
        if (owner == null) return;
        ReconData.Entry e = ReconData.get(server).entry(owner);
        ReconData.Snapshot s = e != null && e.hasTarget ? e.snap : null;
        if (s == null || s.n() < CROP) return;
        int ix = Math.floorDiv(f.tx() - s.x0(), s.step()), iz = Math.floorDiv(f.tz() - s.z0(), s.step());
        if (ix < 0 || iz < 0 || ix >= s.n() || iz >= s.n()) return;
        Report r = new Report();
        CcBda d = get(server);
        r.id = d.nextId++;
        r.impactMs = System.currentTimeMillis();
        r.x = f.tx();
        r.z = f.tz();
        r.warhead = f.build().warheadType();
        r.sx0 = s.x0(); r.sz0 = s.z0(); r.step = s.step(); r.n = s.n();
        int c0x = Math.max(0, Math.min(s.n() - CROP, ix - CROP / 2)), c0z = Math.max(0, Math.min(s.n() - CROP, iz - CROP / 2));
        r.cx0 = s.x0() + c0x * s.step();
        r.cz0 = s.z0() + c0z * s.step();
        r.before = crop(s, c0x, c0z);
        r.beforeIcons = iconsIn(s.icons(), r);
        List<Report> l = d.reports.computeIfAbsent(owner, k -> new ArrayList<>());
        l.add(r);
        while (l.size() > MAX) l.remove(0);
        d.setDirty();
        Wmd.LOGGER.info("Damage report {}: impact of {} at {} {} inside the recon square, {} enemy machine(s) before", r.id, f.launcher(), r.x, r.z, r.beforeIcons.length / 3);
    }

    private static byte[] crop(ReconData.Snapshot s, int c0x, int c0z) {
        byte[] out = new byte[CROP * CROP];
        for (int z = 0; z < CROP; z++) System.arraycopy(s.col(), (c0z + z) * s.n() + c0x, out, z * CROP, CROP);
        return out;
    }

    private static boolean inCrop(Report r, int x, int z) {
        return x >= r.cx0 && x < r.cx0 + CROP * r.step && z >= r.cz0 && z < r.cz0 + CROP * r.step;
    }

    private static int[] iconsIn(int[] ic, Report r) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i + 2 < ic.length; i += 3)
            if (ic[i + 2] != Recon.ICON_AIRSHIP && inCrop(r, ic[i], ic[i + 1])) { l.add(ic[i]); l.add(ic[i + 1]); l.add(ic[i + 2]); }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    static void onMerge(MinecraftServer server, UUID owner, ReconData.Snapshot s, int[] idx, int cnt) {
        if (server == null || owner == null || s == null) return;
        CcBda d = get(server);
        List<Report> l = d.reports.get(owner);
        if (l == null || l.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<Report> it = l.iterator(); it.hasNext(); ) {
            Report r = it.next();
            if (r.done()) continue;
            boolean same = r.sx0 == s.x0() && r.sz0 == s.z0() && r.step == s.step() && r.n == s.n();
            if (!same || now - r.impactMs > STALE_MS) { it.remove(); changed = true; continue; }
            if (now - r.impactMs < DELAY_MS) continue;
            int c0x = (r.cx0 - s.x0()) / s.step(), c0z = (r.cz0 - s.z0()) / s.step(), hits = 0;
            for (int k = 0; k < cnt && k < idx.length; k++) {
                int col = idx[k] % s.n(), row = idx[k] / s.n();
                if (col >= c0x && col < c0x + CROP && row >= c0z && row < c0z + CROP) hits++;
            }
            if (hits < CROP * CROP / 4) continue;
            r.after = crop(s, c0x, c0z);
            r.afterMs = now;
            r.afterIcons = iconsIn(s.icons(), r);
            List<Integer> lost = new ArrayList<>();
            for (int i = 0; i + 2 < r.beforeIcons.length; i += 3) {
                boolean found = false;
                for (int j = 0; j + 2 < r.afterIcons.length && !found; j += 3)
                    found = r.afterIcons[j + 2] == r.beforeIcons[i + 2] && Math.abs(r.afterIcons[j] - r.beforeIcons[i]) <= MATCH
                            && Math.abs(r.afterIcons[j + 1] - r.beforeIcons[i + 1]) <= MATCH;
                if (!found) { lost.add(r.beforeIcons[i]); lost.add(r.beforeIcons[i + 1]); lost.add(r.beforeIcons[i + 2]); }
            }
            r.lost = new int[lost.size()];
            for (int i = 0; i < r.lost.length; i++) r.lost[i] = lost.get(i);
            changed = true;
            Wmd.LOGGER.info("Damage report {} done: {} of {} enemy machine(s) destroyed at {} {}", r.id, r.destroyed(), r.beforeIcons.length / 3, r.x, r.z);
            CcBattleLog.add(server, owner, CcBattleLog.BDA, "log.wmd.bda", Integer.toString(r.x), Integer.toString(r.z), Integer.toString(r.destroyed()),
                    Integer.toString(r.beforeIcons.length / 3));
            CcDefcon.banner(server, owner, CcDefcon.B_BDA, true, "hud.wmd.cc.alert.bda", Integer.toString(r.x), Integer.toString(r.z), Integer.toString(r.destroyed()));
        }
        if (changed) d.setDirty();
    }

    static void answer(ServerPlayer sp, UUID owner, int knownKey) {
        Report r = latest(sp.server, owner);
        int key = r == null ? 0 : (int) (r.id * 2 + (r.done() ? 1 : 0));
        if (key == knownKey) return;
        PacketDistributor.sendToPlayer(sp, r == null ? BdaPayload.NONE : BdaPayload.of(r, pending(sp.server, owner), key));
    }

    static Report testReport(MinecraftServer server, UUID owner, int x, int z, int lostN) {
        ReconData.Entry e = ReconData.get(server).entry(owner);
        ReconData.Snapshot s = e != null && e.hasTarget ? e.snap : null;
        if (s == null || s.n() < CROP) return null;
        int ix = Math.floorDiv(x - s.x0(), s.step()), iz = Math.floorDiv(z - s.z0(), s.step());
        if (ix < 0 || iz < 0 || ix >= s.n() || iz >= s.n()) return null;
        CcBda d = get(server);
        Report r = new Report();
        r.id = d.nextId++;
        r.impactMs = r.afterMs = System.currentTimeMillis();
        r.x = x; r.z = z; r.warhead = "he";
        r.sx0 = s.x0(); r.sz0 = s.z0(); r.step = s.step(); r.n = s.n();
        int c0x = Math.max(0, Math.min(s.n() - CROP, ix - CROP / 2)), c0z = Math.max(0, Math.min(s.n() - CROP, iz - CROP / 2));
        r.cx0 = s.x0() + c0x * s.step(); r.cz0 = s.z0() + c0z * s.step();
        r.before = crop(s, c0x, c0z);
        r.after = r.before.clone();
        for (int k = 0; k < r.after.length; k++) {
            int cx = k % CROP - CROP / 2, cz = k / CROP - CROP / 2;
            if (cx * cx + cz * cz <= 16) r.after[k] = (byte) net.minecraft.world.level.material.MapColor.STONE.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOW);
        }
        r.beforeIcons = iconsIn(s.icons(), r);
        int n = Math.min(lostN, r.beforeIcons.length / 3);
        r.lost = java.util.Arrays.copyOf(r.beforeIcons, n * 3);
        r.afterIcons = java.util.Arrays.copyOfRange(r.beforeIcons, n * 3, r.beforeIcons.length);
        List<Report> l = d.reports.computeIfAbsent(owner, k -> new ArrayList<>());
        l.add(r);
        while (l.size() > MAX) l.remove(0);
        d.setDirty();
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag all = new ListTag();
        for (Map.Entry<UUID, List<Report>> me : reports.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", me.getKey());
            ListTag l = new ListTag();
            for (Report r : me.getValue()) l.add(r.save());
            t.put("reports", l);
            all.add(t);
        }
        tag.put("owners", all);
        tag.putLong("nextId", nextId);
        return tag;
    }

    public static CcBda load(CompoundTag tag, HolderLookup.Provider registries) {
        CcBda d = new CcBda();
        for (Tag raw : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("owner")) continue;
            List<Report> l = new ArrayList<>();
            for (Tag x : t.getList("reports", Tag.TAG_COMPOUND)) { Report r = Report.load((CompoundTag) x); if (r != null) l.add(r); }
            while (l.size() > MAX) l.remove(0);
            d.reports.put(t.getUUID("owner"), l);
        }
        d.nextId = Math.max(1, tag.getLong("nextId"));
        return d;
    }
}
