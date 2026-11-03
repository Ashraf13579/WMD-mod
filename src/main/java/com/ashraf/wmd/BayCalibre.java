package com.ashraf.wmd;

public final class BayCalibre {

    private BayCalibre() {}

    public static final int MAX = 4;

    private static final double[] LEN = { 1.0, 1.0, 1.5, 2.0, 2.5 };
    private static final double[] RAD = { 1.0, 1.0, 1.4, 1.8, 2.4 };

    public static final double R1 = 9.0;

    public static int clamp(int cal) { return Math.max(1, Math.min(MAX, cal)); }
    public static double len(int cal) { return LEN[clamp(cal)]; }
    public static double rad(int cal) { return RAD[clamp(cal)]; }

    public static double lift(int cal) { return R1 * (rad(cal) - 1.0); }
}
