package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientFxVacuum {

    private ClientFxVacuum() {}

    static void tick() {
        for (VacuumBurst.Burst b; (b = VacuumBurst.PENDING.poll()) != null; ) burst(b.x(), b.y(), b.z(), b.size());
    }

    static void burst(double x, double y, double z, float size) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        RandomSource rnd = level.random;
        float s = Mth.clamp(size, 1.0F, 40.0F);
        for (int i = 0; i < 1 + (int) (s / 4.0F); i++)
            level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x + rnd.nextGaussian() * s * 0.15, y + rnd.nextGaussian() * s * 0.15, z + rnd.nextGaussian() * s * 0.15, 0, 0, 0);
        VeilBridge.flash(x, y, z, 1.0F, 0.95F, 0.88F, s * 6.0F + 12.0F, 4.0F, 0.0F, 10);
        var spark = WmdParticles.VACUUM_SPARK.get();
        int n = Math.min(Balance.VACUUM_SPARKS_MAX, ClientConfig.count(40.0F + s * 18.0F, rnd));
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            double v = 0.35 + s * 0.05 + rnd.nextDouble() * 0.25;
            level.addAlwaysVisibleParticle(spark, true, x + d.x * 0.5, y + d.y * 0.5, z + d.z * 0.5, d.x * v, d.y * v, d.z * v);
        }
        WmdSmokeParticle.nextSize = 3.0F;
        try {
            for (int i = 0; i < 4 + (int) (s / 3.0F); i++) {
                Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
                double v = 0.08 + rnd.nextDouble() * 0.12;
                level.addAlwaysVisibleParticle(spark, true, x, y, z, d.x * v, d.y * v, d.z * v);
            }
        } finally {
            WmdSmokeParticle.nextSize = 1.0F;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(new Vec3(x, y, z));
        if (dist < 48.0 + s)
            level.playLocalSound(x, y, z, WmdSounds.HE_FAR.get(), SoundSource.BLOCKS, (float) Mth.clamp(1.2 - dist / (48.0 + s), 0.15, 1.0), 0.5F, false);
        if (dist < 32.0 + s) T4Fx.shakeNear(new Vec3(x, cam.y, z), 32.0 + s, (float) Math.min(1.0, s / 10.0));
    }
}
