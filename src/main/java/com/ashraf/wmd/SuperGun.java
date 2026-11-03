package com.ashraf.wmd;

import java.util.List;

public final class SuperGun {

    private SuperGun() {}

    public static final String SHELL = "shell_super_he", CHARGE = "super_charge", BLOCK = "super_gun";

    public static final double PIVOT_Z = 16.0, TRUNNION_Y = 5.0, BARREL_LEN = 16.25;

    public static final float MAX_ELEVATION = 45.0F, CLEAR_ELEVATION = 4.0F;

    public static final float SHELL_PX = 52.0F, CHARGE_PX = 15.0F;

    public static double velocity(int k) {
        double r = Balance.SUPER_GUN_RANGE * (double) Math.max(1, Math.min(Balance.SUPER_GUN_CHARGES, k)) / Balance.SUPER_GUN_CHARGES;
        return Math.sqrt(Balance.ART_GRAVITY * r * 1.02);
    }

    public static int chargeRange(int k) { return Balance.SUPER_GUN_RANGE * Math.max(1, Math.min(Balance.SUPER_GUN_CHARGES, k)) / Balance.SUPER_GUN_CHARGES; }

    public static int autoCharges(double x, double y) {
        for (int k = 1; k <= Balance.SUPER_GUN_CHARGES; k++) {
            double el = Artillery.elevation(velocity(k), x, y, false);
            if (!Double.isNaN(el) && el <= MAX_ELEVATION) return k;
        }
        return -1;
    }

    public static double spread(double artillerySpread) { return Math.max(Balance.ART_MIN_SPREAD, artillerySpread * Balance.SUPER_GUN_SPREAD_MUL); }

    private static final long WARN_BASE = Long.MIN_VALUE / 4, WARN_SPAN = 1L << 40;

    public static long warnId(long shellId) { return WARN_BASE + (shellId & (WARN_SPAN - 1)); }

    public static boolean isWarning(long id) { return id >= WARN_BASE && id < WARN_BASE + WARN_SPAN; }

    public static boolean anyOnTheWay(List<CommandCenter.Warning> l, long now) {
        if (l == null) return false;
        for (CommandCenter.Warning w : l) if (isWarning(w.id()) && w.impactGt() >= now) return true;
        return false;
    }
}
