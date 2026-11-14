package com.ashraf.wmd;

public final class BayAnim {

    private BayAnim() {}

    public static final double DOCK_Z = 14, WELD_Z = 86, CRANE_Z = 280, WARHEAD_Z = 309, CLEAN_Z = 445, PAINT_IN = 490, PAINT_FAR = 730, PAINT_END = 600, CRYO_IN = 660, CRYO_OUT = 852;

    public static final double T1_LEN = 160;

    public static final double LOADER_REST = 24, GRIP_DZ = 12, STOCK0_Z = 36, ARM_RACK = -24, ARM_LINE = 28, LIFT = 30;

    public static final double[] SEG_C = { 66, 18, 127, 146 };

    public static final String[] SEG = { "air", "motor", "guid", "war" };

    public static final double CR_ORIGIN = 256, CR_STOCK_Z = 88, CR_GRIP = 10, CR_PUSH = 12, CR_REST = 56, CR_DY_PICK = -48.62, CR_DY_CARRY = -22.6, CR_TROL = -52;

    public static final double WL_ORIGIN = 384, WL_REST = 62, WL_SX = -34, WL_DN = 16.62, WL_UP = -8, WL_PUSH = 12;
    public static final double[] WL_STOCK = { 0, 0, 38, 76 }, WL_FIT = { 0, 0, 52, 71 }, WL_GRIP = { 0, 0, 1, 8 };

    public static double atPart(int cal, int k, double t1Z) { return t1Z + SEG_C[k] * (1.0 - BayCalibre.len(cal)); }
    public static double atMid(int cal, double t1Z) { return t1Z - (BayCalibre.len(cal) - 1.0) * T1_LEN / 2.0; }
    public static double atNose(int cal, double t1Z) { return t1Z - (BayCalibre.len(cal) - 1.0) * T1_LEN; }

    public static double dockZ(int cal) { return DOCK_Z; }

    public static final double AIR_NOSE = 120, WELD_NOSE_MIN = 228;
    public static double weldZ(int cal) { double z = atPart(cal, 0, WELD_Z); return z + Math.max(0.0, WELD_NOSE_MIN - (z + AIR_NOSE * BayCalibre.len(cal))); }

    public static double loaderRest(int cal) { return LOADER_REST - 6.0 * (BayCalibre.len(cal) - 1.0); }

    public static final double JOINT = 36;
    public static double craneZ(int cal) { return CRANE_Z + JOINT * (1.0 - BayCalibre.len(cal)); }
    public static double warheadZ(int cal) { return atPart(cal, 2, WARHEAD_Z); }
    public static double cleanZ(int cal) { return atMid(cal, CLEAN_Z); }
    public static double paintIn(int cal) { return atNose(cal, PAINT_IN); }
    public static double paintFar(int cal) { return PAINT_FAR; }
    public static double paintEnd(int cal) { return atMid(cal, PAINT_END); }
    public static double cryoIn(int cal) { return atNose(cal, CRYO_IN); }
    public static double cryoOut(int cal) { return CRYO_OUT; }

    public static double endZ(int stations, int cal) { return stations <= 0 ? warheadZ(cal) : stations == 1 ? cleanZ(cal) : stations == 2 ? paintEnd(cal) : cryoOut(cal); }

    public static final double RUNWAY_GRACE = 32;

    public static double maxNose(int stations, int cal) {
        cal = BayCalibre.clamp(cal);
        final double len = T1_LEN * BayCalibre.len(cal);
        double m = dockZ(cal) + len;
        for (BayModuleBlockEntity.Stage s : BayModuleBlockEntity.Stage.values()) {
            if (s == BayModuleBlockEntity.Stage.IDLE || s.station >= stations || s.store) continue;
            int n = s.len(stations);
            for (int i = 0; i <= 24; i++) m = Math.max(m, compute(s, n * i / 24.0, n, stations, cal).cradleZ + len);
        }
        return m;
    }

    public static double stock0(int cal) { return STOCK0_Z + 28.0 * (BayCalibre.len(cal) - 1.0); }
    public static double loaderPick(int cal) { return stock0(cal) + gripDz(cal); }
    public static double loaderAtt(int cal) { return DOCK_Z + SEG_C[0] + GRIP_DZ; }

