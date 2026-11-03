package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class WmdSound {

    private WmdSound() {}

    public static void play(SoundEvent event, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(new Vec3(x, y, z)), reach = Math.max(64.0, volume * 16.0);
        if (d > reach) return;
        float loud = (float) Mth.clamp(Math.pow(1.0 - d / reach, 0.6) * Math.min(1.0, volume), 0.06, 1.0);
        loud *= ClientConfig.farBoom(d);
        if (loud <= 0.001F) return;
        SoundInstance s = new SimpleSoundInstance(event.getLocation(), SoundSource.BLOCKS, loud, pitch, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, x, y, z, false);
        mc.getSoundManager().playDelayed(s, (int) (d / 17.0));
    }

    public static void playNF(SoundEvent near, SoundEvent far, double farFrom, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean isFar = far != null && mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z)) > farFrom;
        play(isFar ? far : near, x, y, z, volume, pitch);
    }
}
