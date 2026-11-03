package com.ashraf.wmd;

import net.minecraft.util.Mth;

public final class PadRig {

    private PadRig() {}

    public static final double CZ = 128.0, MOUNT = 60.0, RY = 700.0, PARK_RING = 623.0, BEAM_REST = 587.0;

    public static final double[] H = { 300.0, 294.0, 294.0, 231.0 };
    public static final double[] SX = { 96.0, 60.0, 60.0, 101.0 }, SZ = { CZ, CZ + 39.3, CZ - 39.3, 54.0 };
    public static final double[] TX = { 0.0, 0.0, 0.0, 0.0 }, TY = { MOUNT, MOUNT, MOUNT, MOUNT + 300.0 }, TZ = { CZ, CZ + 39.3, CZ - 39.3, CZ };
    public static final double STAND_Y = 58.0, HOOK_DROP = 38.0;

    public static final double FOLD_Z = 196.0, FOLD_X = -34.0, FOLD_Y = 660.0;

    private static final double[][] KEYS = {
            { 0.000, FOLD_Z, FOLD_X, FOLD_Y, -1 }, { 0.020, FOLD_Z, FOLD_X, FOLD_Y, -1 }, { 0.050, 128, 0, 620, -1 },

            { 0.080, 128, 96, 620, -1 }, { 0.105, 128, 96, 58 + 300 + 38, -1 }, { 0.115, 128, 96, 58 + 300 + 38, 0 }, { 0.150, 128, 96, 100 + 300 + 38, 0 },
            { 0.200, 128, 0, 100 + 300 + 38, 0 }, { 0.245, 128, 0, 60 + 300 + 38, 0 }, { 0.255, 128, 0, 60 + 300 + 38, -1 }, { 0.290, 128, 0, 620, -1 },

            { 0.335, CZ + 39.3, 60, 620, -1 }, { 0.360, CZ + 39.3, 60, 58 + 294 + 38, -1 }, { 0.370, CZ + 39.3, 60, 58 + 294 + 38, 1 }, { 0.400, CZ + 39.3, 60, 100 + 294 + 38, 1 },
            { 0.440, CZ + 39.3, 0, 100 + 294 + 38, 1 }, { 0.475, CZ + 39.3, 0, 60 + 294 + 38, 1 }, { 0.485, CZ + 39.3, 0, 60 + 294 + 38, -1 }, { 0.510, CZ + 39.3, 0, 620, -1 },

            { 0.550, CZ - 39.3, 60, 620, -1 }, { 0.575, CZ - 39.3, 60, 58 + 294 + 38, -1 }, { 0.585, CZ - 39.3, 60, 58 + 294 + 38, 2 }, { 0.615, CZ - 39.3, 60, 100 + 294 + 38, 2 },
            { 0.655, CZ - 39.3, 0, 100 + 294 + 38, 2 }, { 0.690, CZ - 39.3, 0, 60 + 294 + 38, 2 }, { 0.700, CZ - 39.3, 0, 60 + 294 + 38, -1 }, { 0.725, CZ - 39.3, 0, 620, -1 },

            { 0.765, 66, 92, 620, -1 }, { 0.790, 66, 92, 58 + 231 + 38, -1 }, { 0.800, 66, 92, 58 + 231 + 38, 3 }, { 0.830, 66, 92, 400 + 231 + 38, 3 },
            { 0.870, 128, 0, 400 + 231 + 38, 3 }, { 0.900, 128, 0, 360 + 231 + 38, 3 }, { 0.910, 128, 0, 360 + 231 + 38, -1 }, { 0.945, 128, 0, 640, -1 },
            { 0.975, 128, 0, 640, -1 }, { 1.000, FOLD_Z, FOLD_X, FOLD_Y, -1 } };

    public static final class State {
        public double bridgeZ, trolleyX, beamY;
        public int carried = -1;
        public final double[] px = new double[4], py = new double[4], pz = new double[4];
        public double weldE0, weldY0, weldE1, weldY1;
        public boolean spark0, spark1;
        public double ringY;
        public boolean ringSpark;
        public double ringAngle;

        public double rope() { return RY - 13.0 - beamY; }
    }

    private static double smooth(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public static State at(double p) {
        State s = new State();
        p = Mth.clamp(p, 0.0, 1.0);

        int i = 0;
        while (i < KEYS.length - 2 && p > KEYS[i + 1][0]) i++;
        double[] a = KEYS[i], b = KEYS[i + 1];
        double t = b[0] == a[0] ? 1.0 : smooth((p - a[0]) / (b[0] - a[0]));
        s.bridgeZ = lerp(a[1], b[1], t); s.trolleyX = lerp(a[2], b[2], t); s.beamY = lerp(a[3], b[3], t);
        s.carried = (int) (p >= b[0] ? b[4] : a[4]);
        if (p < a[0] + 1.0E-9) s.carried = (int) a[4];

        for (int k = 0; k < 4; k++) {
            double att = Double.MAX_VALUE, det = Double.MAX_VALUE;
            for (int j = 0; j < KEYS.length; j++) {
                if ((int) KEYS[j][4] == k) { if (att == Double.MAX_VALUE) att = KEYS[j][0]; det = KEYS[j][0]; }
            }
            if (p < att) { s.px[k] = SX[k]; s.py[k] = STAND_Y; s.pz[k] = SZ[k]; }
            else if (p <= det) { s.px[k] = s.trolleyX; s.py[k] = s.beamY - HOOK_DROP - H[k]; s.pz[k] = s.bridgeZ; }
            else { s.px[k] = TX[k]; s.py[k] = TY[k]; s.pz[k] = TZ[k]; }
        }

        weldRobot(s, 0, p, 0.495, 0.585);
        weldRobot(s, 1, p, 0.710, 0.790);

        double ringWork = MOUNT + 300.0 + 6.0;
        if (p < 0.915) s.ringY = PARK_RING;
        else if (p < 0.945) s.ringY = lerp(PARK_RING, ringWork + 40.0, smooth((p - 0.915) / 0.03));
        else if (p < 0.985) { s.ringY = lerp(ringWork + 40.0, ringWork, smooth((p - 0.945) / 0.01)); s.ringSpark = p > 0.952 && p < 0.980; }
        else s.ringY = lerp(ringWork, PARK_RING, smooth((p - 0.985) / 0.015));
        s.ringAngle = p * 2400.0;
        return s;
    }

    private static void weldRobot(State s, int which, double p, double p0, double p1) {
        double e = 0.0, y = 0.0;
        boolean spark = false;
        if (p > p0 && p < p1) {
            double u = (p - p0) / (p1 - p0);
            if (u < 0.15) e = 36.0 * smooth(u / 0.15);
            else if (u < 0.45) { e = 36.0; spark = u > 0.20; }
            else if (u < 0.60) { e = 36.0; y = 136.0 * smooth((u - 0.45) / 0.15); }
            else if (u < 0.88) { e = 36.0; y = 136.0; spark = u > 0.65; }
            else if (u < 0.94) { e = 36.0 * (1.0 - smooth((u - 0.88) / 0.06)); y = 136.0; }
            else y = 136.0 * (1.0 - smooth((u - 0.94) / 0.06));
        }
        if (which == 0) { s.weldE0 = e; s.weldY0 = y; s.spark0 = spark; } else { s.weldE1 = e; s.weldY1 = y; s.spark1 = spark; }
    }
}
