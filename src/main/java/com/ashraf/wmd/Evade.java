package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class Evade {

    private Evade() {}

    public static final int LEAD = 30, RECOVER = 50;
    public static final double DIST = 110.0, LIFT = 25.0;

    public record Dodge(long arrive, double x, double y, double z) {}

    private static final Map<Long, List<Dodge>> DODGES = new ConcurrentHashMap<>();

    public static boolean any(long flightId) { return DODGES.containsKey(flightId); }
    public static int count(long flightId) { List<Dodge> l = DODGES.get(flightId); return l == null ? 0 : l.size(); }
    public static List<Dodge> of(long flightId) { return DODGES.getOrDefault(flightId, List.of()); }
    public static void remove(long flightId) { DODGES.remove(flightId); }

    static void clearAll() { DODGES.clear(); }

    public static void add(long flightId, Dodge d) {
        List<Dodge> l = DODGES.computeIfAbsent(flightId, k -> new CopyOnWriteArrayList<>());
        for (Dodge e : l) if (e.arrive() == d.arrive()) return;
        l.add(d);
    }

    public static double bump(long arrive, double t) {
        if (t <= arrive - LEAD || t >= arrive + RECOVER) return 0.0;
        double u = t <= arrive ? (t - (arrive - LEAD)) / LEAD : 1.0 - (t - arrive) / RECOVER;
        u = Mth.clamp(u, 0.0, 1.0);
        return u * u * (3.0 - 2.0 * u);
    }

    public static Vec3 offset(long flightId, double t) {
        List<Dodge> l = DODGES.get(flightId);
        if (l == null) return Vec3.ZERO;
        double x = 0, y = 0, z = 0;
        for (Dodge d : l) {
            double b = bump(d.arrive(), t);
            if (b > 0.0) { x += d.x() * b; y += d.y() * b; z += d.z() * b; }
        }
        return new Vec3(x, y, z);
    }

    public static Dodge plan(long flightId, long arrive, Vec3 dir) {
        double hx = dir.x, hz = dir.z, len = Math.hypot(hx, hz);
        if (len < 0.05) { hx = 1.0; hz = 0.0; len = 1.0; }
        double side = (((flightId * 31L + arrive) & 1L) == 0L) ? 1.0 : -1.0;
        return new Dodge(arrive, -hz / len * DIST * side, LIFT, hx / len * DIST * side);
    }

    public static ListTag save(Iterable<Flight> flights) {
        ListTag out = new ListTag();
        for (Flight f : flights) for (Dodge d : of(f.id())) {
            CompoundTag t = new CompoundTag();
            t.putLong("id", f.id()); t.putLong("arrive", d.arrive()); t.putDouble("x", d.x()); t.putDouble("y", d.y()); t.putDouble("z", d.z());
            out.add(t);
        }
        return out;
    }

    public static void load(ListTag list) {
        for (Tag tag : list) {
            CompoundTag t = (CompoundTag) tag;
            add(t.getLong("id"), new Dodge(t.getLong("arrive"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z")));
        }
    }
}
