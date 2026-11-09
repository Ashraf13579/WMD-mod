package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class MgCellAnim {

    private MgCellAnim() {}

    static final float JIB_SWING = -70.05F, LIFT = 4.6F;
    static final float JIB_SIGN = 1.0F;

    static final int TAP_PERIOD = 200;
    static final float TAP_FROM = 0.5F;
    static final float LIFT_UP = 0.10F, SWING_OUT = 0.28F, DIP_IN = 0.38F, FILLED = 0.56F, LIFT_OUT = 0.66F, SWING_BACK = 0.84F, SET_DOWN = 0.96F;
    static final float CAST_COOL = 0.15F;

    static final float CUR_UP = 24.0F, CUR_DOWN = 70.0F;

    static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }
    private static float seg(float s, float a, float b) { return smooth((s - a) / (b - a)); }

    static float tap01(double q) { return q < TAP_FROM ? -1.0F : (float) ((q - TAP_FROM) / (1.0 - TAP_FROM)); }

    static float jib01(double q) {
        float s = tap01(q);
        if (s < LIFT_UP) return 0.0F;
        if (s < SWING_OUT) return seg(s, LIFT_UP, SWING_OUT);
        if (s < LIFT_OUT) return 1.0F;
        if (s < SWING_BACK) return 1.0F - seg(s, LIFT_OUT, SWING_BACK);
        return 0.0F;
    }

    static float lift01(double q) {
        float s = tap01(q);
        if (s < 0.0F) return 0.0F;
        if (s < LIFT_UP) return seg(s, 0.0F, LIFT_UP);
        if (s < SWING_OUT) return 1.0F;
        if (s < DIP_IN) return 1.0F - seg(s, SWING_OUT, DIP_IN);
        if (s < FILLED) return 0.0F;
        if (s < LIFT_OUT) return seg(s, FILLED, LIFT_OUT);
        if (s < SWING_BACK) return 1.0F;
        if (s < SET_DOWN) return 1.0F - seg(s, SWING_BACK, SET_DOWN);
        return 0.0F;
    }

    static float hot01(double q) {
        float s = tap01(q);
        if (s < DIP_IN) return 0.0F;
        if (s < FILLED) return seg(s, DIP_IN, FILLED);
        if (s < SET_DOWN) return 1.0F;
        return 1.0F - seg(s, SET_DOWN, 1.0F);
    }

    static float cast01(double q) {
        float s = tap01(q);
        if (s >= SET_DOWN) return seg(s, SET_DOWN, 1.0F);
        if (q < CAST_COOL) return 1.0F - (float) (q / CAST_COOL);
        return 0.0F;
    }

    static boolean craneMoving(double q) { float s = tap01(q); return s >= 0.0F && s < SET_DOWN; }

    public static void apply(MagnesiumCellBlockEntity be, double tt, Function<String, GeoBone> bone) {
        be.animStep(tt);
        final double q = be.tapQ(tt);
        final boolean run = be.isRunning();
        final boolean dark = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        final float cur = be.currentShown();
        GeoBone b;
        if ((b = bone.apply("mjib")) != null) b.setRotY(JIB_SIGN * (float) Math.toRadians(JIB_SWING) * jib01(q));
        if ((b = bone.apply("mpot")) != null) b.setPosY(LIFT * lift01(q));
        if ((b = bone.apply("mglow")) != null) b.setHidden(dark || cur < 0.03F);
        if ((b = bone.apply("mwell")) != null) b.setHidden(dark || cur < 0.03F);
        if ((b = bone.apply("mhot")) != null) b.setHidden(be.hotShown(tt) < 0.03F);
        if ((b = bone.apply("mcast")) != null) b.setHidden(be.castShown(tt) < 0.03F);
        if ((b = bone.apply("mbeacon")) != null) b.setHidden(dark || !run || !craneMoving(q) || ((long) tt) % 16 >= 8);
    }
}
