package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class HoloAnim {

    private HoloAnim() {}

    private static final float PET = (float) Math.toRadians(70.0), CON = (float) Math.toRadians(70.0);
    private static final float[][] PYL = { { -34.0F, -2.0F }, { 34.0F, -2.0F }, { -34.0F, 34.0F }, { 34.0F, 34.0F } };
    static final float BRIDGE_MIN = -28.0F, BRIDGE_MAX = 28.0F;

    static float ease(float t) { t = Mth.clamp(t, 0.0F, 1.0F); return t * t * (3.0F - 2.0F * t); }

    static float up(HoloTableBlockEntity be, double tt, double d, double len) {
        float k = ease((float) ((tt - be.wakeAt - d) / len));
        return be.awake ? k : 1.0F - ease((float) ((tt - be.wakeAt - (24.0 - d - len)) / len));
    }

    static float scanFront(HoloTableBlockEntity be, double tt) {
        double s = (tt - be.scanAt) / Balance.HOLOT_SCAN_TICKS;
        return s < 0.0 || s > 1.0 ? 1.0F : (float) s;
    }

    static void animate(HoloTableBlockEntity be, CcBones model, double tt, float pt) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();
        boolean online = be.isRunning();
        Player near = online ? level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Balance.HOLOT_WAKE_R, false) : null;
        if (near != null) be.lastActivity = tt;
        boolean want = online && tt - be.lastActivity < Balance.HOLOT_SLEEP_TICKS;
        if (want != be.awake) {
            be.awake = want;
            be.wakeAt = tt;
            if (want) be.scanAt = tt + 14.0;
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, WmdSounds.CAM_SERVO.get(), SoundSource.BLOCKS, Balance.SND_MECH * 0.6F, want ? 1.1F : 0.9F, false);
        }
        int sig = be.zoom() * 31 + Mth.floor(be.viewX()) * 7 + Mth.floor(be.viewZ());
        if (sig != be.lastViewSig) { if (be.lastViewSig != 0 && be.awake) be.scanAt = tt; be.lastViewSig = sig; }
        int warns = be.warnings().size();
        if (warns > be.lastWarnings && be.awake) be.alertAt = tt;
        be.lastWarnings = warns;

        for (int k = 0; k < 6; k++) {
            float p = up(be, tt, 3.0 * k, 10.0);
            float r = (k < 3 ? PET : -PET) * p;
            model.getBone("pet" + k).ifPresent(b -> b.setRotX(r));
        }
        float cons = up(be, tt, 2.0, 12.0);
        model.getBone("con_w").ifPresent(b -> b.setRotZ(CON * cons));
        model.getBone("con_e").ifPresent(b -> b.setRotZ(-CON * cons));
        float pyl = up(be, tt, 4.0, 16.0), heads = up(be, tt, 18.0, 6.0);

        float[] aim = { 0.0F, HoloTableBlockEntity.BED_CZ };
        CcTarget des = be.designated();
        int[] tr = be.tracks();
        float bestD = Float.MAX_VALUE;
        boolean alert = tt - be.alertAt < 60.0 && !be.warnings().isEmpty();
        if (alert) {
            CommandCenter.Warning w = be.warnings().get(be.warnings().size() - 1);
            aim = be.toModel(w.ix() + 0.5, w.iz() + 0.5);
        } else {
            for (int i = 0; i + 4 < tr.length; i += 5) {
                if (tr[i + 3] != HoloTableBlockEntity.K_HOSTILE) continue;
                float[] q = be.toModel(tr[i] + 0.5, tr[i + 2] + 0.5);
                float d = q[0] * q[0] + (q[1] - HoloTableBlockEntity.BED_CZ) * (q[1] - HoloTableBlockEntity.BED_CZ);
                if (d < bestD) { bestD = d; aim = q; }
            }
            if (bestD == Float.MAX_VALUE && des != null) aim = be.toModel(des.x() + 0.5, des.z() + 0.5);
        }
        aim[0] = Mth.clamp(aim[0], -HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_X);
        aim[1] = Mth.clamp(aim[1], HoloTableBlockEntity.BED_Z0, HoloTableBlockEntity.BED_Z1);
        for (int k = 0; k < 4; k++) {
            float hx = PYL[k][0], hz = PYL[k][1];
            float yaw = (float) Math.atan2(-(aim[1] - hz), aim[0] - hx) * heads;
            float lift = Balance.HOLOT_PYL_UP * pyl;
            model.getBone("pyl" + k).ifPresent(b -> b.setPosY(lift));
            model.getBone("phead" + k).ifPresent(b -> b.setRotY(-yaw));
        }

        float sf = scanFront(be, tt);
        float bx;
        boolean lit;
        if (sf < 1.0F) { bx = BRIDGE_MIN + (BRIDGE_MAX - BRIDGE_MIN) * sf; lit = true; }
        else if (des != null && be.awake) { bx = Mth.clamp(be.toModel(des.x() + 0.5, des.z() + 0.5)[0], BRIDGE_MIN, BRIDGE_MAX); lit = true; }
        else { bx = BRIDGE_MIN; lit = false; }
        be.bridgeX += (bx - be.bridgeX) * (sf < 1.0F ? 1.0F : 0.12F);
        float bxx = be.bridgeX;
        model.getBone("bridge").ifPresent(b -> b.setPosX(-bxx));
        final boolean scanLit = lit && be.awake;
        model.getBone("scan").ifPresent(b -> b.setHidden(!scanLit));
        float ring = (float) Math.toRadians((tt * (be.awake ? 3.0 : 0.4)) % 360.0);
        model.getBone("ring").ifPresent(b -> b.setRotY(-ring));
        model.getBone("glow").ifPresent(b -> b.setHidden(!online));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!online || (alert && ((long) tt / 4) % 2 == 0)));
    }

    static boolean alerted(HoloTableBlockEntity be, double tt) { return tt - be.alertAt < 60.0 && !be.warnings().isEmpty(); }
}
