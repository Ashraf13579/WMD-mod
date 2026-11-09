package com.ashraf.wmd;

public final class CbrAnim {

    private CbrAnim() {}

    public static final float STOW = 90.0F;

    public static final float HINGE_Y = 84.0F, HINGE_Z = 34.0F, FOOT_Y = 52.0F, FOOT_Z = 42.0F, LUG_Y = 7.0F, LUG_Z = 8.0F;

    public static final int DEPLOY_TICKS = 90, STOW_TICKS = 110;

    public static final int BEAT_TICKS = 30;

    public static final float SLEW_MAX = 3.0F, SLEW_EASE = 0.10F, SLEW_MIN = 0.25F;

    public static final float FAN_UP = 0.03F, FAN_DOWN = 0.012F, FAN_RAD = 0.7F;

    private static final float REST_DY = HINGE_Y + LUG_Y - FOOT_Y, REST_DZ = HINGE_Z + LUG_Z - FOOT_Z;
    private static final double REST_ANG = Math.atan2(REST_DZ, REST_DY), REST_LEN = Math.hypot(REST_DY, REST_DZ);

    public static float[] jack(float tiltDeg) {
        double a = Math.toRadians(tiltDeg);
        double py = HINGE_Y + LUG_Y * Math.cos(a) - LUG_Z * Math.sin(a);
        double pz = HINGE_Z + LUG_Y * Math.sin(a) + LUG_Z * Math.cos(a);
        double dy = py - FOOT_Y, dz = pz - FOOT_Z;
        return new float[] { (float) (Math.atan2(dz, dy) - REST_ANG), (float) (Math.hypot(dy, dz) - REST_LEN) };
    }

    public static float ease(float t) {
        float x = Math.max(0.0F, Math.min(1.0F, t));
        return x * x * (3.0F - 2.0F * x);
    }

    public static boolean beatDark(long age) {
        return (age >= 2 && age < 5) || (age >= 9 && age < 12);
    }
}
