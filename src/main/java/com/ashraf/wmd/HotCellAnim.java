package com.ashraf.wmd;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

public final class HotCellAnim {
    private HotCellAnim() {}

    static final float[] TONG_X = { -10.0F, -0.5F };
    static final float SH_Y = 36.4F, L_REST = 11.0F, REST_Y = SH_Y - L_REST;
    static final float RACK_X = -14.5F, STAND_X = -6.0F, FUN_X = 1.5F;
    static final float GRIP_RACK_Y = 21.0F, GRIP_STAND_Y = 20.6F, CARRY_Y = 25.0F, FUN_IN_Y = 23.4F;
    static final float JAW_OPEN = 10.0F;

    static final float GRIP_L = 0.185F, REL_L = 0.385F, GRIP_R = 0.565F, BEAT = 0.77F, BEAT_END = 0.80F, FLASH_END = 0.79F, RACK_UP0 = 0.88F, RACK_UP1 = 0.98F;

    private static final float[][] LK = { { 0F, -10.0F, REST_Y }, { 0.04F, -10.0F, REST_Y }, { 0.10F, RACK_X, CARRY_Y }, { 0.16F, RACK_X, GRIP_RACK_Y },
            { 0.20F, RACK_X, GRIP_RACK_Y }, { 0.25F, RACK_X, CARRY_Y }, { 0.32F, STAND_X, CARRY_Y }, { 0.37F, STAND_X, GRIP_STAND_Y },
            { 0.40F, STAND_X, GRIP_STAND_Y }, { 0.44F, STAND_X, CARRY_Y }, { 0.50F, -10.0F, REST_Y } };
    private static final float[][] RK = { { 0F, -0.5F, REST_Y }, { 0.44F, -0.5F, REST_Y }, { 0.50F, STAND_X, CARRY_Y }, { 0.55F, STAND_X, GRIP_STAND_Y },
            { 0.58F, STAND_X, GRIP_STAND_Y }, { 0.63F, STAND_X, 25.2F }, { 0.70F, FUN_X, 25.2F }, { 0.75F, FUN_X, FUN_IN_Y }, { 0.79F, FUN_X, FUN_IN_Y },
            { 0.82F, FUN_X, 25.4F }, { 0.88F, -0.5F, REST_Y } };
    private static final float[][] LJ = { { 0F, 0F }, { 0.08F, JAW_OPEN }, { 0.17F, JAW_OPEN }, { GRIP_L + 0.005F, 0F }, { 0.37F, 0F }, { REL_L, JAW_OPEN },
            { 0.46F, JAW_OPEN }, { 0.52F, 0F } };
    private static final float[][] RJ = { { 0F, 0F }, { 0.46F, JAW_OPEN }, { 0.55F, JAW_OPEN }, { GRIP_R + 0.005F, 0F }, { 0.76F, 0F }, { BEAT, 12.0F },
            { 0.84F, 12.0F }, { 0.90F, 0F } };

    static float smooth(float t) { t = Mth.clamp(t, 0.0F, 1.0F); return t * t * (3.0F - 2.0F * t); }

    static float key(float[][] k, float p, int c) {
        for (int i = k.length - 1; i >= 0; i--) {
            if (p >= k[i][0]) {
                if (i == k.length - 1) return k[i][c];
                float t = (p - k[i][0]) / (k[i + 1][0] - k[i][0]);
                return k[i][c] + (k[i + 1][c] - k[i][c]) * smooth(t);
            }
        }
        return k[0][c];
    }

    static float ease(ProcessUnitBlockEntity be) { return Mth.clamp((be.spinAngle(1.0F) - be.spinAngle(0.0F)) / 0.9F, 0.0F, 1.0F); }

    static float phase(ReprocessingPlantBlockEntity be, float pt) {
        float t = be.animProg + (be.isRunning() ? pt : 0.0F);
        return Mth.clamp(t / Balance.REPRO_CYCLE_TICKS, 0.0F, 1.0F);
    }

