package com.ashraf.wmd;

public final class CcBuildingAnim {

    public static final String GLOW_BONE = "dglow";
    public static final int[] GLOW_RGB = { 0xFF2828, 0xFF7A1E, 0xF0C030, 0x40C8E0, 0x4696FF };

    public static final int[] RADAR_TICKS = { 120, 80, 50 };
    public static final float RADAR_UP = 0.02F, RADAR_DOWN = 0.008F;

    public static final float FAN_MAX = 0.55F, FAN_MIN = 0.35F, FAN_UP = 0.03F, FAN_DOWN = 0.01F;
    public static final int FAN_FULL_MODULES = 12, FANS = 4;

    public static final float DISH_EASE = 0.01F, DISH_AZ_RATE = 0.004F, DISH_EL_MIN = 0.25F, DISH_EL_MAX = 0.65F;

    private float radarA, prevRadarA, radarV;
    private float fanA, prevFanA, fanV;
    private float dishK, prevDishK, dishAz, prevDishAz;
    private long dishT;

    public static float radarSpeed(int defcon) {
        int d = CcDefcon.clamp(defcon);
        int ticks = d >= 4 ? RADAR_TICKS[0] : d >= 2 ? RADAR_TICKS[1] : RADAR_TICKS[2];
        return (float) (2.0 * Math.PI / ticks);
    }

    public static float fanSpeed(int online) {
        float load = Math.min(1.0F, Math.max(0, online) / (float) FAN_FULL_MODULES);
        return FAN_MAX * (FAN_MIN + (1.0F - FAN_MIN) * load);
    }

    public static int glowRgb(int defcon) { return GLOW_RGB[CcDefcon.clamp(defcon) - 1]; }

    public static boolean lightsLit(boolean on, boolean dark, int defcon, boolean sealed, double tt) {
        if (!on || dark) return false;
        if (CcDefcon.clamp(defcon) <= 1) return ((long) (tt / 10.0)) % 2 == 0;
        if (sealed) return ((long) tt) % 20 < 12;
        return true;
    }

    public void tick(boolean on, boolean dark, int defcon, int online, boolean sats) {
        final boolean live = on && !dark;
        prevRadarA = radarA;
        float rt = live ? radarSpeed(defcon) : 0.0F;
        radarV += (rt - radarV) * (rt > radarV ? RADAR_UP : RADAR_DOWN);
        if (radarV < 1.0E-4F && rt == 0.0F) radarV = 0.0F;
        radarA = wrap(radarA + radarV);
        prevRadarA = unwrapPrev(prevRadarA, radarA);

        prevFanA = fanA;
        float ft = live ? fanSpeed(online) : 0.0F;
        fanV += (ft - fanV) * (ft > fanV ? FAN_UP : FAN_DOWN);
        if (fanV < 1.0E-4F && ft == 0.0F) fanV = 0.0F;
        fanA = wrap(fanA + fanV);
        prevFanA = unwrapPrev(prevFanA, fanA);

        prevDishK = dishK; prevDishAz = dishAz;
        float dk = live && sats ? 1.0F : 0.0F;
        dishK = dishK < dk ? Math.min(dk, dishK + DISH_EASE) : Math.max(dk, dishK - DISH_EASE);
        if (dishK > 0.0F) { dishT++; dishAz = wrap(dishAz + DISH_AZ_RATE * dishK); prevDishAz = unwrapPrev(prevDishAz, dishAz); }
    }

    public void apply(CcBones model, float pt, double tt, boolean on, boolean dark, int defcon, boolean sealed) {
        final float ra = lerp(pt, prevRadarA, radarA);
        model.getBone("radar").ifPresent(b -> b.setRotY(ra));
        final float fa = lerp(pt, prevFanA, fanA);
        for (int k = 0; k < FANS; k++) {
            final float a = fa + k * 0.7F;
            model.getBone("fan" + k).ifPresent(b -> b.setRotZ(a));
        }
        final float dk = smooth(lerp(pt, prevDishK, dishK));
        final float az = lerp(pt, prevDishAz, dishAz) * dk;
        final float el = dk * (DISH_EL_MIN + (DISH_EL_MAX - DISH_EL_MIN) * (0.5F + 0.5F * (float) Math.sin((dishT + pt) * 0.01)));
        model.getBone("dish_az").ifPresent(b -> b.setRotY(az));
        model.getBone("dish_el").ifPresent(b -> b.setRotX(el));
        final boolean lit = lightsLit(on, dark, defcon, sealed, tt);
        model.getBone(GLOW_BONE).ifPresent(b -> b.setHidden(!lit));
        final boolean av = on && !dark && ((long) tt) % 30 < 8;
        model.getBone("avlamp").ifPresent(b -> b.setHidden(!av));
    }

    private static float lerp(float pt, float a, float b) { return a + (b - a) * pt; }

    private static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }

    private static final float TAU = (float) (2.0 * Math.PI);

    private static float wrap(float a) { return a >= TAU ? a - TAU : a; }

    private static float unwrapPrev(float prev, float cur) { return prev > cur + Math.PI ? prev - TAU : prev; }
}
