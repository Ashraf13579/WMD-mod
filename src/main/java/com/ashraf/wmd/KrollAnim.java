package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class KrollAnim {

    private KrollAnim() {}

    static final float LID_LIFT = 8.0F;
    static final float FEED_FROM = 0.03F, FEED_TO = 0.85F, LID_UP = 0.86F, LID_OPEN = 0.91F, LID_DOWN = 0.97F;

    static final float HEAT_UP = 52.0F, COOL = 130.0F;
    static final float VALVE_TICKS = 20.0F, DRIP_TICKS = 14.0F, DRIP_FALL = 2.4F, WHEEL_TURN = (float) (Math.PI * 3.0);

    static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }

    static float lid01(double p) {
        if (p < LID_UP) return 0.0F;
        if (p < LID_OPEN) return smooth((float) ((p - LID_UP) / (LID_OPEN - LID_UP)));
        if (p < LID_DOWN) return 1.0F;
        return 1.0F - smooth((float) ((p - LID_DOWN) / (1.0 - LID_DOWN)));
    }

    static boolean feeding(double p) { return p > FEED_FROM && p < FEED_TO; }

    public static void apply(KrollReactorBlockEntity be, double tt, Function<String, GeoBone> bone) {
        be.animStep(tt);
        final boolean run = be.isRunning();
        final boolean dark = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        final float heat = be.heatShown(), lid = be.lidShown(), valve = be.valveShown();
        GeoBone b;
        if ((b = bone.apply("lid")) != null) b.setPosY(LID_LIFT * lid);
        if ((b = bone.apply("tiwheel")) != null) b.setRotZ(WHEEL_TURN * valve);
        if ((b = bone.apply("drip")) != null) {
            final boolean dripping = run && valve > 0.8F;
            b.setHidden(!dripping);
            b.setPosY(dripping ? -DRIP_FALL * (float) ((tt % DRIP_TICKS) / DRIP_TICKS) : 0.0F);
        }
        if ((b = bone.apply("heat")) != null) b.setHidden(heat < 0.03F);
        if ((b = bone.apply("hotlamp")) != null) b.setHidden(dark || !((run && heat < 0.95F) || (!run && heat > 0.05F) || lid > 0.05F));
    }
}
