package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class AirStations {

    private AirStations() {}

    public interface Station {

        int loaded();

        String fire(ServerLevel level, UUID target, AirMissiles.LockSource lock);
        BlockPos stationPos();

        default boolean exposed(ServerLevel level) { return true; }

        default boolean loseStore(ServerLevel level) { return false; }
    }

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> AT = Statics.server(new HashMap<>());

    static void add(ServerLevel level, UUID ship, BlockPos pos) {
        if (ship == null) return;
        AT.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(pos.asLong());
    }

    static void remove(ServerLevel level, UUID ship, BlockPos pos) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = AT.get(level.dimension());
        if (m == null) return;
        Set<Long> s = m.get(ship);
        if (s != null) { s.remove(pos.asLong()); if (s.isEmpty()) m.remove(ship); }
        if (m.isEmpty()) AT.remove(level.dimension());
    }

    public static List<Station> of(ServerLevel level, UUID ship) {
        List<Station> out = new ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = AT.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof Station st) out.add(st);
        }
        out.sort(Comparator.<Station>comparingInt(st -> st.stationPos().getX()).thenComparingInt(st -> st.stationPos().getZ()).thenComparingInt(st -> st.stationPos().getY()));
        return out;
    }

    static AirPicture.Seen heatPick(ServerLevel level, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir, UUID ownShip) {
        if (!AirGate.on()) return null;
        double best = Balance.AAM_HEAT_CONE;
        AirPicture.Seen pick = null;
        long now = level.getGameTime();
        for (AirPicture.Seen s : AirPicture.seenFrom(level, from, Balance.AAM_HEAT_LOCK_RANGE * Balance.HEAT_SIG_HOT, ownShip)) {
            net.minecraft.world.phys.Vec3 d = s.a().pos().subtract(from);
            if (d.length() > Balance.AAM_HEAT_LOCK_RANGE * AirHeat.factor(level, s.a().id(), now)) continue;
            if (!AirHeat.rearAspect(level, s.a().id(), s.a().pos(), s.a().vel(), from, now)) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.normalize().dot(dir.normalize())))));
            if (ang <= best) { best = ang; pick = s; }
        }
        return pick;
    }

    static int agmSpot(ServerLevel level, java.util.UUID owner, Vec3 from, Vec3 dir) {
        if (owner == null || dir.lengthSqr() < 1e-9) return -1;
        Vec3 ax = dir.normalize();
        double cos = Math.cos(Math.toRadians(Balance.AGM_CONE)), best = -2.0;
        int id = -1;
        for (LaserSpots.Spot s : LaserSpots.all()) {
            if (!owner.equals(s.owner()) || LaserSpots.get(level, s.id()) == null) continue;
            Vec3 d = s.pos().subtract(from);
            double l = d.length();
            if (l < 1.0 || l > Balance.AAM_RANGE[AirMissiles.AGM]) continue;
            double c = d.dot(ax) / l;
            if (c >= cos && c > best) { best = c; id = s.id(); }
        }
        return id;
    }

    private static final Map<UUID, Boolean> AGM_GPS = Statics.server(new HashMap<>());

    public static boolean agmGps(UUID ship) { return ship != null && AGM_GPS.getOrDefault(ship, false); }

    public static boolean toggleAgmGps(UUID ship) {
        if (ship == null) return false;
        if (AGM_GPS.size() > 512) AGM_GPS.clear();
        boolean g = !agmGps(ship);
        AGM_GPS.put(ship, g);
        return g;
    }

    static Vec3 gpsPoint(ServerLevel level, UUID owner, UUID ship, Vec3 from, Vec3 dir, String[] label) {
        Vec3 p = TargetingPodBlockEntity.spotAboard(level, ship);
        if (p != null) { label[0] = "pod"; return p; }
        int id = agmSpot(level, owner, from, dir);
        LaserSpots.Spot s = id < 0 ? null : LaserSpots.get(level, id);
        if (s != null) { label[0] = "spot"; return s.pos(); }
        CcTarget t = owner == null ? null : CcTarget.of(owner);
        if (t == null) return null;
        label[0] = "CC";
        int gy = level.getChunkSource().getChunkNow(t.x() >> 4, t.z() >> 4) != null
                ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, t.x(), t.z()) : (int) Math.floor(from.y);
        return new Vec3(t.x() + 0.5, gy, t.z() + 0.5);
    }

    static BlockPos armEmitter(ServerLevel level, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir, UUID owner, UUID ownShip, UUID lockedAircraft) {
        if (lockedAircraft != null) {
            BlockPos on = RadarEmit.onShip(level, lockedAircraft, from, Balance.AAM_RANGE[AirMissiles.ARM], owner);
            if (on != null) return on;
        }
        return RadarEmit.nearestInCone(level, from, dir, Balance.AAM_RANGE[AirMissiles.ARM], Balance.AAM_ARM_CONE, owner, ownShip);
    }

    static int shootOff(ServerLevel level, UUID ship, net.minecraft.world.phys.Vec3 at, double radius) {
        if (!AirGate.on() || ship == null || at == null) return 0;
        int n = 0;
        for (Station st : of(level, ship)) {
            if (st.loaded() < 0 || !st.exposed(level)) continue;
            net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, st.stationPos());
            if (w == null || w.distanceTo(at) > radius + 0.5) continue;
            if (level.random.nextDouble() < Balance.AAM_STORE_SHOT_OFF && st.loseStore(level)) n++;
        }
        return n;
    }
}
