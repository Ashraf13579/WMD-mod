package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

final class SableShips {

    private SableShips() {}

    static final int COUNT_CACHE_TICKS = 100;

    private static final Map<UUID, long[]> COUNTS = Statics.server(new ConcurrentHashMap<>());

    static List<SableBridge.ShipInfo> all(ServerLevel level) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return List.of();
        List<SableBridge.ShipInfo> out = new ArrayList<>();
        for (SubLevel s : c.getAllSubLevels()) if (s instanceof ServerSubLevel ss && !ss.isRemoved() && !SableDebris.isDebris(ss)) out.add(info(level, ss));
        return out;
    }

    static List<SableBridge.ShipInfo> intersecting(ServerLevel level, AABB box) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return List.of();
        List<SableBridge.ShipInfo> out = null;
        for (SubLevel s : c.queryIntersecting(new BoundingBox3d(box))) {
            if (!(s instanceof ServerSubLevel ss) || ss.isRemoved() || SableDebris.isDebris(ss)) continue;
            if (out == null) out = new ArrayList<>(4);
            out.add(info(level, ss));
        }
        return out == null ? List.of() : out;
    }

    static SableBridge.ShipInfo info(ServerLevel level, UUID id) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return null;
        SubLevel s = c.getSubLevel(id);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() && !SableDebris.isDebris(ss) ? info(level, ss) : null;
    }

    static SableBridge.ShipInfo infoAt(ServerLevel level, BlockPos pos) {
        SubLevelAccess a = containing(level, pos);
        return a instanceof ServerSubLevel ss && !ss.isRemoved() && !SableDebris.isDebris(ss) ? info(level, ss) : null;
    }

    static void forgetCount(UUID id) { COUNTS.remove(id); }

    static int fracture(ServerLevel level, UUID id, Vec3 at) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        if (c == null) return 0;
        SubLevel sub = c.getSubLevel(id);
        if (!(sub instanceof ServerSubLevel s) || s.isRemoved() || SableDebris.isDebris(s) || s.getPlot() == null) return 0;
        dev.ryanhcode.sable.companion.math.BoundingBox3ic b = s.getPlot().getBoundingBox();
        Vec3 p = SableBridge.toShip(level, id, at);
        if (b == null || p == null) return 0;
        boolean alongX = b.maxX() - b.minX() >= b.maxZ() - b.minZ();
        int lo = alongX ? b.minX() : b.minZ(), hi = alongX ? b.maxX() : b.maxZ(), span = hi - lo;
        if (span < Balance.SHIP_FRACTURE_MIN_SPAN) return 0;
        int cut = (int) Math.round(Math.max(lo + span * 0.25, Math.min(hi - span * 0.25, alongX ? p.x : p.z)));
        int ulo = alongX ? b.minZ() : b.minX(), uhi = alongX ? b.maxZ() : b.maxX();
        net.minecraft.core.BlockPos.MutableBlockPos q = new net.minecraft.core.BlockPos.MutableBlockPos();
        int removed = 0;
        for (int u = ulo; u <= uhi; u++) for (int y = b.minY(); y <= b.maxY(); y++) {
            int k = cut + ((Math.floorDiv(u, 3) * 73856093 ^ Math.floorDiv(y, 3) * 19349663) & 1);
            if (alongX) q.set(k, y, u); else q.set(u, y, k);
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(q);
            if (st.isAir() || st.hasBlockEntity()) continue;
            level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            removed++;
        }
        COUNTS.remove(id);
        return removed;
    }

    private static SableBridge.ShipInfo info(ServerLevel level, ServerSubLevel s) {
        BoundingBox3dc b = s.boundingBox();
        AABB bounds = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        Vector3dc v = s.latestLinearVelocity;
        Vec3 vel = v == null ? Vec3.ZERO : new Vec3(v.x(), v.y(), v.z());
        MassData m = s.getMassTracker();
        double mass = m == null ? 0.0 : m.getMass();
        String name = s.getName();
        UUID id = s.getUniqueId();
        return new SableBridge.ShipInfo(id, name == null || name.isEmpty() ? "(unnamed)" : name, bounds.getCenter(), vel, bounds, mass,
                blockCount(level, s, id));
    }

    private static int blockCount(ServerLevel level, ServerSubLevel s, UUID id) {
        long now = level.getGameTime();
        long[] hit = id == null ? null : COUNTS.get(id);
        if (hit != null && now - hit[0] >= 0 && now - hit[0] < COUNT_CACHE_TICKS) return (int) hit[1];
        int[] n = { 0 };
        LevelPlot plot = s.getPlot();
        if (plot != null) {
            for (PlotChunkHolder h : plot.getLoadedChunks()) {
                LevelChunk ch = h == null ? null : h.getChunk();
                if (ch == null) continue;
                for (LevelChunkSection sec : ch.getSections()) {
                    if (sec == null || sec.hasOnlyAir()) continue;
                    sec.getStates().count((state, k) -> { if (!state.isAir()) n[0] += k; });
                }
            }
        }
        if (id != null) {
            if (COUNTS.size() > 512) COUNTS.clear();
            COUNTS.put(id, new long[] { now, n[0] });
        }
        return n[0];
    }

    static UUID shipUnder(net.minecraft.world.entity.Entity e) {
        SubLevelAccess a = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(e);
        return a == null ? null : a.getUniqueId();
    }

    static List<BlockPos> blockEntitiesOn(ServerLevel level, UUID id) {
        SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
        SubLevel s = c == null ? null : c.getSubLevel(id);
        LevelPlot plot = s == null || s.isRemoved() ? null : s.getPlot();
        if (plot == null) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (PlotChunkHolder h : plot.getLoadedChunks()) {
            LevelChunk ch = h == null ? null : h.getChunk();
            if (ch != null) out.addAll(ch.getBlockEntities().keySet());
        }
        return out;
    }

    private static SubLevelAccess containing(Level level, BlockPos pos) {
        SableCompanion sc = SableCompanion.INSTANCE;
        if (!sc.isInPlotGrid(level, pos.getX() >> 4, pos.getZ() >> 4)) return null;
        return sc.getContaining(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    static boolean onShip(Level level, BlockPos pos) { return containing(level, pos) != null; }

    static UUID idAt(Level level, BlockPos pos) {
        SubLevelAccess a = containing(level, pos);
        return a == null ? null : a.getUniqueId();
    }

    static Vec3 toWorld(Level level, Vec3 p) {
        int cx = net.minecraft.util.Mth.floor(p.x) >> 4, cz = net.minecraft.util.Mth.floor(p.z) >> 4;
        SableCompanion sc = SableCompanion.INSTANCE;
        if (!sc.isInPlotGrid(level, cx, cz) || sc.getContaining(level, cx, cz) == null) return p;
        Vec3 w = sc.projectOutOfSubLevel(level, (net.minecraft.core.Position) p);
        return w == null ? p : w;
    }

    static Vec3 toShip(Level level, UUID id, Vec3 world) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return null;
        SubLevel s = c.getSubLevel(id);
        return s == null || s.isRemoved() ? null : s.logicalPose().transformPositionInverse(world);
    }
}
