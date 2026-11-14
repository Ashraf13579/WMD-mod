package com.ashraf.wmd;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public final class ClientTankFx {

    private ClientTankFx() {}

    private static final int MAX = 320;

    static void show(TankFxPayload p) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return;
        RandomSource rnd = lv.random;
        double x = p.x(), y = p.y(), z = p.z();
        float r = Math.max(0.5F, p.r());
        int c = p.colour();
        float cr = ((c >> 16) & 255) / 255F, cg = ((c >> 8) & 255) / 255F, cb = (c & 255) / 255F;
        switch (p.kind()) {
            case TankFxPayload.FIREBALL -> {
                VeilBridge.flash(x, y + 1.0, z, 1.0F, 0.55F, 0.15F, r * 6.0F, 2.5F, 14);
                flames(lv, rnd, x, y, z, r, Math.min(MAX, (int) (40 * r)), 0.12 * r, 0.10);
                smoke(lv, rnd, x, y + r * 0.5, z, r, Math.min(MAX / 2, (int) (10 * r)), r * 0.9F);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), x, y, z, Balance.SND_RELEASE, 1.15F);
            }
            case TankFxPayload.BLEVE -> {
                VeilBridge.flash(x, y + r * 0.5, z, 1.0F, 0.75F, 0.35F, r * 4.0F, 4.0F, 24);
                flames(lv, rnd, x, y + r * 0.3, z, r, MAX, 0.05 * r, 0.06 * r);
                smoke(lv, rnd, x, y + r, z, r, MAX / 2, r * 0.6F);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), x, y, z, Balance.SND_ALARM, 0.8F);
            }
            case TankFxPayload.POP -> {
                VeilBridge.flash(x, y, z, 1.0F, 0.6F, 0.2F, r * 4.0F, 1.2F, 8);
                flames(lv, rnd, x, y, z, r, 16, 0.08, 0.06);
                smoke(lv, rnd, x, y, z, r, 5, 1.0F);
            }
            case TankFxPayload.SPLASH -> {
                DustParticleOptions dust = new DustParticleOptions(new Vector3f(cr, cg, cb), 2.0F);
                int n = Math.min(MAX, (int) (60 * r));
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.15 + rnd.nextDouble() * 0.25 * r;
                    lv.addParticle(dust, x, y + rnd.nextDouble() * r, z, Math.cos(a) * s, 0.1 + rnd.nextDouble() * 0.3, Math.sin(a) * s);
                }
                for (int i = 0; i < 30; i++) lv.addParticle(ParticleTypes.SPLASH, x + (rnd.nextDouble() - 0.5) * r * 2, y + 0.5, z + (rnd.nextDouble() - 0.5) * r * 2, 0.0, 0.2, 0.0);
            }
            case TankFxPayload.STEAM -> {
                int n = Math.min(MAX, (int) (30 * r));
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.1 + rnd.nextDouble() * 0.35;
                    lv.addParticle(WmdParticles.VAPOUR.get(), x, y + rnd.nextDouble() * 2.0, z, Math.cos(a) * s, 0.05 + rnd.nextDouble() * 0.2, Math.sin(a) * s);
                }
                WmdSound.play(WmdSounds.NR_STEAM.get(), x, y, z, Balance.SND_HEAVY, 0.9F);
            }
            default -> { }
        }
    }

    private static void flames(ClientLevel lv, RandomSource rnd, double x, double y, double z, float r, int n, double out, double up) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, e = (rnd.nextDouble() - 0.3) * Math.PI * 0.5, s = out * (0.3 + rnd.nextDouble() * 0.7);
            lv.addParticle(WmdParticles.ROCKET_FLAME.get(), x + (rnd.nextDouble() - 0.5) * r * 0.5, y + rnd.nextDouble() * r * 0.5, z + (rnd.nextDouble() - 0.5) * r * 0.5,
                    Math.cos(a) * Math.cos(e) * s, Math.sin(e) * s + up * rnd.nextDouble(), Math.sin(a) * Math.cos(e) * s);
        }
    }

    private static void smoke(ClientLevel lv, RandomSource rnd, double x, double y, double z, float r, int n, float size) {
        WmdSmokeParticle.nextSize = Math.max(1.0F, size);
        try {
            for (int i = 0; i < n; i++)
                lv.addParticle(WmdParticles.TRAIL_SMOKE.get(), x + (rnd.nextDouble() - 0.5) * r, y + rnd.nextDouble() * r, z + (rnd.nextDouble() - 0.5) * r,
                        (rnd.nextDouble() - 0.5) * 0.05, 0.05 + rnd.nextDouble() * 0.1, (rnd.nextDouble() - 0.5) * 0.05);
        } finally { WmdSmokeParticle.nextSize = 1.0F; }
    }
}