    public static double gripDz(int cal) { return loaderAtt(cal) - (DOCK_Z + SEG_C[0] * BayCalibre.len(cal)); }
    public static double loaderLift(int cal) { return LIFT - BayCalibre.lift(cal); }

    public static double toolOut(int cal) { double r = BayCalibre.R1 * BayCalibre.rad(cal), l = BayCalibre.lift(cal); return Math.sqrt(r * r - l * l) + 1.2 - 10.2; }

    public static double crRack(int cal) { return CR_STOCK_Z - CR_GRIP * BayCalibre.len(cal); }
    public static double crWork(int cal) { return craneZ(cal) - CR_ORIGIN + (SEG_C[1] - CR_GRIP) * BayCalibre.len(cal); }
    public static double crApproach(int cal) { return crWork(cal) - CR_PUSH; }
    public static double crPick(int cal) { return CR_DY_PICK + BayCalibre.lift(cal); }
    public static double crCarry(int cal) { return CR_DY_CARRY + 2.0 * BayCalibre.lift(cal); }

    public static double whStock(int cal, int k) { double e = BayCalibre.len(cal) - 1.0; return k == 2 ? WL_STOCK[2] - 6.0 * e : WL_STOCK[3] + 8.0 * e; }
    public static double whFit(int cal, int k) { return k == 2 ? WL_FIT[2] : WL_FIT[2] + (SEG_C[3] - SEG_C[2]) * BayCalibre.len(cal); }
    public static double whGrip(int cal, int k) { return WL_GRIP[k] * BayCalibre.len(cal); }
    public static double whDn(int cal) { return WL_DN - BayCalibre.lift(cal); }

    public static final double RACK_DX = 80, EXT_Z0 = 512;

    public static final double RACK_TAIL = 10, RACK_LINE_X = -RACK_DX, HUB_X = 56, HUB_Y = 78, RING_R = 44, LOAD_ANG = -Math.PI / 3;

    public static final double LOAD_X = HUB_X + RING_R * Math.sin(LOAD_ANG), LOAD_PAD_Y = HUB_Y + RING_R * Math.cos(LOAD_ANG) - 16.2;

    public static final double HOLD_Y = 96, REST_BZ = 200, REST_TX = -50, REST_DY = 9;

    public static double handoverZ(int rackSt) { return EXT_Z0 + 128.0 * rackSt + RACK_TAIL; }

    public static double loadAxis(int cal) { return LOAD_PAD_Y + BayCalibre.R1 * BayCalibre.rad(cal); }
    public static double travelAxis(int cal) { return loadAxis(cal) + 5.0; }
    public static double pickAxis(int cal) { return 47.38 + BayCalibre.lift(cal); }

    public static final class Pose {
        public int cal = 1;
        public double cradleZ = DOCK_Z, loaderZ = LOADER_REST, armX, armDown;
        public double jaw = 1;
        public int held = -1;
        public int attached;
        public final double[] slide = new double[4];
        public double weld;
        public double ring;
        public boolean cradleLoaded = true;
        public boolean moving;
        public double craneZ = CR_REST, trolX, hookDy, tq, tqSpin;
        public double wlz = WL_REST, wlx, ext, clamp;
        public double hood, scan = -1, hatch, rsw, ra, rb, rw;
        public double ringP, gz, spray, arch;
        public boolean ir;
        public double lring, oarm, otilt, cryo;
        public boolean beams;
        public double rbz = REST_BZ, rtx = REST_TX, rdy = REST_DY, rjaw;
        public boolean rcarry, rplaced, wmoving;
        public double wpos;

        public boolean air;
        public int airKind = -1;
        public double airOff, jigClamp, roll;
    }

    public static final double AIR_RT = 60, AIR_DX = 16, AIR_JIG = 78, AIR_MOTOR = 7;

    public static double airLen(int k) { return k == AirMissiles.HEAT ? 38.4 : k == AirMissiles.AGM ? 41.6 : 48.0; }

    public static double airNose(int k) { return k == AirMissiles.HEAT ? 5.0 : k == AirMissiles.RADAR ? 6.3 : k == AirMissiles.ARM ? 7.7 : 6.2; }
    public static double airGuid(int k) { return k == AirMissiles.HEAT ? 9.0 : k == AirMissiles.RADAR ? 8.5 : k == AirMissiles.ARM ? 11.0 : 10.4; }

