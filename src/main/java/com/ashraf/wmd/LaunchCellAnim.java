package com.ashraf.wmd;

import java.util.function.Function;

import software.bernie.geckolib.cache.object.GeoBone;

public final class LaunchCellAnim {

    private LaunchCellAnim() {}

    static final int DOG_T = 8, HOLD = 30, CLOSE_T = 26, SHRED_T = 16, FIRED_T = 100, VENT_T = 26;

    static final int CLOSE_AT = HOLD, LATCH_AT = HOLD + CLOSE_T + DOG_T - 2;

    static final float DOG_TRAVEL = 3.4F;
    static final float[][] DOG_DIRS = { { 1, 1 }, { -1, 1 }, { -1, -1 }, { 1, -1 } };
    static final float XS = -1.0F, GAUGE_SIGN = 1.0F;
    static final float HATCH_DEG = 110.0F, VENT_DEG = 70.0F, GAUGE_DEG = 110.0F;

    static float smooth(double k) { float f = (float) Math.max(0.0, Math.min(1.0, k)); return f * f * (3.0F - 2.0F * f); }

    static double sinceFire(LaunchCellBlockEntity be, double tt) {
        final long op = be.openingTick(), ll = be.lastLaunch(), ab = be.abortTick();
        if (op > ll && op > ab) return tt - op - Balance.LC_OPEN_TICKS;
        if (ll >= ab) return tt - ll;
        return Double.NaN;
    }

    static float[] open(LaunchCellBlockEntity be, double tt) {
        final long op = be.openingTick(), ll = be.lastLaunch(), ab = be.abortTick();
        if (op > ll && op > ab) {
            final double s = tt - op;
            return new float[] { smooth(s / DOG_T), smooth((s - DOG_T) / (Balance.LC_OPEN_TICKS - 2.0 - DOG_T)) };
        }
        final boolean aborted = ab > ll;
        final double s = tt - (aborted ? ab : ll) - (aborted ? 0 : HOLD);
        if (s < 0) return new float[] { 1.0F, 1.0F };
        return new float[] { 1.0F - smooth((s - CLOSE_T) / DOG_T), 1.0F - smooth(s / CLOSE_T) };
    }

    public static void apply(LaunchCellBlockEntity be, double tt, Function<String, GeoBone> bone) {
        final float[] o = open(be, tt);
        final double f = sinceFire(be, tt);
        GeoBone b;
        if ((b = bone.apply("hatch")) != null) b.setRotX(-HATCH_DEG * net.minecraft.util.Mth.DEG_TO_RAD * o[1]);
        for (int k = 0; k < 4; k++) {
            if ((b = bone.apply("dog" + k)) == null) continue;
            final float n = (float) (1.0 / Math.sqrt(2.0)) * DOG_TRAVEL * o[0];
            b.setPosX(XS * DOG_DIRS[k][0] * n);
            b.setPosZ(DOG_DIRS[k][1] * n);
        }

        final boolean torn = be.tornShown();
        if ((b = bone.apply("membrane")) != null) b.setHidden(torn);
        if ((b = bone.apply("torn")) != null) b.setHidden(!torn);
        if ((b = bone.apply("shreds")) != null) {
            final boolean fly = torn && !Double.isNaN(f) && f >= 0 && f < SHRED_T;
            b.setHidden(!fly);
            final float k = fly ? (float) (f / SHRED_T) : 0.0F;
            b.setPosY(52.0F * k * (1.0F - 0.3F * k));
            b.setScaleX(1.0F + 2.2F * k);
            b.setScaleZ(1.0F + 2.2F * k);
        }

        final boolean near = !Double.isNaN(f);
        if ((b = bone.apply("firewheel")) != null) b.setRotX(near ? (float) (Math.PI * 4.0) * smooth((f + 3.0) / 8.0) : 0.0F);
        if ((b = bone.apply("gauge")) != null) {
            final float pk = near && f >= 0 ? smooth(f / 2.0) * (float) Math.exp(-Math.max(0.0, f - 2.0) / 18.0) : 0.0F;
            b.setRotZ(GAUGE_SIGN * GAUGE_DEG * net.minecraft.util.Mth.DEG_TO_RAD * pk);
        }
        if ((b = bone.apply("ventcap")) != null) {
            final float v = near && f >= 1 && f < 1 + VENT_T ? (f < 4 ? smooth((f - 1.0) / 3.0) : 1.0F - smooth((f - 4.0) / (VENT_T - 3.0))) : 0.0F;
            b.setRotX(-VENT_DEG * net.minecraft.util.Mth.DEG_TO_RAD * v);
        }

        final boolean dark = be.cellStatus() == LaunchCellBlockEntity.S_NO_POWER || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        final double sinceLaunch = tt - be.lastLaunch();
        final boolean fired = sinceLaunch >= 0 && sinceLaunch < FIRED_T;
        final boolean opening = be.openingTick() > be.lastLaunch() && be.openingTick() > be.abortTick() && near && f < 0;
        final boolean blink = ((long) tt) % 6 < 3;
        final boolean loaded = be.loadedShown();
        lamp(bone, "lamp_f", !dark && fired && (sinceLaunch > 40 || blink));
        lamp(bone, "lamp_a", !dark && loaded && (opening ? blink : be.cellStatus() == LaunchCellBlockEntity.S_READY));
        lamp(bone, "lamp_l", !dark && loaded);
        lamp(bone, "lamp_e", !dark && !loaded && !fired);
    }

    private static void lamp(Function<String, GeoBone> bone, String n, boolean on) {
        final GeoBone b = bone.apply(n);
        if (b != null) b.setHidden(!on);
    }
}
