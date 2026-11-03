package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;

final class ReconFill {

    private ReconFill() {}

    static final int TICKET_TIMEOUT_TICKS = 20 * 60;

    private static final int ALIVE_TICKS = 50;
    private static final int FLUSH_TICKS = 10;

    static final TicketType<ChunkPos> TICKET = TicketType.create("wmd_recon", Comparator.comparingLong(ChunkPos::toLong), TICKET_TIMEOUT_TICKS);

    private record Load(long chunk, long startTick) {}

    static final class Fill {
        final UUID owner;
        final int x0, z0, step, n;
        final boolean forced;

        final boolean drone;
        final long[] order;
        final List<Load> loads = new ArrayList<>();

        final int[] heights;
        int cursor, loaded, alreadyLoaded, skipped, lastDecile;
        long lastAlive, startMs;

        int cnt;
        int[] idx = new int[256], ys = new int[256];
        byte[] base = new byte[256];
        final Set<Long> scanned = new HashSet<>();
        final List<int[]> icons = new ArrayList<>();

        Fill(UUID owner, int x0, int z0, int step, int n, boolean forced, long tick) {
            this(owner, x0, z0, step, n, forced, tick, false, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
        }

        Fill(UUID owner, int x0, int z0, int step, int n, boolean forced, long tick, boolean drone, int bx0, int bz0, int bx1, int bz1) {
            this.owner = owner; this.x0 = x0; this.z0 = z0; this.step = step; this.n = n; this.forced = forced; this.lastAlive = tick; this.drone = drone;
            this.startMs = System.currentTimeMillis();
            heights = new int[n * n];
            Arrays.fill(heights, Integer.MIN_VALUE);
            int minCx = Math.max(x0, bx0) >> 4, maxCx = Math.min(x0 + n * step - 1, bx1) >> 4, minCz = Math.max(z0, bz0) >> 4, maxCz = Math.min(z0 + n * step - 1, bz1) >> 4;
            int ccx = (x0 + n * step / 2) >> 4, ccz = (z0 + n * step / 2) >> 4;
            List<long[]> l = new ArrayList<>();
            for (int cz = minCz; cz <= maxCz; cz++) for (int cx = minCx; cx <= maxCx; cx++) {
                long dx = cx - ccx, dz = cz - ccz;
                l.add(new long[] { ChunkPos.asLong(cx, cz), dx * dx + dz * dz });
            }
            l.sort(Comparator.comparingLong(a -> a[1]));
            order = new long[l.size()];
            for (int i = 0; i < order.length; i++) order[i] = l.get(i)[0];
        }

        boolean sameArea(int ox0, int oz0, int ostep, int on) { return x0 == ox0 && z0 == oz0 && step == ostep && n == on; }

        boolean complete() { return cursor >= order.length && loads.isEmpty(); }

        int progress() { return order.length == 0 ? 100 : (int) (100L * Math.max(0, cursor - loads.size()) / order.length); }

        void add(int i, MapColor mc, int y) {
            if (cnt == idx.length) { idx = Arrays.copyOf(idx, cnt * 2); ys = Arrays.copyOf(ys, cnt * 2); base = Arrays.copyOf(base, cnt * 2); }
            idx[cnt] = i; ys[cnt] = y; base[cnt] = (byte) mc.id; cnt++;
        }
    }

    private static final LinkedHashMap<UUID, Fill> FILLS = new LinkedHashMap<>();

    private static final Map<UUID, long[]> FINISHED = new HashMap<>();
    private static final long RETRY_MS = 10L * 60_000L;

    static boolean active() { return !FILLS.isEmpty(); }

    private static long areaKey(int x0, int z0) { return ChunkPos.asLong(x0, z0); }

    static int[] heights(UUID owner, int x0, int z0, int step, int n) {
        Fill f = FILLS.get(owner);
        return f != null && f.sameArea(x0, z0, step, n) ? f.heights : null;
    }

    static int state(UUID owner) {
        Fill f = FILLS.get(owner);
        if (f == null) return -1;
        return FILLS.keySet().iterator().next().equals(owner) ? Math.min(99, f.progress()) : -2;
    }

    static void want(MinecraftServer server, UUID owner, ReconData.Entry e) {
        if (e == null || !e.hasTarget || !server.isRunning()) return;
        int step = Math.max(1, Balance.RECON_SNAPSHOT_STEP), n = Recon.samples(), r = n * step / 2;
        int x0 = e.tx - r, z0 = e.tz - r;
        Fill f = FILLS.get(owner);
        if (f != null && f.sameArea(x0, z0, step, n)) { f.lastAlive = server.getTickCount(); return; }
        if (f != null) stop(server, owner, "target moved", false);
        long[] done = FINISHED.get(owner);
        if (done != null && done[0] == areaKey(x0, z0) && System.currentTimeMillis() < done[1]) return;
        FINISHED.remove(owner);
        if (missing(e, x0, z0, step, n) == 0) { FINISHED.put(owner, new long[] { areaKey(x0, z0), System.currentTimeMillis() + RETRY_MS }); return; }
        FILLS.put(owner, new Fill(owner, x0, z0, step, n, false, server.getTickCount()));
    }

