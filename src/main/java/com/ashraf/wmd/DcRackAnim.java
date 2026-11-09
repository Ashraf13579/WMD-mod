package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class DcRackAnim {

    private DcRackAnim() {}

    static final float GATE_DEG = -105.5F, PAWL_DEG = 60.0F;

    static final float K = 3.6F / 13.0F, ROLL_R = 5.3F, PITCH = 13.0F;

    static final float END_Z = -14.4F;

    static final float PAWL_UP = 3.0F, GATE_OPEN_A = 2.0F, GATE_OPEN_B = 5.0F, ROLL_A = 4.0F, ROLL_B = Balance.DC_RELEASE_TICKS,
            SHUT_A = 1.0F, SHUT_B = DepthChargeRackBlockEntity.SHUT_TICKS, PAWL_DOWN_B = 8.0F, FEED_A = 8.0F, FEED_B = 18.0F;

    private static float ramp(double p, double a, double b) { return (float) Math.max(0.0, Math.min(1.0, (p - a) / (b - a))); }

    private static float smooth(float k) { return k * k * (3.0F - 2.0F * k); }

    static boolean pending(DepthChargeRackBlockEntity be) { return be.releaseStart() > be.lastDrop(); }

    static float gate(DepthChargeRackBlockEntity be, double tt) {
        if (pending(be)) return smooth(ramp(tt - be.releaseStart(), GATE_OPEN_A, GATE_OPEN_B));
        double s = tt - be.lastDrop();
        return s >= 0 && s < SHUT_B ? 1.0F - smooth(ramp(s, SHUT_A, SHUT_B)) : 0.0F;
    }

    static float pawl(DepthChargeRackBlockEntity be, double tt) {
        if (pending(be)) return smooth(ramp(tt - be.releaseStart(), 0.0, PAWL_UP));
        double s = tt - be.lastDrop();
        return s >= 0 && s < PAWL_DOWN_B ? 1.0F - smooth(ramp(s, SHUT_B, PAWL_DOWN_B)) : 0.0F;
    }

    static float drumDz(DepthChargeRackBlockEntity be, int i, double tt) {
        if (pending(be)) {
            if (i != 0) return 0.0F;
            float u = ramp(tt - be.releaseStart(), ROLL_A, ROLL_B);
            return END_Z * (0.5F * u + 0.5F * u * u);
        }
        double s = tt - be.lastDrop();
        if (s < 0 || s >= FEED_B || i + 1 >= 3) return 0.0F;
        return PITCH * (1.0F - smooth(ramp(s, FEED_A, FEED_B)));
    }

    static void apply(DepthChargeRackBlockEntity be, double tt, Function<String, GeoBone> bone) {
        GeoBone b;
        if ((b = bone.apply("gate")) != null) b.setRotX(GATE_DEG * net.minecraft.util.Mth.DEG_TO_RAD * gate(be, tt));
        if ((b = bone.apply("pawl")) != null) b.setRotX(PAWL_DEG * net.minecraft.util.Mth.DEG_TO_RAD * pawl(be, tt));
        for (int i = 0; i < 3; i++) {
            if ((b = bone.apply("slot" + i)) == null) continue;
            float dz = drumDz(be, i, tt);
            b.setPosZ(dz);
            b.setPosY(K * dz);
            b.setRotX(dz / ROLL_R);
        }
        if ((b = bone.apply("lamp")) != null) b.setHidden(be.shownCount() == 0);
        double s = tt - be.lastDrop();
        boolean releasing = pending(be) || (s >= 0 && s < SHUT_B);
        if ((b = bone.apply("lamp_r")) != null) b.setHidden(!(releasing && ((long) tt) % 6 < 3));
    }
}