    public static double airCentre(int k, int sec) {
        double l = airLen(k);
        return switch (sec) {
            case 1 -> AIR_RT + AIR_MOTOR / 2.0;
            case 2 -> AIR_RT + l - (airNose(k) + airGuid(k)) / 2.0;
            case 3 -> AIR_RT + l - airNose(k) / 2.0;
            default -> AIR_RT + l / 2.0;
        };
    }

    public static void airPose(Pose p, BayModuleBlockEntity.Stage stage, double f, int k, int stations) {
        p.air = true; p.airKind = k;
        final double w = 90 - airCentre(k, 0), c = SEG_C[1] - airCentre(k, 1), g = SEG_C[2] - airCentre(k, 2), n = SEG_C[3] - airCentre(k, 3), m = 80 - airCentre(k, 0);
        final double last = stations <= 0 ? n : m;
        double off = 0;
        switch (stage) {
            case ROLL_WELD -> off = lerp(0, w, s(f));
            case WELD -> off = w;
            case ROLL_CRANE -> off = lerp(w, c, s(f));
            case CRANE -> off = c;
            case ROLL_WARHEAD -> off = lerp(c, g, s(f));
            case WARHEAD -> off = f < 0.47 ? g : f < 0.53 ? lerp(g, n, s(seg(f, 0.47, 0.53))) : n;
            case ROLL_CLEAN -> off = lerp(n, m, s(f));
            case CLEAN, ROLL_PAINT, PAINT, ROLL_CRYO, CRYO -> off = m;
            case RETURN -> off = lerp(last, 0, s(f));
            default -> off = 0;
        }
        p.airOff = off;
        p.cradleZ += off;
        p.jigClamp = stage == BayModuleBlockEntity.Stage.FETCH ? s(seg(f, 0.84, 0.92)) : 1.0;
        int ord = stage.ordinal();
        p.roll = stage == BayModuleBlockEntity.Stage.WELD ? Math.PI / 2 * s(seg(f, 0.45, 0.55))
                : ord > BayModuleBlockEntity.Stage.WELD.ordinal() && stage != BayModuleBlockEntity.Stage.IDLE ? Math.PI / 2 : 0.0;
        if (stage == BayModuleBlockEntity.Stage.IDLE) p.roll = Math.PI / 2;
    }

