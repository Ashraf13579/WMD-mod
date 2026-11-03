package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class T4Fx {

    private T4Fx() {}

    private static final class Streak { final Vec3 at; final long start; Streak(Vec3 at, long start) { this.at = at; this.start = start; } }
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static float shake;

    private static Vec3 dustAt;
    private static long dustUntil;
    private static final List<Object[]> LATER = new ArrayList<>();

    public static void handle(double x, double y, double z, int stage) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Vec3 at = new Vec3(x, y, z);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(at);
        switch (stage) {
            case WarheadFx.ST_ROD_FALL -> STREAKS.add(new Streak(at, now));
            case WarheadFx.ST_ROD_HIT -> {
                STREAKS.removeIf(s -> s.at.distanceToSqr(at) < 4.0);
                impact(level, at, d, now);
            }
            case WarheadFx.ST_AFTERSHOCK -> {
                addShake(d, 600.0, 2.0F);
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 20.0F, 0.4F);
                for (int i = 0; i < 80; i++) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, x + level.random.nextGaussian() * 25, y + 2 + level.random.nextDouble() * 6, z + level.random.nextGaussian() * 25, 0, 0.1, 0);
            }
            case WarheadFx.ST_DUST -> { dustAt = at; dustUntil = now + 3600; }
            case WarheadFx.ST_HEMP -> hemp(level, at, d, now);
            case WarheadFx.ST_QUAKE -> {
                ClientFxSeismic.quake(x, y, z);
                WmdSound.play(WmdSounds.SEISMIC_RUMBLE.get(), x, y, z, 60.0F, 1.0F);
                WmdSound.play(WmdSounds.SEISMIC_CRACK.get(), x, y, z, 40.0F, 1.0F);
                for (int i = 0; i < 6; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2, r = level.random.nextDouble() * 60;
                    if (!VeilBridge.quasar("he_ring_l", x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r)) break;
                    VeilBridge.quasar("he_debris_m", x + Math.cos(a) * r, y + 0.5, z + Math.sin(a) * r);
                }
                for (int i = 0; i < 160; i++) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, x + level.random.nextGaussian() * 40, y + 1 + level.random.nextDouble() * 4, z + level.random.nextGaussian() * 40, 0, 0.12, 0);
            }
            case WarheadFx.ST_COLLAPSE -> {
                ClientFxSeismic.collapse(x, y, z);
                WmdSound.play(WmdSounds.SEISMIC_CRACK.get(), x, y, z, 50.0F, 0.75F);
                dustAt = at; dustUntil = now + 1200;
                for (int i = 0; i < 200; i++) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x + level.random.nextGaussian() * 60, y + 1 + level.random.nextDouble() * 3, z + level.random.nextGaussian() * 60, 0, 0.15, 0);
            }
            default -> { }
        }
    }

    public static void shakeNear(Vec3 at, double range, float k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        addShake(mc.gameRenderer.getMainCamera().getPosition().distanceTo(at), range, k);
    }

    private static void addShake(double dist, double range, float k) {
        if (dist < range) shake = Math.max(shake, (float) (1.0 - dist / range) * k + 0.1F);
    }

    private static void impact(ClientLevel level, Vec3 at, double d, long now) {
        RandomSource rnd = level.random;
        addShake(d, 1400.0, 3.5F);
        ClientFxRod.impact(at.x, at.y, at.z);
        ImpactFx.start(at.x, at.y, at.z, 3, WarheadType.ROD, 0);
        VeilBridge.flash(at.x, at.y + 6, at.z, 1.0F, 0.95F, 0.85F, 300.0F, 12.0F, 1.5F, 80);
        VeilBridge.flash(at.x, at.y + 30, at.z, 1.0F, 0.6F, 0.2F, 220.0F, 6.0F, 1.0F, 140);
        VeilBridge.shock(at.x, at.y + 1, at.z);
        for (int i = 0; i < 160; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 0.3 + rnd.nextDouble() * 1.8;
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, at.x + rnd.nextGaussian() * 4, at.y + 1, at.z + rnd.nextGaussian() * 4, Math.cos(a) * s * 0.35, 1.5 + rnd.nextDouble() * 3.5, Math.sin(a) * s * 0.35);
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, at.x + rnd.nextGaussian() * 8, at.y + 1, at.z + rnd.nextGaussian() * 8, Math.cos(a) * s, 0.6 + rnd.nextDouble() * 1.8, Math.sin(a) * s);
        }

        WmdSound.play(WmdSounds.ROD_IMPACT.get(), at.x, at.y, at.z, 100.0F, 1.0F);
        WmdSound.play(WmdSounds.ROD_SPLASH.get(), at.x, at.y, at.z, 25.0F, 1.0F);

        if (VeilBridge.quasar("rod_jet_l", at.x, at.y + 1, at.z)) {
            VeilBridge.quasar("he_debris_l", at.x, at.y + 1, at.z);
            VeilBridge.quasar("he_smoke_l", at.x, at.y + 3, at.z);
        }
    }

    private static void hemp(ClientLevel level, Vec3 at, double d, long now) {
        ClientHemp.burst(level, at.x, at.y, at.z);
    }

    private static void later(long tick, Runnable r) { LATER.add(new Object[] { tick, r }); }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { STREAKS.clear(); LATER.clear(); shake = 0; dustUntil = 0; return; }
        long now = level.getGameTime();
        shake *= 0.9F;
        for (int i = LATER.size() - 1; i >= 0; i--) if ((long) LATER.get(i)[0] <= now) { ((Runnable) LATER.remove(i)[1]).run(); }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (int si = STREAKS.size() - 1; si >= 0; si--) {
            Streak s = STREAKS.get(si);
            long age = now - s.start;
            if (age > WarheadFx.ROD_FALL_TICKS + 20) { STREAKS.remove(si); continue; }
            double u = Math.max(0.0, 1.0 - age / (double) WarheadFx.ROD_FALL_TICKS), h = 1800.0 * Math.pow(u, 2.2);
            Vec3 head = new Vec3(s.at.x + h * 0.22, s.at.y + h, s.at.z);
            if (head.distanceTo(cam) > 7000.0) continue;
            Vec3 back = new Vec3(0.22, 1.0, 0.0).normalize();
            for (int i = 0; i < 60; i++) {
                double k = i * 3.0 + rnd.nextDouble() * 3.0;
                Vec3 q = head.add(back.scale(k));
                level.addAlwaysVisibleParticle(i < 8 ? ParticleTypes.END_ROD : WmdParticles.ROCKET_FLAME.get(), true, q.x, q.y, q.z, 0, 0, 0);
                if (i % 3 == 0) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, q.x + rnd.nextGaussian() * 2, q.y, q.z + rnd.nextGaussian() * 2, 0, 0, 0);
            }
            VeilBridge.glow(0x7D00_0000_0000_0000L ^ s.start, head.x, head.y, head.z, 1.0F, 0.85F, 0.6F, 90.0F, 4.0F, 0.6F, now);
            VeilBridge.bloomNear(head.x, head.y, head.z, 1500.0, 1.0F);
            if (age % 40 == 0 && age > 20) WmdSound.play(SoundEvents.ELYTRA_FLYING, head.x, head.y, head.z, 30.0F, 0.4F);
            if (age == 150) WmdSound.play(SoundEvents.WARDEN_SONIC_BOOM, head.x, head.y, head.z, 60.0F, 0.6F);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake < 0.02F) return;
        RandomSource rnd = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.random;
        if (rnd == null) return;
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * shake * 2.2F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * shake * 2.2F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * shake * 1.2F);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || dustAt == null || level.getGameTime() > dustUntil) return;
        double dist = event.getCamera().getPosition().distanceTo(dustAt);
        if (dist > 2500.0) return;
        float k = (float) ((1.0 - dist / 2500.0) * Math.min(1.0, (dustUntil - level.getGameTime()) / 1200.0)) * 0.75F;
        event.setRed(event.getRed() + (0.34F - event.getRed()) * k);
        event.setGreen(event.getGreen() + (0.27F - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (0.22F - event.getBlue()) * k);
    }
}
