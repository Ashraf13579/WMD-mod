package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class TrackNet {

    private TrackNet() {}

    public static final int TTL = 40;

    public static final int BEAT_TTL = 40;

    public static final int NO_RADAR = 0, RADAR_IDLE = 1, RADAR_SEEKING = 2, LOCKED = 3;

    private static final Map<String, Map<Long, Long>> TRACKS = new HashMap<>();

    private static final Map<String, long[]> BEATS = new HashMap<>();

    public static final double SAT_REACH = maxIntRange() + 1000.0;

    private static final Map<String, Map<Long, Long>> SEEKERS = new HashMap<>();

    private static double maxIntRange() {
        double m = 0.0;
        for (double r : Balance.INT_RANGE) m = Math.max(m, r);
        return m;
    }

    static void clearStatics() { TRACKS.clear(); BEATS.clear(); SEEKERS.clear(); RADARS.clear(); SHIP_TRACKS.clear(); }

    private static String key(ServerLevel level, UUID owner, int frequency) { return level.dimension().location() + "|" + owner + "|" + frequency; }

    public static void report(ServerLevel level, UUID owner, int frequency, long flightId, long now) {
        if (owner == null) return;
        TRACKS.computeIfAbsent(key(level, owner, frequency), k -> new HashMap<>()).put(flightId, now + TTL);
    }

    public static void heartbeat(ServerLevel level, UUID owner, int frequency, boolean seeking, long now) {
        if (owner == null) return;
        long[] b = BEATS.computeIfAbsent(key(level, owner, frequency), k -> new long[] { -1000, -1000 });
        b[0] = now;
        if (seeking) b[1] = now;
    }

    public static void heartbeat(ServerLevel level, UUID owner, int frequency, boolean seeking, long now, BlockPos pos) {
        heartbeat(level, owner, frequency, seeking, now);
        if (owner != null && pos != null) RADARS.computeIfAbsent(level.dimension().location() + "|" + owner, k -> new HashMap<>()).put(pos.asLong(), now);
        if (owner != null && seeking && pos != null) SEEKERS.computeIfAbsent(key(level, owner, frequency), k -> new HashMap<>()).put(pos.asLong(), now);
    }

    private static final Map<String, Map<Long, Long>> RADARS = new HashMap<>();

    public static List<BlockPos> radarsOf(ServerLevel level, UUID owner, long now) {
        List<BlockPos> out = new ArrayList<>();
        Map<Long, Long> m = owner == null ? null : RADARS.get(level.dimension().location() + "|" + owner);
        if (m == null) return out;
        m.entrySet().removeIf(e -> now - e.getValue() > BEAT_TTL);
        for (long p : m.keySet()) out.add(BlockPos.of(p));
        return out;
    }

    public static java.util.Set<UUID> radarOwnersNear(ServerLevel level, double x, double z, double range, long now) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        String dim = level.dimension().location() + "|";
        for (var e : RADARS.entrySet()) {
            if (!e.getKey().startsWith(dim)) continue;
            for (var r : e.getValue().entrySet()) {
                if (now - r.getValue() > BEAT_TTL) continue;
                BlockPos p = BlockPos.of(r.getKey());
                double dx = p.getX() - x, dz = p.getZ() - z;
                if (dx * dx + dz * dz <= range * range) {
                    try { out.add(UUID.fromString(e.getKey().substring(dim.length()))); } catch (IllegalArgumentException ignored) {}
                    break;
                }
            }
        }
        return out;
    }

    public static List<Long> tracks(ServerLevel level, UUID owner, int frequency, long now) {
        Map<Long, Long> m = TRACKS.get(key(level, owner, frequency));
        List<Long> out = new ArrayList<>();
        if (m != null) {
            m.entrySet().removeIf(e -> e.getValue() < now);
            out.addAll(m.keySet());
        }
        for (long id : satTracks(level, owner, frequency, now)) if (!out.contains(id)) out.add(id);
        return out;
    }

    public static List<Long> satTracks(ServerLevel level, UUID owner, int frequency, long now) {
        List<Long> out = new ArrayList<>();
        if (owner == null) return out;
        Map<Long, Long> radars = SEEKERS.get(key(level, owner, frequency));
        if (radars == null) return out;
        radars.entrySet().removeIf(e -> now - e.getValue() > BEAT_TTL);
        if (radars.isEmpty() || !Recon.tracking(level, owner)) return out;
        FlightManager mgr = FlightManager.get(level);
        double reach = SAT_REACH + Interceptors.rangeBonus(owner);
        double reach2 = reach * reach;
        for (Flight f : mgr.flights()) {
            if (now < f.start() || now > f.endTime() || f.isOwnedBy(owner, null)) continue;
            if (Hypersonic.hidden(f, now)) continue;
            Vec3 p = f.position(now);
            if (mgr.chaffHides(p.x, p.z, p.x, p.z)) continue;
            double r2 = f.isCruise() ? Balance.CRUISE_RADAR_RANGE * Balance.CRUISE_RADAR_RANGE : reach2;
            for (long rp : radars.keySet()) {
                Vec3 rw = SableBridge.worldPos(level, BlockPos.of(rp));
                double dx = p.x - rw.x, dz = p.z - rw.z;
                if (dx * dx + dz * dz <= r2) { out.add(f.id()); break; }
            }
        }
        return out;
    }

    public static boolean seesPoint(ServerLevel level, UUID owner, int frequency, double x, double z, long now) {
        if (owner == null) return false;
        Map<Long, Long> radars = SEEKERS.get(key(level, owner, frequency));
        if (radars == null) return false;
        double r2 = Balance.RADAR_DETECT_RANGE * Balance.RADAR_DETECT_RANGE;
        for (Map.Entry<Long, Long> e : radars.entrySet()) {
            if (now - e.getValue() > BEAT_TTL) continue;
            Vec3 rw = SableBridge.worldPos(level, BlockPos.of(e.getKey()));
            double dx = x - rw.x, dz = z - rw.z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    public static boolean satFeed(ServerLevel level, UUID owner, int frequency, long now) {
        if (owner == null) return false;
        Map<Long, Long> radars = SEEKERS.get(key(level, owner, frequency));
        if (radars == null) return false;
        for (long t : radars.values()) if (now - t <= BEAT_TTL) return Recon.tracking(level, owner);
        return false;
    }

    public static final int SHIP_TTL = 60;

    private static final Map<String, Map<UUID, Long>> SHIP_TRACKS = new HashMap<>();

    public static void reportShip(ServerLevel level, UUID owner, int frequency, UUID shipId, long now) {
        if (owner == null || shipId == null) return;
        Map<UUID, Long> m = SHIP_TRACKS.computeIfAbsent(key(level, owner, frequency), k -> new HashMap<>());
        if (m.size() > 64) m.clear();
        m.put(shipId, now + SHIP_TTL);
    }

    public static List<UUID> shipTracks(ServerLevel level, UUID owner, int frequency, long now) {
        List<UUID> out = new ArrayList<>();
        Map<UUID, Long> m = owner == null ? null : SHIP_TRACKS.get(key(level, owner, frequency));
        if (m == null) return out;
        m.entrySet().removeIf(e -> e.getValue() < now);
        out.addAll(m.keySet());
        return out;
    }

    public static int linkState(ServerLevel level, UUID owner, int frequency, long now) {
        if (owner == null) return NO_RADAR;
        if (!tracks(level, owner, frequency, now).isEmpty()) return LOCKED;
        long[] b = BEATS.get(key(level, owner, frequency));
        if (b == null || now - b[0] > BEAT_TTL) return NO_RADAR;
        return now - b[1] <= BEAT_TTL ? RADAR_SEEKING : RADAR_IDLE;
    }
}