    private static double s(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double seg(double f, double a, double b) { return (f - a) / (b - a); }

    private static void storeArm(Pose p, double f, int cal, int slot, int from, int rackSt) {
        p.attached = 15; p.cradleZ = handoverZ(rackSt);
        final double bzPick = RACK_TAIL + 80.0 * BayCalibre.len(cal);
        final double dyPick = pickAxis(cal) - HOLD_Y, dyTrav = travelAxis(cal) - HOLD_Y, dyPut = loadAxis(cal) - HOLD_Y;
        double bz = bzPick, tx = RACK_LINE_X, dy = dyTrav, jaw = 0;
        if (f < 0.14) { double v = s(seg(f, 0, 0.14)); bz = lerp(REST_BZ, bzPick, v); tx = lerp(REST_TX, RACK_LINE_X, v); dy = lerp(REST_DY, dyTrav, v); jaw = v; }
        else if (f < 0.26) { dy = lerp(dyTrav, dyPick, s(seg(f, 0.14, 0.26))); jaw = 1; }
        else if (f < 0.31) { dy = dyPick; jaw = 1 - s(seg(f, 0.26, 0.31)); }
        else if (f < 0.43) { dy = lerp(dyPick, dyTrav, s(seg(f, 0.31, 0.43))); }
        else if (f < 0.68) { tx = lerp(RACK_LINE_X, LOAD_X, s(seg(f, 0.43, 0.68))); }
        else if (f < 0.74) { tx = LOAD_X; dy = lerp(dyTrav, dyPut, s(seg(f, 0.68, 0.74))); }
        else if (f < 0.80) { tx = LOAD_X; dy = dyPut; jaw = s(seg(f, 0.74, 0.80)); }
        else if (f < 0.86) { tx = LOAD_X; dy = lerp(dyPut, dyTrav, s(seg(f, 0.80, 0.86))); jaw = 1; }
        else { double v = s(seg(f, 0.86, 1.0)); tx = lerp(LOAD_X, REST_TX, v); bz = lerp(bzPick, REST_BZ, v); dy = lerp(dyTrav, REST_DY, v); jaw = 1 - v; }
        p.rbz = bz; p.rtx = tx; p.rdy = dy; p.rjaw = jaw;
        p.rcarry = f >= 0.31 && f < 0.74;
        p.rplaced = f >= 0.74;
        p.cradleLoaded = f < 0.31;
        p.moving = f < 0.14 || (f >= 0.43 && f < 0.68);

        int delta = Math.floorMod(slot - from + 3, 6) - 3;
        double u = s(f / 0.45);
        p.wpos = from + delta * u;
        p.wmoving = slot >= 0 && delta != 0 && f < 0.45;
    }

    private static void whPart(Pose p, double u, int k, double zStart, int bit, int cal) {
        final double dn = whDn(cal);
        double zs = whStock(cal, k) - whGrip(cal, k), zf = whFit(cal, k) - whGrip(cal, k), za = zf + WL_PUSH;
        double z, x, ext = 0, jaw = 1; int held = -1;
        if (u < 0.18) { double v = s(seg(u, 0, 0.18)); z = lerp(zStart, zs, v); x = lerp(0, WL_SX, v); p.moving = true; }
        else if (u < 0.34) { z = zs; x = WL_SX; ext = lerp(0, dn, s(seg(u, 0.18, 0.34))); }
        else if (u < 0.385) { z = zs; x = WL_SX; ext = dn; jaw = 1 - s(seg(u, 0.34, 0.385)); }
        else if (u < 0.50) { z = zs; x = WL_SX; ext = lerp(dn, WL_UP, s(seg(u, 0.385, 0.50))); jaw = 0; held = k; }
        else if (u < 0.70) { double v = s(seg(u, 0.50, 0.70)); z = lerp(zs, za, v); x = lerp(WL_SX, 0, v); ext = WL_UP; jaw = 0; held = k; p.moving = true; }
        else if (u < 0.80) { z = za; x = 0; ext = lerp(WL_UP, dn, s(seg(u, 0.70, 0.80))); jaw = 0; held = k; }
        else if (u < 0.86) { z = lerp(za, zf, s(seg(u, 0.80, 0.86))); x = 0; ext = dn; jaw = 0; held = k; }
        else if (u < 0.92) { z = zf; x = 0; ext = dn; jaw = s(seg(u, 0.86, 0.92)); p.attached |= bit; }
        else { z = zf; x = 0; ext = lerp(dn, 0, s(seg(u, 0.92, 1.0))); p.attached |= bit; }
        p.wlz = z; p.wlx = x; p.ext = ext; p.jaw = jaw; p.held = held;
    }

    public static Pose compute(BayModuleBlockEntity.Stage stage, double t, int len, int stations, int cal) { return compute(stage, t, len, stations, cal, false, -1, 0, 0); }

    public static Pose compute(BayModuleBlockEntity.Stage stage, double t, int len, int stations, int cal, boolean storing, int slot, int from, int rackSt) {
        cal = BayCalibre.clamp(cal);
        Pose p = new Pose();
        p.cal = cal;
        p.cradleZ = dockZ(cal);
        p.loaderZ = loaderRest(cal);
        double f = len <= 0 ? 0 : Math.max(0, Math.min(1, t / len));
        switch (stage) {
            case IDLE -> { p.attached = 0; p.cradleLoaded = false; }
            case FETCH -> {

                final double pick = loaderPick(cal), att = loaderAtt(cal), lift = loaderLift(cal);
                double z = loaderRest(cal), ax = 0, dn = 0, jaw = 1; int held = -1, att1 = 0;
                if (f < 0.16) { double u = s(seg(f, 0, 0.16)); z = lerp(loaderRest(cal), pick, u); ax = lerp(0, ARM_RACK, u); p.moving = true; }
                else if (f < 0.26) { z = pick; ax = ARM_RACK; dn = lerp(0, lift, s(seg(f, 0.16, 0.26))); }
                else if (f < 0.31) { z = pick; ax = ARM_RACK; dn = lift; jaw = 1 - s(seg(f, 0.26, 0.31)); }
                else if (f < 0.42) { z = pick; ax = ARM_RACK; dn = lerp(lift, 0, s(seg(f, 0.31, 0.42))); jaw = 0; held = 0; }
                else if (f < 0.66) { double u = s(seg(f, 0.42, 0.66)); z = lerp(pick, att, u); ax = lerp(ARM_RACK, ARM_LINE, u); jaw = 0; held = 0; p.moving = true; }
                else if (f < 0.78) { z = att; ax = ARM_LINE; dn = lerp(0, lift, s(seg(f, 0.66, 0.78))); jaw = 0; held = 0; }
                else if (f < 0.84) { z = att; ax = ARM_LINE; dn = lift; jaw = s(seg(f, 0.78, 0.84)); att1 = 1; }
                else if (f < 0.92) { z = att; ax = ARM_LINE; dn = lerp(lift, 0, s(seg(f, 0.84, 0.92))); att1 = 1; }
                else { z = att; ax = lerp(ARM_LINE, 0, s(seg(f, 0.92, 1.0))); att1 = 1; }
                p.loaderZ = z; p.armX = ax; p.armDown = dn; p.jaw = jaw; p.held = held; p.attached = att1;
            }
            case ROLL_WELD -> {
                p.attached = 1; p.cradleZ = lerp(dockZ(cal), weldZ(cal), s(f)); p.moving = true;
                p.loaderZ = lerp(loaderAtt(cal), loaderRest(cal), s(seg(f, 0, 0.8))); p.armX = lerp(ARM_LINE, 0, s(seg(f, 0, 0.5)));
            }
            case WELD -> {
                p.attached = 1; p.cradleZ = weldZ(cal);
                p.weld = Math.min(s(seg(f, 0, 0.08)), 1 - s(seg(f, 0.92, 1.0)));
                p.ring = t * 0.045 * (0.4 + 0.6 * p.weld);
            }
            case ROLL_CRANE -> { p.attached = 1; p.cradleZ = lerp(weldZ(cal), craneZ(cal), s(f)); p.moving = true; }
            case CRANE -> {

                p.attached = 1; p.cradleZ = craneZ(cal);
                final double rack = crRack(cal), approach = crApproach(cal), work = crWork(cal), dyPick = crPick(cal), dyCarry = crCarry(cal);
                double z = CR_REST, x = 0, dy = 0, jaw = 1; int held = -1, att = 1;
                if (f < 0.14) { double u = s(seg(f, 0, 0.14)); z = lerp(CR_REST, rack, u); x = lerp(0, CR_TROL, u); p.moving = true; }
                else if (f < 0.26) { z = rack; x = CR_TROL; dy = lerp(0, dyPick, s(seg(f, 0.14, 0.26))); }
                else if (f < 0.31) { z = rack; x = CR_TROL; dy = dyPick; jaw = 1 - s(seg(f, 0.26, 0.31)); }
                else if (f < 0.42) { z = rack; x = CR_TROL; dy = lerp(dyPick, dyCarry, s(seg(f, 0.31, 0.42))); jaw = 0; held = 1; }
                else if (f < 0.62) { double u = s(seg(f, 0.42, 0.62)); z = lerp(rack, approach, u); x = lerp(CR_TROL, 0, u); dy = dyCarry; jaw = 0; held = 1; p.moving = true; }
                else if (f < 0.70) { z = approach; dy = lerp(dyCarry, dyPick, s(seg(f, 0.62, 0.70))); jaw = 0; held = 1; }
                else if (f < 0.78) { z = lerp(approach, work, s(seg(f, 0.70, 0.78))); dy = dyPick; jaw = 0; held = 1; }
                else if (f < 0.84) { z = work; dy = dyPick; jaw = s(seg(f, 0.78, 0.84)); att |= 2; }
                else { z = lerp(work, CR_REST, s(seg(f, 0.90, 1.0))); dy = lerp(dyPick, 0, s(seg(f, 0.84, 0.92))); att |= 2; }
                p.craneZ = z; p.trolX = x; p.hookDy = dy; p.jaw = jaw; p.held = held; p.attached = att;
                p.tq = f < 0.80 ? 0 : f < 0.86 ? s(seg(f, 0.80, 0.86)) : f < 0.94 ? 1 : 1 - s(seg(f, 0.94, 1.0));
                if (f >= 0.86 && f < 0.94) p.tqSpin = t * 0.6;
            }
            case ROLL_WARHEAD -> { p.attached = 3; p.cradleZ = lerp(craneZ(cal), warheadZ(cal), s(f)); p.moving = true; }
            case WARHEAD -> {
                p.attached = 3; p.cradleZ = warheadZ(cal);
                p.clamp = Math.min(s(seg(f, 0.0, 0.05)), 1 - s(seg(f, 0.92, 0.97)));
                if (f < 0.05) p.wlz = WL_REST;
                else if (f < 0.48) whPart(p, (f - 0.05) / 0.43, 2, WL_REST, 4, cal);
                else if (f < 0.91) { p.attached |= 4; whPart(p, (f - 0.48) / 0.43, 3, whFit(cal, 2) - whGrip(cal, 2), 8, cal); }
                else { p.attached |= 12; p.wlz = lerp(whFit(cal, 3) - whGrip(cal, 3), WL_REST, s(seg(f, 0.91, 1.0))); }
            }
            case ROLL_CLEAN -> { p.attached = 15; p.cradleZ = lerp(warheadZ(cal), cleanZ(cal), s(f)); p.moving = true; }
            case CLEAN -> {
                p.attached = 15; p.cradleZ = cleanZ(cal);
                p.hatch = Math.min(s(seg(f, 0.0, 0.08)), 1 - s(seg(f, 0.10, 0.18)));
                p.hood = Math.min(s(seg(f, 0.04, 0.16)), 1 - s(seg(f, 0.84, 0.96)));
                if (f > 0.20 && f < 0.80) {
                    double u = seg(f, 0.20, 0.80);
                    p.scan = u < 0.5 ? s(u * 2) : 1 - s((u - 0.5) * 2);
                    p.rsw = 0.5 * Math.sin(t * 0.05); p.ra = 0.25 * Math.sin(t * 0.07); p.rb = 0.3 * Math.sin(t * 0.09 + 1); p.rw = 0.2 * Math.sin(t * 0.11);
                }
            }
            case ROLL_PAINT -> { p.attached = 15; p.cradleZ = lerp(cleanZ(cal), paintIn(cal), s(f)); p.moving = true; }
            case PAINT -> {
                p.attached = 15;
                if (f < 0.5) {
                    double u = f / 0.5; p.cradleZ = lerp(paintIn(cal), paintFar(cal), u);
                    p.spray = Math.min(s(seg(f, 0.0, 0.04)), 1 - s(seg(f, 0.46, 0.5)));
                } else if (f < 0.9) {
                    double u = seg(f, 0.5, 0.9); p.cradleZ = lerp(paintFar(cal), paintEnd(cal), s(u));
                    p.ir = true; p.arch = Math.min(s(seg(f, 0.5, 0.58)), 1 - s(seg(f, 0.86, 0.9))) ; p.arch = s(u < 0.5 ? u * 2 : 2 - u * 2);
                } else p.cradleZ = paintEnd(cal);
                p.ringP = t * 0.06 * (0.3 + 0.7 * p.spray);
                p.gz = 14 * Math.sin(t * 0.035) * p.spray + 8 * p.spray;
            }
            case ROLL_CRYO -> { p.attached = 15; p.cradleZ = lerp(paintEnd(cal), cryoIn(cal), s(f)); p.moving = true; }
            case CRYO -> {
                p.attached = 15;
                p.cradleZ = f < 0.08 ? cryoIn(cal) : f < 0.92 ? lerp(cryoIn(cal), cryoOut(cal), seg(f, 0.08, 0.92)) : cryoOut(cal);
                p.cryo = Math.min(s(seg(f, 0.0, 0.08)), 1 - s(seg(f, 0.92, 1.0)));
                p.beams = p.cryo > 0.3;
                p.lring = t * 0.05 * (0.2 + 0.8 * p.cryo);
                p.oarm = Math.sin(t * 0.04) * p.cryo; p.otilt = 0.35 * Math.sin(t * 0.06) * p.cryo;
            }
            case RETURN -> {

                p.attached = 15; p.moving = true;
                if (storing) { p.cradleLoaded = false; p.cradleZ = lerp(handoverZ(rackSt), dockZ(cal), s(f)); }
                else p.cradleZ = lerp(endZ(stations, cal), dockZ(cal), s(f));
            }
            case ROLL_STORE -> { p.attached = 15; p.cradleZ = lerp(endZ(stations, cal), handoverZ(rackSt), s(f)); p.moving = true; }
            case STORE -> storeArm(p, f, cal, slot, from, rackSt);
        }
        return p;
    }
}
