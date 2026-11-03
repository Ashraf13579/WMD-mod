package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAftermath {

    private ClientAftermath() {}

    private static final double VIEW = 256.0;

    private record Zone(AftermathPayload p, double[] px, double[] pz, boolean[] burst) {}

    private static final List<Zone> ZONES = Statics.client(new ArrayList<>());

    static void add(AftermathPayload p) {
        for (Zone z : ZONES) if (z.p.start() == p.start() && z.p.x() == p.x() && z.p.z() == p.z()) return;
        int n = (int) Math.max(2, Math.min(8, p.radius() / 4.0));
        double[] px = new double[n], pz = new double[n];
        RandomSource r = RandomSource.create(Double.doubleToLongBits(p.x()) * 31 + Double.doubleToLongBits(p.z()));
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * p.radius() * 0.85;
            px[i] = p.x() + Math.cos(a) * d;
            pz[i] = p.z() + Math.sin(a) * d;
        }
        ZONES.add(new Zone(p, px, pz, new boolean[1]));
    }

    private static int collapse(ClientLevel level, Zone zone, Vec3 cam, long age, RandomSource r, int budget) {
        AftermathPayload p = zone.p;
        double dx = cam.x - p.x(), dz = cam.z - p.z();
        float r0 = Math.max(2.0F, p.radius());
        if (dx * dx + dz * dz > (VIEW + r0) * (VIEW + r0)) return budget;
        var dust = WmdParticles.COLLAPSE_DUST.get();
        double spread = Math.min(48.0, 8.0 + r0 * 1.5);
        if (!zone.burst[0]) {
            zone.burst[0] = true;
            int n = Math.min(30, 6 + (int) (r0 * 2));
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * r0 * 0.6;
                double x = p.x() + Math.cos(a) * d, z = p.z() + Math.sin(a) * d;
                level.addAlwaysVisibleParticle(dust, true, x, ground(level, x, z) + 0.5, z, Math.cos(a) * 0.12, 0.05 + r.nextDouble() * 0.12, Math.sin(a) * 0.12);
            }
            budget -= n;
        }
        if (age < Balance.COLLAPSE_DUST_ROLL) {
            double t = age / (double) Balance.COLLAPSE_DUST_ROLL, ease = 1.0 - (1.0 - t) * (1.0 - t), front = r0 + spread * ease;
            int n = Math.min(8, 3 + (int) (r0 / 4));
            for (int i = 0; i < n && budget > 0; i++, budget--) {
                double a = r.nextDouble() * Math.PI * 2, x = p.x() + Math.cos(a) * front, z = p.z() + Math.sin(a) * front, v = 0.03 + 0.12 * (1.0 - t);
                level.addAlwaysVisibleParticle(dust, true, x, ground(level, x, z) + 0.3, z, Math.cos(a) * v, 0.004, Math.sin(a) * v);
            }
        } else if ((age & 7) == 0 && budget > 0) {
            double fade = 1.0 - age / (double) p.duration(), a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * (r0 + spread);
            if (r.nextDouble() < fade) {
                double x = p.x() + Math.cos(a) * d, z = p.z() + Math.sin(a) * d;
                level.addAlwaysVisibleParticle(dust, true, x, ground(level, x, z) + 0.5, z, 0.0, 0.002, 0.0);
                budget--;
            }
        }
        return budget;
    }

    private static int special(ClientLevel level, Zone z, AftermathPayload p, Vec3 cam, double dist, double fade, RandomSource r, int budget) {
        boolean glow = p.kind() == Aftermath.GLOW;
        for (int i = 0; i < z.px.length && budget > 0; i++) {
            if (r.nextFloat() > 0.2F * fade + 0.02F) continue;
            double x = z.px[i] + (r.nextDouble() - 0.5) * 3.0, zz = z.pz[i] + (r.nextDouble() - 0.5) * 3.0, y = near(level, p, (int) Math.floor(x), (int) Math.floor(zz)) + 0.4;
            if (glow) level.addAlwaysVisibleParticle(r.nextInt(4) == 0 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH, true, x, y + r.nextDouble() * 2.0, zz, 0.0, 0.015 + 0.02 * fade, 0.0);
            else level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, x, y, zz, (r.nextDouble() - 0.5) * 0.04, 0.0, (r.nextDouble() - 0.5) * 0.04);
            budget--;
        }
        if (!glow && dist < p.radius() * 3.0 + 16.0)
            for (int i = 0; i < Math.ceil(4 * fade) && budget > 0; i++, budget--)
                level.addParticle(ParticleTypes.SNOWFLAKE, cam.x + (r.nextDouble() - 0.5) * 24, cam.y + 3 + r.nextDouble() * 8, cam.z + (r.nextDouble() - 0.5) * 24, 0.0, -0.05, 0.0);
        return budget;
    }

    private static void atmosphere(ClientLevel level, AftermathPayload p, long age, Vec3 cam, RandomSource r) {
        double dx = cam.x - p.x(), dz = cam.z - p.z(), reach = p.radius() * 2.5 + 16.0, d = Math.sqrt(dx * dx + dz * dz);
        if (d > reach || age < 60) return;
        double fade = 1.0 - age / (double) p.duration(), near = 1.0 - d / reach;
        if (age % 160 == 0) level.playLocalSound(p.x(), p.y() + 2.0, p.z(), net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, (float) (0.8 * fade), 0.5F, false);
        if (r.nextDouble() < (0.08 + 0.5 * near * near) * fade)
            level.playLocalSound(cam.x, cam.y, cam.z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.AMBIENT, 0.25F, 1.7F + r.nextFloat() * 0.3F, false);
    }

    private static double near(ClientLevel level, AftermathPayload p, int x, int z) {
        double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return Math.abs(h - p.y()) <= p.radius() + 4.0 ? h : p.y();
    }

    private static double ground(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (ZONES.isEmpty() || level == null || mc.isPaused() || mc.gameRenderer == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        RandomSource r = level.random;
        int budget = Balance.AFTERMATH_PARTICLES;
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            AftermathPayload p = z.p;
            long age = now - p.start();
            if (age > p.duration()) { it.remove(); continue; }
            if (p.kind() == Aftermath.COLLAPSE) { budget = collapse(level, z, cam, age, r, budget); continue; }
            if (p.kind() == Aftermath.NUCLEAR) atmosphere(level, p, age, cam, r);
            if (age < 60) continue;
            double dx = cam.x - p.x(), dz = cam.z - p.z(), dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > VIEW + p.radius()) continue;
            double fade = 1.0 - age / (double) p.duration();
            if (p.kind() == Aftermath.GLOW || p.kind() == Aftermath.FROST) { budget = special(level, z, p, cam, dist, fade, r, budget); continue; }
            boolean big = p.kind() == Aftermath.BIG || p.kind() == Aftermath.NUCLEAR;

            for (int i = 0; i < z.px.length && budget > 0; i++) {
                if (r.nextFloat() > 0.25F * fade + 0.03F) continue;
                int gx = (int) Math.floor(z.px[i]), gz = (int) Math.floor(z.pz[i]);
                double gy = near(level, p, gx, gz);
                level.addAlwaysVisibleParticle(big ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        z.px[i] + (r.nextDouble() - 0.5) * 1.5, gy + 0.2, z.pz[i] + (r.nextDouble() - 0.5) * 1.5, 0.0, 0.05 + 0.04 * fade, 0.0);
                budget--;
            }

            if (p.kind() != Aftermath.DUST && age < p.duration() / 3 && dist < 96.0 + p.radius()) {
                for (int i = 0; i < 2 && budget > 0; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * p.radius() * 0.8;
                    double ex = p.x() + Math.cos(a) * d, ez = p.z() + Math.sin(a) * d;
                    double ey = near(level, p, (int) Math.floor(ex), (int) Math.floor(ez)) + 0.1;
                    level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, ex, ey, ez, 0.0, 0.01, 0.0);
                    budget--;
                }
                if (now % 40 == 0 && dist < 48.0 + p.radius()) level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.2F, 0.7F, false);
            }

            if (dist < p.radius() * 3.0 + 16.0) {
                int n = (int) Math.ceil((big ? 6 : 3) * fade);
                for (int i = 0; i < n && budget > 0; i++) {
                    level.addParticle(p.kind() == Aftermath.DUST ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                            cam.x + (r.nextDouble() - 0.5) * 24, cam.y + 4 + r.nextDouble() * 8, cam.z + (r.nextDouble() - 0.5) * 24, 0.0, -0.02, 0.0);
                    budget--;
                }
            }
        }
    }
}
