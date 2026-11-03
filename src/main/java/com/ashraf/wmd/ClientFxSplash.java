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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ClientFxSplash {

    private ClientFxSplash() {}

    private static final class Splash {
        final double x, y, z, r, h;
        final float big;
        int age;
        Splash(double x, double y, double z, double r, float big) {
            this.x = x; this.y = y; this.z = z; this.r = r; this.big = big;
            this.h = Mth.clamp(r * 1.6, 10.0, r >= 150.0 ? 700.0 : 260.0);
        }
    }

    private static final List<Splash> ACTIVE = new ArrayList<>();
    private static final double G = 0.035;

    static boolean onWater(ClientLevel level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y - 0.5, z);
        return level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.below()).is(FluidTags.WATER);
    }

    static void start(double x, double y, double z, double r, float big) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.add(new Splash(x, y, z, r, big));
        Wmd.LOGGER.info("[splash] a splash column at {} {} {} (crater radius {})", (int) x, (int) y, (int) z, (int) r);
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z));
        float loud = (float) Mth.clamp(2.5 - d / (40.0 * Math.max(4.0, r)), 0.2, 2.5);
        mc.level.playLocalSound(x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, loud * 4.0F, 0.45F, true);
        mc.level.playLocalSound(x, y, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, loud * 3.0F, 0.5F, true);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { ACTIVE.clear(); return; }
        if (ACTIVE.isEmpty()) return;
        RandomSource rnd = level.random;
        var spray = WmdParticles.WATER_SPRAY.get();
        for (Iterator<Splash> it = ACTIVE.iterator(); it.hasNext(); ) {
            Splash s = it.next();
            s.age++;
            double w = Math.max(2.0, s.r * 0.25);
            WmdSmokeParticle.nextSize = s.big;
            try {
                if (s.age <= 24) {
                    int n = ClientConfig.count((float) (10.0 + s.r * 0.6) / (s.big * s.big), rnd);
                    for (int i = 0; i < n; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = w * Math.sqrt(rnd.nextDouble());
                        double vy = Math.sqrt(2.0 * G * s.h) * (0.55 + 0.45 * rnd.nextDouble());
                        level.addAlwaysVisibleParticle(spray, true, s.x + Math.cos(a) * rr, s.y + 0.5, s.z + Math.sin(a) * rr,
                                Math.cos(a) * rr * 0.012, vy, Math.sin(a) * rr * 0.012);
                    }
                }
                if (s.age <= 40 && s.age % 2 == 0) {
                    double ring = w + s.age * Math.max(0.6, s.r * 0.03);
                    int n = ClientConfig.count((float) Math.min(60.0, 8.0 + ring * 0.5) / s.big, rnd);
                    for (int i = 0; i < n; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, v = 0.5 + rnd.nextDouble() * 0.6;
                        level.addAlwaysVisibleParticle(spray, true, s.x + Math.cos(a) * ring, s.y + 0.6 + rnd.nextDouble() * 2.0, s.z + Math.sin(a) * ring,
                                Math.cos(a) * v, 0.12 + rnd.nextDouble() * 0.2, Math.sin(a) * v);
                    }
                }
            } finally {
                WmdSmokeParticle.nextSize = 1.0F;
            }
            if (s.age <= 30) for (int i = 0; i < 12; i++)
                level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, s.x + rnd.nextGaussian() * w, s.y + 1.0, s.z + rnd.nextGaussian() * w, 0.0, 0.3, 0.0);
            if (s.age > 40) it.remove();
        }
    }
}
