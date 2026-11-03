package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ProjectilePath {

    private ProjectilePath() {}

    public enum Kind { SHIP, DEBRIS }

    public record PathHit(Vec3 pos, Kind kind, UUID shipId, String shipName, String method) {}

    public record Shot(UUID owner, UUID launchShip, boolean armed) {}

    public interface Test {
        boolean idle(ServerLevel level);
        PathHit first(ServerLevel level, Vec3 from, Vec3 to, Shot shot, Budget budget);
    }

    public static final class Budget {
        long tick = Long.MIN_VALUE;
        int segments, exact;
        boolean deferred;

        boolean takeSegment() { if (segments >= Balance.SHIP_PATH_MAX_SEGMENTS) return false; segments++; return true; }

        public boolean takeExact() { if (exact >= Balance.SHIP_PATH_MAX_EXACT) return false; exact++; return true; }

        public void defer() { deferred = true; }
    }

    private static final List<Test> TESTS = new CopyOnWriteArrayList<>();

    public static void register(Test t) { if (t != null && !TESTS.contains(t)) TESTS.add(t); }

    public static final Test SHIPS = new Test() {
        @Override
        public boolean idle(ServerLevel level) { return !ShipStrike.anyShips(level); }

        @Override
        public PathHit first(ServerLevel level, Vec3 from, Vec3 to, Shot shot, Budget budget) {
            List<SableBridge.ShipInfo> near = SableBridge.shipsAlongSegment(level, from, to);
            if (near.isEmpty()) return null;
            List<SableBridge.ShipInfo> cands = new ArrayList<>(near.size());
            for (SableBridge.ShipInfo s : near) {
                if (!shot.armed() && s.id().equals(shot.launchShip())) continue;
                if (ShipStrike.spare(level, s.id(), shot.owner())) continue;
                cands.add(s);
            }
            if (cands.isEmpty()) return null;
            if (!budget.takeExact()) { budget.defer(); return null; }
            try { return SableImpact.exact(level, from, to, cands); }
            catch (Throwable t) { SableBridge.fail(t); return null; }
        }
    };

    static { TESTS.add(SHIPS); }

    private static final class Book {
        final Budget budget = new Budget();

        final Map<Long, Long> shellT = new HashMap<>(), flightT = new HashMap<>();

        final Map<Long, UUID> shellShip = new HashMap<>(), flightShip = new HashMap<>();
        int shellRot, flightRot;
    }

    private static final UUID NONE = new UUID(0L, 0L);
    private static final Map<ResourceKey<Level>, Book> BOOKS = new HashMap<>();

    private static Book book(ServerLevel level) {
        Book b = BOOKS.computeIfAbsent(level.dimension(), k -> new Book());
        long now = level.getGameTime();
        if (b.budget.tick != now) { b.budget.tick = now; b.budget.segments = 0; b.budget.exact = 0; }
        return b;
    }

    private static boolean active(ServerLevel level, Book b) {
        for (Test t : TESTS) {
            try { if (!t.idle(level)) return true; } catch (Throwable ignored) {}
        }
        if (!b.shellT.isEmpty() || !b.flightT.isEmpty()) { b.shellT.clear(); b.flightT.clear(); b.shellShip.clear(); b.flightShip.clear(); }
        return false;
    }

    private static PathHit first(ServerLevel level, Vec3 from, Vec3 to, Shot shot, Budget budget) {
        PathHit best = null;
        double bd = Double.MAX_VALUE;
        for (Test t : TESTS) {
            if (t.idle(level)) continue;
            PathHit h = t.first(level, from, to, shot, budget);
            if (h == null) continue;
            double d = h.pos().distanceToSqr(from);
            if (d < bd) { bd = d; best = h; }
        }
        return best;
    }

    private static boolean real(UUID o) { return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L); }

    record ShellHit(ArtShell shell, PathHit hit) {}

    static List<ShellHit> shells(ServerLevel level, List<ArtShell> shells, long now) {
        if (shells.isEmpty()) return List.of();
        Book b = book(level);
        if (!active(level, b)) return List.of();
        List<ShellHit> out = null;
        int n = shells.size();
        int start = Math.floorMod(b.shellRot++, n);
        for (int k = 0; k < n; k++) {
            ArtShell s = shells.get((start + k) % n);
            if (now <= s.start()) continue;
            Long done = b.shellT.get(s.id());
            long t0 = Math.max(s.start(), done != null ? done : now - 1), t1 = Math.min(now, s.endTime());
            if (t0 >= t1) continue;
            if (!b.budget.takeSegment()) break;
            Vec3 from = s.position(t0), to = s.position(t1), origin = new Vec3(s.ox(), s.oy(), s.oz());
            boolean armed = t1 - s.start() >= Balance.SHIP_PATH_ARM_TICKS && to.distanceTo(origin) >= Balance.SHIP_PATH_ARM_BLOCKS;
            UUID ship = null;
            if (!armed) {
                ship = b.shellShip.get(s.id());
                if (ship == null) {
                    ship = s.gunPos() != 0L ? SableBridge.shipIdAt(level, BlockPos.of(s.gunPos())) : null;
                    if (ship == null) ship = shipAround(level, origin);
                    b.shellShip.put(s.id(), ship == null ? NONE : ship);
                }
                if (NONE.equals(ship)) ship = null;
            }
            b.budget.deferred = false;
            PathHit h = first(level, from, to, new Shot(real(s.owner()) ? s.owner() : null, ship, armed), b.budget);
            if (b.budget.deferred && h == null) continue;
            b.shellT.put(s.id(), t1);
            if (h != null) {
                if (out == null) out = new ArrayList<>(2);
                out.add(new ShellHit(s, h));
            }
        }
        if (now % 100 == 0) {
            Set<Long> live = new HashSet<>();
            for (ArtShell s : shells) live.add(s.id());
            b.shellT.keySet().retainAll(live);
            b.shellShip.keySet().retainAll(live);
        }
        return out == null ? List.of() : out;
    }

    record FlightHit(Flight flight, PathHit hit) {}

    static boolean strikes(Flight f) {
        return f != null && f.build() != null && !f.build().isInterceptor() && !Satellites.toOrbit(f.build()) && !Asat.isAsat(f.build()) && !Cubesat.isCubesat(f.build());
    }

    static List<FlightHit> flights(ServerLevel level, List<Flight> flights, long now) {
        if (flights.isEmpty()) return List.of();
        Book b = book(level);
        if (!active(level, b)) return List.of();
        List<FlightHit> out = null;
        int n = flights.size();
        int start = Math.floorMod(b.flightRot++, n);
        for (int k = 0; k < n; k++) {
            Flight f = flights.get((start + k) % n);
            if (now <= f.start() || !strikes(f)) continue;
            Long done = b.flightT.get(f.id());
            long t0 = Math.max(f.start(), done != null ? done : now - 1), t1 = Math.min(now, f.endTime());
            if (t0 >= t1) continue;
            if (!b.budget.takeSegment()) break;
            Vec3 from = f.position(t0), to = f.position(t1), origin = new Vec3(f.ox(), f.oy(), f.oz());
            boolean armed = t1 - f.start() >= Balance.SHIP_PATH_ARM_TICKS && to.distanceTo(origin) >= Balance.SHIP_PATH_ARM_BLOCKS;
            UUID ship = null;
            if (!armed) {
                ship = b.flightShip.get(f.id());
                if (ship == null) {
                    ship = shipAround(level, origin);
                    b.flightShip.put(f.id(), ship == null ? NONE : ship);
                }
                if (NONE.equals(ship)) ship = null;
            }
            b.budget.deferred = false;
            UUID owner = level.getServer() == null ? null : CcBattleLog.ownerOf(level.getServer(), f);
            PathHit h = first(level, from, to, new Shot(owner, ship, armed), b.budget);
            if (b.budget.deferred && h == null) continue;
            b.flightT.put(f.id(), t1);
            if (h != null) {
                if (out == null) out = new ArrayList<>(2);
                out.add(new FlightHit(f, h));
            }
        }
        if (now % 100 == 0) {
            Set<Long> live = new HashSet<>();
            for (Flight f : flights) live.add(f.id());
            b.flightT.keySet().retainAll(live);
            b.flightShip.keySet().retainAll(live);
        }
        return out == null ? List.of() : out;
    }

    private static UUID shipAround(ServerLevel level, Vec3 p) {
        List<SableBridge.ShipInfo> l = SableBridge.shipsIntersecting(level, new AABB(p, p).inflate(4.0));
        SableBridge.ShipInfo best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : l) {
            double d = s.worldCenter().distanceToSqr(p);
            if (d < bd) { bd = d; best = s; }
        }
        return best == null ? null : best.id();
    }

    static void log(String what, PathHit h) {
        Wmd.LOGGER.info("WMD path hit: {} struck {} '{}' at {} {} {} (found by {})", what, h.kind(), h.shipName(), Math.round(h.pos().x), Math.round(h.pos().y),
                Math.round(h.pos().z), h.method());
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { BOOKS.clear(); }
}
