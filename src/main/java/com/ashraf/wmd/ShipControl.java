package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ShipControl {

    private ShipControl() {}

    public static final int SRC_TELEGRAPH = 1, SRC_HELM = 2, SRC_COCKPIT = 3;

    public static final int SRC_AUTOPILOT = 4;
    public static final int STEPS = Balance.PROP_STEP_FRAC.length;

    public record Order(int step, float rudder, float planes, float roll, int source, BlockPos from, long changed, long seen, float thr, boolean afterburner) {

        public double throttle() { return Float.isNaN(thr) ? Balance.PROP_STEP_FRAC[step] : thr; }
    }

    public record Answer(int step, int engines, int running, double thrust, double rudder, double planes, long time) {}

    private record Report(int step, boolean running, double thrust, double rudder, double planes, long seen) {}

    private static final Map<Level, Map<UUID, Map<BlockPos, Order>>> ORDERS = Statics.server(new WeakHashMap<>());
    private static final Map<Level, Map<UUID, Map<BlockPos, Report>>> REPORTS = Statics.server(new WeakHashMap<>());

    private static final Map<Level, Map<UUID, Long>> BOOSTS = Statics.server(new WeakHashMap<>());

    private record Bow(float axis, long seen) {}
    private static final Map<Level, Map<UUID, Bow>> BOWS = Statics.server(new WeakHashMap<>());

    public static void publishBow(ServerLevel level, UUID ship, float axis) {
        if (level == null || ship == null) return;
        BOWS.computeIfAbsent(level, l -> new HashMap<>()).put(ship, new Bow(clamp1(axis), level.getGameTime()));
    }

    public static float bow(ServerLevel level, UUID ship) {
        Map<UUID, Bow> m = BOWS.get(level);
        Bow b = m == null || ship == null ? null : m.get(ship);
        if (b == null) return 0.0F;
        if (level.getGameTime() - b.seen() > Balance.PROP_INPUT_TTL) { m.remove(ship); return 0.0F; }
        return b.axis();
    }

    public static int clampStep(int step) { return Math.max(0, Math.min(STEPS - 1, step)); }

    public static int stepOf(double frac) {
        int best = Balance.PROP_STEP_STOP;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < STEPS; i++) {
            double d = Math.abs(Balance.PROP_STEP_FRAC[i] - frac);
            if (d < bd - 1.0E-9) { bd = d; best = i; }
        }
        return best;
    }

    public static void publish(ServerLevel level, UUID ship, BlockPos from, int source, int step, float rudder, float planes, float roll, long changed) {
        if (level == null || ship == null || from == null) return;
        Order o = new Order(clampStep(step), clamp1(rudder), clamp1(planes), clamp1(roll), source, from.immutable(), changed, level.getGameTime(), Float.NaN, false);
        ORDERS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(ship, s -> new HashMap<>()).put(o.from(), o);
    }

    public static void publishJet(ServerLevel level, UUID ship, BlockPos from, int step, float rudder, float planes, float roll, float throttle, boolean afterburner, long changed) {
        if (level == null || ship == null || from == null) return;
        float t = Float.isFinite(throttle) ? Math.max(0.0F, Math.min(1.0F, throttle)) : 0.0F;
        Order o = new Order(clampStep(step), clamp1(rudder), clamp1(planes), clamp1(roll), SRC_COCKPIT, from.immutable(), changed, level.getGameTime(), t, afterburner);
        ORDERS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(ship, s -> new HashMap<>()).put(o.from(), o);
    }

    public static void publishJet(ServerLevel level, UUID ship, BlockPos from, int step, float rudder, float planes, float roll, float throttle, boolean afterburner, long changed, int source) {
        if (level == null || ship == null || from == null) return;
        float t = Float.isFinite(throttle) ? Math.max(0.0F, Math.min(1.0F, throttle)) : 0.0F;
        Order o = new Order(clampStep(step), clamp1(rudder), clamp1(planes), clamp1(roll), source, from.immutable(), changed, level.getGameTime(), t, afterburner);
        ORDERS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(ship, s -> new HashMap<>()).put(o.from(), o);
    }

    public static void withdraw(ServerLevel level, UUID ship, BlockPos from) {
        Map<UUID, Map<BlockPos, Order>> m = ORDERS.get(level);
        if (m == null || ship == null) return;
        Map<BlockPos, Order> s = m.get(ship);
        if (s != null) { s.remove(from); if (s.isEmpty()) m.remove(ship); }
    }

    public static Order newest(ServerLevel level, UUID ship) {
        Map<UUID, Map<BlockPos, Order>> m = ORDERS.get(level);
        Map<BlockPos, Order> s = m == null || ship == null ? null : m.get(ship);
        if (s == null) return null;
        long now = level.getGameTime();
        Order best = null;
        for (var it = s.values().iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (now - o.seen() > Balance.PROP_INPUT_TTL) { it.remove(); continue; }
            if (best == null || o.changed() > best.changed() || (o.changed() == best.changed() && o.source() > best.source())) best = o;
        }
        return best;
    }

    public static void answer(ServerLevel level, UUID ship, BlockPos engine, int step, boolean running, double thrust, double rudderDeg, double planesDeg) {
        if (level == null || ship == null || engine == null) return;
        REPORTS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(ship, s -> new HashMap<>())
                .put(engine.immutable(), new Report(clampStep(step), running, thrust, rudderDeg, planesDeg, level.getGameTime()));
    }

    public static Answer answerOf(ServerLevel level, UUID ship) {
        Map<UUID, Map<BlockPos, Report>> m = REPORTS.get(level);
        Map<BlockPos, Report> s = m == null || ship == null ? null : m.get(ship);
        if (s == null) return null;
        long now = level.getGameTime();
        int n = 0, run = 0, step = Balance.PROP_STEP_STOP;
        double thrust = 0.0, strongest = -1.0, rud = 0.0, pln = 0.0;
        for (var it = s.values().iterator(); it.hasNext(); ) {
            Report r = it.next();
            if (now - r.seen() > Balance.PROP_INPUT_TTL) { it.remove(); continue; }
            n++;
            thrust += r.thrust();
            rud += r.rudder();
            pln += r.planes();
            if (r.running()) {
                run++;
                if (Math.abs(r.thrust()) > strongest) { strongest = Math.abs(r.thrust()); step = r.step(); }
            }
        }
        if (n == 0) { m.remove(ship); return null; }
        return new Answer(step, n, run, thrust, rud / n, pln / n, now);
    }

    public static void boost(ServerLevel level, UUID ship) {
        if (level == null || ship == null) return;
        BOOSTS.computeIfAbsent(level, l -> new HashMap<>()).put(ship, level.getGameTime());
    }

    public static long boostAt(ServerLevel level, UUID ship) {
        Map<UUID, Long> m = BOOSTS.get(level);
        Long t = m == null || ship == null ? null : m.get(ship);
        return t == null ? -1L : t;
    }

    private static float clamp1(float v) { return Float.isFinite(v) ? Math.max(-1.0F, Math.min(1.0F, v)) : 0.0F; }
}
