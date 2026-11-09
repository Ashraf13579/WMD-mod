package com.ashraf.wmd;

final class XlDockAnim {

    private XlDockAnim() {}

    static final String NAME = "xl_dock";
    static final int CHOCK_AT = 6, CHOCK_STEP = 6, CHOCK_LEN = 8, SWING_AT = 34, SWING_LEN = 26, SHOE_AT = 60, SHOE_LEN = 8, CONTACT_TICKS = 70;
    static final int LEAVE_SHOW = 50, NGL = 14, NBAR = 10;

    static final int RELEASE_HOLD = 20;

    static final float CLAMP_ANG = 34.5F, ARM_FOLD = 90.0F, PLUG_DROP = 1.15F;
    static final int[] CHOCK_DIR = { 1, -1, 1, -1, -1, 1, -1, 1 };

    static final float[][] CHOCK_PIV = { { -36.5F, 4.0F, -4.0F }, { -36.5F, 4.0F, 70.0F }, { 20.5F, 4.0F, -4.0F }, { 20.5F, 4.0F, 70.0F },
            { -36.5F, 4.0F, 260.0F }, { -36.5F, 4.0F, 186.0F }, { 20.5F, 4.0F, 260.0F }, { 20.5F, 4.0F, 186.0F } };
    static final float[] ARM_PIV = { 32.0F, 30.0F, 241.0F };

    static float ramp(float t, float at, float len) { return t <= at ? 0.0F : t >= at + len ? 1.0F : (t - at) / len; }
    static float ease(float v) { return v * v * (3.0F - 2.0F * v); }

    static float chockRx(int k, float f) { return -CHOCK_DIR[k] * CLAMP_ANG * ease(f); }

    static boolean guideLit(int k, int phase, int seq, int leaveAge, long ticks) {
        if (phase == RobotDockBlockEntity.OFF) return false;
        if (leaveAge >= 0 && leaveAge < LEAVE_SHOW) { int head = NGL - 1 - (leaveAge / 2) % (NGL + 3); return k >= head && k < head + 3; }
        if (phase == RobotDockBlockEntity.INBOUND) { int head = (int) ((ticks / 2) % (NGL + 3)); return k <= head && k > head - 3; }
        if (phase == RobotDockBlockEntity.CONNECTED) return seq < 18 && (seq / 6) % 2 == 0;
        return (k == 0 || k == NGL - 1) && SatelliteMesh.blink(ticks * 0.5, k * 7L);
    }

    static boolean thresholdLit(int phase, int leaveAge) {
        return phase == RobotDockBlockEntity.INBOUND || leaveAge >= 0 && leaveAge < LEAVE_SHOW && phase != RobotDockBlockEntity.OFF;
    }

    static boolean barLit(int k, boolean contact, int level10, boolean charging, long ticks) {
        if (!contact) return false;
        if (charging && level10 < NBAR && k == level10) return ticks % 20 < 10;
        return k < level10;
    }
}
