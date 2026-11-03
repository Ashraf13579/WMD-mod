package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SmokeScreens {

    private SmokeScreens() {}

    static final double SKIRT = 4.0;

    public record Cloud(int id, ResourceKey<Level> dim, Vec3 centre, long start, UUID owner) {

        double radius(long now) {
            if (now < start || over(now)) return 0.0;
            return Balance.SMOKE_RADIUS * Math.min(1.0, (now - start) / (Balance.SMOKE_GROW_SECONDS * 20.0));
        }
        boolean over(long now) { return now - start >= Balance.SMOKE_SECONDS * 20L; }
        double bottom() { return centre.y - SKIRT; }
        double top() { return centre.y + Balance.SMOKE_HEIGHT; }
    }

    private static final Map<Integer, Cloud> CLOUDS = new LinkedHashMap<>();
    private static int nextId = 1;

    static Cloud launch(ServerLevel level, Vec3 centre, UUID owner) {
        Cloud c;
        synchronized (SmokeScreens.class) {
            c = new Cloud(nextId++, level.dimension(), centre, level.getGameTime(), owner);
            CLOUDS.put(c.id(), c);
        }
        broadcast(level);
        return c;
    }

    static synchronized List<Cloud> of(Level level) {
        List<Cloud> l = new ArrayList<>();
        for (Cloud c : CLOUDS.values()) if (c.dim().equals(level.dimension())) l.add(c);
        return l;
    }

    public static synchronized boolean inside(Level level, Vec3 p) {
        if (CLOUDS.isEmpty() || level == null || p == null) return false;
        long now = level.getGameTime();
        for (Cloud c : CLOUDS.values()) {
            if (!c.dim().equals(level.dimension()) || p.y < c.bottom() || p.y > c.top()) continue;
            double r = c.radius(now);
            if (r > 0.0 && Math.hypot(p.x - c.centre().x, p.z - c.centre().z) <= r) return true;
        }
        return false;
    }

    public static synchronized boolean insideColumn(Level level, double x, double z) {
        if (CLOUDS.isEmpty() || level == null) return false;
        long now = level.getGameTime();
        for (Cloud c : CLOUDS.values()) {
            if (!c.dim().equals(level.dimension())) continue;
            double r = c.radius(now);
            if (r > 0.0 && Math.hypot(x - c.centre().x, z - c.centre().z) <= r) return true;
        }
        return false;
    }

    public static synchronized double entry(Level level, Vec3 a, Vec3 b) {
        if (CLOUDS.isEmpty() || level == null) return -1.0;
        long now = level.getGameTime();
        double len = a.distanceTo(b), best = -1.0;
        if (len < 1e-6) return inside(level, a) ? 0.0 : -1.0;
        Vec3 d = b.subtract(a).scale(1.0 / len);
        for (Cloud c : CLOUDS.values()) {
            if (!c.dim().equals(level.dimension())) continue;
            double r = c.radius(now);
            if (r <= 0.0) continue;

            double ox = a.x - c.centre().x, oz = a.z - c.centre().z, qa = d.x * d.x + d.z * d.z, qb = 2.0 * (ox * d.x + oz * d.z), qc = ox * ox + oz * oz - r * r;
            double t0, t1;
            if (qa < 1e-9) { if (qc > 0.0) continue; t0 = 0.0; t1 = len; }
            else {
                double disc = qb * qb - 4.0 * qa * qc;
                if (disc < 0.0) continue;
                double s = Math.sqrt(disc);
                t0 = (-qb - s) / (2.0 * qa);
                t1 = (-qb + s) / (2.0 * qa);
            }

            if (Math.abs(d.y) < 1e-9) { if (a.y < c.bottom() || a.y > c.top()) continue; }
            else {
                double h0 = (c.bottom() - a.y) / d.y, h1 = (c.top() - a.y) / d.y;
                t0 = Math.max(t0, Math.min(h0, h1));
                t1 = Math.min(t1, Math.max(h0, h1));
            }
            t0 = Math.max(t0, 0.0);
            t1 = Math.min(t1, len);
            if (t0 > t1) continue;
            if (best < 0.0 || t0 < best) best = t0;
        }
        return best;
    }

    @SubscribeEvent
    static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || sl.getGameTime() % 20 != 0) return;
        boolean changed;
        synchronized (SmokeScreens.class) {
            if (CLOUDS.isEmpty()) return;
            long now = sl.getGameTime();
            changed = CLOUDS.values().removeIf(c -> c.dim().equals(sl.dimension()) && c.over(now));
        }
        if (changed) broadcast(sl);
        if (of(sl).isEmpty()) return;
        for (LaserSpots.Spot s : LaserSpots.all())
            if (s.dim().equals(sl.dimension()) && inside(sl, s.pos())) LaserSpots.remove(s.id());
    }

    private static SmokePayload payload(ServerLevel level) {
        List<SmokePayload.Entry> l = new ArrayList<>();
        for (Cloud c : of(level)) {
            if (l.size() >= SmokePayload.MAX) break;
            l.add(new SmokePayload.Entry(c.id(), (float) c.centre().x, (float) c.centre().y, (float) c.centre().z, c.start()));
        }
        return new SmokePayload(List.copyOf(l));
    }

    static void broadcast(ServerLevel level) {
        SmokePayload p = payload(level);
        for (ServerPlayer sp : level.players()) PacketDistributor.sendToPlayer(sp, p);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, payload(sp.serverLevel()));
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, payload(sp.serverLevel()));
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        synchronized (SmokeScreens.class) { CLOUDS.clear(); }
    }
}
