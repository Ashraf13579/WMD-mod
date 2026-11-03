package com.ashraf.wmd;

import java.util.ArrayList;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Interceptors {

    private Interceptors() {}

    public record Shot(long id, long flightId, Vec3 from, Vec3 to, long start, int duration, int tier, boolean refined, double roll) {
        public Shot(long id, long flightId, Vec3 from, Vec3 to, long start, int duration, int tier, boolean refined) { this(id, flightId, from, to, start, duration, tier, refined, Math.random()); }
        public Shot(long id, long flightId, Vec3 from, Vec3 to, long start, int duration, int tier) { this(id, flightId, from, to, start, duration, tier, false); }
        public long arrive() { return start + duration; }
    }

    public static final int DEPLOY = 24;

    public static Vec3 flarePos(Vec3 m0, long shotId, int i, double dt) {
        long h = (shotId + 1) * 0x9E3779B97F4A7C15L + (i + 1) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        double a = ((h & 0xFFFFL) / 65535.0) * Math.PI * 2.0, sp = 0.55 + (((h >>> 16) & 0xFFL) / 255.0) * 0.6;
        double k = (1.0 - Math.exp(-0.05 * dt)) / 0.05;
        return new Vec3(m0.x + Math.cos(a) * sp * k, m0.y - 0.30 * k - 0.0025 * dt * dt, m0.z + Math.sin(a) * sp * k);
    }

    public static Vec3 decoyOf(Flight f, long shotId, long arrive, int counter) {
        Vec3 m0 = f.position(arrive - DEPLOY);
        if (counter == 1) return flarePos(m0, shotId, 0, DEPLOY);
        return m0.add(f.position(arrive)).scale(0.5);
    }

    public static double[] odds(Shot s, Flight f, int counter) {
        double base = Math.min(1.0, killChance(s.tier(), Math.max(1, f.build().airframe())) + (s.refined() ? Balance.INT_REFINED_BONUS : 0.0));
        if (Hypersonic.isHyper(f)) base = 0.0;
        boolean heat = s.tier() <= Balance.SEEKER_HEAT_MAX_TIER;
        double cut = counter == 0 ? 0.0 : (counter == 1) == heat ? Balance.COUNTER_MATCH : Balance.COUNTER_MISMATCH;
        return new double[] { base, cut };
    }

    static void announce(ServerLevel level, FlightManager mgr, Shot s) {
        Flight f = mgr.find(s.flightId());
        if (f == null) return;

        if (f.build().divert() && Evade.count(f.id()) < Balance.DIVERT_DODGES && Evade.offset(f.id(), s.arrive()).length() < Evade.DIST * 0.6) {
            Vec3 dir = f.basePosition(s.arrive() + 1.0).subtract(f.basePosition(s.arrive() - 1.0));
            Evade.Dodge d = Evade.plan(f.id(), s.arrive(), dir);
            Evade.add(f.id(), d);
            mgr.setDirty();
            PacketDistributor.sendToPlayersNear(level, null, s.to().x, s.to().y, s.to().z, 6000, new EvadePayload(f.id(), d.arrive(), d.x(), d.y(), d.z()));
            Wmd.LOGGER.info("Divert missile {} dodges the interceptor arriving at {}", f.id(), s.arrive());
        }
        int ctr = mgr.counterOf(s.flightId());
        if (ctr == 0) return;
        double[] o = odds(s, f, ctr);
        boolean fooled = s.roll() >= o[0] * (1.0 - o[1]) && s.roll() < o[0];
        PacketDistributor.sendToPlayersNear(level, null, s.to().x, s.to().y, s.to().z, 6000, new CounterPayload(s.id(), s.flightId(), ctr, fooled, s.arrive()));
    }

    public static Vec3 expected(Flight f, double t) {
        if (f.swerveAt() < 0 || f.hasLegs()) return f.position(t);
        Flight apparent = new Flight(f.id(), f.tier(), f.ox(), f.oy(), f.oz(), f.fx(), f.fz(), f.endY(), f.aimY(), f.start(), f.duration(), f.launcher(), f.ownerId(),
                f.prepared(), f.build(), f.fx(), f.fz(), -1);
        return apparent.position(t);
    }

    public static Shot plan(long shotId, Vec3 site, Flight f, long now, int tier) { return plan(shotId, site, f, now, tier, Balance.INT_MIN_ELEV, Balance.INT_MAX_ELEV); }

    public static Shot plan(long shotId, Vec3 site, Flight f, long now, int tier, java.util.UUID owner) {
        return plan(shotId, site, f, now, tier, Balance.INT_MIN_ELEV, Balance.INT_MAX_ELEV, rangeOf(tier, owner));
    }

    public static Shot plan(long shotId, Vec3 site, Flight f, long now, int tier, double minElev, double maxElev, java.util.UUID owner) {
        return plan(shotId, site, f, now, tier, minElev, maxElev, rangeOf(tier, owner));
    }

    public static double rangeBonus(java.util.UUID owner) {
        return owner != null && CommandCenter.hasModule(owner, CcModule.AIR_DEFENCE) ? Balance.CC_AIR_DEFENCE_RANGE_BONUS : 0.0;
    }

    public static double rangeOf(int tier, java.util.UUID owner) {
        return Balance.INT_RANGE[Math.max(1, Math.min(4, tier)) - 1] + rangeBonus(owner);
    }

    public static Shot plan(long shotId, Vec3 site, Flight f, long now, int tier, double minElev, double maxElev) {
        return plan(shotId, site, f, now, tier, minElev, maxElev, Balance.INT_RANGE[tier - 1]);
    }

    private static Shot plan(long shotId, Vec3 site, Flight f, long now, int tier, double minElev, double maxElev, double range) {
        double speed = Balance.INT_SPEED[tier - 1];
        double horizon = 40.0;
        if (f.isCruise()) { minElev = Math.min(minElev, Balance.CRUISE_INT_MIN_ELEV); horizon = 8.0; }
        for (int dt = Balance.INT_MIN_LEAD; dt <= Balance.INT_MAX_FLIGHT; dt += 4) {
            long t = now + dt;
            if (t > f.endTime() - 10) break;
            Vec3 p = expected(f, t);
            if (p.y < site.y + horizon) continue;
            double elev = Math.toDegrees(Math.atan2(p.y - site.y, Math.hypot(p.x - site.x, p.z - site.z)));
            if (elev < minElev || elev > maxElev) continue;
            double dist = site.distanceTo(p);
            if (dist > range) continue;
            if (dist / speed <= dt) return new Shot(shotId, f.id(), site, p, now, dt, tier);
        }
        return null;
    }

    public static double killChance(int interceptorTier, int airframeTier) {
        if (interceptorTier > airframeTier) return Balance.INT_KILL_HIGHER;
        if (interceptorTier == airframeTier) return Balance.INT_KILL_EQUAL;
        return Balance.INT_KILL_LOWER;
    }

    public static boolean chased(FlightManager mgr, long flightId) {
        for (Shot s : mgr.shots) if (s.flightId() == flightId) return true;
        return false;
    }

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        if (mgr.shots.isEmpty()) return;
        RandomSource rnd = level.random;
        for (Shot s : new ArrayList<>(mgr.shots)) {
            if (now < s.arrive()) continue;
            mgr.shots.remove(s);
            Flight f = null;
            for (Flight g : mgr.flights()) if (g.id() == s.flightId()) f = g;
            boolean kill = false;
            int fooled = 0;
            Vec3 at = s.to();
            if (f != null) {
                Vec3 real = f.position(s.arrive());
                if (real.distanceTo(s.to()) <= Balance.INT_KILL_RADIUS) {
                    at = real;
                    int ctr = mgr.counterOf(f.id());
                    double[] od = odds(s, f, ctr);
                    double base = od[0], cut = od[1];
                    double roll = s.roll();
                    kill = roll < base * (1.0 - cut);
                    if (!kill && ctr > 0 && roll < base) { fooled = ctr; at = decoyOf(f, s.id(), s.arrive(), ctr); }
                }
            }
            if (kill) { CcBattleLog.onIntercept(level, s.from(), f); mgr.kill(level, f); }
            if (f != null && fooled > 0) mgr.counters.remove(f.id());
            PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 6000, new InterceptFxPayload(at.x, at.y, at.z, kill, fooled));
            Wmd.LOGGER.info("Interception: interceptor tier {} vs flight {}: {}", s.tier(), s.flightId(), kill ? "KILL" : "miss");
        }
    }
}
