package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class SeekerGuidance {

    private SeekerGuidance() {}

    private static final Map<Long, Integer> FOLLOW = Statics.server(new HashMap<>());

    private static final Map<Long, BlockPos> SHIP_TRACK = Statics.server(new HashMap<>());

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        if (now % 20 == 0) { LaserSpots.expire(level); HeatLog.expire(level); FOLLOW.keySet().removeIf(id -> !FlightManager.alive(level.getServer(), id)); SHIP_TRACK.keySet().removeIf(id -> !FlightManager.alive(level.getServer(), id)); }
        List<Flight> list = mgr.flights();
        for (int i = 0; i < list.size(); i++) {
            Flight f = list.get(i);
            String seeker = f.build().seeker();
            if (seeker.isEmpty() || Satellites.toOrbit(f.build()) || f.build().isInterceptor() || now < f.start() || now >= f.endTime() - 2) continue;
            if (Uplink.lockActive(mgr, f)) continue;
            if ((f.uflags() & Flight.U_SEEK_LOST) != 0) continue;
            if ((f.uflags() & Flight.U_SEEK_DONE) == 0) {
                if (f.isCruise()) {
                    if (f.isDeadReckoned()) continue;
                    double s = f.cruise().distanceAt(now - f.start()), last = f.cruise().lastCorner() - f.cruise().lastTurnReach() - 5.0;
                    if (s < last - LOOK_AHEAD) continue;
                    if (s > last) { done(mgr, f); continue; }
                    acquire(level, mgr, f, seeker, now);
                    continue;
                }
                if (f.endTime() - now > Balance.SEEKER_SECONDS * 20L && !f.build().hypersonic()) continue;
                acquire(level, mgr, f, seeker, now);
                continue;
            }
            if (f.isCruise()) continue;
            if ((f.uflags() & Flight.U_SEEK_LOCK) != 0 && seeker.equals(MissileBuild.SEEK_LASER) && (now + f.id()) % Balance.LASER_FOLLOW == 0) follow(level, mgr, f, now);
            if ((f.uflags() & Flight.U_SEEK_LOCK) != 0 && SHIP_TRACK.containsKey(f.id()) && (now + f.id()) % 20 == 10) followShip(level, mgr, f, seeker, now);
            if ((f.uflags() & Flight.U_SEEK_LOCK) != 0 && seeker.equals(MissileBuild.SEEK_HEAT) && (now + f.id()) % 20 == 0
                    && (SmokeScreens.insideColumn(level, f.tx() + 0.5, f.tz() + 0.5) || Camo.thermalHidesColumn(level, f.tx(), f.tz()))) {
                Wmd.LOGGER.info("Seeker: flight {} (heat) lost its target in smoke / under a thermal net", f.id());
                lost(level, mgr, f, now);
            }
        }
    }

    static void release(FlightManager mgr, long id) {
        FOLLOW.remove(id);
        if (SHIP_TRACK.remove(id) != null) mgr.shipLocks.remove(id);
    }

    static boolean tracksShip(long id) { return SHIP_TRACK.containsKey(id); }

    private static void acquire(ServerLevel level, FlightManager mgr, Flight f, String seeker, long now) {
        UUID owner = Uplink.owner(level.getServer(), f);
        double ax = f.tx() + 0.5, az = f.tz() + 0.5, r = Balance.SEEKER_RADIUS;
        int[] aim = null;
        String what = "";
        switch (seeker) {
            case MissileBuild.SEEK_LASER -> {
                LaserSpots.Spot s = owner == null ? null : LaserSpots.nearest(level, owner, ax, az, r);
                if (s != null) { aim = new int[] { (int) Math.floor(s.pos().x), (int) Math.floor(s.pos().z) }; FOLLOW.put(f.id(), s.id()); what = "laser spot " + s.id(); }
            }
            case MissileBuild.SEEK_HEAT -> {
                HeatLog.Hot h = HeatLog.nearest(level, ax, az, r, owner);
                if (h != null) { aim = worldAim(level, mgr, f, h.pos()); what = "hot machine " + h.pos().toShortString(); }
            }
            case MissileBuild.SEEK_ARM -> {
                BlockPos p = RadarEmit.nearest(level, ax, az, r, owner);
                if (p != null) { aim = worldAim(level, mgr, f, p); what = "radar " + p.toShortString(); }
            }
            default -> {}
        }
        boolean ok = aim != null && (f.isCruise() ? cruiseLock(level, mgr, f, aim[0], aim[1], now) : steer(level, mgr, f, aim[0], aim[1], now, Flight.U_SEEK_DONE | Flight.U_SEEK_LOCK));
        if (!ok) { done(mgr, f); return; }
        Wmd.LOGGER.info("Seeker: flight {} ({}) locked on {}", f.id(), seeker, what);
    }

    private static int[] worldAim(ServerLevel level, FlightManager mgr, Flight f, BlockPos p) {
        java.util.UUID ship = SableBridge.shipIdAt(level, p);
        if (ship == null) return new int[] { p.getX(), p.getZ() };
        SHIP_TRACK.put(f.id(), p.immutable());
        mgr.shipLocks.put(f.id(), ship);
        Vec3 w = SableBridge.worldPos(level, p);
        return new int[] { (int) Math.floor(w.x), (int) Math.floor(w.z) };
    }

    private static void followShip(ServerLevel level, FlightManager mgr, Flight f, String seeker, long now) {
        BlockPos p = SHIP_TRACK.get(f.id());
        if (p == null || SableBridge.shipIdAt(level, p) == null) return;
        if (seeker.equals(MissileBuild.SEEK_ARM) && !(level.getBlockEntity(p) != null && RadarEmit.emitting(level.getBlockEntity(p)))) return;
        Vec3 w = SableBridge.worldPos(level, p);
        int x = (int) Math.floor(w.x), z = (int) Math.floor(w.z);
        if (x != f.tx() || z != f.tz()) steer(level, mgr, f, x, z, now, 0);
        Flight nf = mgr.find(f.id());
        if (nf != null) mgr.shipEnd(level, nf);
    }

    private static void follow(ServerLevel level, FlightManager mgr, Flight f, long now) {
        Integer id = FOLLOW.get(f.id());
        LaserSpots.Spot s = id == null ? null : LaserSpots.get(level, id);
        if (s == null && id == null) {
            UUID owner = Uplink.owner(level.getServer(), f);
            s = owner == null ? null : LaserSpots.nearest(level, owner, f.tx() + 0.5, f.tz() + 0.5, Balance.SEEKER_RADIUS);
            if (s != null) FOLLOW.put(f.id(), s.id());
        }
        if (s == null) { lost(level, mgr, f, now); return; }
        int x = (int) Math.floor(s.pos().x), z = (int) Math.floor(s.pos().z);
        if (x == f.tx() && z == f.tz()) return;
        steer(level, mgr, f, x, z, now, 0);
    }

    static void lost(ServerLevel level, FlightManager mgr, Flight f, long now) {
        FOLLOW.remove(f.id());
        double reach = Uplink.reach(f, now);
        double a = level.random.nextDouble() * Math.PI * 2.0;
        double d = Balance.DIVERT_MIN + level.random.nextDouble() * (Balance.DIVERT_MAX - Balance.DIVERT_MIN);
        Vec3 at = f.basePosition(now);
        for (int tries = 0; tries < 6; tries++, d *= 0.6) {
            int x = f.tx() + (int) Math.round(Math.cos(a) * d), z = f.tz() + (int) Math.round(Math.sin(a) * d);
            if (Math.hypot(x + 0.5 - at.x, z + 0.5 - at.z) > reach) continue;
            if (steer(level, mgr, f, x, z, now, Flight.U_SEEK_LOST)) {
                Wmd.LOGGER.info("Seeker: flight {} lost its guidance, diverts {} blocks to {} {}", f.id(), Math.round(d), x, z);
                return;
            }
        }
        mgr.flights().replaceAll(x -> x.id() == f.id() ? x.withFlags(x.uflags() | Flight.U_SEEK_LOST) : x);
        mgr.setDirty();
    }

    private static void done(FlightManager mgr, Flight f) {
        mgr.flights().replaceAll(x -> x.id() == f.id() ? x.withFlags(x.uflags() | Flight.U_SEEK_DONE) : x);
        mgr.setDirty();
    }

    static final double LOOK_AHEAD = 120.0;

    private static boolean cruiseLock(ServerLevel level, FlightManager mgr, Flight f, int x, int z, long now) {
        CruiseRoute old = f.cruise();
        double s = Math.min(old.length(), old.distanceAt(now - f.start()));
        CruiseRoute r = old.withEnd(x, z);
        double[] g0 = old.ground(s), g1 = r.ground(s);
        if (Math.hypot(g0[0] - g1[0], g0[1] - g1[1]) > 1.0) return false;
        if (r.length() > MissileStats.range(f.build()) * 1.05) return false;
        r = r.withAlt(r.sampleAltitude(level));
        int dur = r.ticks();
        if (f.start() + dur <= now + 5) return false;
        Flight nf = f.withCruiseEnd(x, z, r, dur, CruiseRoute.groundAt(level, x, z)).withFlags(f.uflags() | Flight.U_SEEK_DONE | Flight.U_SEEK_LOCK);
        mgr.flights().replaceAll(q -> q.id() == f.id() ? nf : q);
        mgr.setDirty();
        mgr.syncSoon();
        return true;
    }

    private static boolean steer(ServerLevel level, FlightManager mgr, Flight f, int x, int z, long now, int set) {
        Vec3 at = f.basePosition(now);
        double k = Uplink.legK(f, now, Math.hypot(x + 0.5 - at.x, z + 0.5 - at.z));
        if (k < 0) return false;
        return mgr.releg(level, f, x, z, k, set, 0) != null;
    }
}
