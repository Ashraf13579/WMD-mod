package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class TorpedoLineAnim {

    private TorpedoLineAnim() {}

    static final double X0 = 8.0, AY = 16.0;
    static final double HOOK_Y = 26.0, HOOK_MAX = 12.0;
    static final double LOAD_DROP = 10.6, PLATE_DROP = 7.6;
    static final double CRANE_REST = 40.0, TROLLEY_REST = 20.0;
    static final double T_SHOP = 20.0, T_FIT = 119.0, T_TEST = 194.0;
    static final double TOP_X = 29.0, TOP_Y = 19.6, ROLL_Z = 23.0, OUT_Z = -2.0, FEED_X = 18.5, FEED_TOP = 15.6, FEED_IN = 9.0;
    static final double TORCH_PARK = 38.0, SEAM_A = 19.0, SEAM_B = 27.0;
    static final double STAND_X = -10.0, STAND_Z = 16.0;
    static final double FIT = 96.0, TEST = 192.0;
    static final double FIT_C = 43.0, TEST_C = 22.0;
    static final double WH_X = 29.0, WH_Y = 13.6, WH_Z = 28.0;
    static final double TANK_X = 30.2, TANK_Z = 44.0, SUB_AY = 9.6, WATER = 17.6, RACK_Z = 56.0;
    static final double[] RACK_X = { -16.4, -8.4 }, RACK_Y = { 9.4, 22.0 };

    public static final int[] RACK_ORDER = { 0, 2, 1, 3 };
    static double slotX(int s) { return RACK_X[s % 2]; }
    static double slotY(int s) { return RACK_Y[s / 2]; }

    static final double[][] ARM = { { -9.5, 30.0 }, { 28.0, 64.0 } };

    public enum Piece { NOSE, WARHEAD, BODY0, BODY1, BODY2, TAIL }
    private static final double[] CUT = { 0.0, 0.18, 0.39, 0.52, 0.65, 0.78, 1.0 };

    public static double[] range(TorpedoKind k, Piece p) {
        double h = k == TorpedoKind.UUV ? 18.5 : k == TorpedoKind.ATT ? 17.6 : 28.0, a = -h + 2.0 * h * CUT[p.ordinal()], b = -h + 2.0 * h * CUT[p.ordinal() + 1];
        return new double[] { a, b };
    }
    public static double centre(TorpedoKind k, Piece p) { double[] r = range(k, p); return (r[0] + r[1]) / 2.0; }

    static double joint(TorpedoKind k, Piece p) { return range(k, p)[1]; }

    public enum Load { NONE, PLATE, SECTION, WARHEAD, TORPEDO }

    public static final class Pose {
        public TorpedoLineKind active;

        public double trolley, crane, hoistX, hookY, rollSpin, plateFeedX, torchZ = TORCH_PARK, formedZ = ROLL_Z, cutterSpin;
        public boolean plateFeedOn, rolling, welding, formedOn, cutSparks;
        public int stockPlates = 3, formedIndex, sections;
        public Load load = Load.NONE;
        public int loadIndex;

        public boolean warhead, nose, tail, cellIn;

        public final double[][] wrist = { park(0), park(1) };
        public final double[] grip = new double[2];
        public boolean cellCarried, noseCarried, tailCarried;
        public final boolean[] armWeld = new boolean[2];
        public boolean stockCell = true, stockWarhead = true, stockNose = true, stockTail = true;

        public double jaws, paint;
        public boolean onTrolley = true, submerged, splashing, dripping, spraying, bubbling;
        public int rackTarget = -1;
    }

    static double[] park(int a) {
        double s = a == 0 ? 1.0 : -1.0;
        return new double[] { ARM[a][0] + s * 8.0, 30.0, ARM[a][1] };
    }

    static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    static double win(double f, double a, double b) { return ease((f - a) / (b - a)); }
    static double lin(double f, double a, double b) { return Mth.clamp((f - a) / (b - a), 0.0, 1.0); }
    static double mix(double a, double b, double k) { return a + (b - a) * k; }
    static boolean in(double f, double a, double b) { return f >= a && f < b; }

    private static void hookMove(Pose p, double k, double[] from, double[] to) {
        double up = lin(k, 0.0, 0.25), across = win(k, 0.25, 0.75), down = lin(k, 0.75, 1.0);
        p.hoistX = mix(from[0], to[0], across);
        p.crane = mix(from[1], to[1], across);
        p.hookY = k < 0.25 ? mix(from[2], HOOK_MAX, ease(up)) : k < 0.75 ? HOOK_MAX : mix(HOOK_MAX, to[2], ease(down));
    }

    private static double[] at(double x, double z, double hookY) { return new double[] { x - X0, z - CRANE_REST, hookY }; }
    private static double forAxis(double axis) { return axis + LOAD_DROP - HOOK_Y; }
    private static double forPlate(double underside) { return underside + PLATE_DROP - HOOK_Y; }

    private static double[] armMove(double[] a, double[] b, double k, double hi) {
        double up = lin(k, 0.0, 0.25), across = win(k, 0.25, 0.75), down = lin(k, 0.75, 1.0);
        double y = k < 0.25 ? mix(a[1], hi, ease(up)) : k < 0.75 ? hi : mix(hi, b[1], ease(down));
        return new double[] { mix(a[0], b[0], across), y, mix(a[2], b[2], across) };
    }
    private static double[] v(double x, double y, double z) { return new double[] { x, y, z }; }

    private static final double TIP = 4.4;

    public static Pose compute(TorpedoLineBlockEntity.Stage s, double t, int len, TorpedoKind k, int rackTarget) {
        Pose p = new Pose();
        double f = len > 0 ? Mth.clamp(t / len, 0.0, 1.0) : 0.0;
        p.rackTarget = rackTarget;
        double[] stand0 = at(STAND_X, STAND_Z, HOOK_MAX);
        double trolleyZ = TROLLEY_REST + T_SHOP;
        switch (s) {
            case IDLE -> { }
            case LOAD -> {
                p.active = TorpedoLineKind.SHOP;
                p.trolley = T_SHOP * ease(f);
                hookMove(p, f, new double[] { 0, 0, 0 }, stand0);
            }
            case BEND -> {
                p.active = TorpedoLineKind.SHOP; p.trolley = T_SHOP;
                int c = Math.min(2, (int) (f * 3.0));
                double g = f * 3.0 - c;
                p.sections = c;
                p.stockPlates = 3 - c;
                double[] prevEnd = c == 0 ? stand0 : at(X0, trolleyZ + centre(k, Piece.values()[Piece.BODY0.ordinal() + c - 1]), forAxis(AY) + 2.0);
                double plateTop = 11.8 - 0.6 * c;
                double[] pick = at(STAND_X, STAND_Z, forPlate(plateTop));
                double[] feed = at(FEED_X, ROLL_Z, forPlate(FEED_TOP));
                double[] feedUp = at(FEED_X, ROLL_Z, HOOK_MAX);
                double[] outUp = at(TOP_X, OUT_Z, HOOK_MAX), outPick = at(TOP_X, OUT_Z, forAxis(TOP_Y));
                Piece sec = Piece.values()[Piece.BODY0.ordinal() + c];
                double[] place = at(X0, trolleyZ + centre(k, sec), forAxis(AY));
                if (g < 0.10) hookMove(p, g / 0.10, prevEnd, stand0);
                else if (g < 0.14) { hookMove(p, 1.0, stand0, pick); p.hookY = mix(HOOK_MAX, pick[2], win(g, 0.10, 0.14)); }
                else if (g < 0.30) { p.load = Load.PLATE; p.stockPlates = 2 - c; hookMove(p, lin(g, 0.14, 0.30), pick, feed); }
                else if (g < 0.36) { p.stockPlates = 2 - c; hookMove(p, 1.0, pick, feed); p.hookY = mix(feed[2], HOOK_MAX, win(g, 0.30, 0.36)); }
                else if (g < 0.80) { p.stockPlates = 2 - c; hookMove(p, lin(g, 0.62, 0.80), feedUp, outUp); if (g < 0.62) { p.hoistX = feedUp[0]; p.crane = feedUp[1]; p.hookY = HOOK_MAX; } }
                else if (g < 0.86) { p.stockPlates = 2 - c; p.hoistX = outPick[0]; p.crane = outPick[1]; p.hookY = mix(HOOK_MAX, outPick[2], win(g, 0.80, 0.86)); }
                else if (g < 0.98) { p.stockPlates = 2 - c; p.load = Load.SECTION; p.loadIndex = c; hookMove(p, lin(g, 0.86, 0.98), outPick, place); }
                else { p.stockPlates = 2 - c; p.sections = c + 1; p.hoistX = place[0]; p.crane = place[1]; p.hookY = mix(place[2], place[2] + 2.0, lin(g, 0.98, 1.0)); }

                p.plateFeedOn = g >= 0.30 && g < 0.55;
                p.plateFeedX = FEED_IN * win(g, 0.32, 0.55);
                p.rolling = g >= 0.32 && g < 0.70;
                p.rollSpin = Mth.clamp(g - 0.32, 0.0, 0.38) * 3.0 * 40.0;
                p.formedOn = g >= 0.45 && g < 0.86;
                p.formedIndex = c;
                p.formedZ = g < 0.72 ? ROLL_Z : mix(ROLL_Z, OUT_Z, win(g, 0.72, 0.80));
                p.torchZ = g < 0.56 ? TORCH_PARK : g < 0.60 ? mix(TORCH_PARK, SEAM_A, win(g, 0.56, 0.60)) : g < 0.70 ? mix(SEAM_A, SEAM_B, lin(g, 0.60, 0.70))
                        : mix(SEAM_B, TORCH_PARK, win(g, 0.70, 0.74));
                p.welding = g >= 0.60 && g < 0.70;
            }
            case TO_FIT -> {
                p.trolley = mix(T_SHOP, T_FIT, ease(f)); p.sections = 3; p.stockPlates = 0;
                p.cutterSpin = f * 60.0;
                double tz = TROLLEY_REST + p.trolley;
                for (Piece j : new Piece[] { Piece.BODY0, Piece.BODY1 }) if (Math.abs(tz + joint(k, j) - 60.0) < 2.0) p.cutSparks = true;
                if (Math.abs(tz + range(k, Piece.BODY0)[0] - 60.0) < 2.0 || Math.abs(tz + range(k, Piece.BODY2)[1] - 60.0) < 2.0) p.cutSparks = true;
                double[] last = at(X0, trolleyZ + centre(k, Piece.BODY2), forAxis(AY) + 2.0);
                hookMove(p, f, last, at(WH_X, FIT + WH_Z, HOOK_MAX));
            }
            case FIT -> fit(p, f, k);
            case TO_TEST -> {
                p.trolley = mix(T_FIT, T_TEST, ease(f)); fitDone(p);
                hookMove(p, f, at(X0, FIT + FIT_C + centre(k, Piece.WARHEAD), HOOK_MAX), at(X0, TEST + TEST_C, HOOK_MAX));
            }
            case MATE -> {
                p.active = TorpedoLineKind.TEST; p.trolley = T_TEST; fitDone(p);
                p.jaws = f < 0.25 ? win(f, 0.0, 0.25) : f < 0.75 ? 1.0 : 1.0 - win(f, 0.75, 1.0);
                double[] h = at(X0, TEST + TEST_C, HOOK_MAX); p.hoistX = h[0]; p.crane = h[1]; p.hookY = h[2];
            }
            case TANK -> {
                p.active = TorpedoLineKind.TEST; p.trolley = T_TEST; fitDone(p);
                double[] grab = at(X0, TEST + TEST_C, forAxis(AY)), up = at(X0, TEST + TEST_C, HOOK_MAX);
                double[] over = at(TANK_X, TEST + TANK_Z, HOOK_MAX), under = at(TANK_X, TEST + TANK_Z, forAxis(SUB_AY));
                if (f < 0.08) { p.hoistX = grab[0]; p.crane = grab[1]; p.hookY = mix(HOOK_MAX, grab[2], win(f, 0.0, 0.08)); }
                else {
                    p.onTrolley = false; p.load = Load.TORPEDO;
                    if (f < 0.16) { p.hoistX = grab[0]; p.crane = grab[1]; p.hookY = mix(grab[2], HOOK_MAX, win(f, 0.08, 0.16)); }
                    else if (f < 0.30) { double q = win(f, 0.16, 0.30); p.hoistX = mix(up[0], over[0], q); p.crane = mix(up[1], over[1], q); p.hookY = HOOK_MAX; }
                    else if (f < 0.42) { p.hoistX = over[0]; p.crane = over[1]; p.hookY = mix(HOOK_MAX, under[2], win(f, 0.30, 0.42)); }
                    else if (f < 0.72) { p.hoistX = under[0]; p.crane = under[1]; p.hookY = under[2] + 0.15 * Math.sin(f * 400.0); }
                    else { p.hoistX = over[0]; p.crane = over[1]; p.hookY = mix(under[2], HOOK_MAX, win(f, 0.72, 0.84)); }
                    double axis = p.hookY + HOOK_Y - LOAD_DROP;
                    p.submerged = axis + 6.0 < WATER;
                    p.splashing = (f >= 0.30 && f < 0.42 && axis - 6.0 < WATER && axis - 6.0 > WATER - 4.0) || (f >= 0.72 && f < 0.84 && axis - 6.0 < WATER + 1.0 && axis - 6.0 > WATER - 3.0);
                    p.bubbling = f >= 0.42 && f < 0.72;
                    p.dripping = f >= 0.76;
                }
            }
            case STORE -> {
                p.active = TorpedoLineKind.TEST; p.trolley = T_TEST; fitDone(p); p.onTrolley = false; p.load = Load.TORPEDO;
                int slot = rackTarget < 0 ? 0 : rackTarget;
                double[] over = at(TANK_X, TEST + TANK_Z, HOOK_MAX), along = at(TANK_X, TEST + RACK_Z, HOOK_MAX);
                double[] col = at(slotX(slot), TEST + RACK_Z, HOOK_MAX), down = at(slotX(slot), TEST + RACK_Z, forAxis(slotY(slot)));
                if (f < 0.15) { double q = win(f, 0.0, 0.15); p.hoistX = over[0]; p.crane = mix(over[1], along[1], q); p.hookY = HOOK_MAX; }
                else if (f < 0.35) { double q = win(f, 0.15, 0.35); p.hoistX = mix(along[0], col[0], q); p.crane = along[1]; p.hookY = HOOK_MAX; }
                else if (f < 0.65) { p.hoistX = col[0]; p.crane = col[1]; p.hookY = HOOK_MAX; }
                else if (f < 0.90) { p.hoistX = col[0]; p.crane = col[1]; p.hookY = mix(HOOK_MAX, down[2], win(f, 0.65, 0.90)); }
                else { p.hoistX = down[0]; p.crane = down[1]; p.hookY = down[2]; }
                p.paint = win(f, 0.35, 0.65);
                p.spraying = f >= 0.35 && f < 0.65;
                p.dripping = f < 0.30;
            }
            case TO_HOME -> {
                p.trolley = T_TEST * (1.0 - ease(f));
                int slot = Math.max(0, rackTarget);
                double[] from = at(slotX(slot), TEST + RACK_Z, forAxis(slotY(slot)));
                hookMove(p, Math.min(1.0, f * 1.25), from, new double[] { 0, 0, 0 });
                p.stockPlates = 0; p.stockCell = p.stockWarhead = p.stockNose = p.stockTail = false;
                p.onTrolley = false;
            }
        }
        return p;
    }

    private static void fitDone(Pose p) {
        p.sections = 3; p.stockPlates = 0; p.warhead = p.nose = p.tail = p.cellIn = true;
        p.stockCell = p.stockWarhead = p.stockNose = p.stockTail = false;
    }

    private static void fit(Pose p, double f, TorpedoKind k) {
        p.active = TorpedoLineKind.FITOUT; p.trolley = T_FIT; p.sections = 3; p.stockPlates = 0;
        double c = FIT_C, yHi = 30.0;

        double front = c + range(k, Piece.BODY0)[0];
        double[] park1 = park(0), cellUp = v(-16.0, yHi, 19.0), cellAt = v(-16.0, 17.0 + TIP, 19.0);
        double[] inA = v(X0, AY + TIP, front - 4.0), inB = v(X0, AY + TIP, front + 2.0);
        double jw = c + joint(k, Piece.WARHEAD), jn = c + joint(k, Piece.NOSE), jt = c + joint(k, Piece.BODY2);
        double[] weldW = v(X0 - 3.5, AY + 4.7 + TIP, jw), weldN = v(X0 - 3.5, AY + 4.7 + TIP, jn);
        double[] noseAt = v(-17.7, 12.6 + 4.0 + TIP, 45.6), nosePlace = v(X0, AY + 4.0 + TIP, c + centre(k, Piece.NOSE));
        double[] w1;
        if (f < 0.04) w1 = armMove(park1, cellAt, f / 0.04, yHi);
        else if (f < 0.06) { w1 = cellAt; p.grip[0] = win(f, 0.04, 0.06); }
        else if (f < 0.12) { w1 = armMove(cellAt, inA, lin(f, 0.06, 0.12), yHi); p.grip[0] = 1; p.cellCarried = true; }
        else if (f < 0.15) { w1 = new double[] { X0, AY + TIP, mix(inA[2], inB[2], win(f, 0.12, 0.15)) }; p.grip[0] = 1; p.cellCarried = true; }
        else if (f < 0.20) { w1 = armMove(inB, park1, lin(f, 0.15, 0.20), yHi); p.cellIn = true; }
        else if (f < 0.42) { w1 = park1; p.cellIn = true; }
        else if (f < 0.46) { w1 = armMove(park1, weldW, lin(f, 0.42, 0.46), yHi); p.cellIn = true; }
        else if (f < 0.52) { w1 = weldW; p.armWeld[0] = true; p.cellIn = true; }
        else if (f < 0.58) { w1 = armMove(weldW, noseAt, lin(f, 0.52, 0.58), yHi); p.cellIn = true; }
        else if (f < 0.60) { w1 = noseAt; p.grip[0] = win(f, 0.58, 0.60); p.cellIn = true; }
        else if (f < 0.70) { w1 = armMove(noseAt, nosePlace, lin(f, 0.60, 0.70), yHi); p.grip[0] = 1; p.noseCarried = true; p.cellIn = true; }
        else if (f < 0.72) { w1 = nosePlace; p.grip[0] = 1.0 - win(f, 0.70, 0.72); p.nose = true; p.cellIn = true; }
        else if (f < 0.75) { w1 = armMove(nosePlace, weldN, lin(f, 0.72, 0.75), yHi); p.nose = true; p.cellIn = true; }
        else if (f < 0.82) { w1 = weldN; p.armWeld[0] = true; p.nose = true; p.cellIn = true; }
        else { w1 = armMove(weldN, park1, lin(f, 0.82, 0.90), yHi); p.nose = true; p.cellIn = true; }
        p.wrist[0][0] = w1[0]; p.wrist[0][1] = w1[1]; p.wrist[0][2] = w1[2];
        p.stockCell = f < 0.06; p.stockNose = f < 0.60;

        double[] whUp = at(WH_X, FIT + WH_Z, HOOK_MAX), whPick = at(WH_X, FIT + WH_Z, forAxis(WH_Y));
        double[] whPlace = at(X0, FIT + c + centre(k, Piece.WARHEAD), forAxis(AY));
        if (f < 0.20) { p.hoistX = whUp[0]; p.crane = whUp[1]; p.hookY = HOOK_MAX; }
        else if (f < 0.25) { p.hoistX = whPick[0]; p.crane = whPick[1]; p.hookY = mix(HOOK_MAX, whPick[2], win(f, 0.20, 0.25)); }
        else if (f < 0.40) { p.load = Load.WARHEAD; hookMove(p, lin(f, 0.25, 0.40), whPick, whPlace); }
        else { p.warhead = true; p.hoistX = whPlace[0]; p.crane = whPlace[1]; p.hookY = mix(whPlace[2], HOOK_MAX, win(f, 0.40, 0.46)); }
        p.stockWarhead = f < 0.25;

        double[] park2 = park(1), tailAt = v(29.0, 12.2 + 4.0 + TIP, 76.0), tailPlace = v(X0, AY + 4.0 + TIP, c + centre(k, Piece.TAIL));
        double[] weldT = v(X0 + 5.5, AY + 3.0 + TIP, jt);
        double[] w2;
        if (f < 0.30) w2 = park2;
        else if (f < 0.36) w2 = armMove(park2, tailAt, lin(f, 0.30, 0.36), yHi);
        else if (f < 0.38) { w2 = tailAt; p.grip[1] = win(f, 0.36, 0.38); }
        else if (f < 0.50) { w2 = armMove(tailAt, tailPlace, lin(f, 0.38, 0.50), yHi); p.grip[1] = 1; p.tailCarried = true; }
        else if (f < 0.52) { w2 = tailPlace; p.grip[1] = 1.0 - win(f, 0.50, 0.52); p.tail = true; }
        else if (f < 0.56) { w2 = armMove(tailPlace, weldT, lin(f, 0.52, 0.56), yHi); p.tail = true; }
        else if (f < 0.64) { w2 = weldT; p.armWeld[1] = true; p.tail = true; }
        else { w2 = armMove(weldT, park2, lin(f, 0.64, 0.72), yHi); p.tail = true; }
        p.wrist[1][0] = w2[0]; p.wrist[1][1] = w2[1]; p.wrist[1][2] = w2[2];
        p.stockTail = f < 0.38;
    }

    static final double SH_Y = 15.5, L1 = Math.hypot(6.0, 11.5), L2 = 10.0, A1R = Math.atan2(11.5, 6.0), A2R = Math.atan2(-6.0, 8.0);

    public static double[] ik(int a, double[] w) {
        double x = ARM[a][0], z = ARM[a][1], s = a == 0 ? 1.0 : -1.0;
        double dx = w[0] - x, dz = w[2] - z, h = Math.hypot(dx, dz);
        double yaw = h < 1e-6 ? 0.0 : Math.atan2(-s * dz, s * dx);
        double vy = w[1] - SH_Y, d = Mth.clamp(Math.hypot(h, vy), Math.abs(L1 - L2) + 0.01, L1 + L2 - 0.01);
        double beta = Math.atan2(vy, h), gamma = Math.acos(Mth.clamp((L1 * L1 + d * d - L2 * L2) / (2.0 * L1 * d), -1.0, 1.0));
        double t1 = beta + gamma;
        double ex = L1 * Math.cos(t1), ey = L1 * Math.sin(t1);
        double tx = d * Math.cos(beta), ty = d * Math.sin(beta);
        double t2 = Math.atan2(ty - ey, tx - ex);
        double sh = s * (t1 - A1R), el = s * ((t2 - t1) - (A2R - A1R));
        return new double[] { yaw, sh, el, -(sh + el) };
    }
}
