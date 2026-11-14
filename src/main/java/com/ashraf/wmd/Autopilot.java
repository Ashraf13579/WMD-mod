package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Autopilot {

    static final int OFF = 0, HOLD = 1, ROUTE = 2, ORBIT = 3, HOME = 4, PARKED = 5;
    static final int END_ORBIT = 0, END_LOOP = 1, END_HOLD = 2;
    static final int SIDE_AUTO = 0, SIDE_LEFT = 1, SIDE_RIGHT = 2;
    static final int LAND_RETURN = 0, LAND_APPROACH = 1, LAND_FULL = 2;
    static final int PH_RETURN = 0, PH_CIRCUIT = 1, PH_FINAL = 2, PH_FLARE = 3, PH_ROLL = 4, PH_GOAROUND = 5, PH_CIRCLE = 6;

    static final int R_NONE = 0, R_STICK = 1, R_DAMAGE = 2, R_FUEL = 3, R_STALL = 4, R_POWER = 5, R_EMPTY = 6, R_MINIMUMS = 7, R_LANDED = 8, R_OFF = 9,
            R_NOHOME = 10, R_LANDFAIL = 11, R_BINGO = 12, R_HOMEEMPTY = 13, R_GOAROUND = 14, R_BOLTER = 15;

    record Wp(double x, double z, float alt, float spd) {}

    record Home(int kind, Vec3 t, double hdg, double len, BlockPos beacon, double eye) {}

    record Ctx(ServerLevel sl, UUID ship, Vec3 at, Vec3 vel, double heading, double pitch, double roll, Vec3 right, boolean power, boolean pilot,
               long now, BlockPos cockpit) {}

    record Out(float pitch, float roll, float yaw, float throttle, boolean brake) {}

    float holdHdg, holdAlt = 120.0F, holdSpd = 55.0F;
    final List<Wp> route = new ArrayList<>();
    int end = END_ORBIT, side = SIDE_AUTO, floor = Balance.AP_FLOOR, landLevel = LAND_FULL;
    double orbX, orbZ;
    float orbR = (float) Balance.AP_ORBIT_R_DEF, orbAlt = 140.0F, orbSpd = 50.0F;
    Home home;

    int mode = OFF, leg, phase, goArounds, reason;
    long reasonAt = -1000;
    private float thrTrim = 0.6F, lastRollDeg, lastGammaDeg, outT;

    private float pitchTrim;

    private double vLift;
    private long vLiftLog = Long.MIN_VALUE / 2;
    private int lastBlocks = -1, lastDamaged, emptyTicks, sideResolved = SIDE_LEFT, circuitLeg = -1, rollTicks, chuteAsked;
    private double circuitSide = 1.0, groundSeen = Double.NaN, stall = -1.0;
    private long stallAt = Long.MIN_VALUE / 2, ownDropAt = Long.MIN_VALUE / 2, fuelAt = Long.MIN_VALUE / 2;
    private float fuelShare = Float.NaN, fuelThen = Float.NaN;
    private long fuelThenAt;
    private boolean decided, gearAsked;

    private Vec3 rollStart;
    private double rollEye = Double.NaN;
    private boolean rolling;

    boolean on() { return mode != OFF; }
    boolean flying() { return mode != OFF && mode != PARKED; }
    float throttle() { return outT; }

    void ownDrop(long now) { ownDropAt = now; }

    private static final Map<UUID, Long> OWN_DROPS = Statics.server(new HashMap<>());

    static void ownDrop(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return;
        if (OWN_DROPS.size() > 256) OWN_DROPS.clear();
        OWN_DROPS.put(ship, sl.getGameTime());
    }

    static final int DROP_SEEN_LATE = SableShips.COUNT_CACHE_TICKS + 20;

    String engage(Ctx c, int m) {
        if (!c.power()) return "message.wmd.ap.no_power";
        if (c.ship() == null) return "message.wmd.ap.no_craft";

        if (HeliRotorBlockEntity.aboard(c.sl(), c.ship())) return "message.wmd.ap.heli";
        if (LandingGearBlockEntity.onWheels(c.sl(), c.ship())) return "message.wmd.ap.on_ground";
        double v = c.vel().length();
        refreshStall(c, true);
        if (v < letGoSpeed()) return "message.wmd.ap.too_slow";
        if (m == ROUTE && route.isEmpty()) return "message.wmd.ap.no_route";
        if (m == HOME && home == null) return "message.wmd.ap.no_home";
        mode = m; leg = 0; reason = R_NONE; goArounds = 0; decided = false; gearAsked = false; circuitLeg = -1; rollTicks = 0; chuteAsked = 0; resetApproach();
        phase = m == HOME ? (landLevel == LAND_RETURN ? PH_CIRCLE : PH_RETURN) : 0;
        if (m == ROUTE) leg = nearestLeg(c);
        lastBlocks = -1; emptyTicks = 0;
        lastRollDeg = (float) c.roll(); lastGammaDeg = (float) gamma(c);
        thrTrim = Mth.clamp(outT > 0.0F ? outT : 0.6F, 0.2F, 1.0F);
        pitchTrim = 0.0F;
        sideResolved = side == SIDE_AUTO ? turretSide(c) : side;
        return null;
    }

    void devOrbit(double cx, double cz, float alt, float r, float spd) {
        orbX = cx; orbZ = cz; orbAlt = alt; orbR = r; orbSpd = spd; side = SIDE_LEFT; sideResolved = SIDE_LEFT;
        mode = ORBIT; leg = 0; reason = R_NONE; goArounds = 0; decided = false; gearAsked = false; circuitLeg = -1; rollTicks = 0; chuteAsked = 0;
        phase = 0; lastBlocks = -1; emptyTicks = 0; thrTrim = 0.8F; pitchTrim = 0.0F; vLift = 0.0;
        crewless = true;
        launchGrace = Balance.AP_LAUNCH_GRACE;
    }

    int launchGrace;

    boolean crewless;

    String engageHere(Ctx c) {
        holdHdg = (float) c.heading();
        holdAlt = (float) c.at().y;
        holdSpd = (float) c.vel().length();
        return engage(c, HOLD);
    }

    void letGo(int why, long now) {
        crewless = false;
        launchGrace = 0;
        if (mode == OFF) return;
        mode = OFF; reason = why; reasonAt = now;
    }

    int reasonShown(long now) { return now - reasonAt < Balance.AP_REASON_TICKS ? reason : R_NONE; }

    Out step(Ctx c) {
        Out o = stepInner(c);
        flightLog(c, o);
        return o;
    }

    private static final String[] MODE_N = { "OFF", "HOLD", "ROUTE", "ORBIT", "HOME", "PARKED" };
    private static final String[] PH_N = { "RETURN", "CIRCUIT", "FINAL", "FLARE", "ROLL", "GOAROUND", "CIRCLE" };
    private static final String[] R_N = { "-", "STICK", "DAMAGE", "FUEL", "STALL", "POWER", "EMPTY", "MINIMUMS", "LANDED", "OFF", "NOHOME", "LANDFAIL",
            "BINGO", "HOMEEMPTY", "GOAROUND", "BOLTER" };
    private int logMode = -1, logPhase = -1, logReason = -1;
    private long logAt = Long.MIN_VALUE / 2;

    private static String name(String[] n, int i) { return i >= 0 && i < n.length ? n[i] : Integer.toString(i); }

    private void flightLog(Ctx c, Out o) {
        if (c == null || c.ship() == null) return;
        boolean change = mode != logMode || (mode == HOME && phase != logPhase) || reason != logReason;
        boolean periodic = (mode == HOME || mode == PARKED && logMode == HOME) && Balance.AP_LOG_EVERY > 0 && c.now() - logAt >= Balance.AP_LOG_EVERY;
        if (!change && !periodic) return;
        if (!change && mode == OFF) return;
        StringBuilder s = new StringBuilder(String.format(java.util.Locale.ROOT, "[ap] %s %s%s%s | at %.0f %.0f %.0f v %.1f hdg %.0f pitch %.0f roll %.0f stall %.1f",
                c.ship().toString().substring(0, 8), name(MODE_N, mode), mode == HOME ? " " + name(PH_N, phase) : "", reason != R_NONE ? " reason " + name(R_N, reason) : "",
                c.at().x, c.at().y, c.at().z, c.vel().length(), c.heading(), c.pitch(), c.roll(), stall));
        s.append(String.format(java.util.Locale.ROOT, " track %.0f skid %.0f trim %.2f lift-limit %.1f", track(c), Mth.wrapDegrees(c.heading() - track(c)), pitchTrim, vLift));
        if (mode == HOME || mode == PARKED) {
            Geo g = home == null ? null : geo(c);
            if (home == null) s.append(" | NO HOME MARKED");
            else if (g == null) s.append(" | HOME GONE (beacon off / unloaded)");
            else {
                double eye = home.eye() > 0.0 ? home.eye() : Balance.AL_EYE_DEF;
                Vec3 u = g.u(), rel = c.at().subtract(g.t());
                double along = rel.x * u.x + rel.z * u.z, side = rel.x * -u.z + rel.z * u.x, drift = latV(c, g, new Vec3(-u.z, 0.0, u.x));
                double axis = (Math.toDegrees(Math.atan2(u.x, -u.z)) + 360.0) % 360.0;
                double vref = vrefBase();
                s.append(String.format(java.util.Locale.ROOT, " | touchdown %.0f %.0f %.0f %s along %.0f side %.1f drift %.1f wheels-over-td %.1f runway %.0f (off %.0f) vref %.1f wow %s land %s goarounds %d decided %s",
                        g.t().x, g.t().y, g.t().z, home.kind() == 1 ? "beacon" : "strip " + Math.round(g.len()), along, side, drift, c.at().y - g.t().y - eye, axis,
                        Mth.wrapDegrees(axis - c.heading()), vref, LandingGearBlockEntity.onWheels(c.sl(), c.ship()), landLevel == LAND_RETURN ? "return" : landLevel == LAND_APPROACH ? "approach" : "full",
                        goArounds, decided));
                if (mode == HOME && phase == PH_CIRCUIT && path != null) s.append(String.format(java.util.Locale.ROOT, " | plan %s leg %d", path.name(), path.seg));
            }
        }
        if (o != null) s.append(String.format(java.util.Locale.ROOT, " | out pitch %.2f roll %.2f yaw %.2f thr %.2f brake %s", o.pitch(), o.roll(), o.yaw(), o.throttle(), o.brake()));
        Wmd.LOGGER.info(s.toString());
        logMode = mode; logPhase = phase; logReason = reason; logAt = c.now();
    }

    private Out stepInner(Ctx c) {
        if (c.ship() == null) { if (mode != OFF) letGo(R_POWER, c.now()); return null; }
        record(c);
        if (mode == OFF) return null;
        if (mode == PARKED) {
            if (c.vel().length() > 3.0) { letGo(R_OFF, c.now()); return null; }
            return new Out(0.0F, 0.0F, 0.0F, 0.0F, true);
        }
        if (!c.power()) { letGo(R_POWER, c.now()); return null; }
        if (c.now() % 20 == 0) {
            refreshStall(c, false);
            if (damaged(c)) { letGo(R_DAMAGE, c.now()); return null; }
            if (fuelCheck(c)) return null;
            if (crewCheck(c)) return null;
        }
        double v = c.vel().length();
        boolean ground = mode == HOME && (phase == PH_ROLL || phase == PH_FLARE);
        if (launchGrace > 0) launchGrace--;
        else if (!ground && v < letGoSpeed()) { letGo(R_STALL, c.now()); return null; }
        Out o = switch (mode) {
            case HOLD -> fly(c, holdHdg, holdAlt, holdSpd, Balance.AP_BANK_MAX);
            case ROUTE -> route(c);
            case ORBIT -> orbit(c, orbX, orbZ, orbR, orbAlt, orbSpd, sideResolved);
            case HOME -> home(c);
            default -> null;
        };
        if (o != null) outT = o.throttle();
        return o;
    }

    private static double gamma(Ctx c) { double h = Math.hypot(c.vel().x, c.vel().z); return Math.toDegrees(Math.atan2(c.vel().y, Math.max(1.0, h))); }

    private double lastHdgDeg = Double.NaN;
    private long lastHdgTick = Long.MIN_VALUE;
    private double hdgRate;

    private double track(Ctx c) {
        double h = Math.hypot(c.vel().x, c.vel().z);
        return h > Balance.AP_TRACK_MIN_SPEED ? ApPath.hdgOf(c.vel().x, c.vel().z) : c.heading();
    }

    private double lastSlipDeg = Double.NaN;
    private double slipRate;

    private float[] steer(Ctx c, double want, double bankMax) {

        double tr = track(c), slip = Mth.wrapDegrees(c.heading() - tr);
        double e = Mth.wrapDegrees(want - tr);
        if (c.now() != lastHdgTick) {
            boolean next = c.now() == lastHdgTick + 1;
            hdgRate = next && !Double.isNaN(lastHdgDeg) ? Mth.wrapDegrees(tr - lastHdgDeg) * 20.0 : 0.0;
            slipRate = next && !Double.isNaN(lastSlipDeg) ? Mth.wrapDegrees(slip - lastSlipDeg) * 20.0 : 0.0;
            lastHdgDeg = tr;
            lastSlipDeg = slip;
            lastHdgTick = c.now();
        }
        bankMax = Math.min(bankMax, liftBank(c.vel().length()));
        double bank = Mth.clamp(bankBias + Balance.AP_K_HDG * e - Balance.AP_K_HDG_D * hdgRate, -bankMax, bankMax);
        float rate = (float) Mth.wrapDegrees(c.roll() - lastRollDeg);
        lastRollDeg = (float) c.roll();
        float roll = (float) Mth.clamp(Balance.AP_K_ROLL * (bank - c.roll()) - Balance.AP_K_ROLL_D * rate, -1.0, 1.0);
        float yaw = (float) Mth.clamp(-Balance.AP_K_SLIP * slip - Balance.AP_K_SLIP_D * slipRate, -Balance.AP_YAW_MAX, Balance.AP_YAW_MAX);
        return new float[] { roll, yaw };
    }

    private float climb(Ctx c, double want) {
        double g = gamma(c);
        float rate = (float) (g - lastGammaDeg);
        lastGammaDeg = (float) g;
        float out = (float) Mth.clamp(-Balance.AP_K_PITCH * (want - g) + pitchTrim + Balance.AP_K_PITCH_D * rate, -1.0, 1.0);

        if (Math.abs(out) < 0.99F && Math.abs(want - g) < Balance.AP_PITCH_I_BAND) {
            double inc = -Balance.AP_K_PITCH_I * (want - g);
            if (inc * pitchTrim < 0.0) inc *= Balance.AP_K_PITCH_UNWIND;
            pitchTrim = (float) Mth.clamp(pitchTrim + inc, -Balance.AP_PITCH_TRIM_MAX, Balance.AP_PITCH_TRIM_MAX);
        }

        double v = c.vel().length();
        double shortG = want - g;
        if (out <= -Balance.AP_STICK_LIMIT && pitchTrim <= -Balance.AP_PITCH_TRIM_MAX + 0.05 && g < Math.min(want, 0.0) - Balance.AP_STICK_SHORT && shortG < Balance.AP_STICK_SHORT_MAX
                && Math.abs(rate) < Balance.AP_STICK_STEADY && Math.abs(c.roll()) < Balance.AP_STICK_ROLL) {
            double need = Math.min(v * Math.cos(Math.toRadians(c.roll())) + Balance.AP_STICK_SPD_ADD, stall > 0.0 ? stall * Balance.AP_STICK_MAX_K : Balance.AIRCRAFT_MAX_SPEED * 0.6);
            if (need > vLift + 1.0 && c.now() - vLiftLog > 40) {
                vLiftLog = c.now();
                Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s lift limit: the stick at %.2f still %.1f deg under the path at %.1f b/s: slowest speed now %.1f",
                        c.ship().toString().substring(0, 8), out, want - g, v, need));
            }
            vLift = Math.max(vLift, need);
        } else if (out > -Balance.AP_STICK_EASE) vLift = Math.max(0.0, vLift - Balance.AP_STICK_SPD_DECAY);
        return out;
    }

    private float speed(Ctx c, double want) {
        double e = want - c.vel().length();
        thrTrim = (float) Mth.clamp(thrTrim + Balance.AP_K_SPD_I * e, 0.05, 1.0);
        return (float) Mth.clamp(thrTrim + Balance.AP_K_SPD * e, 0.0, 1.0);
    }

    private double minSpeed() { return Math.max(stall > 0.0 ? stall * Balance.AP_MIN_SPEED_K : Balance.AP_MIN_SPEED_FALLBACK, vLift); }

    private double letGoSpeed() {
        double a = stall > 0.0 ? stall * Balance.AP_STALL_LETGO : 0.0;
        double b = vLift > 0.0 ? (vLift - Balance.AP_STICK_SPD_ADD) * Balance.AP_LETGO_LIFT_K : 0.0;
        return Math.max(a, b);
    }

    private double liftBank(double v) {
        if (v <= 1.0) return Balance.AP_BANK_LIFT_MIN;
        double cb = Math.min(1.0, Balance.AP_BANK_LIFT_K * minSpeed() / v);
        return Math.max(Balance.AP_BANK_LIFT_MIN, Math.toDegrees(Math.acos(cb)));
    }

    private double vrefBase() { return Math.max(stall > 0.0 ? stall * Balance.AL_VREF : Balance.AP_MIN_SPEED_FALLBACK * 1.1, vLift); }

    private double cap(double spd) { return Mth.clamp(spd, minSpeed(), Balance.AIRCRAFT_MAX_SPEED - 5.0); }

    private Out fly(Ctx c, double hdg, double alt, double spd, double bankMax) {
        double want = Math.max(alt, floorAlt(c));
        double err = want - c.at().y;

        if (Math.abs(err) > Balance.AP_PROTECT_ALT || c.vel().y < -Balance.AP_PROTECT_SINK) bankMax = Math.min(bankMax, Balance.AP_BANK_SAFE);

        double agl = aglAhead(c);
        if (agl < Balance.AP_PULLUP_AGL && c.vel().y < -Balance.AP_PULLUP_SINK) {
            if (c.now() - pullupLog > 40) { pullupLog = c.now(); Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s PULL UP: %.1f over the ground, sinking %.1f b/s", c.ship().toString().substring(0, 8), agl, -c.vel().y)); }
            float[] lv = steer(c, track(c), 0.0);
            return new Out(-1.0F, lv[0], lv[1], 1.0F, false);
        }
        float[] s = steer(c, hdg, bankMax);
        double v = Math.max(1.0, c.vel().length());
        double gDown = Math.min(Balance.AP_PITCH_MAX, Math.toDegrees(Math.asin(Math.min(1.0, Balance.AP_VS_DOWN / v))));
        double gUp = Math.min(Balance.AP_PITCH_MAX, Math.toDegrees(Math.asin(Math.min(1.0, Balance.AP_VS_UP / v))));
        double g = Mth.clamp(Balance.AP_K_ALT * err, -gDown, gUp);
        return new Out(climb(c, g), s[0], s[1], energy(c, g, speed(c, cap(spd))), false);
    }

    private long pullupLog = Long.MIN_VALUE / 2;

    private float energy(Ctx c, double gWant, float thr) {
        double shortDeg = gWant - gamma(c) - Balance.AP_E_BAND;
        if (shortDeg <= 0.0) return thr;
        return (float) Math.min(1.0, thr + Balance.AP_K_THR_G * shortDeg);
    }

    private double aglAhead(Ctx c) {
        Vec3 v = c.vel();
        double g0 = ground(c.sl(), c.at().x, c.at().z), g1 = ground(c.sl(), c.at().x + v.x * 2.0, c.at().z + v.z * 2.0);
        double top = Math.max(Double.isNaN(g0) ? -1.0E9 : g0, Double.isNaN(g1) ? -1.0E9 : g1);
        return top < -1.0E8 ? Double.MAX_VALUE : c.at().y - top - (home != null && home.eye() > 0.0 ? home.eye() : Balance.AL_EYE_DEF);
    }

    private boolean floorLifting(Ctx c, double alt) { return floorAlt(c) > alt + 1.0; }

    private double floorAlt(Ctx c) {
        Vec3 v = c.vel();
        double h = Math.hypot(v.x, v.z);
        double dx = h > 1.0 ? v.x / h : -Math.sin(Math.toRadians(-c.heading())), dz = h > 1.0 ? v.z / h : -Math.cos(Math.toRadians(c.heading()));
        double reach = Math.max(32.0, h * Balance.AP_LOOKAHEAD);
        double top = Double.NEGATIVE_INFINITY;
        boolean blind = false;
        for (int fan = -1; fan <= 1; fan++) {
            double a = Math.toRadians(15.0 * fan), ca = Math.cos(a), sa = Math.sin(a);
            double fx = dx * ca - dz * sa, fz = dx * sa + dz * ca;
            for (int k = 0; k <= 4; k++) {
                double d = reach * k / 4.0;
                double g = ground(c.sl(), c.at().x + fx * d, c.at().z + fz * d);
                if (Double.isNaN(g)) blind = true; else top = Math.max(top, g);
            }
        }
        if (top > Double.NEGATIVE_INFINITY) groundSeen = Double.isNaN(groundSeen) ? top : Math.max(top, groundSeen * 0.98 + top * 0.02);
        if (blind && !Double.isNaN(groundSeen)) top = Math.max(top, groundSeen);
        return top == Double.NEGATIVE_INFINITY ? Double.NEGATIVE_INFINITY : top + floor;
    }

    static double ground(ServerLevel sl, double x, double z) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        if (!sl.hasChunk(ix >> 4, iz >> 4)) return Double.NaN;
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
    }

    private static double bearing(Vec3 from, double x, double z) { return (Math.toDegrees(Math.atan2(x - from.x, -(z - from.z))) + 360.0) % 360.0; }

    private int nearestLeg(Ctx c) {
        return 0;
    }

    private Out route(Ctx c) {
        if (route.isEmpty()) { letGo(R_OFF, c.now()); return null; }
        if (leg >= route.size()) leg = 0;
        Wp w = route.get(leg);
        double dist = Math.hypot(w.x() - c.at().x, w.z() - c.at().z);
        double reach = Math.max(Balance.AP_WP_REACH, c.vel().length() * Balance.AP_WP_REACH_S);
        if (dist < reach) {
            leg++;
            if (leg >= route.size()) {
                Wp last = route.get(route.size() - 1);
                if (end == END_LOOP) leg = 0;
                else if (end == END_HOLD) { holdHdg = (float) c.heading(); holdAlt = last.alt(); holdSpd = last.spd(); mode = HOLD; }
                else { orbX = last.x(); orbZ = last.z(); orbAlt = last.alt(); orbSpd = last.spd(); mode = ORBIT; sideResolved = side == SIDE_AUTO ? turretSide(c) : side; }
                return mode == ROUTE ? route(c) : step2(c);
            }
            w = route.get(leg);
        }
        return fly(c, bearing(c.at(), w.x(), w.z()), w.alt(), w.spd(), Balance.AP_BANK_MAX);
    }

    private Out step2(Ctx c) {
        return mode == HOLD ? fly(c, holdHdg, holdAlt, holdSpd, Balance.AP_BANK_MAX) : orbit(c, orbX, orbZ, orbR, orbAlt, orbSpd, sideResolved);
    }

    private Out orbit(Ctx c, double cx, double cz, double r, double alt, double spd, int turn) {
        r = orbitRadius(r, spd);
        double bankCap = Math.min(Double.isNaN(orbitCap) ? Balance.AP_ORBIT_BANK_MAX : orbitCap, liftBank(spd));
        r = Math.max(r, spd * spd / (WingTrails.GRAV * Math.tan(Math.toRadians(bankCap))) * Balance.AP_ORBIT_R_MARGIN);
        orbREff = (float) r;
        double dx = c.at().x - cx, dz = c.at().z - cz, d = Math.hypot(dx, dz);
        double b = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        double corr = Mth.clamp(Balance.AP_K_ORBIT * (d - r) / Math.max(1.0, r), -45.0, 45.0);
        double hdg = turn == SIDE_RIGHT ? b + 90.0 + corr : b - 90.0 - corr;

        Vec3 v = c.vel();
        double h = Math.hypot(v.x, v.z);
        double ff = Math.toDegrees(Math.atan(h * h / (WingTrails.GRAV * Math.max(1.0, r))));
        double bankMax = launchGrace > 0 ? Balance.AP_LAUNCH_BANK : bankCap;
        bankBias = Mth.clamp(turn == SIDE_RIGHT ? ff : -ff, -bankMax, bankMax);
        try { return fly(c, hdg, alt, spd, bankMax); } finally { bankBias = 0.0; }
    }

    static double orbitRadius(double asked, double spd) {
        double min = spd * spd / (WingTrails.GRAV * Math.tan(Math.toRadians(Balance.AP_ORBIT_BANK_MAX))) * Balance.AP_ORBIT_R_MARGIN;
        return Math.max(asked, min);
    }

    private double orbitCap = Double.NaN;

    private double bankBias;

    float orbREff = (float) Balance.AP_ORBIT_R_DEF;

    private int turretSide(Ctx c) {
        int score = 0;
        for (BlockPos q : SableBridge.blockEntitiesOn(c.sl(), c.ship())) {
            if (!c.sl().isLoaded(q)) continue;
            BlockEntity be = c.sl().getBlockEntity(q);
            if (!(be instanceof GunTurretBlockEntity)) continue;
            var st = be.getBlockState();
            if (!st.hasProperty(MachineBlock.FACING)) continue;
            Direction out = Machines.dirToWorld(st.getValue(MachineBlock.FACING), Machines.mount(st), Direction.DOWN);
            Vec3 a = SableBridge.worldPos(c.sl(), q), b = SableBridge.worldPos(c.sl(), q.relative(out));
            if (a == null || b == null) continue;
            double dot = b.subtract(a).dot(c.right());
            if (dot > 0.3) score++; else if (dot < -0.3) score--;
        }
        return score > 0 ? SIDE_RIGHT : SIDE_LEFT;
    }

    private record Geo(Vec3 t, Vec3 u, Vec3 deckVel, double len, UUID deck) {}

    private Geo geo(Ctx c) {
        if (home == null) return null;
        if (home.kind() == 0) {
            double h = Math.toRadians(home.hdg());
            Vec3 u = new Vec3(Math.sin(h), 0.0, -Math.cos(h));
            if (apRev && apRevT != null) return new Geo(apRevT, u.scale(-1.0), Vec3.ZERO, home.len(), null);
            return new Geo(home.t(), u, Vec3.ZERO, home.len(), null);
        }
        Vec3[] f = DeckLightBlockEntity.beaconFrame(c.sl(), home.beacon());
        if (f == null) return null;
        UUID deck = SableBridge.shipIdAt(c.sl(), home.beacon());
        Vec3 dv = Vec3.ZERO;
        if (deck != null) { SableBridge.ShipInfo si = SableBridge.shipInfo(c.sl(), deck); if (si != null) dv = si.linearVelocity(); }
        return new Geo(f[0], f[1], dv, Balance.AL_BEACON_ROLL, deck);
    }

    private Out home(Ctx c) {
        Geo g = geo(c);
        if (g == null) {
            reason = R_NOHOME; reasonAt = c.now();
            return orbit(c, c.at().x, c.at().z, Balance.AP_ORBIT_R_DEF, Math.max(c.at().y, 100.0), cap(minSpeed() * 1.3), SIDE_LEFT);
        }
        double eye = home.eye() > 0.0 ? home.eye() : Balance.AL_EYE_DEF;

        if (Double.isNaN(apH) || ((phase == PH_RETURN || phase == PH_CIRCUIT) && apKnown < 0.999 && c.now() - apAt >= Balance.AL_RESCAN_TICKS)) {
            planApproach(c, g);
            g = geo(c);
            if (g == null) return home(c);
        }
        double slope = Math.toRadians(apSlope);
        double pattern = g.t().y + apH;
        double fixD = apH / Math.tan(slope);
        Vec3 u = g.u(), rgt = new Vec3(-u.z, 0.0, u.x);
        Vec3 rel = c.at().subtract(g.t());
        double along = rel.x * u.x + rel.z * u.z, side = rel.x * rgt.x + rel.z * rgt.z;
        double axisHdg = (Math.toDegrees(Math.atan2(u.x, -u.z)) + 360.0) % 360.0;
        double deckAlong = g.deckVel().x * u.x + g.deckVel().z * u.z;
        double vref = vrefBase() + deckAlong;
        double hT = c.at().y - g.t().y - eye;
        boolean wow = LandingGearBlockEntity.onWheels(c.sl(), c.ship());
        switch (phase) {
            case PH_CIRCLE -> {
                orbitCap = Balance.AL_TURN_BANK;
                try { return orbit(c, g.t().x, g.t().z, Math.max(Balance.AP_ORBIT_R_DEF, fixD * 0.4), pattern + 20.0, cap(vref * 1.25), SIDE_LEFT); } finally { orbitCap = Double.NaN; }
            }
            case PH_RETURN -> {
                Vec3 fix = g.t().subtract(u.scale(fixD));
                if (Math.hypot(c.at().x - fix.x, c.at().z - fix.z) < fixD * 2.5) { phase = PH_CIRCUIT; circuitLeg = -1; }
                return fly(c, bearing(c.at(), fix.x, fix.z), pattern, cap(Math.max(vref * 1.5, holdSpd)), Balance.AP_BANK_MAX);
            }
            case PH_CIRCUIT -> {

                if (circuitLeg < 0) { path = null; circuitLeg = 0; }
                Vec3 entry = g.t().subtract(u.scale(fixD));
                double offAx = Math.abs(Mth.wrapDegrees(track(c) - axisHdg)), before = -along;

                if (before >= fixD * 0.7 && before <= fixD * 1.6 && Math.abs(side) <= Balance.AL_DIRECT_SIDE && offAx <= Balance.AL_DIRECT_HDG
                        && c.vel().length() <= vref * Balance.AL_DIRECT_SPD) {
                    path = null; phase = PH_FINAL; decided = false;
                    if (!gearAsked) { LandingGearBlockEntity.setAll(c.sl(), c.ship(), true); TailhookBlockEntity.setAll(c.sl(), c.ship(), true); gearAsked = true; }
                    return home(c);
                }

                double vPlan = cap(vref * 1.15) * Balance.AL_PLAN_V_PAD;
                double rTurn = vPlan * vPlan / (WingTrails.GRAV * Math.tan(Math.toRadians(Math.min(Balance.AL_PLAN_BANK, liftBank(vPlan))))) * Balance.AL_PLAN_R_MARGIN;
                boolean shrink = path != null && c.now() - pathAt >= 40 && path.r > rTurn * Balance.AL_PLAN_SHRINK;
                if (path == null || (c.now() - pathAt >= 40 && offPath(c, path)) || shrink) {
                    ApPath best = null;
                    double bestCost = Double.MAX_VALUE;
                    for (double k = 1.0; k <= Balance.AL_ENTRY_K_MAX + 1.0E-6; k += 0.1) {
                        Vec3 e = g.t().subtract(u.scale(fixD * k));
                        ApPath p = ApPath.plan(c.at().x, c.at().z, track(c), e.x, e.z, axisHdg, rTurn);
                        if (p == null) continue;
                        double cost = p.length() + fixD * k;
                        if (cost < bestCost) { bestCost = cost; best = p; entry = e; }
                    }
                    path = best;
                    pathAt = c.now();
                    if (c.now() != pathChkTick) { pathChkTick = c.now(); pathChkN = 0; }
                    if (path != null && pathChkN++ < 4 && pathTooLow(c, g, path)) return home(c);
                    if (path != null) Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s plan %s radius %.0f length %.0f (turn %.0f deg, straight %.0f, turn %.0f deg) to the final entry %.0f %.0f heading %.0f, %.0f out%s",
                            c.ship().toString().substring(0, 8), path.name(), path.r, path.length(), path.arc1, path.straight, path.arc2, entry.x, entry.z, axisHdg,
                            Math.hypot(entry.x - g.t().x, entry.z - g.t().z), shrink ? " (planned again: slowed down)" : ""));
                }
                if (path != null) {
                    if (path.seg == 2 && !gearAsked) { LandingGearBlockEntity.setAll(c.sl(), c.ship(), true); TailhookBlockEntity.setAll(c.sl(), c.ship(), true); gearAsked = true; }
                    Out po = followPath(c, path, axisHdg, pattern, cap(vref * 1.15));
                    if (po != null) return po;

                    if (Math.abs(side) > Balance.AL_DIRECT_SIDE * 2.0) { path = null; return home(c); }
                    path = null; phase = PH_FINAL; decided = false;
                    if (!gearAsked) { LandingGearBlockEntity.setAll(c.sl(), c.ship(), true); TailhookBlockEntity.setAll(c.sl(), c.ship(), true); gearAsked = true; }
                    return home(c);
                }
                double[] tgt = circuit(c, g, along, side, axisHdg, fixD);
                if (tgt == null) { phase = PH_FINAL; decided = false; if (!gearAsked) { LandingGearBlockEntity.setAll(c.sl(), c.ship(), true); TailhookBlockEntity.setAll(c.sl(), c.ship(), true); gearAsked = true; } return home(c); }
                if (circuitLeg == 2 && !gearAsked) { LandingGearBlockEntity.setAll(c.sl(), c.ship(), true); TailhookBlockEntity.setAll(c.sl(), c.ship(), true); gearAsked = true; }
                return fly(c, bearing(c.at(), tgt[0], tgt[1]), pattern, cap(vref * 1.15), Balance.AP_BANK_MAX);
            }
            case PH_FINAL -> {
                double d = -along;
                if (d < -fixD * 0.2 || Math.abs(side) > fixD * 0.6) { goAround(c, R_GOAROUND); return home(c); }

                double xk = Math.min(1.0, Math.pow(Balance.AL_XT_V / Math.max(1.0, c.vel().length()), 2.0));
                double hdg = axisHdg + Mth.clamp(-Balance.AL_K_XT * xk * side - Balance.AL_K_XTA * driftDeg(c, g.deckVel(), rgt), -Balance.AL_XT_MAX, Balance.AL_XT_MAX);
                double altWant = Math.min(g.t().y + eye + Math.max(0.0, d) * Math.tan(slope), pattern);

                if (!decided && c.vel().length() > vref * Balance.AL_FINAL_OVERSPEED) { goAround(c, R_GOAROUND); return home(c); }
                if (!decided && d > Balance.AL_FINAL_CLEAR_FROM) {
                    Vec3 hv = new Vec3(c.vel().x, 0.0, c.vel().z);
                    Vec3 ahead = c.at().add(hv.scale(2.0));
                    double gNow = ground(c.sl(), c.at().x, c.at().z), gAhead = ground(c.sl(), ahead.x, ahead.z);
                    double over = c.at().y - eye - Math.max(Double.isNaN(gNow) ? -1.0E9 : gNow, Double.isNaN(gAhead) ? -1.0E9 : gAhead);
                    if (over < Balance.AL_FINAL_CLEAR) { goAround(c, R_GOAROUND); return home(c); }
                }
                float[] s = steer(c, hdg, Balance.AL_BANK_MAX);

                double low = altWant - c.at().y;
                double gWant = -apSlope + Mth.clamp(Balance.AL_K_GS * low, -3.0, apSlope + Balance.AL_GS_UP);
                if (!decided && hT <= Balance.AL_DH) {
                    decided = true;
                    int why = gate(c, g, side, axisHdg, altWant, d, vref);
                    if (why != R_NONE) { goAround(c, why); return home(c); }
                    if (landLevel == LAND_APPROACH) {
                        if (c.pilot()) { letGo(R_MINIMUMS, c.now()); return null; }
                        goAround(c, R_GOAROUND); return home(c);
                    }
                }
                if (decided && hT <= Balance.AL_FLARE) phase = PH_FLARE;
                float thrF = energy(c, gWant, speed(c, vref));

                double vNow = c.vel().length();
                double lowE = low - (vNow * vNow - vref * vref) / (2.0 * WingTrails.GRAV);
                if (lowE > Balance.AL_GS_THR_DEAD) thrF = (float) Math.min(1.0, thrF + Balance.AL_K_GS_THR * (lowE - Balance.AL_GS_THR_DEAD));
                return new Out(climb(c, gWant), s[0], s[1], thrF, false);
            }
            case PH_FLARE -> {
                if (wow) { phase = PH_ROLL; rollTicks = 0; return home(c); }
                double hv = Math.max(1.0, Math.hypot(c.vel().x, c.vel().z));
                double gWant = -Math.toDegrees(Math.atan2(Balance.AL_SINK_MAX, hv));
                float[] s = steer(c, axisHdg + Mth.clamp(-Balance.AL_K_XT * side - Balance.AL_K_XTA * driftDeg(c, g.deckVel(), rgt), -10.0, 10.0), 8.0);
                float thr = hT <= Balance.AL_RETARD ? 0.0F : speed(c, vref);
                if (g.deck() != null) thr = Math.max(thr, 0.6F);
                return new Out(climb(c, gWant), s[0], s[1], thr, false);
            }
            case PH_ROLL -> {
                rollTicks++;
                Vec3 relV = c.vel().subtract(g.deckVel());
                double rv = relV.length();
                if (g.deck() != null && rollTicks > Balance.AL_BOLTER_TICKS && rv > vref * 0.5) { goAround(c, R_BOLTER); return home(c); }
                if (rv < Balance.AL_STOP_SPEED) { mode = PARKED; reason = R_LANDED; reasonAt = c.now(); return new Out(0.0F, 0.0F, 0.0F, 0.0F, true); }
                double e = Mth.wrapDegrees(axisHdg - c.heading());
                float yaw = (float) Mth.clamp(Balance.AL_K_ROLLOUT_H * e - Balance.AL_K_ROLLOUT_X * side, -1.0, 1.0);
                float roll = (float) Mth.clamp(-Balance.AP_K_ROLL * c.roll(), -1.0, 1.0);
                if (chuteAsked == 0 && rollTicks > (shortField ? 0 : 4)) { ShipModes.requestChute(c.ship(), c.now()); chuteAsked = 1; }
                boolean brake = rollTicks > (shortField ? 0 : Balance.AL_BRAKE_DELAY);
                float thr = g.deck() != null && rollTicks <= Balance.AL_BOLTER_TICKS ? 1.0F : 0.0F;
                return new Out(0.25F, roll, yaw, thr, brake);
            }
            case PH_GOAROUND -> {
                if (c.at().y >= pattern - 2.0) { phase = goArounds > Balance.AL_GA_MAX ? PH_CIRCLE : PH_CIRCUIT; circuitLeg = -1; if (phase == PH_CIRCLE) { reason = R_LANDFAIL; reasonAt = c.now(); } }
                float[] s = steer(c, axisHdg, Balance.AP_BANK_MAX * 0.5);
                return new Out(climb(c, Balance.AL_GA_CLIMB), s[0], s[1], 1.0F, false);
            }
            default -> { phase = PH_RETURN; return null; }
        }
    }

    private double apH = Double.NaN, apSlope = Double.NaN, apMin;

    private boolean apRev, apNoWay;
    private Vec3 apRevT;
    private long apAt = Long.MIN_VALUE / 2;
    private double apKnown = 1.0, apPathMin;
    private long pathChkTick = Long.MIN_VALUE;
    private int pathChkN;

    private void resetApproach() { shortField = false; apH = Double.NaN; apMin = 0.0; apRev = false; apRevT = null; apNoWay = false; apKnown = 1.0; apPathMin = 0.0; }

    private void planApproach(Ctx c, Geo g) {
        boolean first = Double.isNaN(apH);
        Vec3 tF = g.t(), uF = g.u();
        if (home.kind() == 0) { double hr = Math.toRadians(home.hdg()); tF = home.t(); uF = new Vec3(Math.sin(hr), 0.0, -Math.cos(hr)); }
        double tdY = tF.y;
        double h = Mth.clamp(c.at().y - tdY, Balance.AL_PATTERN, Balance.AL_APPROACH_MAX);
        double gMax = Double.NEGATIVE_INFINITY;
        int n = (int) (Balance.AL_TERRAIN_R / 32.0), all = 0, seen = 0;
        for (int i = -n; i <= n; i++) for (int k = -n; k <= n; k++) {
            if (i * i + k * k > n * n) continue;
            all++;
            double gy = ground(c.sl(), tF.x + i * 32.0, tF.z + k * 32.0);
            if (!Double.isNaN(gy)) { seen++; gMax = Math.max(gMax, gy); }
        }
        double known = all > 0 ? seen / (double) all : 1.0;
        if (gMax > Double.NEGATIVE_INFINITY) h = Math.max(h, gMax - tdY + Balance.AL_TERRAIN_MARGIN);
        h = Math.max(h, floor + Math.max(0.0, gMax > Double.NEGATIVE_INFINITY ? gMax - tdY : 0.0));
        if (known < Balance.AL_SCAN_KNOWN) h = Math.max(h, Balance.AL_APPROACH_MAX);
        h = Math.min(h, Math.max(Balance.AL_APPROACH_HARD, gMax > Double.NEGATIVE_INFINITY ? gMax - tdY + Balance.AL_TERRAIN_MARGIN : 0.0));
        h = Math.max(h, apMin);
        h = Math.max(h, apPathMin);
        double[] fw = glideNeed(c, tF, uF, h);
        double[] use = fw;
        boolean rev = false;
        Vec3 tR = null;
        double other = Double.NaN;
        if (fw[0] > Balance.AL_SLOPE_MAX && home.kind() == 0) {
            Vec3 far = tF.add(uF.scale(home.len()));
            double gyR = ground(c.sl(), far.x, far.z);
            tR = new Vec3(far.x, Double.isNaN(gyR) ? tdY : gyR, far.z);
            double[] rv = glideNeed(c, tR, uF.scale(-1.0), h);
            other = rv[0];
            if (rv[0] < fw[0]) { rev = true; use = rv; }
        }
        double slope = Math.min(use[0], Balance.AL_SLOPE_MAX);
        known = Math.min(known, use[3]);
        boolean changed = first || rev != apRev || Math.abs(h - apH) > 2.0 || Math.abs(slope - apSlope) > 0.2;
        double oldFix = first ? Double.NaN : apH / Math.tan(Math.toRadians(apSlope));
        apRev = rev; apRevT = rev ? tR : null;
        apH = h; apSlope = slope; apAt = c.now(); apKnown = known;
        if (!changed) return;
        if (!first && (rev || Double.isNaN(oldFix) || Math.abs(h / Math.tan(Math.toRadians(slope)) - oldFix) > 30.0)) path = null;
        String sh = c.ship().toString().substring(0, 8);
        Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s approach: height %.0f over touchdown, glide %.1f deg%s, final %.0f long (highest ground near: %s, %.0f%% of it loaded%s)%s",
                sh, h, slope, use[1] > 0.0 ? String.format(java.util.Locale.ROOT, ", steepened by ground at y %.0f %.0f out", use[1], use[2]) : "",
                h / Math.tan(Math.toRadians(slope)), gMax > Double.NEGATIVE_INFINITY ? String.format(java.util.Locale.ROOT, "%.0f", gMax) : "not loaded", known * 100.0,
                known < Balance.AL_SCAN_KNOWN ? ": flying high until it loads" : "",
                rev ? String.format(java.util.Locale.ROOT, " | LANDING THE OTHER WAY: the marked way needs %.1f deg", fw[0]) : ""));

        if (home.kind() == 0) {
            double vTd = vrefBase();
            double[] pc = SableBridge.physicsAt(c.sl(), c.at()), pt = SableBridge.physicsAt(c.sl(), new Vec3(tF.x, tdY + 2.0, tF.z));
            if (stall > 0.0 && pc != null && pt != null && pt[0] > 1.0E-6) vTd = Math.max(stall * pc[0] / pt[0] * Balance.AL_VREF, vLift);
            double need = stopDistance(c, vTd, slope, true);
            if (need > home.len() * Balance.AL_STOP_PLAN_SLACK) {
                Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s strip too short: landing at %.0f b/s needs %.0f blocks even braking hard (%s), the strip is %.0f: circling over it, mark a longer strip or land by hand",
                        sh, vTd, need, DragChuteBlockEntity.stateAboard(c.sl(), c.ship()) == DragChuteBlockEntity.PACKED ? "brakes + chute" : "brakes only, no packed chute", home.len()));
                apNoWay = true;
                phase = PH_CIRCLE; reason = R_LANDFAIL; reasonAt = c.now();
                return;
            }
        }
        if (use[0] > Balance.AL_SLOPE_MAX + 0.01) {
            if (!apNoWay) Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s no approach clears the terrain: the marked way needs %.1f deg%s, at most %.0f: circling over it, land by hand",
                    sh, fw[0], Double.isNaN(other) ? "" : String.format(java.util.Locale.ROOT, ", the other way %.1f", other), Balance.AL_SLOPE_MAX));
            apNoWay = true;
            phase = PH_CIRCLE; reason = R_LANDFAIL; reasonAt = c.now();
        }
    }

    private double[] glideNeed(Ctx c, Vec3 t, Vec3 u, double h) {
        double slope = Math.max(Balance.AL_SLOPE, Math.toDegrees(Math.atan(h / Balance.AL_FINAL_MAX)));
        double fixD = h / Math.tan(Math.toRadians(slope));
        Vec3 r = new Vec3(-u.z, 0.0, u.x);
        double sy = 0.0, sd = 0.0;
        int all = 0, seen = 0;
        for (double d = 16.0; d <= fixD + 16.0; d += 16.0)
            for (double s = -8.0; s <= 8.0; s += 8.0) {
                all++;
                double gy = ground(c.sl(), t.x - u.x * d + r.x * s, t.z - u.z * d + r.z * s);
                if (Double.isNaN(gy)) continue;
                seen++;
                double clr = Balance.AL_GLIDE_CLEAR * Math.min(1.0, d / Balance.AL_GLIDE_CLEAR_D);
                double need = Math.toDegrees(Math.atan((gy - t.y + clr) / d));
                if (need > slope) { slope = need; sy = gy; sd = d; }
            }
        return new double[] { slope, sy, sd, all > 0 ? seen / (double) all : 1.0 };
    }

    private boolean pathTooLow(Ctx c, Geo g, ApPath p) {
        double top = Double.NEGATIVE_INFINITY;
        int all = 0, seen = 0;
        for (double[] q : p.points(Balance.AL_PATH_STEP)) {
            all++;
            double gy = ground(c.sl(), q[0], q[1]);
            if (Double.isNaN(gy)) continue;
            seen++;
            top = Math.max(top, gy);
        }
        double need = top > Double.NEGATIVE_INFINITY ? top - g.t().y + Balance.AL_TERRAIN_MARGIN : 0.0;
        double known = all > 0 ? seen / (double) all : 1.0;
        if (known < Balance.AL_SCAN_KNOWN) need = Math.max(need, Balance.AL_APPROACH_MAX);
        need = Math.min(need, Math.max(Balance.AL_APPROACH_HARD, top > Double.NEGATIVE_INFINITY ? top - g.t().y + Balance.AL_TERRAIN_MARGIN : 0.0));
        if (need <= apH + 2.0) return false;
        Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[ap] %s the planned circuit crosses ground at y %s (%.0f%% of it loaded): approach height %.0f -> %.0f",
                c.ship().toString().substring(0, 8), top > Double.NEGATIVE_INFINITY ? String.format(java.util.Locale.ROOT, "%.0f", top) : "?", known * 100.0, apH, need));
        apPathMin = need;
        apH = Double.NaN;
        path = null;
        return true;
    }

    private double stopDistance(Ctx c, double v, double slopeDeg, boolean hard) {
        boolean chute = DragChuteBlockEntity.stateAboard(c.sl(), c.ship()) == DragChuteBlockEntity.PACKED;
        double tanS = Math.tan(Math.toRadians(Math.max(0.5, Double.isNaN(slopeDeg) ? Balance.AL_SLOPE : slopeDeg)));
        double d = Math.max(0.0, Balance.AL_FLARE / Math.max(0.5, (v * tanS + Balance.AL_SINK_MAX) / 2.0) * v - Balance.AL_FLARE / tanS);
        double brake = Balance.GEAR_BRAKE_MU * WingTrails.GRAV, dt = 0.05;
        for (int tick = 0; v > Balance.AL_STOP_SPEED && tick < 4_000; tick++) {
            double a = Balance.GEAR_ROLL * v;
            if (tick > (hard ? 0 : Balance.AL_BRAKE_DELAY)) a += Math.min(brake, Balance.GEAR_BRAKE * v);
            if (chute && tick > (hard ? 0 : 4) && v > Balance.CHUTE_MIN_SPEED) a += Math.min(Balance.CHUTE_MAX_DECEL, Balance.CHUTE_DRAG_K * v * v);
            d += v * dt;
            v = Math.max(0.0, v - a * dt);
        }
        return d;
    }

    private boolean shortField;

    private void goAround(Ctx c, int why) {
        apMin = Double.isNaN(apH) ? 0.0 : apH; apH = Double.NaN;
        goArounds++;
        shortField = false;
        phase = PH_GOAROUND; decided = false;
        reason = why; reasonAt = c.now();
    }

    private static double latV(Ctx c, Geo g, Vec3 rgt) {
        Vec3 v = c.vel().subtract(g.deckVel());
        return v.x * rgt.x + v.z * rgt.z;
    }

    private static double driftDeg(Ctx c, Vec3 deckVel, Vec3 rgt) {
        Vec3 v = c.vel().subtract(deckVel);
        double h = Math.max(1.0, Math.hypot(v.x, v.z));
        return Math.toDegrees(Math.asin(Mth.clamp((v.x * rgt.x + v.z * rgt.z) / h, -1.0, 1.0)));
    }

    private int gate(Ctx c, Geo g, double side, double axisHdg, double altWant, double d, double vref) {

        String sh = c.ship().toString().substring(0, 8);
        double hOff = Mth.wrapDegrees(axisHdg - track(c)), gsOff = c.at().y - altWant, gsTol = Math.max(1.0, d * Math.tan(Math.toRadians(Balance.AL_GATE_GS)));
        double v = c.vel().length();
        double fast = g.deck() != null ? Balance.AL_GATE_SPD : Balance.AL_GATE_SPD_FAST;
        String why = Math.abs(side) > Balance.AL_GATE_SIDE ? String.format(java.util.Locale.ROOT, "%.1f off the centre line (allowed %.0f)", side, Balance.AL_GATE_SIDE)
                : Math.abs(hOff) > Balance.AL_GATE_HDG ? String.format(java.util.Locale.ROOT, "track %.0f deg off the runway (allowed %.0f)", hOff, Balance.AL_GATE_HDG)
                : Math.abs(gsOff) > gsTol ? String.format(java.util.Locale.ROOT, "%.1f %s the glide (allowed %.1f)", Math.abs(gsOff), gsOff < 0 ? "under" : "over", gsTol)
                : v > vref * (1.0 + fast) || v < vref * (1.0 - Balance.AL_GATE_SPD_SLOW) ? String.format(java.util.Locale.ROOT, "speed %.1f against vref %.1f (allowed %.0f%% slow .. %.0f%% fast)", v, vref, Balance.AL_GATE_SPD_SLOW * 100, fast * 100)
                : LandingGearBlockEntity.stateAboard(c.sl(), c.ship()) % 10 != 2 ? "landing gear not down and locked"
                : !clear(c, g) ? "the strip is not clear"
                : g.deck() == null && stopDistance(c, v, apSlope, true) > g.len() ? String.format(java.util.Locale.ROOT, "the strip is too short: %.0f blocks to stop from %.1f b/s even braking hard, the strip is %.0f", stopDistance(c, v, apSlope, true), v, g.len())
                : null;
        if (why == null) {
            shortField = g.deck() == null && stopDistance(c, v, apSlope, false) * Balance.AL_STOP_MARGIN > g.len();
            Wmd.LOGGER.info("[ap] {} decision: LAND (off the line {}, glide {}, speed {} / vref {}){}", sh, String.format(java.util.Locale.ROOT, "%.1f", side), String.format(java.util.Locale.ROOT, "%+.1f", gsOff),
                    String.format(java.util.Locale.ROOT, "%.1f", v), String.format(java.util.Locale.ROOT, "%.1f", vref),
                    shortField ? String.format(java.util.Locale.ROOT, " SHORT STRIP: brakes + chute at touchdown (%.0f of %.0f blocks)", stopDistance(c, v, apSlope, true), g.len()) : "");
            return R_NONE;
        }
        Wmd.LOGGER.info("[ap] {} decision: GO AROUND: {}", sh, why);
        return R_GOAROUND;
    }

    private boolean clear(Ctx c, Geo g) {
        if (g.deck() != null) return !DeckLightBlockEntity.foulDeck(c.sl(), g.deck());
        Vec3 u = g.u(), r = new Vec3(-u.z, 0.0, u.x);
        double w = Balance.AL_CLEAR_W, top = g.t().y + 1.5;
        for (double a = 0.0; a <= g.len(); a += 2.0)
            for (double s = -w; s <= w; s += 2.0) {
                double gy = ground(c.sl(), g.t().x + u.x * a + r.x * s, g.t().z + u.z * a + r.z * s);
                if (!Double.isNaN(gy) && gy > top) return false;
            }
        Vec3 end = g.t().add(u.scale(g.len()));
        AABB box = new AABB(Math.min(g.t().x, end.x) - w, g.t().y - 1.0, Math.min(g.t().z, end.z) - w, Math.max(g.t().x, end.x) + w, g.t().y + 4.0, Math.max(g.t().z, end.z) + w);
        for (Entity e : c.sl().getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive()))
            if (!c.ship().equals(SableBridge.shipUnder(e))) return false;
        for (SableBridge.ShipInfo si : SableBridge.shipsIntersecting(c.sl(), box)) if (!si.id().equals(c.ship())) return false;
        return true;
    }

    private ApPath path;
    private long pathAt;

    private Out followPath(Ctx c, ApPath p, double finalHdg, double alt, double spd) {
        double h = track(c);
        if (p.seg == 0) {
            double rem = ApPath.turn(h, p.segHdg, p.s1);
            if (p.arc1 < 8.0 || rem < 8.0 || rem > 352.0) p.seg = 1;
            else return arc(c, p.c1x, p.c1z, p.r, p.s1, alt, spd);
        }
        if (p.seg == 1) {
            double dx = Math.sin(Math.toRadians(p.segHdg)), dz = -Math.cos(Math.toRadians(p.segHdg)), rx = -dz, rz = dx;
            double ahead = (c.at().x - p.t2x) * dx + (c.at().z - p.t2z) * dz;
            if (p.straight < 1.0 || ahead >= -p.r * 0.05) p.seg = 2;
            else {
                double cross = (c.at().x - p.t1x) * rx + (c.at().z - p.t1z) * rz;
                return fly(c, p.segHdg + Mth.clamp(-Balance.AL_K_XT * cross - Balance.AL_K_XTA * driftDeg(c, Vec3.ZERO, new Vec3(rx, 0.0, rz)), -Balance.AL_XT_MAX, Balance.AL_XT_MAX), alt, spd, Balance.AP_BANK_MAX);
            }
        }
        double rem = ApPath.turn(h, finalHdg, p.s2);
        if (p.arc2 < 5.0 || rem < 10.0 || rem > 350.0) return null;
        return arc(c, p.c2x, p.c2z, p.r, p.s2, alt, spd);
    }

    private Out arc(Ctx c, double cx, double cz, double r, int s, double alt, double spd) {
        double hdg = ApPath.arcHeading(c.at().x, c.at().z, cx, cz, r, s, Balance.AP_K_ORBIT);
        double hv = Math.hypot(c.vel().x, c.vel().z);
        double ff = Math.toDegrees(Math.atan(hv * hv / (WingTrails.GRAV * Math.max(1.0, r))));
        bankBias = Mth.clamp(s > 0 ? ff : -ff, -Balance.AL_TURN_BANK, Balance.AL_TURN_BANK);
        try { return fly(c, hdg, alt, spd, Balance.AL_TURN_BANK); } finally { bankBias = 0.0; }
    }

    private boolean offPath(Ctx c, ApPath p) {
        double lim = p.r * 0.5;
        if (p.seg == 0) return Math.abs(Math.hypot(c.at().x - p.c1x, c.at().z - p.c1z) - p.r) > lim;
        if (p.seg == 2) return Math.abs(Math.hypot(c.at().x - p.c2x, c.at().z - p.c2z) - p.r) > lim;
        double dx = Math.sin(Math.toRadians(p.segHdg)), dz = -Math.cos(Math.toRadians(p.segHdg));
        return Math.abs((c.at().x - p.t1x) * -dz + (c.at().z - p.t1z) * dx) > lim;
    }

    private double[] circuit(Ctx c, Geo g, double along, double side, double axisHdg, double fixD) {
        Vec3 u = g.u(), r = new Vec3(-u.z, 0.0, u.x);
        Vec3 fix = g.t().subtract(u.scale(fixD)), out = g.t().subtract(u.scale(fixD + Balance.AL_CIRCUIT_OUT));
        double before = -along;
        double off = Math.abs(Mth.wrapDegrees(track(c) - axisHdg));
        if (circuitLeg < 0) {
            circuitLeg = before >= fixD * 0.7 && Math.abs(side) <= before * 0.5 ? 2 : before >= fixD * 0.7 ? 1 : 0;
            circuitSide = side >= 0.0 ? 1.0 : -1.0;
        }
        if (circuitLeg == 0) {
            double tx = out.x + r.x * circuitSide * Balance.AL_CIRCUIT_SIDE, tz = out.z + r.z * circuitSide * Balance.AL_CIRCUIT_SIDE;
            if (Math.hypot(c.at().x - tx, c.at().z - tz) < Balance.AL_LEG_REACH) circuitLeg = 1;
            else return new double[] { tx, tz };
        }
        if (circuitLeg == 1) {
            if (Math.hypot(c.at().x - out.x, c.at().z - out.z) < Balance.AL_LEG_REACH) circuitLeg = 2;
            else return new double[] { out.x, out.z };
        }
        if (Math.hypot(c.at().x - fix.x, c.at().z - fix.z) < Balance.AL_LEG_REACH && off < 25.0) { circuitLeg = -1; return null; }
        if (before < fixD * 0.5) { circuitLeg = -1; return new double[] { out.x, out.z }; }
        return new double[] { fix.x, fix.z };
    }

    private void refreshStall(Ctx c, boolean now) {
        if (!AirGate.on()) return;
        if (!now && c.now() - stallAt < Balance.AP_STALL_REFRESH) return;
        stallAt = c.now();
        CompoundTag t = FlightCheck.compute(c.sl(), c.ship());
        stall = t == null ? -1.0 : t.getDouble("stall");
    }

    private boolean damaged(Ctx c) {
        SableBridge.ShipInfo si = SableBridge.shipInfo(c.sl(), c.ship());
        int[] e = AirEngineBlockEntity.systemsAboard(c.sl(), c.ship());
        boolean hit = false;
        if (si != null) {
            long drop = Math.max(ownDropAt, OWN_DROPS.getOrDefault(c.ship(), Long.MIN_VALUE / 2));
            if (lastBlocks >= 0 && si.blockCount() < lastBlocks && c.now() - drop > Balance.AP_OWN_DROP_TICKS + DROP_SEEN_LATE) hit = true;
            lastBlocks = si.blockCount();
        }
        if (e[1] > lastDamaged) hit = true;
        lastDamaged = e[1];
        return hit;
    }

    private boolean fuelCheck(Ctx c) {
        int[] f = PowerCoreBlockEntity.fuelOf(c.sl(), c.ship());
        if (f[1] <= 0) return false;
        float share = f[0] / (float) f[1];
        if (Float.isNaN(fuelThen) || c.now() - fuelThenAt > 400) { fuelThen = share; fuelThenAt = c.now(); }
        fuelShare = share;
        if (mode == HOME) return false;
        if (home != null) {
            double burnPerTick = (fuelThen - share) / Math.max(1.0, c.now() - fuelThenAt);
            Geo g = geo(c);
            if (g != null && burnPerTick > 1.0E-7 && c.now() - fuelThenAt >= 100) {
                double dist = Math.hypot(g.t().x - c.at().x, g.t().z - c.at().z) + Balance.AL_PATTERN / Math.tan(Math.toRadians(Balance.AL_SLOPE)) * 3.0;
                double ticksHome = dist / Math.max(10.0, c.vel().length()) * 20.0;
                if (share < burnPerTick * ticksHome * (1.0 + Balance.AL_BINGO_MARGIN)) { goHome(c, R_BINGO); return false; }
            }
            return false;
        }
        if (share < Balance.AP_FUEL_MIN) { letGo(R_FUEL, c.now()); return true; }
        return false;
    }

    private boolean crewCheck(Ctx c) {
        if (crewless) { emptyTicks = 0; return false; }
        boolean any = false;
        for (ServerPlayer p : c.sl().players()) {
            if (p.isSpectator()) continue;
            Entity on = p.getVehicle() != null ? p.getVehicle() : p;
            if (c.ship().equals(SableBridge.shipUnder(on)) || c.ship().equals(SableBridge.shipUnder(p))) { any = true; break; }
        }
        emptyTicks = any ? 0 : emptyTicks + 20;
        if (emptyTicks < Balance.AP_EMPTY_TICKS) return false;
        if (home != null) { if (mode != HOME) goHome(c, R_HOMEEMPTY); return false; }
        outT = 0.0F;
        letGo(R_EMPTY, c.now());
        return true;
    }

    private void goHome(Ctx c, int why) {
        resetApproach();
        mode = HOME; phase = landLevel == LAND_RETURN ? PH_CIRCLE : PH_RETURN; goArounds = 0; decided = false; gearAsked = false; circuitLeg = -1;
        reason = why; reasonAt = c.now();
    }

    private void record(Ctx c) {
        boolean wow = LandingGearBlockEntity.onWheels(c.sl(), c.ship());
        double v = c.vel().length();
        if (wow && v < Balance.AL_ROLL_START_SPEED) {
            rollStart = c.at(); rolling = true;
            double gy = ground(c.sl(), c.at().x, c.at().z);
            rollEye = Double.isNaN(gy) ? Double.NaN : c.at().y - gy;
            return;
        }
        if (!rolling || rollStart == null) return;
        if (wow) return;
        rolling = false;

    }

    private double eyeNow() { return !Double.isNaN(rollEye) && rollEye > 0.5 && rollEye < 8.0 ? rollEye : Balance.AL_EYE_DEF; }

    String markMap(ServerLevel sl, double x, double z, double hdg, double len) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(hdg) || !Double.isFinite(len)) return "message.wmd.ap.home_bad";
        if (len < Balance.AL_STRIP_MIN) return "message.wmd.ap.home_short";
        double gy = ground(sl, x, z);
        if (Double.isNaN(gy)) return "message.wmd.ap.home_unloaded";
        resetApproach();
        home = new Home(0, new Vec3(x, gy, z), ((hdg % 360.0) + 360.0) % 360.0, Math.min(len, Balance.AL_STRIP_MAX), null, eyeNow());
        return null;
    }

    String markHere(Ctx c) {
        if (!LandingGearBlockEntity.onWheels(c.sl(), c.ship()) || c.vel().length() > Balance.AL_ROLL_START_SPEED) return "message.wmd.ap.home_wheels";
        double gy = ground(c.sl(), c.at().x, c.at().z);
        if (!Double.isNaN(gy) && c.at().y - gy > 0.5 && c.at().y - gy < 8.0) rollEye = c.at().y - gy;
        BlockPos b = DeckLightBlockEntity.beaconNear(c.sl(), c.at(), Balance.AL_BEACON_NEAR);
        if (b != null) { resetApproach(); home = new Home(1, null, 0.0, 0.0, b, eyeNow()); return null; }
        if (Double.isNaN(gy) || c.at().y - gy > 8.0) return "message.wmd.ap.home_deck";
        resetApproach();
        home = new Home(0, new Vec3(c.at().x, gy, c.at().z), c.heading(), Balance.AL_STRIP_HERE, null, eyeNow());
        return null;
    }

    void clearHome() { home = null; resetApproach(); }

    ApHudPayload hud(Ctx c) {
        float tx = Float.NaN, ty = 0, tz = 0, alt = 0, spd = 0, hdg = 0, dist = 0;
        int legs = route.size();
        if (mode == ROUTE && leg < route.size()) { Wp w = route.get(leg); tx = (float) w.x(); ty = w.alt(); tz = (float) w.z(); alt = w.alt(); spd = w.spd(); }
        else if (mode == ORBIT) { tx = (float) orbX; ty = orbAlt; tz = (float) orbZ; alt = orbAlt; spd = orbSpd; }
        else if (mode == HOLD) { alt = holdAlt; spd = holdSpd; hdg = holdHdg; }
        else if (mode == HOME || mode == PARKED) { Geo g = geo(c); if (g != null) { tx = (float) g.t().x; ty = (float) g.t().y; tz = (float) g.t().z; } }
        if (!Float.isNaN(tx)) dist = (float) Math.hypot(tx - c.at().x, tz - c.at().z);
        int flags = (flying() && floorLifting(c, mode == ROUTE && leg < route.size() ? route.get(leg).alt() : mode == ORBIT ? orbAlt : holdAlt) ? ApHudPayload.F_FLOOR : 0)
                | (home != null ? ApHudPayload.F_HOME : 0) | (sideResolved == SIDE_RIGHT ? ApHudPayload.F_RIGHT : 0);
        return new ApHudPayload(c.cockpit(), mode, phase, reasonShown(c.now()), leg, legs, flags, tx, ty, tz, alt, spd, hdg, dist, mode == ORBIT || (mode == ROUTE && end == END_ORBIT) ? orbREff : (float) orbR, outT);
    }

    CompoundTag screenTag(Ctx c) {
        CompoundTag t = save();
        t.putInt("mode", mode); t.putInt("leg", leg); t.putInt("phase", phase);
        t.putDouble("x", c.at().x); t.putDouble("y", c.at().y); t.putDouble("z", c.at().z); t.putDouble("hdg", c.heading()); t.putDouble("v", c.vel().length());
        refreshStall(c, false);
        t.putDouble("stall", stall); t.putDouble("min", minSpeed()); t.putDouble("cap", Balance.AIRCRAFT_MAX_SPEED);
        t.putFloat("fuel", Float.isNaN(fuelShare) ? -1.0F : fuelShare);
        if (home != null) {
            Geo g = geo(c);
            if (g != null) { t.putDouble("hx", g.t().x); t.putDouble("hy", g.t().y); t.putDouble("hz", g.t().z); t.putDouble("hh", (Math.toDegrees(Math.atan2(g.u().x, -g.u().z)) + 360.0) % 360.0); t.putDouble("hl", g.len()); t.putBoolean("hb", home.kind() == 1); }
        }
        return t;
    }

    void apply(CompoundTag in) {
        if (in.contains("hh")) holdHdg = (float) ((fin(in.getFloat("hh"), holdHdg) % 360.0 + 360.0) % 360.0);
        if (in.contains("ha")) holdAlt = clampAlt(fin(in.getFloat("ha"), holdAlt));
        if (in.contains("hs")) holdSpd = (float) Mth.clamp(fin(in.getFloat("hs"), holdSpd), 10.0, Balance.AIRCRAFT_MAX_SPEED);
        if (in.contains("rt", Tag.TAG_LIST)) {
            ListTag l = in.getList("rt", Tag.TAG_COMPOUND);
            route.clear();
            for (int i = 0; i < l.size() && i < Balance.AP_WP_MAX; i++) {
                CompoundTag w = l.getCompound(i);
                double x = w.getDouble("x"), z = w.getDouble("z");
                if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7) continue;
                route.add(new Wp(x, z, clampAlt(fin(w.getFloat("a"), 120.0F)), (float) Mth.clamp(fin(w.getFloat("s"), 55.0F), 10.0, Balance.AIRCRAFT_MAX_SPEED)));
            }
            leg = 0;
        }
        if (in.contains("end")) end = Mth.clamp(in.getInt("end"), 0, 2);
        if (in.contains("ox")) { double x = in.getDouble("ox"), z = in.getDouble("oz"); if (Double.isFinite(x) && Double.isFinite(z) && Math.abs(x) < 3.0E7 && Math.abs(z) < 3.0E7) { orbX = x; orbZ = z; } }
        if (in.contains("or")) orbR = (float) Mth.clamp(fin(in.getFloat("or"), orbR), Balance.AP_ORBIT_R_MIN, Balance.AP_ORBIT_R_MAX);
        if (in.contains("oa")) orbAlt = clampAlt(fin(in.getFloat("oa"), orbAlt));
        if (in.contains("os")) orbSpd = (float) Mth.clamp(fin(in.getFloat("os"), orbSpd), 10.0, Balance.AIRCRAFT_MAX_SPEED);
        if (in.contains("side")) side = Mth.clamp(in.getInt("side"), 0, 2);
        if (in.contains("floor")) floor = Mth.clamp(in.getInt("floor"), Balance.AP_FLOOR_MIN, Balance.AP_FLOOR_MAX);
        if (in.contains("land")) landLevel = Mth.clamp(in.getInt("land"), 0, 2);
    }

    private static float fin(float v, float fallback) { return Float.isFinite(v) ? v : fallback; }
    private static float clampAlt(float a) { return Mth.clamp(a, -32.0F, 600.0F); }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("hh", holdHdg); t.putFloat("ha", holdAlt); t.putFloat("hs", holdSpd);
        ListTag l = new ListTag();
        for (Wp w : route) { CompoundTag c = new CompoundTag(); c.putDouble("x", w.x()); c.putDouble("z", w.z()); c.putFloat("a", w.alt()); c.putFloat("s", w.spd()); l.add(c); }
        t.put("rt", l);
        t.putInt("end", end); t.putDouble("ox", orbX); t.putDouble("oz", orbZ); t.putFloat("or", orbR); t.putFloat("oa", orbAlt); t.putFloat("os", orbSpd);
        t.putInt("side", side); t.putInt("floor", floor); t.putInt("land", landLevel);
        if (home != null) {
            CompoundTag h = new CompoundTag();
            h.putInt("k", home.kind()); h.putDouble("hdg", home.hdg()); h.putDouble("len", home.len()); h.putDouble("eye", home.eye());
            if (home.t() != null) { h.putDouble("tx", home.t().x); h.putDouble("ty", home.t().y); h.putDouble("tz", home.t().z); }
            if (home.beacon() != null) h.putLong("b", home.beacon().asLong());
            t.put("home", h);
        }
        return t;
    }

    void load(CompoundTag t) {
        apply(t);
        if (t.contains("home", Tag.TAG_COMPOUND)) {
            CompoundTag h = t.getCompound("home");
            int k = h.getInt("k");
            Vec3 tp = h.contains("tx") ? new Vec3(h.getDouble("tx"), h.getDouble("ty"), h.getDouble("tz")) : null;
            BlockPos b = h.contains("b") ? BlockPos.of(h.getLong("b")) : null;
            if ((k == 0 && tp != null) || (k == 1 && b != null)) home = new Home(k, tp, h.getDouble("hdg"), h.getDouble("len"), b, h.getDouble("eye"));
        } else home = null;
    }

    static long ownDropAt(UUID ship) { return ship == null ? Long.MIN_VALUE / 2 : OWN_DROPS.getOrDefault(ship, Long.MIN_VALUE / 2); }

    static boolean crewAboard(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return false;
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator()) continue;
            Entity on = p.getVehicle() != null ? p.getVehicle() : p;
            if (ship.equals(SableBridge.shipUnder(on)) || ship.equals(SableBridge.shipUnder(p))) return true;
        }
        return false;
    }

    static double bearingTo(Vec3 from, double x, double z) { return bearing(from, x, z); }
}