    static boolean force(MinecraftServer server, UUID owner, ReconData.Entry e) {
        if (e == null || !e.hasTarget) return false;
        if (FILLS.containsKey(owner)) stop(server, owner, "restarted", false);
        FINISHED.remove(owner);
        int step = Math.max(1, Balance.RECON_SNAPSHOT_STEP), n = Recon.samples(), r = n * step / 2;
        FILLS.put(owner, new Fill(owner, e.tx - r, e.tz - r, step, n, true, server.getTickCount()));
        return true;
    }

    static boolean area(MinecraftServer server, UUID owner, ReconData.Entry e, int cx, int cz, int r) {
        if (e == null || !e.hasTarget || !server.isRunning()) return false;
        Fill old = FILLS.get(owner);
        if (old != null && old.drone) return false;
        if (old != null) stop(server, owner, "recon drone over the area", false);
        int step = Math.max(1, Balance.RECON_SNAPSHOT_STEP), n = Recon.samples(), half = n * step / 2;
        FILLS.put(owner, new Fill(owner, e.tx - half, e.tz - half, step, n, true, server.getTickCount(), true, cx - r, cz - r, cx + r, cz + r));
        return true;
    }

    static boolean stop(MinecraftServer server, UUID owner, String why, boolean remember) {
        Fill f = FILLS.remove(owner);
        if (f == null) return false;
        if (remember) FINISHED.put(owner, new long[] { areaKey(f.x0, f.z0), why.equals("stopped by command") ? Long.MAX_VALUE : System.currentTimeMillis() + RETRY_MS });
        end(server, f, why, true);
        return true;
    }

    static void forget(MinecraftServer server, UUID owner) {
        stop(server, owner, "target changed", false);
        FINISHED.remove(owner);
    }

    static void clear(MinecraftServer server) {
        if (server != null) {
            ServerChunkCache cache = server.overworld().getChunkSource();
            for (Fill f : FILLS.values()) release(cache, f);
        }
        FILLS.clear();
        FINISHED.clear();
    }

    private static void release(ServerChunkCache cache, Fill f) {
        for (Load ld : f.loads) {
            ChunkPos cp = new ChunkPos(ld.chunk());
            try { cache.removeRegionTicket(TICKET, cp, 0, cp); } catch (RuntimeException ignored) {}
        }
        f.skipped += f.loads.size();
        f.loads.clear();
    }

    private static void end(MinecraftServer server, Fill f, String why, boolean report) {
        release(server.overworld().getChunkSource(), f);
        ReconData.Snapshot s = null;
        try { s = flush(server, f); } catch (RuntimeException ex) { Wmd.LOGGER.warn("WMD recon: merge at the end of a fill failed", ex); }
        if (s == null) { ReconData.Entry e = ReconData.get(server).entry(f.owner); s = e != null ? e.snap : null; }
        boolean same = s != null && s.sameArea(f.x0, f.z0, f.step, f.n);
        int pct = same ? s.loadedPct() : 0, machines = same ? s.icons().length / 3 : 0;
        ServerPlayer p = server.getPlayerList().getPlayer(f.owner);
        Wmd.LOGGER.info("WMD recon fill for {} over {} {} ended ({}) after {} s: {} chunks loaded / generated + imaged, {} were already loaded, {} skipped, {} of {} handled; {}% of the area seen",
                p != null ? p.getGameProfile().getName() : f.owner, f.x0 + f.n * f.step / 2, f.z0 + f.n * f.step / 2, why, (System.currentTimeMillis() - f.startMs) / 1000L,
                f.loaded, f.alreadyLoaded, f.skipped, Math.min(f.cursor, f.order.length), f.order.length, pct);
        if (report && p != null && !f.drone)
            p.sendSystemMessage(Component.translatable("message.wmd.recon.fill_done", f.x0 + f.n * f.step / 2, f.z0 + f.n * f.step / 2, pct, machines)
                    .withStyle(ChatFormatting.AQUA));
    }

    private static double budget;

