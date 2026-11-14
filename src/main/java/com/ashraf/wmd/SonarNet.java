package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SonarNet {

    private SonarNet() {}

    public static final int DOME = 0, ARRAY = 1, BUOY = 2, UUV = 3;

    public static final int TOWED = 4, NODE = 5;

    public record Sensor(long pos, Vec3 ear, int kind, double range, UUID ship, long until) {}

    public record Bearing(long sensor, Vec3 ear, double bearing, double level, NoiseNet.Kind kind, double dist, long time) {}

    public record Fix(double x, double y, double z, double err, String label, boolean submerged, double depth, long until, UUID ship) {}

    private static final Map<String, Map<Long, Sensor>> SENSORS = Statics.server(new HashMap<>());
    private static final Map<String, Map<Object, Map<Long, Bearing>>> BEARINGS = Statics.server(new HashMap<>());
    private static final Map<String, List<Fix>> PINGS = Statics.server(new HashMap<>());

    private static final Map<String, Map<UUID, Fix>> PING_RAW = Statics.server(new HashMap<>());

    public static String key(ServerLevel level, UUID owner, int freq) {
        return (owner == null ? "-" : owner.toString()) + "|" + freq + "|" + level.dimension().location();
    }

    private static final Map<String, java.util.Set<Long>> FINE = Statics.server(new HashMap<>());

    static void beat(ServerLevel level, UUID owner, int freq, long pos, Vec3 ear, int kind, double range, UUID ship, boolean fine) {
        java.util.Set<Long> f = FINE.computeIfAbsent(key(level, owner, freq), k -> new java.util.HashSet<>());
        if (fine) f.add(pos); else f.remove(pos);
        beat(level, owner, freq, pos, ear, kind, range, ship);
    }

    static void beat(ServerLevel level, UUID owner, int freq, long pos, Vec3 ear, int kind, double range, UUID ship) {
        SENSORS.computeIfAbsent(key(level, owner, freq), k -> new HashMap<>()).put(pos, new Sensor(pos, ear, kind, range, ship, level.getGameTime() + Balance.SONAR_SCAN_TICKS * 3L));
    }

    static void forget(ServerLevel level, UUID owner, int freq, long pos) {
        Map<Long, Sensor> m = SENSORS.get(key(level, owner, freq));
        if (m != null) m.remove(pos);
        java.util.Set<Long> f = FINE.get(key(level, owner, freq));
        if (f != null) f.remove(pos);
    }

    public static List<Sensor> sensors(ServerLevel level, UUID owner, int freq) {
        Map<Long, Sensor> m = SENSORS.get(key(level, owner, freq));
        List<Sensor> out = new ArrayList<>();
        if (m == null) return out;
        long now = level.getGameTime();
        m.values().removeIf(s -> s.until() < now);
        out.addAll(m.values());
        return out;
    }

    static void report(ServerLevel level, UUID owner, int freq, long sensor, Vec3 ear, List<NoiseNet.Heard> heard) {
        Map<Object, Map<Long, Bearing>> net = BEARINGS.computeIfAbsent(key(level, owner, freq), k -> new HashMap<>());
        long now = level.getGameTime();
        for (Map<Long, Bearing> b : net.values()) b.remove(sensor);
        for (NoiseNet.Heard h : heard)
            net.computeIfAbsent(h.src().key(), k -> new HashMap<>()).put(sensor, new Bearing(sensor, ear, h.bearing(), h.level(), h.src().kind(), h.dist(), now));
        net.values().removeIf(Map::isEmpty);
    }

    static int ping(ServerLevel level, UUID owner, int freq, Vec3 ear, UUID ownShip) {
        long now = level.getGameTime();
        List<Fix> l = PINGS.computeIfAbsent(key(level, owner, freq), k -> new ArrayList<>());
        l.removeIf(f -> f.until() - Balance.SONAR_PING_SHOW < now);
        int n = 0;
        Map<UUID, Fix> raw = PING_RAW.computeIfAbsent(key(level, owner, freq), k -> new java.util.LinkedHashMap<>());
        raw.values().removeIf(f -> f.until() - Balance.SONAR_PING_SHOW < now);
        double r = Balance.SONAR_PING_RANGE;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, new net.minecraft.world.phys.AABB(ear, ear).inflate(r))) {
            if (s.id().equals(ownShip) || s.worldCenter().distanceTo(ear) > r * Anechoic.pingFactor(level, s.id()) || !NavalLayer.inWater(level, s)) continue;
            boolean sub = NavalLayer.submerged(level, s);
            raw.put(s.id(), new Fix(s.worldCenter().x, s.worldCenter().y, s.worldCenter().z, 0.0, (DeepSeasBridge.isSub(s.id()) ? "sub" : "ship") + ":" + s.blockCount(),
                    sub, NavalLayer.shipDepth(level, s), now + Balance.SONAR_PING_SHOW, s.id()));
            n++;
        }

        for (ParkedShips.Parked p : ParkedShips.near(level, ear, r)) {
            if (p.id().equals(ownShip) || raw.containsKey(p.id()) || !p.wet()) continue;
            if (Math.sqrt((p.x() - ear.x) * (p.x() - ear.x) + (p.y() - ear.y) * (p.y() - ear.y) + (p.z() - ear.z) * (p.z() - ear.z)) > r * Anechoic.pingFactorOf(Anechoic.cached(p.id()))) continue;
            raw.put(p.id(), new Fix(p.x(), p.y(), p.z(), 0.0, (p.sub() ? "sub" : "ship") + ":" + p.blocks(), p.submerged(), p.depth(), now + Balance.SONAR_PING_SHOW, p.id()));
            n++;
        }
        l.clear();
        l.addAll(mergeWrecks(raw.values()));
        while (l.size() > 64) l.remove(0);
        l.addAll(SeaMines.pingFixes(level, ear, r, now + Balance.SONAR_PING_SHOW));
        NoiseNet.pulse(level, ear, Balance.SONAR_PING_DB, NoiseNet.Kind.PING, owner, Balance.SONAR_PING_TICKS);
        return n;
    }

    static List<Fix> mergeWrecks(java.util.Collection<Fix> fixes) {
        List<Fix> in = new ArrayList<>(fixes), out = new ArrayList<>();
        boolean[] used = new boolean[in.size()];
        double m2 = Balance.SONAR_WRECK_MERGE * Balance.SONAR_WRECK_MERGE;
        for (int i = 0; i < in.size(); i++) {
            if (used[i]) continue;
            List<Fix> group = new ArrayList<>();
            java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>();
            todo.add(i);
            used[i] = true;
            while (!todo.isEmpty()) {
                Fix a = in.get(todo.poll());
                group.add(a);
                for (int j = 0; j < in.size(); j++) {
                    if (used[j]) continue;
                    Fix b = in.get(j);
                    double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                    if (dx * dx + dy * dy + dz * dz <= m2) { used[j] = true; todo.add(j); }
                }
            }
            if (group.size() == 1) { out.add(group.get(0)); continue; }
            double x = 0, y = 0, z = 0, depth = 0;
            int blocks = 0;
            boolean sub = false;
            for (Fix f : group) {
                x += f.x(); y += f.y(); z += f.z();
                depth = Math.max(depth, f.depth());
                sub |= f.submerged();
                int c = f.label().lastIndexOf(':');
                try { blocks += Integer.parseInt(f.label().substring(c + 1)); } catch (RuntimeException ignored) {  }
            }
            int k = group.size();
            out.add(new Fix(x / k, y / k, z / k, 0.0, "wreck:" + k + ":" + blocks, sub, depth, group.get(0).until(), group.get(0).ship()));
        }
        return out;
    }

    public record Contact(NoiseNet.Kind kind, double bearing, double level, long sensor, Vec3 ear, Fix fix) {}

    public static List<Contact> contacts(ServerLevel level, UUID owner, int freq) {
        List<Contact> out = new ArrayList<>();
        Map<Object, Map<Long, Bearing>> net = BEARINGS.get(key(level, owner, freq));
        if (net == null) return out;
        long now = level.getGameTime();
        java.util.Set<Long> fine = FINE.getOrDefault(key(level, owner, freq), java.util.Set.of());
        for (Iterator<Map<Long, Bearing>> it = net.values().iterator(); it.hasNext(); ) {
            Map<Long, Bearing> bs = it.next();
            bs.values().removeIf(b -> now - b.time() > Balance.SONAR_SCAN_TICKS * 3L);
            if (bs.isEmpty()) { it.remove(); continue; }
            Bearing best = null;
            for (Bearing b : bs.values()) if (best == null || b.level() > best.level()) best = b;
            Fix fix = null;
            List<Bearing> l = new ArrayList<>(bs.values());
            outer:
            for (int i = 0; i < l.size(); i++) for (int j = i + 1; j < l.size(); j++) {
                Fix f = cross(l.get(i), l.get(j), now, fixErr(fine, l.get(i).sensor(), l.get(j).sensor()));
                if (f != null) { fix = f; break outer; }
            }
            out.add(new Contact(best.kind(), best.bearing(), best.level(), best.sensor(), best.ear(), fix));
        }
        out.sort((a, b) -> Double.compare(b.level(), a.level()));
        return out;
    }

    public static List<Fix> ownerFixes(ServerLevel level, UUID owner) {
        List<Fix> out = new ArrayList<>();
        if (owner == null) return out;
        String pre = owner + "|", dim = "|" + level.dimension().location();
        for (String k : new ArrayList<>(BEARINGS.keySet())) {
            if (!k.startsWith(pre) || !k.endsWith(dim) || k.length() <= pre.length() + dim.length()) continue;
            int f;
            try { f = Integer.parseInt(k.substring(pre.length(), k.length() - dim.length())); } catch (NumberFormatException e) { continue; }
            for (Contact c : contacts(level, owner, f)) if (c.fix() != null) out.add(c.fix());
        }
        return out;
    }

    public static List<Fix> pingFixes(ServerLevel level, UUID owner, int freq) {
        List<Fix> l = PINGS.get(key(level, owner, freq));
        if (l == null) return List.of();
        long now = level.getGameTime();
        l.removeIf(f -> f.until() < now);
        return new ArrayList<>(l);
    }

    static Fix cross(Bearing a, Bearing b, long now) { return cross(a, b, now, Balance.SONAR_FIX_ERR); }

    static double fixErr(java.util.Set<Long> fine, long a, long b) {
        return ((fine.contains(a) ? Balance.SONAR_FIX_ERR_UP : Balance.SONAR_FIX_ERR) + (fine.contains(b) ? Balance.SONAR_FIX_ERR_UP : Balance.SONAR_FIX_ERR)) / 2.0;
    }

    static Fix cross(Bearing a, Bearing b, long now, double errPerBlock) {
        double d = Math.abs(((a.bearing() - b.bearing()) % 360.0 + 540.0) % 360.0 - 180.0);
        if (d < Balance.SONAR_FIX_MIN_DEG || d > 180.0 - Balance.SONAR_FIX_MIN_DEG) return null;
        double ax = Math.sin(Math.toRadians(a.bearing())), az = -Math.cos(Math.toRadians(a.bearing()));
        double bx = Math.sin(Math.toRadians(b.bearing())), bz = -Math.cos(Math.toRadians(b.bearing()));
        double den = ax * bz - az * bx;
        if (Math.abs(den) < 1e-6) return null;
        double dx = b.ear().x - a.ear().x, dz = b.ear().z - a.ear().z;
        double t = (dx * bz - dz * bx) / den, u = (dx * az - dz * ax) / den;
        if (t < 0 || u < 0) return null;
        double x = a.ear().x + ax * t, z = a.ear().z + az * t;
        return new Fix(x, (a.ear().y + b.ear().y) / 2.0, z, Math.max(2.0, errPerBlock * Math.max(t, u)), "fix", false, 0.0, now + Balance.SONAR_SCAN_TICKS * 3L, null);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || sl.getGameTime() % Balance.SHIP_NOISE_TICKS != 7 || !SableBridge.active()) return;
        String dim = "|" + sl.dimension().location();
        List<Sensor> live = new ArrayList<>();
        long now = sl.getGameTime();
        for (Map.Entry<String, Map<Long, Sensor>> e : SENSORS.entrySet()) {
            if (!e.getKey().endsWith(dim)) continue;
            for (Sensor s : e.getValue().values()) if (s.until() >= now) live.add(s);
        }
        if (live.isEmpty()) return;
        java.util.Set<UUID> done = new java.util.HashSet<>();
        for (Sensor s : live) {
            for (SableBridge.ShipInfo ship : SableBridge.shipsIntersecting(sl, new net.minecraft.world.phys.AABB(s.ear(), s.ear()).inflate(s.range()))) {
                if (!done.add(ship.id()) || !NavalLayer.inWater(sl, ship)) continue;
                double db = shipNoise(ship);
                if (db > 0.0) db = Math.max(1.0, db - Anechoic.noiseCut(sl, ship.id()));
                if (db > 0.0) NoiseNet.emit(sl, ship.id(), ship.worldCenter(), db, NoiseNet.Kind.ENGINE, null, ship.id());
            }
        }
    }

    public static double shipNoise(SableBridge.ShipInfo s) {
        double v = s.speed();
        if (stopped(s)) return 0.0;
        double size = 10.0 * Math.log10(Math.max(1.0, s.blockCount() / Balance.SHIP_NOISE_REF_BLOCKS));
        if (v < Balance.SILENT_SPEED) return Balance.SHIP_NOISE_SILENT + size;
        return Balance.SHIP_NOISE_BASE + Math.min(Balance.SHIP_NOISE_SPEED_MAX, Balance.SHIP_NOISE_PER_SPEED * v) + size;
    }

    public static boolean stopped(SableBridge.ShipInfo s) {
        Vec3 v = s.linearVelocity();
        return Math.hypot(v.x, v.z) < Balance.STOPPED_SPEED && Math.abs(v.y) < Balance.DEPTH_HOLD_SPEED;
    }

    public static double layerLoss(ServerLevel level, Vec3 ear, Vec3 src) {
        double de = NavalLayer.depthBelowSurface(level, ear), ds = NavalLayer.depthBelowSurface(level, src);
        return (de >= Balance.SONAR_LAYER_DEPTH) != (ds >= Balance.SONAR_LAYER_DEPTH) ? Balance.SONAR_LAYER_DB : 0.0;
    }
}
