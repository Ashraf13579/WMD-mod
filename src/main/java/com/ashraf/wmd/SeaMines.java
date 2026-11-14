package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SeaMines {

    private SeaMines() {}

    public record Mine(long pos, int kind, Vec3 caseAt, UUID owner) {}

    private static final Map<Level, Map<Long, Mine>> MINES = Statics.server(new WeakHashMap<>());

    static void put(ServerLevel level, BlockPos pos, int kind, Vec3 caseAt, UUID owner) {
        MINES.computeIfAbsent(level, l -> new HashMap<>()).put(pos.asLong(), new Mine(pos.asLong(), kind, caseAt, owner));
    }

    static void remove(Level level, BlockPos pos) {
        Map<Long, Mine> m = MINES.get(level);
        if (m != null) m.remove(pos.asLong());
    }

    public static List<Mine> near(ServerLevel level, Vec3 p, double r) {
        List<Mine> out = new ArrayList<>();
        Map<Long, Mine> m = MINES.get(level);
        if (m == null || p == null) return out;
        double r2 = r * r;
        for (Mine x : m.values()) if (x.caseAt().distanceToSqr(p) <= r2) out.add(x);
        return out;
    }

    static List<SonarNet.Fix> pingFixes(ServerLevel level, Vec3 ear, double r, long until) {
        List<SonarNet.Fix> out = new ArrayList<>();
        for (Mine x : near(level, ear, r)) {
            Vec3 c = x.caseAt();
            out.add(new SonarNet.Fix(c.x, c.y, c.z, 0.0, "mine:" + x.kind(), true, NavalLayer.depthBelowSurface(level, c), until, null));
            if (out.size() >= 16) break;
        }
        return out;
    }

    static void shock(ServerLevel level, Vec3 at, float power) {
        double r = Balance.MINE_SYMPATHY_PER_POWER * power;
        if (r <= 0.0) return;
        for (Mine x : near(level, at, r)) {
            BlockPos p = BlockPos.of(x.pos());
            DUE.computeIfAbsent(level, l -> new ArrayList<>()).add(new long[] { level.getGameTime() + Balance.MINE_SYMPATHY_TICKS, p.asLong() });
        }
    }

    private static final Map<Level, List<long[]>> DUE = Statics.server(new WeakHashMap<>());

    @net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
    static final class Ticker {
        @net.neoforged.bus.api.SubscribeEvent
        static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) {
            if (!(e.getLevel() instanceof ServerLevel sl)) return;
            List<long[]> due = DUE.get(sl);
            if (due == null || due.isEmpty()) return;
            long now = sl.getGameTime();
            List<long[]> go = new ArrayList<>();
            due.removeIf(d -> { if (d[0] <= now) { go.add(d); return true; } return false; });
            for (long[] d : go) {
                BlockPos p = BlockPos.of(d[1]);
                if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof SeaMineBlockEntity be) be.sympathetic(sl);
            }
        }
    }

    static boolean friendly(ServerLevel level, UUID ship, UUID owner) {
        if (owner == null || ship == null || !SableBridge.active()) return false;
        try { return SableImpact.friendly(level, ship, owner); }
        catch (Throwable t) { SableBridge.fail(t); return false; }
    }

    static List<SableBridge.ShipInfo> strangersNear(ServerLevel level, Vec3 p, double r, UUID owner) {
        List<SableBridge.ShipInfo> out = new ArrayList<>();
        if (!SableBridge.active()) return out;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, new AABB(p, p).inflate(r))) {
            if (dist(s.worldBounds(), p) > r || !NavalLayer.inWater(level, s) || friendly(level, s.id(), owner)) continue;
            out.add(s);
        }
        out.sort((a, b) -> Double.compare(dist(a.worldBounds(), p), dist(b.worldBounds(), p)));
        return out;
    }

    static double dist(AABB b, Vec3 p) {
        double dx = Math.max(0.0, Math.max(b.minX - p.x, p.x - b.maxX)), dy = Math.max(0.0, Math.max(b.minY - p.y, p.y - b.maxY)), dz = Math.max(0.0, Math.max(b.minZ - p.z, p.z - b.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static Vec3 closest(AABB b, Vec3 p) {
        return new Vec3(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY, p.y)), Math.max(b.minZ, Math.min(b.maxZ, p.z)));
    }
}
