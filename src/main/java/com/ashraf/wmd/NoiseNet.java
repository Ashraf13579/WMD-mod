package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class NoiseNet {

    private NoiseNet() {}

    public enum Kind { ENGINE, MACHINE, LAUNCH, TORPEDO, PING, BLAST, DECOY }

    public record Source(Object key, Vec3 pos, double db, Kind kind, UUID owner, UUID ship, long until) {}

    public record Heard(Source src, double level, double dist, double bearing) {}

    private static final Map<Level, LinkedHashMap<Object, Source>> SOURCES = Statics.server(new WeakHashMap<>());

    public static void emit(ServerLevel level, Object key, Vec3 pos, double db, Kind kind, UUID owner, UUID ship) {
        put(level, key, pos, db, kind, owner, ship, Balance.NOISE_TTL);
    }

    public static void pulse(ServerLevel level, Vec3 pos, double db, Kind kind, UUID owner, int ticks) {
        put(level, new Object(), pos, db, kind, owner, null, Math.max(1, ticks));
    }

    private static void put(ServerLevel level, Object key, Vec3 pos, double db, Kind kind, UUID owner, UUID ship, int ticks) {
        if (level == null || key == null || pos == null) return;
        LinkedHashMap<Object, Source> m = SOURCES.computeIfAbsent(level, l -> new LinkedHashMap<>());
        m.remove(key);
        m.put(key, new Source(key, pos, db, kind, owner, ship, level.getGameTime() + ticks));
        while (m.size() > Balance.NOISE_MAX) { Iterator<Object> it = m.keySet().iterator(); it.next(); it.remove(); }
    }

    public static void silence(ServerLevel level, Object key) {
        Map<Object, Source> m = SOURCES.get(level);
        if (m != null) m.remove(key);
    }

    public static double received(double db, double d) { return db - 20.0 * Math.log10(Math.max(1.0, d)); }

    public static List<Heard> heard(ServerLevel level, Vec3 ear, double range, double floorDb, UUID ownShip, Object ownKey) {
        List<Heard> out = new ArrayList<>();
        LinkedHashMap<Object, Source> m = SOURCES.get(level);
        if (m == null || ear == null) return out;
        long now = level.getGameTime();
        double r2 = range * range;
        for (Iterator<Source> it = m.values().iterator(); it.hasNext(); ) {
            Source s = it.next();
            if (s.until() < now) { it.remove(); continue; }
            if (s.key() == ownKey || (ownShip != null && ownShip.equals(s.ship()))) continue;
            double d2 = s.pos().distanceToSqr(ear);
            if (d2 > r2) continue;
            double d = Math.sqrt(d2), lv = received(s.db(), d);
            if (lv < floorDb) continue;
            double bearing = (Math.toDegrees(Math.atan2(s.pos().x - ear.x, -(s.pos().z - ear.z))) + 360.0) % 360.0;
            out.add(new Heard(s, lv, d, bearing));
        }
        out.sort((a, b) -> Double.compare(b.level(), a.level()));
        return out;
    }

    public static int count(ServerLevel level) {
        Map<Object, Source> m = SOURCES.get(level);
        return m == null ? 0 : m.size();
    }
}
