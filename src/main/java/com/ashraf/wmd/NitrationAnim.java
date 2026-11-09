package com.ashraf.wmd;

public final class NitrationAnim {

    private NitrationAnim() {}

    static final float XS = -1.0F, TILT_SIGN = 1.0F;

    static final float GATE_TRAVEL = 6.4F, TROUGH_TILT = 4.81F, CHARGE_RUN = 18.5F, CHARGE_DROP = 2.6F;
    static final float LEVEL_HEEL = 0.2F, LEVEL_FULL = 0.85F;
    static final float[] PULSE_AT = { 0.02F, 0.12F, 0.22F };
    static final float PULSE_LEN = 0.06F;
    static final float DOSE_END = 0.30F, GATE_OPEN = 0.80F, GATE_OPENED = 0.84F, DROP_AT = 0.835F, SLIDE_AT = 0.86F, SLIDE_END = 0.97F,
            DRAIN_FROM = 0.82F, DRAIN_TO = 0.95F, GATE_SHUT = 0.93F, GATE_SHUT_END = 0.98F;
    static final float WHEEL_TURN = (float) (Math.PI * 3.0);

    static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }

    static float star(double p) {
        float a = 0.0F;
        for (float s : PULSE_AT) a += smooth((float) ((p - s) / PULSE_LEN));
        return a * (float) (Math.PI / 3.0);
    }

    static float wheel01(double p) {
        if (p < 0.06) return smooth((float) (p / 0.06));
        if (p < 0.24) return 1.0F;
        return 1.0F - smooth((float) ((p - 0.24) / 0.06));
    }

    static float level(double p) {
        if (p < DOSE_END) return LEVEL_HEEL + (LEVEL_FULL - LEVEL_HEEL) * smooth((float) (p / DOSE_END));
        if (p < DRAIN_FROM) return LEVEL_FULL;
        return LEVEL_FULL - (LEVEL_FULL - LEVEL_HEEL) * smooth((float) ((p - DRAIN_FROM) / (DRAIN_TO - DRAIN_FROM)));
    }

    static float gate01(double p) {
        if (p < GATE_OPEN) return 0.0F;
        if (p < GATE_OPENED) return smooth((float) ((p - GATE_OPEN) / (GATE_OPENED - GATE_OPEN)));
        if (p < GATE_SHUT) return 1.0F;
        return 1.0F - smooth((float) ((p - GATE_SHUT) / (GATE_SHUT_END - GATE_SHUT)));
    }

    static float[] charge(double p) {
        if (p < DROP_AT || p >= SLIDE_END) return new float[] { 0.0F, 0.0F, 0.0F };
        if (p < SLIDE_AT) {
            float k = (float) ((p - DROP_AT) / (SLIDE_AT - DROP_AT));
            return new float[] { 1.0F, 0.0F, CHARGE_DROP * (1.0F - k * k) };
        }
        float k = (float) ((p - SLIDE_AT) / (SLIDE_END - SLIDE_AT));
        return new float[] { 1.0F, CHARGE_RUN * k * k, 0.0F };
    }

    static boolean beat(double p) { return p < DOSE_END || (p >= GATE_OPEN && p < GATE_SHUT_END); }
}
