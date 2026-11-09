package com.ashraf.wmd;

import java.util.function.Function;

import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

public final class DeckLauncherAnim {

    private DeckLauncherAnim() {}

    static final float KICK = 1.6F;
    static final int HOLD = 30, FIRED_T = 100, CAP_T = 8, DOOR_T = 10;
    static final float CAP_DEG = 100.0F, DOOR_DEG = 100.0F, YS = -1.0F;

    static final float GEAR = 30.0F / 8.0F;

    static float smooth(double k) { float f = (float) Math.max(0.0, Math.min(1.0, k)); return f * f * (3.0F - 2.0F * f); }

    static int nextTube(int[] kinds) {
        int att = -1;
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] < 0) continue;
            if (kinds[i] != TorpedoKind.ATT.ordinal()) return i;
            if (att < 0) att = i;
        }
        return att;
    }

    static float kick(double since) {
        if (since < 0 || since > 25) return 0.0F;
        return since < 2 ? (float) (since / 2.0) : (float) Math.exp(-(since - 2.0) / 5.0);
    }

    public static void apply(TorpedoLauncherDeckBlockEntity be, double tt, float pt, Function<String, GeoBone> bone) {
        final float tr = be.shownTrain(pt) * TorpedoLauncherDeckBlockEntity.TRAIN_SIGN * Mth.DEG_TO_RAD;
        GeoBone b;
        if ((b = bone.apply("train")) != null) b.setRotY(tr);
        if ((b = bone.apply("pinion")) != null) b.setRotY(-GEAR * tr);
        final int[] kinds = be.kinds();
        final int next = nextTube(kinds), last = be.lastSlot();
        final double since = tt - be.lastFire();
        final boolean dark = be.fireStatus() == TorpedoLauncherBlockEntity.S_NO_POWER
                || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        final boolean blink = ((long) tt) % 6 < 3;
        for (int t = 0; t < 3; t++) {
            final float cap = smooth(be.capShown(t, pt)), door = smooth(be.doorShown(t, pt));
            if ((b = bone.apply("cap" + t)) != null) {
                if (t == 0) b.setRotX(CAP_DEG * Mth.DEG_TO_RAD * cap);
                else b.setRotY(YS * (t == 1 ? CAP_DEG : -CAP_DEG) * Mth.DEG_TO_RAD * cap);
            }
            if ((b = bone.apply("door" + t)) != null) {
                if (t == 0) b.setRotX(-DOOR_DEG * Mth.DEG_TO_RAD * door);
                else b.setRotY(YS * (t == 1 ? -DOOR_DEG : DOOR_DEG) * Mth.DEG_TO_RAD * door);
            }
            if ((b = bone.apply("tube" + t)) != null) b.setPosZ(t == last ? KICK * kick(since) : 0.0F);
            final boolean loaded = t < kinds.length && kinds[t] >= 0;
            if ((b = bone.apply("nose" + t)) != null) b.setHidden(!loaded);
            final boolean fired = t == last && since >= 0 && since < FIRED_T;
            final boolean ready = loaded && t == next && be.fireStatus() == TorpedoLauncherBlockEntity.S_READY;
            lamp(bone, "lamp" + t + "_f", !dark && fired && (since > 40 || blink));
            lamp(bone, "lamp" + t + "_r", !dark && ready && (cap > 0.98F || blink));
            lamp(bone, "lamp" + t + "_l", !dark && loaded);
            lamp(bone, "lamp" + t + "_e", !dark && !loaded && !fired);
        }
    }

    private static void lamp(Function<String, GeoBone> bone, String n, boolean on) {
        final GeoBone b = bone.apply(n);
        if (b != null) b.setHidden(!on);
    }
}
