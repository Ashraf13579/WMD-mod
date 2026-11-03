package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class FlightIntel {

    static final double VIEW_RANGE = 4096.0;

    static final int MAX_VIEW = 256;

    private static final double KEEP = 1.1;

    static final double DR_TOL = 1.0, DR_REVEAL = 200.0;
    static final int DR_LOOK = 6, DR_REFRESH = 30, DR_BLEND = 12, DR_HORIZON = 120;

    private record Sent(Flight source, boolean full, int phase, Flight sent) {}

    private final Map<UUID, Map<Long, Sent>> views = new HashMap<>();

    static boolean swerving(List<Flight> flights, long now) {
        for (Flight f : flights) if (f.diverts() && f.swerveAt() == now) return true;
        return false;
    }

    void sync(ServerLevel level, List<Flight> flights, long now) {
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : level.players()) here.add(p.getUUID());
        views.keySet().retainAll(here);
        if (flights.isEmpty()) {
            boolean any = false;
            for (Map<Long, Sent> v : views.values()) if (!v.isEmpty()) { any = true; break; }
            if (!any) return;
        }
        Radars radars = new Radars(level, now);
        for (ServerPlayer p : level.players()) send(p, flights, now, radars, false);
    }

    void sendFull(ServerPlayer p, List<Flight> flights, long now) {
        views.remove(p.getUUID());
        send(p, flights, now, new Radars(p.serverLevel(), now), true);
    }

    private void send(ServerPlayer p, List<Flight> flights, long now, Radars radars, boolean full) {
        Map<Long, Sent> had = full ? new HashMap<>() : views.computeIfAbsent(p.getUUID(), k -> new HashMap<>());

        record Pick(Flight f, boolean full, double d) {}
        List<Pick> picks = new ArrayList<>();
        Set<UUID> team = CommandCenter.ownersTrusting(p.getUUID());
        for (Flight f : flights) {
            UUID owner = ownerOf(f);
            if (f.isOwnedBy(p.getUUID(), p.getGameProfile().getName()) || (owner != null && team.contains(owner))) { picks.add(new Pick(f, true, -1.0)); continue; }
            Vec3 pos = f.position(Mth.clamp(now, f.start(), f.endTime()));
            double keep = had.containsKey(f.id()) ? KEEP : 1.0;
            double dx = pos.x - p.getX(), dz = pos.z - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d <= VIEW_RANGE * keep || radars.sees(p.getUUID(), team, f, pos, keep)) picks.add(new Pick(f, false, d));
        }
        if (picks.size() > MAX_VIEW) {
            picks.sort((a, b) -> Double.compare(a.d(), b.d()));
            picks = new ArrayList<>(picks.subList(0, MAX_VIEW));
        }
        Map<Long, Sent> next = new HashMap<>();
        List<Flight> up = new ArrayList<>();
        for (Pick k : picks) {
            Flight f = k.f();
            boolean dr = !k.full() && f.isCruise();
            int phase = dr ? cruisePhase(f, now) : !k.full() && f.diverts() && now < f.swerveAt() ? 0 : 1;
            Sent s = had.get(f.id());
            if (s != null && s.source() == f && s.full() == k.full() && s.phase() == phase && !(dr && drifted(s.sent(), f, now))) { next.put(f.id(), s); continue; }
            Flight out = k.full() ? f : dr ? deadReckon(f, now, s == null ? null : s.sent()) : redact(f, now);
            next.put(f.id(), new Sent(f, k.full(), phase, out));
            up.add(out);
        }
        List<Long> gone = new ArrayList<>();
        for (Long id : had.keySet()) if (!next.containsKey(id)) gone.add(id);
        views.put(p.getUUID(), next);
        if (full || !up.isEmpty() || !gone.isEmpty()) PacketDistributor.sendToPlayer(p, new FlightsPayload(full, up, full ? List.of() : gone));
    }

    void tickCruise(List<Flight> flights, ServerLevel level, long now) {
        Map<Long, Flight> live = null;
        for (Flight f : flights) if (f.isCruise()) { if (live == null) live = new HashMap<>(); live.put(f.id(), f); }
        if (live == null) return;
        for (ServerPlayer p : level.players()) {
            Map<Long, Sent> v = views.get(p.getUUID());
            if (v == null || v.isEmpty()) continue;
            List<Flight> up = null;
            for (Map.Entry<Long, Sent> e : v.entrySet()) {
                Sent s = e.getValue();
                if (s.full()) continue;
                Flight f = live.get(e.getKey());
                if (f == null || now > f.endTime()) continue;
                int phase = cruisePhase(f, now);
                if (s.source() == f && phase == s.phase() && !drifted(s.sent(), f, now)) continue;
                Flight out = deadReckon(f, now, s.sent());
                e.setValue(new Sent(f, false, phase, out));
                if (up == null) up = new ArrayList<>();
                up.add(out);
            }
            if (up != null) PacketDistributor.sendToPlayer(p, new FlightsPayload(false, up, List.of()));
        }
    }

    static int cruisePhase(Flight f, long now) {
        CruiseRoute r = f.cruise();
        double t = Mth.clamp(now - f.start(), 0.0, (double) f.duration()), L = r.length();
        double s = t >= f.duration() ? L : Math.min(L, r.distanceAt(t));
        int prof = s < CruiseRoute.CLIMB_D ? 0 : s > L - CruiseRoute.DIVE_D ? 2 : 1;
        boolean known = L - s <= DR_REVEAL;
        return (known ? 1 : 0) | prof << 1 | r.segmentAt(s) << 3;
    }

    static boolean drifted(Flight sent, Flight f, long now) {
        if (sent == null || !sent.isDeadReckoned()) return true;
        CruiseRoute.Dr d = sent.cruise().dr;
        if (now - d.t0() >= DR_REFRESH) return true;
        double tol2 = DR_TOL * DR_TOL;
        return d.line(now).distanceToSqr(f.basePosition(now)) > tol2 || d.line(now + DR_LOOK).distanceToSqr(f.basePosition(now + DR_LOOK)) > tol2;
    }

    static Flight deadReckon(Flight f, long now, Flight prev) {
        long at = Mth.clamp(now, f.start(), f.endTime());
        Vec3 p = f.basePosition(at), v = f.basePosition(at + 2).subtract(p).scale(0.5);
        Vec3 e = Vec3.ZERO, w = Vec3.ZERO;
        boolean blend = false;
        if (prev != null && at >= prev.start() && at <= prev.endTime()) {
            Vec3 pd = prev.basePosition(at), vd = prev.basePosition(at + 1).subtract(prev.basePosition(at - 1)).scale(0.5);
            e = pd.subtract(p); w = vd.subtract(v);
            blend = e.lengthSqr() <= 64.0 * 64.0 && w.lengthSqr() <= 16.0;
            if (!blend) { e = Vec3.ZERO; w = Vec3.ZERO; }
        }
        boolean known = (cruisePhase(f, now) & 1) != 0;
        CruiseRoute.Dr d = new CruiseRoute.Dr(at, p.x, p.y, p.z, v.x, v.y, v.z, e.x, e.y, e.z, w.x, w.y, w.z, blend ? DR_BLEND : 0, known);
        int tx = known ? f.tx() : Mth.floor(p.x), tz = known ? f.tz() : Mth.floor(p.z);
        double endY = known ? f.endY() : p.y;
        int dur = known ? f.duration() : (int) Math.max(1L, at - f.start() + DR_HORIZON);
        return new Flight(f.id(), f.tier(), p.x, p.y, p.z, tx, tz, endY, endY, f.start(), dur, "", "", f.prepared(), visual(f.build()),
                tx, tz, -1L, tx, tz, dur, List.of(), 0, CruiseRoute.deadReckoned(d));
    }

    private static UUID ownerOf(Flight f) {
        if (f.ownerId().isEmpty()) return null;
        try { return UUID.fromString(f.ownerId()); } catch (IllegalArgumentException e) { return null; }
    }

    static Flight redact(Flight f, long now) {
        if (f.isCruise()) return deadReckon(f, now, null);
        MissileBuild b = visual(f.build());
        long at = Mth.clamp(now, f.start(), f.endTime());
        boolean decoy = f.diverts() && now < f.swerveAt();
        int ttx = decoy ? f.fx() : f.tx(), ttz = decoy ? f.fz() : f.tz();
        Vec3 l = f.basePosition(at);
        double vy = f.basePosition(Math.min(f.endTime(), at + 1)).y - l.y;
        double rem = Math.hypot(ttx + 0.5 - l.x, ttz + 0.5 - l.z), d = Math.max(500.0, rem);
        double top = Math.max(l.y + (vy > 0.05 ? 50.0 : 0.0), f.aimY() + 500.0);
        double oy = vy > 0.05 ? Math.min(l.y, f.aimY()) : top - Flight.cruiseAltitude(f.tier(), d) + Flight.LAUNCH_HEIGHT;
        if (Flight.plan(f.tier(), d, oy, f.aimY())[0] < top) oy = top - Flight.cruiseAltitude(f.tier(), d) + Flight.LAUNCH_HEIGHT;
        double[] p = Flight.plan(f.tier(), d, oy, f.aimY());
        int pdur = (int) Math.max(20L, Math.round(p[1] + p[2] + p[3]));
        double kp = pdur / (p[1] + p[2] + p[3]), ta = p[1] * kp, tc = p[2] * kp, td = p[3] * kp;
        long remTicks = Math.max(1L, f.endTime() - at);
        Flight ref = new Flight(f.id(), f.tier(), ttx + 0.5 - d, oy, ttz + 0.5, ttx, ttz, f.endY(), f.aimY(), 0L, pdur, "", "", f.prepared(), b,
                ttx, ttz, -1L, ttx, ttz, pdur, List.of(), 0, null);
        double t0;
        if (vy > 0.05) {
            double lo = 0.0, hi = ta;
            for (int i = 0; i < 16; i++) { double mid = (lo + hi) * 0.5; if (ref.planPoint(mid)[0] < l.y) lo = mid; else hi = mid; }
            t0 = (lo + hi) * 0.5;
        } else if (vy >= -0.05 && l.y >= f.aimY() + 500.0) {
            t0 = ta + tc * Mth.clamp(1.0 - (remTicks - td) / Math.max(1.0, tc), 0.0, 1.0);
        } else {
            double lo = ta + tc, hi = pdur;
            for (int i = 0; i < 16; i++) { double mid = (lo + hi) * 0.5; if (ref.planPoint(mid)[0] > l.y) lo = mid; else hi = mid; }
            t0 = (lo + hi) * 0.5;
        }
        double h = Mth.clamp(ref.planPoint(t0)[1], 0.0, 0.999);
        double k = Math.max(1.0E-3, (pdur - t0) / (double) remTicks);
        long fakeStart = at - Math.max(f.build().staged() && l.y - f.aimY() > 250.0 ? 200L : 0L, Math.round(t0));
        int fakeDur = (int) Math.max(1L, f.endTime() - fakeStart);
        double s = fakeStart + t0;
        return new Flight(f.id(), f.tier(), ttx + 0.5 - d, oy, ttz + 0.5, ttx, ttz, f.endY(), f.aimY(), fakeStart, fakeDur, "", "", f.prepared(), b, ttx, ttz, -1L, ttx, ttz, pdur, List.of(new Flight.Leg(at, s, k, l.x, l.z, h)), 0, null);

    }

    static MissileBuild visual(MissileBuild b) {
        String w = b.warheadType();
        boolean keep = b.isInterceptor() || Satellites.isSatellite(b) || Cubesat.isCubesat(b) || Asat.isAsat(b) || "rod".equals(w);
        return new MissileBuild(b.airframe(), b.motor(), b.guidance(), b.warhead(), keep ? w : "he", false, false, false, b.staged(), b.boosters(), b.kind(), b.camPod());
    }

    static final class Radars {
        private final ServerLevel level;
        private final long now;
        private final Map<UUID, List<BlockPos>> byOwner = new HashMap<>();
        private final Map<UUID, Double> reach = new HashMap<>();

        Radars(ServerLevel level, long now) { this.level = level; this.now = now; }

        private List<BlockPos> of(UUID owner) {
            return byOwner.computeIfAbsent(owner, o -> TrackNet.radarsOf(level, o, now));
        }

        private double reachOf(UUID owner) {
            return reach.computeIfAbsent(owner, o -> Recon.tracking(level, o)
                    ? Math.max(Balance.RADAR_DETECT_RANGE, TrackNet.SAT_REACH + Interceptors.rangeBonus(o)) : Balance.RADAR_DETECT_RANGE);
        }

        boolean sees(UUID viewer, Set<UUID> team, Flight f, Vec3 pos, double keep) {
            return seesPoint(viewer, team, pos.x, pos.z, f.isCruise() ? Balance.CRUISE_RADAR_RANGE : Double.MAX_VALUE, keep);
        }

        boolean seesPoint(UUID viewer, Set<UUID> team, double x, double z, double cap, double keep) {
            if (covered(viewer, x, z, cap, keep)) return true;
            for (UUID o : team) if (covered(o, x, z, cap, keep)) return true;
            return false;
        }

        private boolean covered(UUID owner, double x, double z, double cap, double keep) {
            List<BlockPos> rs = of(owner);
            if (rs.isEmpty()) return false;
            double r = Math.min(cap, reachOf(owner)) * keep + 200.0;
            double r2 = r * r;
            for (BlockPos b : rs) {
                double dx = x - (b.getX() + 0.5), dz = z - (b.getZ() + 0.5);
                if (dx * dx + dz * dz <= r2) return true;
            }
            return false;
        }
    }

    static boolean friendly(ServerPlayer p, UUID owner, Set<UUID> team) {
        return owner != null && (owner.equals(p.getUUID()) || team.contains(owner));
    }

    static boolean near(ServerPlayer p, double x, double z, double keep) {
        double dx = x - p.getX(), dz = z - p.getZ(), r = VIEW_RANGE * keep;
        return dx * dx + dz * dz <= r * r;
    }
}
