package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

public final class ClientUnderwaterBlast {

    private ClientUnderwaterBlast() {}

    private static final class Blast {
        final double x, y, z, depth, surface, s;
        final int type;
        final boolean wet;
        int age;
        Blast(UnderwaterBlastPayload p) {
            x = p.x(); y = p.y(); z = p.z(); type = p.kind(); depth = p.depth(); surface = p.surface();
            wet = !Double.isNaN(surface);
            s = Math.max(0.5, p.power() / 8.0);
        }
        double colHeight() { return (type == UnderwaterBlast.DEPTH_CHARGE ? 30.0 : type == UnderwaterBlast.MINE ? 28.0 : type == UnderwaterBlast.T4 ? 24.0 : 20.0); }
        int surfaceDelay() { return depth <= Balance.UW_SHALLOW ? 2 : depth <= Balance.UW_MID ? (int) (6 + depth * 0.4) : (int) (20 + depth * 0.6); }
    }

    private static final List<Blast> ACTIVE = new ArrayList<>();

    static void onReceive(UnderwaterBlastPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Blast b = new Blast(p);
        ACTIVE.add(b);
        Vec3 at = new Vec3(b.x, b.y, b.z), cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(at);
        VeilBridge.flash(b.x, b.y, b.z, 0.72F, 0.88F, 1.0F, (float) (18.0 * b.s), 3.0F, 0.6F, 8);
        VeilBridge.flash(b.x, b.y, b.z, 1.0F, 0.78F, 0.5F, (float) (6.0 * b.s), 4.0F, 4);
        T4Fx.shakeNear(at, 48.0 * b.s, (float) (1.2 * b.s));
        boolean ear = mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        float loud = (float) Math.max(0.3, 6.0 - d / 40.0);

        if (ear) mc.level.playLocalSound(b.x, b.y, b.z, WmdSounds.UW_BLAST_UNDER.get(), SoundSource.BLOCKS, loud, 1.0F, false);
        else if (!b.wet) mc.level.playLocalSound(b.x, b.y, b.z, WmdSounds.HE_NEAR.get(), SoundSource.BLOCKS, loud, 0.7F, false);
        else mc.level.playLocalSound(b.x, b.y, b.z, mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(b.x, b.y, b.z)) < 96.0
                ? WmdSounds.UW_BLAST_ABOVE.get() : WmdSounds.UW_BLAST_ABOVE_FAR.get(), SoundSource.BLOCKS, loud, 1.0F, true);
        if (ear) mc.level.playLocalSound(b.x, b.y, b.z, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.BLOCKS, loud * 0.5F, 0.4F, false);
        Wmd.LOGGER.info("[uw-blast] type {} at {} {} {} depth {} (surface {})", b.type, (int) b.x, (int) b.y, (int) b.z, (int) b.depth, b.wet ? (int) b.surface : "-");
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { ACTIVE.clear(); return; }
        if (ACTIVE.isEmpty()) return;
        RandomSource rnd = level.random;
        for (Iterator<Blast> it = ACTIVE.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            b.age++;
            if (!b.wet) dry(level, b, rnd);
            else {
                ring(level, b, rnd);
                bubble(level, b, rnd);
                surface(level, b, rnd);
            }
            if (b.age > 200) it.remove();
        }
    }

    private static boolean water(ClientLevel level, double x, double y, double z) { return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER); }

    private static void ring(ClientLevel level, Blast b, RandomSource rnd) {
        if (b.age > 9) return;
        double r = 1.0 + b.age * 3.0 * b.s;
        int n = ClientConfig.count(70.0F, rnd);
        WmdSmokeParticle.nextSize = 0.5F;
        try {
            for (int i = 0; i < n; i++) {
                double u = rnd.nextDouble() * 2.0 - 1.0, a = rnd.nextDouble() * Math.PI * 2.0, q = Math.sqrt(1.0 - u * u);
                double px = b.x + Math.cos(a) * q * r, py = b.y + u * r, pz = b.z + Math.sin(a) * q * r;
                if (!water(level, px, py, pz)) continue;
                level.addAlwaysVisibleParticle(WmdParticles.UW_GAS.get(), true, px, py, pz, Math.cos(a) * q * 0.3, u * 0.3, Math.sin(a) * q * 0.3);
            }
        } finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    private static void bubble(ClientLevel level, Blast b, RandomSource rnd) {
        if (b.age > 110) return;
        double deep = 1.0 / (1.0 + b.depth / 60.0);
        double period = 30.0 / (1.0 + b.depth / 80.0);
        double r = 3.5 * b.s * deep * Math.abs(Math.sin(b.age / period * Math.PI)) * Math.exp(-b.age / 70.0) + 0.3;
        double cy = b.y + (b.depth <= Balance.UW_SHALLOW ? b.age * 0.12 : b.age * 0.05);
        if (b.wet && cy > b.surface - 0.5) cy = b.surface - 0.5;
        if (r > 0.5) {
            int n = (int) Math.min(140, r * r * 8.0);
            n = ClientConfig.count((float) n, rnd);
            WmdSmokeParticle.nextSize = (float) (0.7 + r * 0.18);
            try {
                for (int i = 0; i < n; i++) {
                    double u = rnd.nextDouble() * 2.0 - 1.0, a = rnd.nextDouble() * Math.PI * 2.0, q = Math.sqrt(1.0 - u * u);
                    double px = b.x + Math.cos(a) * q * r, py = cy + u * r, pz = b.z + Math.sin(a) * q * r;
                    if (!water(level, px, py, pz)) continue;
                    level.addAlwaysVisibleParticle(WmdParticles.UW_GAS.get(), true, px, py, pz, 0.0, 0.02, 0.0);
                }
            } finally { WmdSmokeParticle.nextSize = 1.0F; }
        }
        if (b.age <= 6) for (int i = 0; i < 6; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, b.x + rnd.nextGaussian() * 0.6, b.y + rnd.nextGaussian() * 0.6, b.z + rnd.nextGaussian() * 0.6, 0, 0, 0);
        int shed = b.age < 40 ? 10 : 3;
        for (int i = 0; i < shed; i++) {
            double px = b.x + rnd.nextGaussian() * r, py = cy + rnd.nextGaussian() * r * 0.6, pz = b.z + rnd.nextGaussian() * r;
            if (!water(level, px, py, pz)) continue;
            level.addParticle(i % 3 == 0 ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.BUBBLE, px, py, pz, rnd.nextGaussian() * 0.05, 0.15 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.05);
        }
    }

    private static void surface(ClientLevel level, Blast b, RandomSource rnd) {
        int k = b.age - b.surfaceDelay();
        if (k < 0) return;
        double sy = b.surface;
        if (b.depth <= Balance.UW_SHALLOW) {
            if (k <= 10) {
                double r = (2.0 + k * 0.7) * b.s, h = k * 0.6 * b.s;
                WmdSmokeParticle.nextSize = 1.2F;
                try {
                    for (int i = 0; i < ClientConfig.count(40.0F, rnd); i++) {
                        double a = rnd.nextDouble() * Math.PI * 2.0, f = rnd.nextDouble();
                        double px = b.x + Math.cos(a) * r * Math.sqrt(1.0 - f * f), pz = b.z + Math.sin(a) * r * Math.sqrt(1.0 - f * f);
                        level.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, px, sy + f * h, pz, Math.cos(a) * 0.08, 0.25 + f * 0.3, Math.sin(a) * 0.08);
                    }
                } finally { WmdSmokeParticle.nextSize = 1.0F; }
            }
            if (k == 6) { ClientFxSplash.start(b.x, sy, b.z, b.colHeight() * b.s / 1.6, 1.0F); level.playLocalSound(b.x, sy, b.z, WmdSounds.UW_COLUMN.get(), SoundSource.BLOCKS, 4.0F, 1.0F, true); }
        } else if (b.depth <= Balance.UW_MID) {
            if (k == 0) { ClientFxSplash.start(b.x, sy, b.z, b.colHeight() * b.s * 0.55 / 1.6, 0.9F); level.playLocalSound(b.x, sy, b.z, WmdSounds.UW_COLUMN.get(), SoundSource.BLOCKS, 3.0F, 1.1F, true); }
        } else if (k <= 90) {
            double r = (1.5 + k * 0.05) * b.s;
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0, rr = r * Math.sqrt(rnd.nextDouble());
                level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_POP, true, b.x + Math.cos(a) * rr, sy + 0.05, b.z + Math.sin(a) * rr, 0, 0.02, 0);
            }
            if (k % 2 == 0) level.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, b.x + rnd.nextGaussian() * r, sy + 0.05, b.z + rnd.nextGaussian() * r, 0, 0.002, 0);
            if (k % 6 == 0) level.addParticle(ParticleTypes.SPLASH, b.x + rnd.nextGaussian() * r * 0.5, sy + 0.1, b.z + rnd.nextGaussian() * r * 0.5, 0, 0.1, 0);
            if (k == 0) level.playLocalSound(b.x, sy, b.z, WmdSounds.UW_BOIL.get(), SoundSource.BLOCKS, 3.0F, 1.0F, true);
        }
        if (k <= 40 && k % 2 == 0) {
            double strength = b.depth <= Balance.UW_SHALLOW ? 1.0 : b.depth <= Balance.UW_MID ? 0.6 : 0.3;
            double ring = (2.0 + k * 0.6) * b.s;
            int n = ClientConfig.count((float) (24.0 * strength), rnd);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                level.addAlwaysVisibleParticle(WmdParticles.TORPEDO_WAKE.get(), true, b.x + Math.cos(a) * ring, sy + 0.05, b.z + Math.sin(a) * ring, Math.cos(a) * 0.05, 0.001, Math.sin(a) * 0.05);
            }
        }
    }

    private static void dry(ClientLevel level, Blast b, RandomSource rnd) {
        if (b.age > 6) return;
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0, v = 0.2 + rnd.nextDouble() * 0.4;
            level.addAlwaysVisibleParticle(WmdParticles.WATER_SPRAY.get(), true, b.x, b.y + 0.5, b.z, Math.cos(a) * v, 0.3 + rnd.nextDouble() * 0.3, Math.sin(a) * v);
            level.addAlwaysVisibleParticle(WmdParticles.VACUUM_SPARK.get(), true, b.x, b.y + 0.5, b.z, Math.cos(a) * v * 2, rnd.nextDouble() * 0.5, Math.sin(a) * v * 2);
        }
        if (b.age == 1) for (int i = 0; i < 8; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, b.x + rnd.nextGaussian(), b.y + 0.5, b.z + rnd.nextGaussian(), 0, 0.05, 0);
    }
}
