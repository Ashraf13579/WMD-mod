package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class LaserSpots {

    private LaserSpots() {}

    public static final int SRC_DEV = 0, SRC_TRIPOD = 1, SRC_SATELLITE = 2, SRC_DRONE = 3;

    public record Spot(int id, UUID owner, ResourceKey<Level> dim, Vec3 pos, int source, long seen, Vec3 origin) {
        Spot moved(Vec3 p, Vec3 o, long now) { return new Spot(id, owner, dim, p, source, now, o != null ? o : origin); }
    }

    private static final Map<Integer, Spot> SPOTS = Statics.server(new LinkedHashMap<>());
    private static int nextId = 1;

    public static synchronized int light(ServerLevel level, UUID owner, Vec3 pos, int source) { return light(level, owner, pos, source, null); }

    public static synchronized int light(ServerLevel level, UUID owner, Vec3 pos, int source, Vec3 origin) {
        int id = nextId++;
        SPOTS.put(id, new Spot(id, owner, level.dimension(), pos, source, level.getGameTime(), origin));
        return id;
    }

    public static synchronized boolean refresh(ServerLevel level, int id, Vec3 pos) { return refresh(level, id, pos, null); }

    public static synchronized boolean refresh(ServerLevel level, int id, Vec3 pos, Vec3 origin) {
        Spot s = SPOTS.get(id);
        if (s == null || !s.dim().equals(level.dimension())) return false;
        SPOTS.put(id, s.moved(pos, origin, level.getGameTime()));
        return true;
    }

    public static synchronized void remove(int id) { SPOTS.remove(id); }

    public static synchronized void clear(UUID owner) { SPOTS.values().removeIf(s -> s.owner().equals(owner)); }

    public static synchronized Spot get(ServerLevel level, int id) {
        Spot s = SPOTS.get(id);
        return s != null && s.dim().equals(level.dimension()) && alive(s, level.getGameTime()) ? s : null;
    }

    public static synchronized Spot nearest(ServerLevel level, UUID owner, double x, double z, double r) {
        Spot best = null;
        double bd = r * r;
        long now = level.getGameTime();
        for (Spot s : SPOTS.values()) {
            if (!s.owner().equals(owner) || !s.dim().equals(level.dimension()) || !alive(s, now)) continue;
            double dx = s.pos().x - x, dz = s.pos().z - z, d = dx * dx + dz * dz;
            if (d <= bd) { bd = d; best = s; }
        }
        return best;
    }

    public static synchronized List<Spot> all() { return new ArrayList<>(SPOTS.values()); }

    private static boolean alive(Spot s, long now) { return s.source() == SRC_DEV || now - s.seen() <= Balance.LASER_SPOT_TTL; }

    static synchronized void draw(ServerLevel level) {
        if (level.getGameTime() % 4 != 0 || SPOTS.isEmpty()) return;
        var red = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 2.5F);
        long now = level.getGameTime();
        for (Spot s : SPOTS.values()) {
            if (!s.dim().equals(level.dimension()) || !alive(s, now)) continue;
            for (net.minecraft.server.level.ServerPlayer p : level.players())
                if (p.distanceToSqr(s.pos()) < 256.0 * 256.0) level.sendParticles(p, red, true, s.pos().x, s.pos().y + 0.08, s.pos().z, 4, 0.08, 0.03, 0.08, 0.0);
        }
    }

    static synchronized void expire(ServerLevel level) {
        long now = level.getGameTime();
        SPOTS.values().removeIf(s -> s.dim().equals(level.dimension()) && !alive(s, now));
    }
}
