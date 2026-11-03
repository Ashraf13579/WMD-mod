package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class T4Launch {

    private T4Launch() {}

    private static final double CORE_NOZZLE = 10.5 / 16.0, BOOSTER_NOZZLE = 39.3 / 16.0;

    private static double[] turn(Direction facing, double x, double z) {
        double a = Math.toRadians(180.0 - facing.toYRot()), cs = Math.cos(a), sn = Math.sin(a);
        return new double[] { x * cs + z * sn, -x * sn + z * cs };
    }

    private static boolean loading(Level level, LaunchTerminalBlockEntity be, Vec3 c, MissileBuild b) {
        RandomSource rnd = level.random;
        long now = level.getGameTime();
        float p = be.progress(0.0F);
        long key = be.getBlockPos().asLong() * 41L;
        Direction f = be.getFacing();
        PadRig.State st = PadRig.at(p);

        double[] down = { 0.255, 0.485, 0.700, 0.910 };
        for (int k = 0; k < 4; k++) {
            double[] o = turn(f, (PadRig.TX[k]) / 16.0, (PadRig.TZ[k] - PadRig.CZ) / 16.0);
            landed(level, rnd, p, (float) down[k], c.x + o[0], c.y + (PadRig.TY[k] - PadRig.MOUNT) / 16.0 + 0.5, c.z + o[1], k == 3 ? 1.0F : k == 0 ? 1.0F : 0.7F);
        }

        for (int w = 0; w < 2; w++) {
            boolean on = w == 0 ? st.spark0 : st.spark1;
            if (!on) continue;
            double e = w == 0 ? st.weldE0 : st.weldE1, y = w == 0 ? st.weldY0 : st.weldY1, z = PadRig.CZ + (w == 0 ? 39.3 : -39.3);
            double[] o = turn(f, (54.0 - e) / 16.0, (z - PadRig.CZ) / 16.0);
            weldFx(level, rnd, c.x + o[0], c.y + (97.0 + y - PadRig.MOUNT) / 16.0, c.z + o[1], key + 50 + w, now);
        }

        if (st.ringSpark) {
            double ang = Math.toRadians(st.ringAngle);
            for (int i = 0; i < 2; i++) {
                double a2 = i == 0 ? ang : rnd.nextDouble() * Math.PI * 2;
                double[] o = turn(f, Math.cos(a2) * 21.6 / 16.0, Math.sin(a2) * 21.6 / 16.0);
                weldFx(level, rnd, c.x + o[0], c.y + (st.ringY - 2.0 - PadRig.MOUNT) / 16.0, c.z + o[1], key + 60 + i, now);
            }
        }

        double[] hook = turn(f, st.trolleyX / 16.0, (st.bridgeZ - PadRig.CZ) / 16.0);
        PadFx.glow(key + 30, c.x + hook[0], c.y + (st.beamY - PadRig.MOUNT) / 16.0 - 1.0, c.z + hook[1], 1.0F, 0.92F, 0.75F, 20.0F, 1.5F, 0.3F, now);
        PadFx.glow(key + 31, c.x, c.y + 8.0, c.z, 0.95F, 0.9F, 0.8F, 18.0F, 1.1F, 0.3F, now);
        PadFx.glow(key + 32, c.x, c.y + 24.0, c.z, 1.0F, 0.75F, 0.4F, 16.0F, 0.9F + 0.4F * (float) Math.sin(now * 0.4), 0.3F, now);
        if (now % 80 == 0) PadFx.sound(level, c.x + hook[0], c.y + 40.0, c.z + hook[1], WmdSounds.T4_CRANE_LOOP.get(), SoundSource.BLOCKS, 8.0F, 1.0F, false);
        if (now % 120 == 40) PadFx.sound(level, c.x + hook[0], c.y + 30.0, c.z + hook[1], WmdSounds.T4_WINCH.get(), SoundSource.BLOCKS, 6.0F, 1.0F, false);
        return true;
    }

    private static void weldFx(Level level, RandomSource rnd, double x, double y, double z, long key, long now) {
        for (int i = 0; i < 22; i++)
            PadFx.always(level, ParticleTypes.ELECTRIC_SPARK, true, x, y, z, rnd.nextGaussian() * 0.22, 0.10 + rnd.nextDouble() * 0.45, rnd.nextGaussian() * 0.22);
        for (int i = 0; i < 4; i++)
            PadFx.always(level, ParticleTypes.LAVA, true, x + rnd.nextGaussian() * 0.15, y, z + rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.05, 0.08, rnd.nextGaussian() * 0.05);
        for (int i = 0; i < 3; i++)
            PadFx.always(level, ParticleTypes.FLAME, true, x, y, z, rnd.nextGaussian() * 0.05, 0.05 + rnd.nextDouble() * 0.08, rnd.nextGaussian() * 0.05);
        if (now % 3 == 0) PadFx.always(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, y + 0.3, z, rnd.nextGaussian() * 0.01, 0.05, rnd.nextGaussian() * 0.01);
        float flick = 0.6F + 0.8F * rnd.nextFloat();
        PadFx.glow(key, x, y, z, 0.7F, 0.85F, 1.0F, 16.0F, 6.0F * flick, 0.5F, now);
        PadFx.glow(key + 1000, x, y - 0.5, z, 1.0F, 0.6F, 0.25F, 8.0F, 3.0F * flick, 0.5F, now);
        if ((now + key) % 80 == 0) PadFx.sound(level, x, y, z, WmdSounds.T4_WELD_LOOP.get(), SoundSource.BLOCKS, 2.0F, 0.95F + rnd.nextFloat() * 0.1F, false);
    }

    private static final java.util.Map<Long, Integer> LANDED = new java.util.HashMap<>();

    static void clearStatics() { LANDED.clear(); }

    private static void landed(Level level, RandomSource rnd, float p, float at, double x, double y, double z, float k) {
        long key = Float.floatToIntBits(at) * 1000003L + (long) Math.floor(level.getGameTime() / 400.0);
        if (p < at || p > at + 0.012F || LANDED.containsKey(key)) return;
        LANDED.put(key, 1);
        if (LANDED.size() > 40) LANDED.clear();
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.15 + rnd.nextDouble() * 0.5;
            PadFx.always(level, ParticleTypes.ELECTRIC_SPARK, true, x + Math.cos(a) * 1.5, y, z + Math.sin(a) * 1.5, Math.cos(a) * s, 0.2 + rnd.nextDouble() * 0.4, Math.sin(a) * s);
            if (i % 3 == 0) PadFx.always(level, WmdParticles.LAUNCH_SMOKE.get(), true, x + Math.cos(a) * 2.0, y, z + Math.sin(a) * 2.0, Math.cos(a) * s * 0.7, 0.03, Math.sin(a) * s * 0.7);
        }
        PadFx.sound(level, x, y, z, WmdSounds.T4_STAGE_SET.get(), SoundSource.BLOCKS, 20.0F * k, 1.0F, false);
        PadFx.flash(x, y + 1.0, z, 1.0F, 0.85F, 0.6F, 30.0F, 2.0F * k, 6);
    }

    private static boolean fuelling(Level level, LaunchTerminalBlockEntity be, Vec3 c, MissileBuild b, boolean ready) {
        RandomSource rnd = level.random;
        long now = level.getGameTime();
        long key = be.getBlockPos().asLong() * 43L;
        Direction f = be.getFacing();
        float k = ready ? 1.0F : Math.max(0.15F, be.progress(0.0F));
        double h = MissileParts.lengthOf(b);

        for (int v = 0; v < 6; v++) {
            double ang = Math.toRadians(v * 60 + (now % 360) * 0.0), vy = c.y + 3.0 + v * 2.6;
            double[] o = turn(f, Math.cos(ang) * 1.45, Math.sin(ang) * 1.45);
            if (rnd.nextFloat() < 0.35F * k + 0.1F)
                PadFx.always(level, WmdParticles.VAPOUR.get(), true, c.x + o[0], vy, c.z + o[1], o[0] * 0.07, -0.03 - rnd.nextDouble() * 0.04, o[1] * 0.07);
        }
        for (int s = 0; s < 2; s++) {
            double[] o = turn(f, 0.0, (s == 0 ? 1.0 : -1.0) * 2.46);
            if (rnd.nextFloat() < 0.5F) PadFx.always(level, WmdParticles.VAPOUR.get(), true, c.x + o[0] + rnd.nextGaussian() * 0.3, c.y + MissileParts.boosterLength() * 0.6, c.z + o[1] + rnd.nextGaussian() * 0.3, o[0] * 0.04, -0.05, o[1] * 0.04);
        }

        for (int i = 0; i < ClientConfig.count(3.0F + 7.0F * k, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = 1.0 + rnd.nextDouble() * 3.0;
            PadFx.always(level, WmdParticles.VAPOUR.get(), true, c.x + Math.cos(a) * rr, c.y + 0.3 + rnd.nextDouble() * 0.6, c.z + Math.sin(a) * rr, Math.cos(a) * 0.09, 0.012, Math.sin(a) * 0.09);
        }

        for (int i = 0; i < ClientConfig.count(4.0F * k, rnd); i++) {
            double a = rnd.nextDouble() * Math.PI * 2, y = c.y + 2.0 + rnd.nextDouble() * (h * 0.55);
            PadFx.always(level, ParticleTypes.SNOWFLAKE, true, c.x + Math.cos(a) * 1.4, y, c.z + Math.sin(a) * 1.4, Math.cos(a) * 0.01, -0.03, Math.sin(a) * 0.01);
        }

        float pulse = 0.85F + 0.15F * (float) Math.sin(now * 0.2);
        for (int i = 0; i < 3; i++) PadFx.glow(key + i, c.x, c.y + h * (0.2 + 0.3 * i), c.z, 0.8F, 0.9F, 1.0F, 16.0F, (0.7F + 0.8F * k) * pulse, 0.3F, now);
        double a = now * 0.3;
        PadFx.glow(key + 5, c.x + Math.cos(a) * 2.0, c.y + h + 1.0, c.z + Math.sin(a) * 2.0, 1.0F, ready ? 0.15F : 0.55F, ready ? 0.1F : 0.12F, 12.0F, 1.6F, 0.3F, now);
        if (now % 100 == 0) PadFx.sound(level, c.x, c.y + 10.0, c.z, WmdSounds.T4_FUEL_LOOP.get(), SoundSource.BLOCKS, 8.0F * k + 4.0F, ready ? 0.9F : 1.0F, false);
        return true;
    }

    public static boolean particles(Level level, LaunchTerminalBlockEntity be, Vec3 c) {
        LaunchTerminalBlockEntity.Stage st = be.getStage();
        MissileBuild bb = be.getLoadedBuild();
        if (st == LaunchTerminalBlockEntity.Stage.LOADING) return !bb.staged() || loading(level, be, c, bb);
        if (st == LaunchTerminalBlockEntity.Stage.FUELLING) return fuelling(level, be, c, bb, false);
        if (st == LaunchTerminalBlockEntity.Stage.READY) return fuelling(level, be, c, bb, true);
        if (st != LaunchTerminalBlockEntity.Stage.COUNTDOWN && st != LaunchTerminalBlockEntity.Stage.LAUNCHING) return false;
        RandomSource rnd = level.random;
        Direction f = be.getFacing();
        int t = be.getStageTicks();
        long now = level.getGameTime();
        long key = be.getBlockPos().asLong() * 37L;
        double[] tr = turn(f, 1.0, 0.0);
        if (st == LaunchTerminalBlockEntity.Stage.COUNTDOWN) {
            int left = Missiles.COUNTDOWN_TICKS - t;
            if (left < 140) {
                float k = Math.min(1.0F, (140 - left) / 40.0F);
                for (int i = 0; i < ClientConfig.count(14.0F * k, rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = 2.6 + rnd.nextDouble() * 1.4;
                    PadFx.always(level, ParticleTypes.FALLING_WATER, true, c.x + Math.cos(a) * rr, c.y + 6.0 + rnd.nextDouble() * 4.0, c.z + Math.sin(a) * rr, 0, -0.35, 0);
                    PadFx.always(level, ParticleTypes.SPLASH, true, c.x + Math.cos(a) * rr, c.y + 0.2, c.z + Math.sin(a) * rr, 0, 0.1, 0);
                }
                for (int i = 0; i < ClientConfig.count(5.0F * k, rnd); i++) {
                    double sgn = rnd.nextBoolean() ? 1.0 : -1.0, d = 3.0 + rnd.nextDouble() * 5.0;
                    PadFx.always(level, WmdParticles.VAPOUR.get(), true, c.x + tr[0] * sgn * d, c.y + 0.4, c.z + tr[1] * sgn * d, tr[0] * sgn * 0.12, 0.05 + rnd.nextDouble() * 0.06, tr[1] * sgn * 0.12);
                }
            }
            if (left < 60) PadFx.glow(key + 1, c.x, c.y + 1.5, c.z, 1.0F, 0.55F, 0.25F, 14.0F, 1.6F * (1.0F - left / 60.0F), 0.4F, now);
            return true;
        }

        float up = Missiles.ascent(t), prevUp = Missiles.ascent(t - 1);
        double tail = c.y + up;
        if (t == 1) {
            PadFx.always(level, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 0, 0, 0);
            for (int i = 0; i < 14; i++) PadFx.add(level, ParticleTypes.EXPLOSION, c.x + rnd.nextGaussian() * 3.0, c.y + 0.6, c.z + rnd.nextGaussian() * 3.0, 0, 0, 0);
            PadFx.flash(c.x, c.y + 3.0, c.z, 1.0F, 0.85F, 0.6F, 120.0F, 9.0F, 1.2F, 50);
            PadFx.shock(c.x, c.y + 0.5, c.z);
            if (!LaunchSounds.on()) {
                PadFx.sound(Minecraft.getInstance().level, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 60.0F, 0.35F, false);
                PadFx.sound(Minecraft.getInstance().level, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 40.0F, 0.5F, false);
            }
        }
        if (!LaunchSounds.on()) {
            if (t % 10 == 0 && t < 200) PadFx.sound(Minecraft.getInstance().level, c.x, tail, c.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.BLOCKS, 30.0F, 0.4F, false);
            if (t % 6 == 0 && t < 160) PadFx.sound(Minecraft.getInstance().level, c.x, tail, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 30.0F, 0.35F, false);
        }

        double[][] noz = new double[5][];
        for (int k = 0; k < 3; k++) { double a = Math.toRadians(120 * k + 90); double[] o = turn(f, CORE_NOZZLE * Math.cos(a), CORE_NOZZLE * Math.sin(a)); noz[k] = new double[] { o[0], o[1], 1.0 }; }
        for (int k = 0; k < 2; k++) { double[] o = turn(f, 0.0, (k == 0 ? 1.0 : -1.0) * BOOSTER_NOZZLE); noz[3 + k] = new double[] { o[0], o[1], 1.25 }; }
        boolean fire = t >= Missiles.IGNITION_TICKS - 8;
        for (int k = 0; k < noz.length; k++) {
            double nx = c.x + noz[k][0], nz = c.z + noz[k][1], sc = noz[k][2];
            if (fire) {
                for (int i = 0; i < ClientConfig.count((float) (7 * sc), rnd) + 1; i++)
                    PadFx.always(level, WmdParticles.ROCKET_FLAME.get(), true, nx + rnd.nextGaussian() * 0.22 * sc, tail - 0.3 - rnd.nextDouble() * 1.4, nz + rnd.nextGaussian() * 0.22 * sc,
                            rnd.nextGaussian() * 0.05, -0.9 - rnd.nextDouble() * 0.8, rnd.nextGaussian() * 0.05);
                float flick = 0.85F + rnd.nextFloat() * 0.3F;
                PadFx.glow(key + 10 + k, nx, tail - 1.6, nz, 1.0F, 0.66F, 0.3F, (float) (18.0 * sc), 2.6F * flick, 0.4F, now);
            }
            if (t >= Missiles.IGNITION_TICKS - 4 && up < 300) {
                double from = Math.max(0.0, prevUp - 1.0), to = up;
                double step = Math.max(0.9, (to - from) / Math.max(1, ClientConfig.count(16, rnd)));
                for (double yy = from; yy <= to + 0.01; yy += step)
                    PadFx.always(level, WmdParticles.TRAIL_SMOKE.get(), true, nx + rnd.nextGaussian() * 0.9, c.y + yy - 1.0, nz + rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.03, -0.03 - rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.03);
            }
        }
        if (fire) {
            PadFx.bloomNear(c.x, tail, c.z, 600.0, 1.3F);
            PadFx.haze(c.x, tail - 1.0, c.z, 9.0, 260.0, 1.0F);
        }

        if (up < 60 && fire) {
            float k = up < 10 ? 1.0F : 1.0F - (up - 10) / 50.0F;
            for (int i = 0; i < ClientConfig.count(26.0F * k, rnd); i++) {
                double sgn = rnd.nextBoolean() ? 1.0 : -1.0, d = 2.0 + rnd.nextDouble() * 5.0, s = 0.6 + rnd.nextDouble() * 1.1;
                PadFx.always(level, WmdParticles.LAUNCH_SMOKE.get(), true, c.x + tr[0] * sgn * d, c.y + 0.5 + rnd.nextDouble() * 1.2, c.z + tr[1] * sgn * d, tr[0] * sgn * s, 0.02 + rnd.nextDouble() * 0.06, tr[1] * sgn * s);
            }
            for (int i = 0; i < ClientConfig.count(14.0F * k, rnd); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, s = 0.35 + rnd.nextDouble() * 0.7;
                PadFx.always(level, WmdParticles.LAUNCH_SMOKE.get(), true, c.x + Math.cos(a) * 3.0, c.y + 0.4, c.z + Math.sin(a) * 3.0, Math.cos(a) * s, 0.04 + rnd.nextDouble() * 0.1, Math.sin(a) * s);
            }
            PadFx.glow(key + 20, c.x, c.y + 1.0, c.z, 1.0F, 0.5F, 0.18F, 34.0F, 4.2F * k * (0.85F + rnd.nextFloat() * 0.3F), 0.6F, now);
        }
        return true;
    }
}