    static void tick(MinecraftServer server) {
        if (FILLS.isEmpty() || !server.isRunning()) return;
        int perSecond = Config.Server.reconFillPerSecond();
        budget = perSecond <= 0 ? Double.MAX_VALUE : Math.min(Math.max(1.0, perSecond), (budget == Double.MAX_VALUE ? 0.0 : budget) + perSecond / 20.0);
        long t = server.getTickCount();
        Fill f = FILLS.values().iterator().next();
        if (!f.forced && t - f.lastAlive > ALIVE_TICKS) { stop(server, f.owner, "satellite / wall / target lost", false); return; }
        ReconData.Entry e = ReconData.get(server).entry(f.owner);
        if (e == null || !e.hasTarget || e.tx - f.n * f.step / 2 != f.x0 || e.tz - f.n * f.step / 2 != f.z0) { forget(server, f.owner); return; }
        ServerLevel level = server.overworld();
        ServerChunkCache cache = level.getChunkSource();
        try {
            poll(level, cache, f, t);

            double mspt = server.getAverageTickTimeNanos() / 1.0e6;
            int cap = Math.max(1, Config.Server.reconFillInFlight());
            while (mspt <= Config.Server.reconFillMaxMspt() && f.loads.size() < cap && f.cursor < f.order.length && budget >= 1.0) {
                long c = f.order[f.cursor++];
                int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
                if (!f.drone && imaged(e, f, cx, cz)) continue;
                LevelChunk ch = cache.getChunkNow(cx, cz);
                if (ch != null) { image(level, f, ch); f.alreadyLoaded++; continue; }
                ChunkPos cp = new ChunkPos(c);
                cache.addRegionTicket(TICKET, cp, 0, cp);
                f.loads.add(new Load(c, t));
                if (budget != Double.MAX_VALUE) budget -= 1.0;
            }
        } catch (RuntimeException ex) {
            Wmd.LOGGER.warn("WMD recon fill failed", ex);
            stop(server, f.owner, "error", true);
            return;
        }
        if (t % FLUSH_TICKS == 0 || f.complete()) flush(server, f);
        int dec = f.progress() / 10;
        if (dec > f.lastDecile && dec < 10) {
            f.lastDecile = dec;
            Wmd.LOGGER.info("WMD recon fill over {} {}: {}% ({} chunks loaded / generated, {} already loaded, {} skipped)", f.x0 + f.n * f.step / 2,
                    f.z0 + f.n * f.step / 2, dec * 10, f.loaded, f.alreadyLoaded, f.skipped);
        }
        if (f.complete()) stop(server, f.owner, "complete", !f.drone);
    }

    private static void poll(ServerLevel level, ServerChunkCache cache, Fill f, long t) {
        for (Iterator<Load> it = f.loads.iterator(); it.hasNext(); ) {
            Load ld = it.next();
            ChunkPos cp = new ChunkPos(ld.chunk());
            LevelChunk ch = cache.getChunkNow(cp.x, cp.z);
            if (ch != null) {
                try { image(level, f, ch); f.loaded++; }
                finally { cache.removeRegionTicket(TICKET, cp, 0, cp); it.remove(); }
            } else if (t - ld.startTick() >= TICKET_TIMEOUT_TICKS - 20) {
                cache.removeRegionTicket(TICKET, cp, 0, cp);
                it.remove();
                f.skipped++;
            }
        }
    }

    private interface SampleSink { void accept(int index, int x, int z); }

    private static void forSamples(Fill f, int cx, int cz, SampleSink out) {
        int bx = cx << 4, bz = cz << 4, half = f.step / 2;
        int i0 = Math.max(0, Mth.ceil((bx - f.x0 - half) / (double) f.step)), k0 = Math.max(0, Mth.ceil((bz - f.z0 - half) / (double) f.step));
        for (int k = k0; k < f.n; k++) {
            int z = f.z0 + k * f.step + half;
            if (z >= bz + 16) break;
            for (int i = i0; i < f.n; i++) {
                int x = f.x0 + i * f.step + half;
                if (x >= bx + 16) break;
                out.accept(k * f.n + i, x, z);
            }
        }
    }

    private static boolean imaged(ReconData.Entry e, Fill f, int cx, int cz) {
        ReconData.Snapshot s = e.snap;
        if (s == null || !s.sameArea(f.x0, f.z0, f.step, f.n)) return false;
        byte[] col = s.col();
        boolean[] all = { true };
        forSamples(f, cx, cz, (i, x, z) -> { if ((col[i] & 0xFF) < 4) all[0] = false; });
        return all[0];
    }

    private static int missing(ReconData.Entry e, int x0, int z0, int step, int n) {
        ReconData.Snapshot s = e.snap;
        if (s == null || !s.sameArea(x0, z0, step, n)) return n * n;
        int k = 0;
        for (byte b : s.col()) if ((b & 0xFF) < 4) k++;
        return k;
    }

    private static void image(ServerLevel level, Fill f, LevelChunk ch) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        forSamples(f, ch.getPos().x, ch.getPos().z, (i, x, z) -> {
            long v = Recon.sampleColumn(level, ch, x, z, pos);
            f.add(i, MapColor.byId((int) (v >>> 32)), (int) v);
        });
        f.scanned.add(ch.getPos().toLong());
        Recon.scanChunk(ch, f.owner, f.x0, f.z0, f.x0 + f.n * f.step, f.z0 + f.n * f.step, f.icons);
    }

    private static ReconData.Snapshot flush(MinecraftServer server, Fill f) {
        if (f.cnt == 0 && f.scanned.isEmpty()) return null;
        ReconData.Snapshot s = Recon.merge(server, f.owner, f.x0, f.z0, f.step, f.n, f.idx, f.base, f.ys, f.cnt, f.scanned, f.icons, f.heights);
        f.cnt = 0;
        f.scanned.clear();
        f.icons.clear();
        if (f.idx.length > 4096) { f.idx = new int[256]; f.ys = new int[256]; f.base = new byte[256]; }
        return s;
    }
}