    public static void apply(Function<String, Optional<GeoBone>> bone, ReprocessingPlantBlockEntity be, float spin, float pt) {
        final float w = ease(be), p = phase(be, pt);

        for (int i = 0; i < 2; i++) {
            float[][] k = i == 0 ? LK : RK;
            float sx = TONG_X[i];
            float wx = sx + (key(k, p, 1) - sx) * w, wy = REST_Y + (key(k, p, 2) - REST_Y) * w;
            float jaw = key(i == 0 ? LJ : RJ, p, 1) * w;
            float th = (float) Math.atan2(wx - sx, SH_Y - wy), ext = (float) Math.hypot(wx - sx, SH_Y - wy) - L_REST;
            String pre = i == 0 ? "rp_l" : "rp_r", m = i == 0 ? "rp_ml" : "rp_mr";
            final float jr = jaw * Mth.DEG_TO_RAD;
            bone.apply(pre + "s").ifPresent(b -> b.setRotZ(th));
            bone.apply(pre + "t").ifPresent(b -> b.setPosY(-ext));
            bone.apply(pre + "w").ifPresent(b -> b.setRotZ(-th));
            bone.apply(pre + "j0").ifPresent(b -> b.setRotZ(-jr));
            bone.apply(pre + "j1").ifPresent(b -> b.setRotZ(jr));
            bone.apply(m).ifPresent(b -> b.setRotZ(0.6F * th));
            bone.apply(m + "h").ifPresent(b -> b.setPosY(-Math.min(4.0F, 0.7F * ext)));
        }

        final String b = be.animBreeder ? "b" : "";
        final float rackRise = p >= RACK_UP0 ? smooth((p - RACK_UP0) / (RACK_UP1 - RACK_UP0)) : 1.0F;
        show(bone, "rp_rack", b, p < GRIP_L || p >= RACK_UP0);
        bone.apply("rp_rack" + b).ifPresent(x -> x.setPosY(-7.0F * (1.0F - rackRise)));
        show(bone, "rp_lrod", b, p >= GRIP_L && p < REL_L);
        show(bone, "rp_stand", b, p >= REL_L && p < GRIP_R);
        show(bone, "rp_rrod", b, p >= GRIP_R && p < BEAT);
        show(bone, "rp_drop", b, p >= BEAT && p < BEAT_END);
        bone.apply("rp_drop" + b).ifPresent(x -> x.setPosY(-7.5F * smooth((p - BEAT) / (BEAT_END - BEAT))));
        final boolean dark = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        bone.apply("rp_flash").ifPresent(x -> x.setHidden(dark || !be.isRunning() || p < BEAT || p >= FLASH_END));

        for (int i = 0; i < 2; i++) {
            final float cam = spin * 0.35F + i * Mth.PI;
            bone.apply("rp_cam" + i).ifPresent(x -> x.setRotZ(cam));
            bone.apply("rp_bel" + i).ifPresent(x -> x.setScaleY(0.88F + 0.12F * (0.5F + 0.5F * Mth.cos(cam))));
            bone.apply("rp_lvl" + i).ifPresent(x -> x.setPosY(1.0F * w * Mth.sin(cam)));
        }

        final boolean run = be.isRunning();
        bone.apply("rp_celllamp").ifPresent(x -> x.setHidden(dark || !(run || be.animState != ReprocessingPlantBlockEntity.ANIM_OFF)));
        bone.apply("rp_lamp_a").ifPresent(x -> x.setHidden(dark || run || be.animState != ReprocessingPlantBlockEntity.ANIM_READY));
        bone.apply("rp_lamp_r").ifPresent(x -> x.setHidden(dark || run || be.animState != ReprocessingPlantBlockEntity.ANIM_FAULT));
    }

    private static void show(Function<String, Optional<GeoBone>> bone, String name, String b, boolean on) {
        bone.apply(name).ifPresent(x -> x.setHidden(!(on && b.isEmpty())));
        bone.apply(name + "b").ifPresent(x -> x.setHidden(!(on && !b.isEmpty())));
    }
}
