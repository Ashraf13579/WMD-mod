package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class CokerAnim {

    private CokerAnim() {}

    static final float FEED = 14.0F, HATCH_SLIDE = 6.5F;
    static final double CH_X0 = 6.0, CH_Y0 = 11.0, CH_DEG = -8.0, TR_X0 = -13.0, TR_Y0 = 13.0, TR_DEG = -4.5, CHUNK = 1.1, CHUNK_LIFT = 0.25;
    static final double[] CHUNK_X = { 9.0, 12.5, 16.0, 19.5 };

    static final double SWITCH_END = 0.08, HATCH_OPEN = 0.16, STEM_DOWN = 0.30, CUT_END = 0.75, STEM_UP = 0.87, HATCH_SHUT = 0.95;

    static final double STEM_HALF_TURNS = 24.0;
    static final float LEVER_RAD = 0.7F;

    static double chuteTop(double x) { return CH_Y0 + Math.tan(Math.toRadians(CH_DEG)) * (x - CH_X0); }
    static double troughTop(double x) { return TR_Y0 + Math.tan(Math.toRadians(TR_DEG)) * (x - TR_X0); }

    private static float sm(double k) { k = Math.max(0.0, Math.min(1.0, k)); return (float) (k * k * (3.0 - 2.0 * k)); }

    static float hatch01(double p) {
        return p < SWITCH_END ? 0.0F : p < HATCH_OPEN ? sm((p - SWITCH_END) / (HATCH_OPEN - SWITCH_END)) : p < STEM_UP ? 1.0F
                : p < HATCH_SHUT ? 1.0F - sm((p - STEM_UP) / (HATCH_SHUT - STEM_UP)) : 0.0F;
    }

    static float drop(double p) {
        if (p < HATCH_OPEN) return 0.0F;
        if (p < STEM_DOWN) return FEED * sm((p - HATCH_OPEN) / (STEM_DOWN - HATCH_OPEN));
        if (p < CUT_END) return FEED - 0.6F * (float) (1.0 - Math.cos((p - STEM_DOWN) / (CUT_END - STEM_DOWN) * Math.PI * 10.0));
        if (p < STEM_UP) return FEED * (1.0F - sm((p - CUT_END) / (STEM_UP - CUT_END)));
        return 0.0F;
    }

    static boolean cutting(double p) { return p >= STEM_DOWN && p < CUT_END; }

    static float levelDecoked(double p) { return p < STEM_DOWN ? 1.0F : p < CUT_END ? 1.0F - sm((p - STEM_DOWN) / (CUT_END - STEM_DOWN)) : 0.0F; }
    static float levelFilling(double p) { return sm(p); }

    static double[] chunkAt(int dec, double q) {
        final double c = CHUNK + CHUNK_LIFT;
        if (dec == 1) { double x = 9.0 + 14.0 * q; return new double[] { x, chuteTop(x) + c }; }
        if (q < 0.55) { double x = -9.0 + 14.0 * q / 0.55; return new double[] { x, troughTop(x) + c }; }
        if (q < 0.60) { double k = (q - 0.55) / 0.05, x = 5.0 + 1.5 * k; return new double[] { x, troughTop(5.0) + c + (chuteTop(6.5) - troughTop(5.0)) * k * k }; }
        double x = 6.5 + 16.5 * (q - 0.60) / 0.40;
        return new double[] { x, chuteTop(x) + c };
    }

    static void apply(CokerBlockEntity be, double tt, float pt, Function<String, GeoBone> bone) {
        final double cyc = be.cycle(pt);
        final int dec = cyc < 1.0 ? 0 : 1;
        final double p = cyc - dec;
        final boolean on = be.isRunning();
        final boolean dark = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        final float run = be.run01(pt);

        final float stemK = sm((run - 0.4) / 0.6), hatchK = sm(run * 3.0);
        final String[] side = { "W", "E" };
        for (int s = 0; s < 2; s++) {
            final boolean decoked = s == dec;
            final float h = decoked ? hatch01(p) * hatchK : 0.0F;
            final float d = decoked ? drop(p) * stemK : 0.0F;
            final float rot = decoked && p >= HATCH_OPEN && p < STEM_UP ? (float) ((p - HATCH_OPEN) / (STEM_UP - HATCH_OPEN) * STEM_HALF_TURNS * Math.PI) : 0.0F;
            final float out = s == 0 ? HATCH_SLIDE * h : -HATCH_SLIDE * h;
            final float lvl = decoked ? levelDecoked(p) : levelFilling(p);
            final GeoBone hb = bone.apply("hatch" + side[s]), sb = bone.apply("stem" + side[s]), kb = bone.apply("hook" + side[s]), rb = bone.apply("rope" + side[s]);
            final GeoBone lb = bone.apply("lvl" + side[s]), fb = bone.apply("fill" + side[s]), cb = bone.apply("cut" + side[s]);
            if (hb != null) hb.setPosX(out);
            if (sb != null) { sb.setPosY(-d); sb.setRotY(rot); }
            if (kb != null) kb.setPosY(-d);
            if (rb != null) rb.setScaleY(1.0F + d);
            if (lb != null) lb.setScaleY(Math.max(lvl, 0.001F));
            if (fb != null) fb.setHidden(!on || dark || decoked);
            if (cb != null) cb.setHidden(!on || dark || !decoked);
        }

        final float to = dec == 0 ? 1.0F : -1.0F;
        final float lever = p < SWITCH_END ? -to + 2.0F * to * sm(p / SWITCH_END) : to;
        final GeoBone sw = bone.apply("switch");
        if (sw != null) sw.setRotZ(lever * LEVER_RAD);

        final boolean coke = on && cutting(p);
        final double spin = be.spinAngle(pt);
        for (int k = 0; k < CHUNK_X.length; k++) {
            final GeoBone cb = bone.apply("coke" + k);
            if (cb == null) continue;
            cb.setHidden(!coke);
            if (!coke) continue;
            final double q = ((spin * 0.028 + k * 0.25) % 1.0 + 1.0) % 1.0;
            final double[] at = chunkAt(dec, q);
            final double restY = chuteTop(CHUNK_X[k]) + CHUNK + CHUNK_LIFT;
            cb.setPosX((float) -(at[0] - CHUNK_X[k]));
            cb.setPosY((float) (at[1] - restY));
            cb.setRotZ((float) (q * Math.PI * 4.0));
        }
    }
}
