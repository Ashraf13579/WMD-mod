package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class KGunAnim {

    private KGunAnim() {}

    static final float HOOK_DROP = 4.0F, DAVIT_DEG = -84.0F, RECOIL_PX = 3.0F, SLIDE_PX = 8.0F;

    static final float ARBOR_DROP = 6.0F, ARBOR_SEAT = 0.08F;

    private static final float AX = -0.5F, AY = 0.8660254F;

    static double reload01(DepthChargeThrowerBlockEntity be, double tt) {
        double p = (tt - be.reloadStart()) / Balance.KG_RELOAD_TICKS;
        return p >= 0.0 && p <= 1.0 ? p : -1.0;
    }

    static boolean carrying(double p) { return p >= 0.12 && p < 0.74; }

    private static float ramp(double p, double a, double b) { return (float) Math.max(0.0, Math.min(1.0, (p - a) / (b - a))); }

    private static float smooth(float k) { return k * k * (3.0F - 2.0F * k); }

    static void apply(DepthChargeThrowerBlockEntity be, double tt, Function<String, GeoBone> bone) {
        double since = tt - be.lastFire(), p = reload01(be, tt);

        float rec = since >= 0 && since < 2 ? (float) (since / 2.0) : since >= 2 && since < 12 ? 1.0F - smooth((float) ((since - 2) / 10.0)) : 0.0F;
        GeoBone barrel = bone.apply("barrel");
        if (barrel != null) { barrel.setPosX(AX * RECOIL_PX * rec); barrel.setPosY(-AY * RECOIL_PX * rec); }
        boolean leaving = since >= 0 && since < 2;
        float slide = leaving ? SLIDE_PX * (float) (since / 2.0) : 0.0F;

        boolean onArbor = be.arborShown() || leaving || p >= 0.74;
        boolean arborIn = onArbor || p >= 0.0;
        if (p >= 0.0 && p < ARBOR_SEAT) slide = ARBOR_DROP * (1.0F - smooth(ramp(p, 0.0, ARBOR_SEAT)));
        GeoBone arbor = bone.apply("arbor"), drum = bone.apply("drum"), dial = bone.apply("dial");
        if (arbor != null) { arbor.setHidden(!arborIn); arbor.setPosX(-AX * slide); arbor.setPosY(AY * slide); }
        if (drum != null) drum.setHidden(!onArbor);
        if (dial != null) {
            dial.setHidden(!onArbor);
            float set = (float) (be.depth() / Balance.DC_DEPTH_MAX * Math.PI * 2.0);
            dial.setRotZ(p >= 0.74 ? set * smooth(ramp(p, 0.74, 1.0)) : set);
        }

        float hook = p < 0 ? 0.0F : p < 0.12 ? smooth(ramp(p, 0.0, 0.12)) : p < 0.22 ? 1.0F - smooth(ramp(p, 0.12, 0.22))
                : p < 0.62 ? 0.0F : p < 0.74 ? smooth(ramp(p, 0.62, 0.74)) : p < 0.86 ? 1.0F - smooth(ramp(p, 0.74, 0.86)) : 0.0F;
        float swing = p < 0 ? 0.0F : p < 0.22 ? 0.0F : p < 0.62 ? smooth(ramp(p, 0.22, 0.62)) : p < 0.86 ? 1.0F : 1.0F - smooth(ramp(p, 0.86, 1.0));
        GeoBone davit = bone.apply("davit"), hk = bone.apply("hook"), spare = bone.apply("spare"), lamp = bone.apply("lamp");
        if (davit != null) davit.setRotY(DAVIT_DEG * net.minecraft.util.Mth.DEG_TO_RAD * swing);
        if (hk != null) hk.setPosY(-HOOK_DROP * hook);
        if (spare != null) spare.setHidden(!be.spareShown() || p >= 0.12);
        if (lamp != null) lamp.setHidden(!be.arborShown());
    }
}
