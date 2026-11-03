package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3dc;

final class SableAssembly {

    private SableAssembly() {}

    static final int PRE_CHUNKS = 8;

    static final int PRE_PER_TICK = 4;

    static final int SCANS_PER_TICK = 8;
    static final int CARRIER_TICKS = 5, SWEEP_TICKS = 100;

    static final double LAND_MARGIN = 6.0;

    private record Scan(UUID ship, AABB world, String cause) {}

    private static final class Carrier {
        int count;
        AABB bounds;
    }

    private static final class State {
        final ArrayDeque<Scan> queue = new ArrayDeque<>();
        final Map<UUID, Carrier> carriers = new HashMap<>();
        long preTick = Long.MIN_VALUE;
        int preThisTick;
    }

    private static final Map<Level, State> STATES = Statics.server(new WeakHashMap<>());

    static void clear() { STATES.clear(); }

    private static State state(ServerLevel level) { return STATES.get(level); }

    private static final class Observer implements SubLevelObserver {
        private final ServerLevel level;
        Observer(ServerLevel level) { this.level = level; }

        @Override
        public void onSubLevelAdded(SubLevel s) {
            try {
                if (!(s instanceof ServerSubLevel) || SableDebris.isDebris(s) || !ShipRelink.enabled()) return;
                State st = state(level);
                if (st == null) return;
                preSweep(level, st, s.logicalPose());
                st.queue.add(new Scan(s.getUniqueId(), null, "assembly"));
            } catch (Throwable t) { SableBridge.fail(t); }
        }

        @Override
        public void onSubLevelRemoved(SubLevel s, SubLevelRemovalReason reason) {
            try {
                if (reason != SubLevelRemovalReason.REMOVED || SableDebris.isDebris(s) || !ShipRelink.enabled()) return;
                State st = state(level);
                if (st == null) return;
                Carrier c = st.carriers.remove(s.getUniqueId());
                AABB box = c != null ? c.bounds : null;
                if (box == null) box = bounds(s);
                if (box != null) st.queue.add(new Scan(s.getUniqueId(), box, "disassembly"));
            } catch (Throwable t) { SableBridge.fail(t); }
        }
    }

    static void tick(ServerLevel level) {
        State st = STATES.get(level);
        if (st == null) {
            SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
            if (c == null) return;
            st = new State();
            STATES.put(level, st);
            c.addObserver(new Observer(level));
        }
        long now = level.getGameTime();
        for (int i = 0; i < SCANS_PER_TICK && !st.queue.isEmpty(); i++) run(level, st, st.queue.poll());
        if (now % CARRIER_TICKS == 0 && !st.carriers.isEmpty()) checkCarriers(level, st);
        if (now % SWEEP_TICKS == 0) sweepShips(level, st);
    }

    private static void run(ServerLevel level, State st, Scan s) {
        if (s.world() == null) {
            SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
            SubLevel sub = c == null ? null : c.getSubLevel(s.ship());
            if (!(sub instanceof ServerSubLevel ss) || ss.isRemoved() || SableDebris.isDebris(ss)) return;
            List<BlockEntity> bes = plotBlockEntities(ss);
            ShipRelink.collect(level, bes, s.cause() + " of ship " + name(ss));
            remember(st, ss, bes);
        } else {
            ShipRelink.collect(level, worldBlockEntities(level, s.world().inflate(LAND_MARGIN)), s.cause() + " of ship " + s.ship());
        }
    }

    private static void checkCarriers(ServerLevel level, State st) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return;
        var it = st.carriers.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            SubLevel sub = c.getSubLevel(e.getKey());
            if (!(sub instanceof ServerSubLevel ss) || ss.isRemoved() || SableDebris.isDebris(ss)) continue;
            int n = 0;
            for (BlockEntity be : plotBlockEntities(ss)) if (ShipRelink.ours(be)) n++;
            Carrier k = e.getValue();
            if (n < k.count && k.bounds != null) st.queue.add(new Scan(e.getKey(), k.bounds, "machines left ship " + name(ss)));
            AABB b = bounds(ss);
            if (b != null && n > 0) k.bounds = b;
            k.count = n;
            if (n == 0) it.remove();
        }
    }

    private static void sweepShips(ServerLevel level, State st) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return;
        for (SubLevel sub : c.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ss) || ss.isRemoved() || SableDebris.isDebris(ss)) continue;
            List<BlockEntity> bes = plotBlockEntities(ss);
            if (bes.isEmpty()) continue;
            ShipRelink.collect(level, bes, "sweep of ship " + name(ss));
            remember(st, ss, bes);
        }
    }

    private static void remember(State st, ServerSubLevel ss, List<BlockEntity> bes) {
        int n = 0;
        for (BlockEntity be : bes) if (ShipRelink.ours(be)) n++;
        if (n == 0) return;
        Carrier k = st.carriers.computeIfAbsent(ss.getUniqueId(), u -> new Carrier());
        k.count = n;
        AABB b = bounds(ss);
        if (b != null) k.bounds = b;
    }

    private static void preSweep(ServerLevel level, State st, Pose3dc pose) {
        long now = level.getGameTime();
        if (st.preTick != now) { st.preTick = now; st.preThisTick = 0; }
        if (st.preThisTick >= PRE_PER_TICK || pose == null) return;
        st.preThisTick++;
        Vector3dc p = pose.position();
        int cx = Mth.floor(p.x()) >> 4, cz = Mth.floor(p.z()) >> 4;
        for (int x = cx - PRE_CHUNKS; x <= cx + PRE_CHUNKS; x++)
            for (int z = cz - PRE_CHUNKS; z <= cz + PRE_CHUNKS; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(x, z);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (ShipRelink.ours(be)) ShipRelink.stamp(be);
            }
    }

    private static List<BlockEntity> plotBlockEntities(ServerSubLevel ss) {
        LevelPlot plot = ss.getPlot();
        if (plot == null) return List.of();
        List<BlockEntity> out = new ArrayList<>();
        for (PlotChunkHolder h : plot.getLoadedChunks()) {
            LevelChunk ch = h == null ? null : h.getChunk();
            if (ch != null) out.addAll(ch.getBlockEntities().values());
        }
        return out;
    }

    private static List<BlockEntity> worldBlockEntities(ServerLevel level, AABB box) {
        List<BlockEntity> out = new ArrayList<>();
        int x0 = Mth.floor(box.minX) >> 4, x1 = Mth.floor(box.maxX) >> 4, z0 = Mth.floor(box.minZ) >> 4, z1 = Mth.floor(box.maxZ) >> 4;
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 4096) return out;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(x, z);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    var b = be.getBlockPos();
                    if (b.getX() >= box.minX - 1 && b.getX() <= box.maxX && b.getY() >= box.minY - 1 && b.getY() <= box.maxY
                            && b.getZ() >= box.minZ - 1 && b.getZ() <= box.maxZ) out.add(be);
                }
            }
        return out;
    }

    private static AABB bounds(SubLevel s) {
        BoundingBox3dc b = s.boundingBox();
        if (b == null) return null;
        AABB a = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        return a.getSize() > 0.0 && a.getSize() < 4096.0 ? a : null;
    }

    private static String name(ServerSubLevel ss) {
        String n = ss.getName();
        return n == null || n.isEmpty() ? String.valueOf(ss.getUniqueId()) : n;
    }
}
