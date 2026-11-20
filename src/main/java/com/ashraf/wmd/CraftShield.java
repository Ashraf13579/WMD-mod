package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CraftShield {

    private CraftShield() {}

    static final class Bubble {
        final UUID ship;
        final ResourceKey<Level> dim;
        double cx, cy, cz, ax, ay, az, wr, drift;
        Vec3 wc = Vec3.ZERO;
        int pct = 100;
        long until, boxAt = Long.MIN_VALUE / 2, sentAt = Long.MIN_VALUE / 2;
        boolean tooBig;
        final List<BlockPos> gens = new ArrayList<>();

        Bubble(UUID ship, ResourceKey<Level> dim) { this.ship = ship; this.dim = dim; }
    }

    private static final Map<UUID, Bubble> BUBBLES = Statics.server(new HashMap<>());

    private static final Map<UUID, Long> COOL = Statics.server(new HashMap<>());

    private static final Map<UUID, double[]> MEMO = Statics.server(new HashMap<>());

    private static int pierce;

    private static final Map<ResourceKey<Level>, Long> PIERCE = Statics.server(new HashMap<>());

    public static void piercingNow(ServerLevel sl) { if (sl != null && !BUBBLES.isEmpty()) PIERCE.put(sl.dimension(), sl.getGameTime()); }

    private static boolean piercing(ServerLevel sl) {
        if (pierce > 0) return true;
        Long t = PIERCE.get(sl.dimension());
        return t != null && t == sl.getGameTime();
    }

    public static final int ST_NONE = 0, ST_UP = 1, ST_TOO_BIG = 2;

    static int report(ServerLevel sl, UUID ship, BlockPos gen, long now) {
        if (!AirGate.on() || sl == null || ship == null) return ST_NONE;
        Bubble b = BUBBLES.get(ship);
        if (b == null || b.dim != sl.dimension()) { b = new Bubble(ship, sl.dimension()); BUBBLES.put(ship, b); }
        if (!b.gens.contains(gen)) b.gens.add(gen.immutable());
        b.until = now + Balance.CSH_HOLD_TICKS;
        if (now - b.boxAt >= Balance.CSH_BOX_TICKS) { b.boxAt = now; box(sl, b); }
        return b.tooBig ? ST_TOO_BIG : ST_UP;
    }

    static void dropped(ServerLevel sl, UUID ship, BlockPos gen) {
        Bubble b = ship == null ? null : BUBBLES.get(ship);
        if (b == null) return;
        b.gens.remove(gen);
        if (b.gens.isEmpty()) BUBBLES.remove(ship);
    }

    static long coolUntil(UUID ship) { Long t = ship == null ? null : COOL.get(ship); return t == null ? 0L : t; }

    private static final Map<ResourceKey<Level>, Map<UUID, java.util.Set<Long>>> ABOARD = Statics.server(new HashMap<>());

    static void aboard(ServerLevel sl, UUID ship, BlockPos gen) {
        if (sl == null || ship == null) return;
        ABOARD.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new java.util.LinkedHashSet<>()).add(gen.asLong());
    }

    static void left(ServerLevel sl, UUID ship, BlockPos gen) {
        Map<UUID, java.util.Set<Long>> m = sl == null || ship == null ? null : ABOARD.get(sl.dimension());
        java.util.Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(gen.asLong()); if (s.isEmpty()) m.remove(ship); }
    }

    static List<CraftShieldBlockEntity> gensOf(ServerLevel sl, UUID ship) {
        List<CraftShieldBlockEntity> out = new ArrayList<>();
        Map<UUID, java.util.Set<Long>> m = sl == null || ship == null ? null : ABOARD.get(sl.dimension());
        java.util.Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof CraftShieldBlockEntity g && !g.isRemoved()) out.add(g);
        }
        return out;
    }

    public static int[] toggleAll(ServerLevel sl, UUID ship) {
        List<CraftShieldBlockEntity> l = AirGate.on() ? gensOf(sl, ship) : List.of();
        if (l.isEmpty()) return new int[] { -1, 0 };
        boolean any = false;
        for (CraftShieldBlockEntity g : l) any |= g.switchedOn();
        for (CraftShieldBlockEntity g : l) g.setOn(!any);
        int[] ch = ShipPower.coreCharge(sl, ship);
        return new int[] { any ? 0 : 1, ch[1] <= 0 ? 0 : (int) Math.round(100.0 * ch[0] / ch[1]) };
    }

    private static void box(ServerLevel sl, Bubble b) {
        int[] pb = SableBridge.plotBox(sl, b.ship);
        if (pb == null) { b.tooBig = true; return; }
        double pad = Balance.CSH_PAD;
        b.cx = (pb[0] + pb[3] + 1) * 0.5; b.cy = (pb[1] + pb[4] + 1) * 0.5; b.cz = (pb[2] + pb[5] + 1) * 0.5;
        b.ax = (pb[3] - pb[0] + 1) * 0.5 + pad; b.ay = (pb[4] - pb[1] + 1) * 0.5 + pad; b.az = (pb[5] - pb[2] + 1) * 0.5 + pad;
        b.tooBig = Math.max(b.ax, Math.max(b.ay, b.az)) > Balance.CSH_MAX_HALF;
        b.wr = Math.max(b.ax, Math.max(b.ay, b.az)) + 1.0;
        Vec3 w = SableBridge.toWorld(sl, new Vec3(b.cx, b.cy, b.cz));
        if (w != null) b.wc = w;
        int[] ch = ShipPower.coreCharge(sl, b.ship);
        b.pct = ch[1] <= 0 ? 0 : (int) Math.round(100.0 * ch[0] / ch[1]);
    }

    public static boolean anyUp() { return !BUBBLES.isEmpty(); }

    private static List<Bubble> live(ServerLevel sl) {
        if (BUBBLES.isEmpty()) return List.of();
        long now = sl.getGameTime();
        List<Bubble> out = new ArrayList<>();
        BUBBLES.values().removeIf(b -> now > b.until || now < b.until - 10L * Balance.CSH_HOLD_TICKS);
        for (Bubble b : BUBBLES.values()) if (b.dim == sl.dimension() && !b.tooBig && !EmpBlackout.out(sl, b.ship)) out.add(b);
        return out;
    }

    public static boolean up(ServerLevel sl, UUID ship) {
        if (ship == null || BUBBLES.isEmpty() || EmpBlackout.out(sl, ship)) return false;
        Bubble b = BUBBLES.get(ship);
        return b != null && !b.tooBig && b.dim == sl.dimension() && sl.getGameTime() <= b.until;
    }

    private static Vec3 unit(ServerLevel sl, Bubble b, Vec3 p) {
        Vec3 q = SableBridge.toShip(sl, b.ship, p);
        if (q == null) return null;
        return new Vec3((q.x - b.cx) / b.ax, (q.y - b.cy) / b.ay, (q.z - b.cz) / b.az);
    }

    private static Vec3 fromUnit(ServerLevel sl, Bubble b, Vec3 u) {
        return SableBridge.toWorld(sl, new Vec3(b.cx + u.x * b.ax, b.cy + u.y * b.ay, b.cz + u.z * b.az));
    }

    private static boolean near(Vec3 a, Vec3 b, Vec3 c, double r) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double l2 = dx * dx + dy * dy + dz * dz;
        double k = l2 < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((c.x - a.x) * dx + (c.y - a.y) * dy + (c.z - a.z) * dz) / l2));
        double ex = a.x + dx * k - c.x, ey = a.y + dy * k - c.y, ez = a.z + dz * k - c.z;
        return ex * ex + ey * ey + ez * ez <= r * r;
    }

    static boolean inside(ServerLevel sl, Bubble b, Vec3 p) {
        double rr = b.wr + b.drift;
        if (p.distanceToSqr(b.wc) > rr * rr) return false;
        Vec3 u = unit(sl, b, p);
        return u != null && u.lengthSqr() <= 1.0;
    }

    public record Crossing(UUID ship, Vec3 at, Vec3 normal) {}

    public static Crossing crossing(ServerLevel sl, Vec3 a, Vec3 b, UUID skip) {
        if (sl == null || a == null || b == null || BUBBLES.isEmpty() || pierce > 0) return null;
        Crossing best = null;
        double bestT = 2.0;
        for (Bubble bu : live(sl)) {
            if (bu.ship.equals(skip) || !near(a, b, bu.wc, bu.wr + bu.drift)) continue;
            Vec3 ua = unit(sl, bu, a), ub = unit(sl, bu, b);
            if (ua == null || ub == null || ua.lengthSqr() <= 1.0) continue;
            Vec3 d = ub.subtract(ua);
            double qa = d.lengthSqr(), qb = 2.0 * ua.dot(d), qc = ua.lengthSqr() - 1.0, disc = qb * qb - 4.0 * qa * qc;
            if (qa < 1.0E-12 || disc < 0.0) continue;
            double t = (-qb - Math.sqrt(disc)) / (2.0 * qa);
            if (t < 0.0 || t > 1.0 || t >= bestT) continue;
            Vec3 u = ua.add(d.scale(t));
            if (waterline(sl, a.add(b.subtract(a).scale(t)))) continue;
            Vec3 at = fromUnit(sl, bu, u);
            if (at == null) continue;
            Vec3 c = fromUnit(sl, bu, Vec3.ZERO);
            bestT = t;
            best = new Crossing(bu.ship, at, c == null ? Vec3.ZERO : at.subtract(c).normalize());
        }
        return best;
    }

    private static boolean waterline(ServerLevel sl, Vec3 p) {
        return sl.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER);
    }

    static int drain(double r) {
        long v = (long) Balance.CSH_HIT_FE + (long) (Balance.CSH_PER_R2 * r * r);
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, v));
    }

    public static boolean hit(ServerLevel sl, Crossing c, int fe, boolean piercing, String what) {
        if (sl == null || c == null) return false;
        if (piercing || pierce > 0) { shatter(sl, c.ship(), c.at(), what + " (pierces)"); return false; }
        if (!pay(sl, c.ship(), fe)) { shatter(sl, c.ship(), c.at(), what + " (" + String.format("%,d", fe) + " FE: the core cannot pay)"); return false; }
        Bubble b = BUBBLES.get(c.ship());
        if (b != null) { int[] ch = ShipPower.coreCharge(sl, c.ship()); b.pct = ch[1] <= 0 ? 0 : (int) Math.round(100.0 * ch[0] / ch[1]); }
        fx(sl, c.ship(), CraftShieldPayload.HIT, c.at(), Math.min(1.0F, 0.25F + fe / 1_000_000.0F));
        Wmd.LOGGER.info("[craft shield] {} took {} at {} {} {}: {} FE, core {} %", c.ship(), what, Math.round(c.at().x), Math.round(c.at().y),
                Math.round(c.at().z), String.format("%,d", fe), b == null ? "?" : b.pct);
        return true;
    }

    public static boolean hitBlast(ServerLevel sl, Crossing c, double r, WarheadType t, int tier, String what) {
        return hit(sl, c, drain(r), t != null && Forcefield.pierces(t, tier), what);
    }

    public static boolean stopsFlight(ServerLevel sl, Vec3 a, Vec3 b, UUID own, double craterR, WarheadType t, int tier, String what) {
        if (sl == null || a == null || b == null || BUBBLES.isEmpty()) return false;
        Crossing c = crossing(sl, a, b, own);
        if (c == null) return false;
        WarheadType wt = t == null ? WarheadType.HE : t;
        if (!hitBlast(sl, c, craterR, wt, tier, what)) return false;
        PacketDistributor.sendToPlayersNear(sl, null, c.at().x, c.at().y, c.at().z, 4000, new ImpactPayload(c.at().x, c.at().y, c.at().z, Math.max(1, tier),
                wt.ordinal(), 0, 0.0F, ImpactPayload.SHELL));
        return true;
    }

    public static boolean stopsAt(ServerLevel sl, Vec3 at, Vec3 from, int fe, String what) {
        if (sl == null || at == null || from == null || BUBBLES.isEmpty() || pierce > 0) return false;
        for (Bubble b : live(sl)) {
            if (!inside(sl, b, at) || inside(sl, b, from)) continue;
            Vec3 u = unit(sl, b, from);
            Vec3 hitAt = u == null || u.lengthSqr() < 1.0E-9 ? at : fromUnit(sl, b, u.normalize());
            return hit(sl, new Crossing(b.ship, hitAt == null ? at : hitAt, Vec3.ZERO), fe, false, what);
        }
        return false;
    }

    public static boolean blastStopped(ServerLevel sl, UUID ship, Vec3 pos, double r) {
        if (sl == null || ship == null || pos == null || !up(sl, ship)) return false;
        long now = sl.getGameTime();
        double[] m = MEMO.get(ship);
        if (m != null && m[0] == now && m[1] == pos.x && m[2] == pos.y && m[3] == pos.z) return m[4] > 0.5;
        boolean took = false;
        Bubble b = BUBBLES.get(ship);
        if (b != null && !inside(sl, b, pos) && !waterline(sl, pos)) {
            Vec3 u = unit(sl, b, pos);
            Vec3 at = u == null || u.lengthSqr() < 1.0E-9 ? pos : fromUnit(sl, b, u.normalize());
            took = hit(sl, new Crossing(ship, at == null ? pos : at, Vec3.ZERO), drain(r), piercing(sl), "a blast r " + Math.round(r));
        }
        if (MEMO.size() > 256) MEMO.clear();
        MEMO.put(ship, new double[] { now, pos.x, pos.y, pos.z, took ? 1.0 : 0.0 });
        return took;
    }

    public static boolean chewStopped(ServerLevel sl, UUID ship, Vec3 from, Vec3 to, int blocks) {
        if (sl == null || ship == null || from == null || !up(sl, ship)) return false;
        Bubble b = BUBBLES.get(ship);
        if (b == null || inside(sl, b, from)) return false;
        Crossing c = crossing(sl, from, to, null);
        if (c == null || !c.ship().equals(ship)) {
            Vec3 u = unit(sl, b, from);
            Vec3 at = u == null ? from : fromUnit(sl, b, u.normalize());
            c = new Crossing(ship, at == null ? from : at, Vec3.ZERO);
        }
        return hit(sl, c, Balance.CSH_ROUND_FE * Math.max(1, blocks), false, "a gun line (" + blocks + " blocks)");
    }

    public static boolean shelters(ServerLevel sl, Vec3 at, Vec3 from) {
        if (sl == null || at == null || from == null || BUBBLES.isEmpty() || piercing(sl)) return false;
        for (Bubble b : live(sl)) if (inside(sl, b, at) && !inside(sl, b, from) && !waterline(sl, from)) return true;
        return false;
    }

    public static boolean shieldCraft(ServerLevel sl, UUID ship, Vec3 from, int fe, String what) {
        if (sl == null || ship == null || !up(sl, ship)) return false;
        Bubble b = BUBBLES.get(ship);
        if (b == null || (from != null && inside(sl, b, from))) return false;
        Vec3 at = b.wc;
        if (from != null) { Vec3 u = unit(sl, b, from); Vec3 w = u == null || u.lengthSqr() < 1.0E-9 ? null : fromUnit(sl, b, u.normalize()); if (w != null) at = w; }
        return hit(sl, new Crossing(ship, at, Vec3.ZERO), fe, false, what);
    }

    public static void piercing(Runnable r) {
        pierce++;
        try { r.run(); } finally { pierce--; }
    }

    static boolean pay(ServerLevel sl, UUID ship, int fe) {
        if (fe <= 0) return true;
        if (EmpBlackout.out(sl, ship)) return false;
        int[] ch = ShipPower.coreCharge(sl, ship);
        if (ch[1] <= 0 || ch[0] - (long) fe < (long) (ch[1] * Balance.CSH_FLOOR)) return false;
        int left = fe, chunk = 250_000;
        while (left > 0) {
            int c = Math.min(left, chunk);
            if (ShipPower.draw(sl, ship, c)) { left -= c; continue; }
            if (chunk <= 1_000) return false;
            chunk /= 4;
        }
        return true;
    }

    static void shatter(ServerLevel sl, UUID ship, Vec3 at, String why) {
        Bubble b = BUBBLES.remove(ship);
        COOL.put(ship, sl.getGameTime() + Balance.CSH_COOLDOWN);
        if (COOL.size() > 256) { long now = sl.getGameTime(); COOL.values().removeIf(t -> t < now); }
        if (b != null) fxFor(sl, b, CraftShieldPayload.SHATTER, at, 1.0F);
        Wmd.LOGGER.info("[craft shield] {} SHATTERED by {}", ship, why);
    }

    private static void fx(ServerLevel sl, UUID ship, int kind, Vec3 at, float power) {
        Bubble b = BUBBLES.get(ship);
        if (b != null) fxFor(sl, b, kind, at, power);
    }

    private static void fxFor(ServerLevel sl, Bubble b, int kind, Vec3 at, float power) {
        Vec3 u = at == null ? Vec3.ZERO : unit(sl, b, at);
        if (u == null) u = Vec3.ZERO;
        CraftShieldPayload p = new CraftShieldPayload(b.ship, kind, (float) b.cx, (float) b.cy, (float) b.cz, (float) b.ax, (float) b.ay, (float) b.az,
                (float) u.x, (float) u.y, (float) u.z, b.pct, power);
        PacketDistributor.sendToPlayersNear(sl, null, b.wc.x, b.wc.y, b.wc.z, Balance.CSH_FX_RANGE + b.wr, p);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (BUBBLES.isEmpty() || !(event.getLevel() instanceof ServerLevel sl) || !AirGate.on()) return;
        long now = sl.getGameTime();
        for (Bubble b : live(sl)) {
            Vec3 w = SableBridge.toWorld(sl, new Vec3(b.cx, b.cy, b.cz));
            if (w != null) { b.drift = Math.min(64.0, w.distanceTo(b.wc)) + 1.0; b.wc = w; }
            if (now - b.sentAt < Balance.CSH_SYNC_TICKS) continue;
            b.sentAt = now;
            fxFor(sl, b, CraftShieldPayload.STATE, null, 0.0F);
        }
    }

    static int count(ServerLevel sl) { return live(sl).size(); }
}
