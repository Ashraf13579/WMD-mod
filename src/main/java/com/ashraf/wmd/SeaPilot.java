package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class SeaPilot {

    static final int OFF = 0, HOLD = 1, ROUTE = 2, STATION = 3;
    static final int END_STATION = 0, END_LOOP = 1, END_HOLD = 2;
    static final int R_NONE = 0, R_HELM = 1, R_TELEGRAPH = 2, R_DAMAGE = 3, R_GROUND = 4, R_SHOAL = 5, R_POWER = 6, R_EMPTY = 7, R_CRUSH = 8, R_ANCHOR = 9,
            R_OFF = 10, R_NOCRAFT = 11;
    static final String[] REASONS = { "none", "helm", "telegraph", "damage", "ground", "shoal", "power", "empty", "crush", "anchor", "off", "nocraft" };
    static final String[] MODES = { "off", "hold", "route", "station" };

    static final int SPEEDS = 4;
    static final int STOP = Balance.PROP_STEP_STOP;

    static int stepOf(int speed) { return STOP + 1 + Mth.clamp(speed, 0, SPEEDS - 1); }

    record Wp(double x, double z, int speed, int depth) {}

    record Ctx(ServerLevel sl, UUID ship, BlockPos helm, Vec3 at, double heading, double fwdSpeed, double speed, double depth, boolean sub, int crush,
               double draft, double surfaceY, boolean power, long now) {

        double keelBelow() { return Math.max(0.0, draft - Math.max(0.0, depth)); }
    }

    record Out(int step, float rudder, float planes, float bow) {}

    private static final Out STOP_OUT = new Out(STOP, 0.0F, 0.0F, 0.0F);

    float holdCourse;
    int holdSpeed = 1, holdDepth = 20;
    final List<Wp> route = new ArrayList<>();
    int end = END_STATION;
    double stX, stZ;
    int stR = Balance.SP_STATION_R_DEF, stDepth = 20;

    int margin = -1;

    int mode = OFF, leg, reason;
    long reasonAt = -1000, engagedAt;

    boolean capped;
    double clearance = Double.NaN, wantDepth = -1.0;
    private int lastBlocks = -1, emptyTicks, slowTicks;
    private double lastHdg = Double.NaN, lastDepth = Double.NaN, floorAhead = Double.NaN;
    private boolean fresh;

    private final long[] track = new long[Balance.SP_TRACK_MAX];
    private int trackN, trackHead;
    private long trackAt = Long.MIN_VALUE / 2;

    boolean on() { return mode != OFF; }

    int margin(boolean sub) { return margin >= 0 ? margin : sub ? Balance.SP_MARGIN_SUB : Balance.SP_MARGIN_SHIP; }

    private static final Map<UUID, Long> ANCHORS = Statics.server(new HashMap<>());

    static void anchorDropped(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return;
        if (ANCHORS.size() > 256) ANCHORS.clear();
        ANCHORS.put(ship, sl.getGameTime());
    }

    String engage(Ctx c, int m) {
        if (m < HOLD || m > STATION) return "message.wmd.sp.bad_mode";
        if (c == null || c.ship() == null) return "message.wmd.sp.no_craft";
        if (!c.power()) return "message.wmd.sp.no_power";
        if (m == ROUTE && route.isEmpty()) return "message.wmd.sp.no_route";
        if (m == ROUTE) {
            int bad = SeaChart.firstBadLeg(c.sl(), c.at(), route, c.sub(), c.sub() ? c.keelBelow() : c.draft(), margin(c.sub()));
            if (bad >= 0) return "message.wmd.sp.route_shoal|" + (bad + 1);
        }
        if (m == STATION && stX == 0.0 && stZ == 0.0) { stX = c.at().x; stZ = c.at().z; }
        mode = m;
        leg = m == ROUTE ? nearestLeg(c) : 0;
        reason = R_NONE;
        engagedAt = c.now();
        lastBlocks = -1; emptyTicks = 0; slowTicks = 0; lastHdg = Double.NaN; lastDepth = Double.NaN; fresh = false;
        return null;
    }

    String engageHere(Ctx c, int helmStep) {
        if (c == null) return "message.wmd.sp.no_craft";
        holdCourse = (float) Math.round(c.heading());
        holdSpeed = Mth.clamp(helmStep - STOP - 1, 0, SPEEDS - 1);
        holdDepth = c.sub() ? (int) Math.round(Math.max(0.0, c.depth())) : holdDepth;
        return engage(c, HOLD);
    }

    void letGo(int why, long now) {
        if (mode == OFF) return;
        mode = OFF;
        reason = why;
        reasonAt = now;
        capped = false;
    }

    int reasonShown(long now) { return now - reasonAt < Balance.SP_REASON_TICKS ? reason : R_NONE; }

    private int nearestLeg(Ctx c) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = Math.hypot(route.get(i).x() - c.at().x, route.get(i).z() - c.at().z);
            if (d < bd) { bd = d; best = i; }
        }
        if (best + 1 < route.size()) {
            Wp w = route.get(best), n = route.get(best + 1);
            double ax = n.x() - w.x(), az = n.z() - w.z(), bx = c.at().x - w.x(), bz = c.at().z - w.z();
            if (ax * bx + az * bz > 0.0) best++;
        }
        return best;
    }

    Out step(Ctx c) {
        if (mode == OFF) return null;
        if (c == null || c.ship() == null) { letGo(R_NOCRAFT, c == null ? reasonAt : c.now()); return null; }
        long now = c.now();
        if (!fresh) { fresh = true; if (engagedAt <= 0L || engagedAt > now) engagedAt = now; }
        if (!c.power()) { letGo(R_POWER, now); return null; }
        Long anchor = ANCHORS.get(c.ship());
        if (anchor != null && anchor > engagedAt) { letGo(R_ANCHOR, now); return null; }
        ShipControl.Order o = ShipControl.newest(c.sl(), c.ship());
        if (o != null && !o.from().equals(c.helm()) && o.changed() > engagedAt) {
            letGo(o.source() == ShipControl.SRC_TELEGRAPH ? R_TELEGRAPH : R_HELM, now);
            return null;
        }
        if (now % 20 == 0) {
            if (damaged(c)) { letGo(R_DAMAGE, now); return null; }
            emptyTicks = Autopilot.crewAboard(c.sl(), c.ship()) ? 0 : emptyTicks + 20;
            if (emptyTicks >= Balance.SP_EMPTY_TICKS) { letGo(R_EMPTY, now); return STOP_OUT; }
        }
        if (now % 5 == 0 || Double.isNaN(floorAhead)) floorAhead = seabedAhead(c);

        double wantHdg = c.heading();
        int wantStep = STOP;
        float bow = 0.0F;
        boolean inside = false;
        double depthGoal = holdDepth;
        for (int guard = 0; guard <= route.size() + 1 && mode == ROUTE; guard++) {
            if (route.isEmpty()) { letGo(R_OFF, now); return null; }
            leg = Mth.clamp(leg, 0, route.size() - 1);
            Wp w = route.get(leg);
            double d = Math.hypot(w.x() - c.at().x, w.z() - c.at().z);
            if (d > Balance.SP_WP_REACH) {
                wantHdg = Autopilot.bearingTo(c.at(), w.x(), w.z());
                wantStep = stepOf(w.speed());
                depthGoal = w.depth();
                if (leg == route.size() - 1 && end == END_STATION && d < Balance.SP_WP_REACH * 4.0) wantStep = Math.min(wantStep, stepOf(0));
                break;
            }
            if (leg + 1 < route.size()) leg++;
            else if (end == END_LOOP) leg = 0;
            else if (end == END_HOLD) { mode = HOLD; holdCourse = (float) c.heading(); holdSpeed = w.speed(); holdDepth = w.depth(); }
            else { mode = STATION; stX = w.x(); stZ = w.z(); stDepth = w.depth(); }
        }
        if (mode == HOLD) { wantHdg = holdCourse; wantStep = stepOf(holdSpeed); depthGoal = holdDepth; }
        else if (mode == STATION) {
            depthGoal = stDepth;
            double dx = stX - c.at().x, dz = stZ - c.at().z, d = Math.hypot(dx, dz);
            if (d > stR) {
                wantHdg = Autopilot.bearingTo(c.at(), stX, stZ);
                wantStep = d > stR * 4.0 ? stepOf(1) : stepOf(0);
            } else {
                inside = true;
                wantStep = c.fwdSpeed() > 0.8 ? STOP - 1 : c.fwdSpeed() < -0.8 ? STOP + 1 : STOP;
                double hr = Math.toRadians(c.heading());
                bow = (float) Mth.clamp((dx * Math.cos(hr) + dz * Math.sin(hr)) / 6.0, -1.0, 1.0);
            }
        }

        int mar = margin(c.sub());
        double keelY = c.surfaceY() - c.draft();
        clearance = Double.isNaN(floorAhead) ? Double.NaN : keelY - floorAhead;
        double cap = Double.MAX_VALUE;
        if (!Double.isNaN(floorAhead)) cap = c.surfaceY() - floorAhead - c.keelBelow() - mar;
        if (c.crush() > 0) {
            cap = Math.min(cap, Balance.SP_CRUSH_CAP * c.crush());
            if (c.depth() > Balance.SP_CRUSH_CAP * c.crush()) cap = Math.min(cap, Balance.SP_CRUSH_WARN * c.crush());
        }
        if (!Double.isNaN(clearance) && clearance < mar && (!c.sub() || cap < 0.0)) { letGo(R_SHOAL, now); return STOP_OUT; }

        if (wantStep >= STOP + 2 && now - engagedAt > Balance.SP_GROUND_GRACE) {
            slowTicks = Math.abs(c.speed()) < Balance.SP_GROUND_SPEED ? slowTicks + 1 : 0;
            if (slowTicks >= Balance.SP_GROUND_TICKS) { letGo(R_GROUND, now); return STOP_OUT; }
        } else slowTicks = 0;

        double err = Mth.wrapDegrees(wantHdg - c.heading());
        double rate = Double.isNaN(lastHdg) ? 0.0 : Mth.wrapDegrees(c.heading() - lastHdg) * 20.0;
        lastHdg = c.heading();
        float rudder = inside ? 0.0F : (float) Mth.clamp(err / Balance.SP_RUDDER_DEG - rate * Balance.SP_RUDDER_DAMP, -1.0, 1.0);
        float planes = 0.0F;
        capped = false;
        wantDepth = -1.0;
        if (c.sub()) {
            double want = Mth.clamp(depthGoal, 0.0, Balance.SP_DEPTH_MAX);
            if (cap < want) { capped = true; want = Math.max(0.0, cap); }
            wantDepth = want;
            double vr = Double.isNaN(lastDepth) ? 0.0 : (c.depth() - lastDepth) * 20.0;
            lastDepth = c.depth();
            planes = (float) Mth.clamp((want - c.depth()) / Balance.SP_DEPTH_FULL - vr * Balance.SP_DEPTH_DAMP, -1.0, 1.0);
        }
        return new Out(wantStep, rudder, planes, bow);
    }

    private static double seabedAhead(Ctx c) {
        double d = Math.max(Balance.SP_LOOKAHEAD_MIN, Math.abs(c.speed()) * Balance.SP_LOOKAHEAD_S), best = Double.NaN;
        double dir = c.fwdSpeed() < -0.2 ? c.heading() + 180.0 : c.heading();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(dir + (i == 6 ? -15.0 : i == 7 ? 15.0 : 0.0)), r = i >= 6 ? d : d * i / 5.0;
            int x = Mth.floor(c.at().x + Math.sin(a) * r), z = Mth.floor(c.at().z - Math.cos(a) * r);
            if (!c.sl().hasChunk(x >> 4, z >> 4)) continue;
            double f = c.sl().getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (Double.isNaN(best) || f > best) best = f;
        }
        return best;
    }

    private boolean damaged(Ctx c) {
        SableBridge.ShipInfo si = SableBridge.shipInfo(c.sl(), c.ship());
        if (si == null) return false;
        boolean hit = lastBlocks >= 0 && si.blockCount() < lastBlocks
                && c.now() - Autopilot.ownDropAt(c.ship()) > Balance.AP_OWN_DROP_TICKS + Autopilot.DROP_SEEN_LATE;
        lastBlocks = si.blockCount();
        return hit;
    }

    void trackTick(Vec3 at, long now) {
        if (at == null || now - trackAt < Balance.SP_TRACK_TICKS) return;
        trackAt = now;
        track[trackHead] = ((long) Mth.floor(at.x)) << 32 | (Mth.floor(at.z) & 0xFFFFFFFFL);
        trackHead = (trackHead + 1) % track.length;
        trackN = Math.min(trackN + 1, track.length);
    }

    int[] trackXZ() {
        int[] out = new int[trackN * 2];
        for (int i = 0; i < trackN; i++) {
            long v = track[(trackHead - trackN + i + track.length) % track.length];
            out[2 * i] = (int) (v >> 32);
            out[2 * i + 1] = (int) v;
        }
        return out;
    }

    void apply(CompoundTag in, Vec3 at) {
        if (in == null) return;
        if (in.contains("hc")) { float h = in.getFloat("hc"); if (Float.isFinite(h)) holdCourse = ((h % 360.0F) + 360.0F) % 360.0F; }
        if (in.contains("hs")) holdSpeed = Mth.clamp(in.getInt("hs"), 0, SPEEDS - 1);
        if (in.contains("hd")) holdDepth = Mth.clamp(in.getInt("hd"), 0, Balance.SP_DEPTH_MAX);
        if (in.contains("rt")) {
            ListTag l = in.getList("rt", Tag.TAG_COMPOUND);
            route.clear();
            for (int i = 0; i < l.size() && i < Balance.SP_WP_MAX; i++) {
                CompoundTag w = l.getCompound(i);
                double x = w.getDouble("x"), z = w.getDouble("z");
                if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7) continue;
                if (at != null && Math.hypot(x - at.x, z - at.z) > Balance.SP_ROUTE_RANGE) continue;
                route.add(new Wp(x, z, Mth.clamp(w.getInt("s"), 0, SPEEDS - 1), Mth.clamp(w.getInt("d"), 0, Balance.SP_DEPTH_MAX)));
            }
            if (mode == ROUTE && (route.isEmpty() || leg >= route.size())) leg = route.isEmpty() ? 0 : route.size() - 1;
            if (mode == ROUTE && route.isEmpty()) mode = OFF;
        }
        if (in.contains("end")) end = Mth.clamp(in.getInt("end"), END_STATION, END_HOLD);
        if (in.contains("sx")) {
            double x = in.getDouble("sx"), z = in.getDouble("sz");
            if (Double.isFinite(x) && Double.isFinite(z) && Math.abs(x) < 3.0E7 && Math.abs(z) < 3.0E7
                    && (at == null || Math.hypot(x - at.x, z - at.z) <= Balance.SP_ROUTE_RANGE)) { stX = x; stZ = z; }
        }
        if (in.contains("sr")) stR = Mth.clamp(in.getInt("sr"), Balance.SP_STATION_R_MIN, Balance.SP_STATION_R_MAX);
        if (in.contains("sd")) stDepth = Mth.clamp(in.getInt("sd"), 0, Balance.SP_DEPTH_MAX);
        if (in.contains("mar")) { int m = in.getInt("mar"); margin = m < 0 ? -1 : Mth.clamp(m, Balance.SP_MARGIN_MIN, Balance.SP_MARGIN_MAX); }
    }

    void writeSettings(CompoundTag t) {
        t.putFloat("hc", holdCourse); t.putInt("hs", holdSpeed); t.putInt("hd", holdDepth);
        ListTag l = new ListTag();
        for (Wp w : route) { CompoundTag c = new CompoundTag(); c.putDouble("x", w.x()); c.putDouble("z", w.z()); c.putInt("s", w.speed()); c.putInt("d", w.depth()); l.add(c); }
        t.put("rt", l);
        t.putInt("end", end); t.putDouble("sx", stX); t.putDouble("sz", stZ); t.putInt("sr", stR); t.putInt("sd", stDepth); t.putInt("mar", margin);
        t.putInt("mode", mode); t.putInt("leg", leg);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        writeSettings(t);
        t.putLong("eng", engagedAt);
        long[] tr = new long[trackN];
        for (int i = 0; i < trackN; i++) tr[i] = track[(trackHead - trackN + i + track.length) % track.length];
        t.put("trk", new LongArrayTag(tr));
        return t;
    }

    void load(CompoundTag t) {
        if (t == null || t.isEmpty()) return;
        apply(t, null);
        mode = Mth.clamp(t.getInt("mode"), OFF, STATION);
        leg = Math.max(0, t.getInt("leg"));
        if (mode == ROUTE && route.isEmpty()) mode = OFF;
        engagedAt = t.getLong("eng");
        fresh = false;
        long[] tr = t.getLongArray("trk");
        trackN = 0; trackHead = 0;
        for (long v : tr) { track[trackHead] = v; trackHead = (trackHead + 1) % track.length; trackN = Math.min(trackN + 1, track.length); }
    }
}
