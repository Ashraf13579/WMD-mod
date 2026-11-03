package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientIntercepts {

    private ClientIntercepts() {}

    record Shot(long id, Vec3 from, Vec3 to, long start, int duration, int kind) {}

    record Burst(double x, double y, double z, boolean kill, long tick) {}

    static final class Counter {
        final long shot, flight, arrive; final int type; final boolean fooled;
        Vec3 m0, m1; boolean started;
        Counter(CounterPayload p) { shot = p.shot(); flight = p.flight(); arrive = p.arrive(); type = p.kind(); fooled = p.fooled(); }
    }
    static final Map<Long, Counter> COUNTERS = new ConcurrentHashMap<>();

    static final Map<Long, Vec3> DECOYS = new ConcurrentHashMap<>();

    public static void evade(EvadePayload p) {
        Evade.add(p.flight(), new Evade.Dodge(p.arrive(), p.x(), p.y(), p.z()));
    }

    static void evadeFx(ClientLevel level, long now) {
        RandomSource rnd = level.random;
        for (Flight f : ClientFlights.FLIGHTS.values()) {
            for (Evade.Dodge d : Evade.of(f.id())) {
                long start = d.arrive() - Evade.LEAD;
                if (now < start || now > d.arrive() + 12) continue;
                Vec3 p = f.position(now);
                Vec3 side = new Vec3(d.x(), 0.0, d.z()).normalize();
                Vec3 out = side.scale(-1.0);
                if (now == start) {
                    VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.8F, 0.5F, 90.0F, 3.0F, 12);
                    WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, p.x, p.y, p.z, 20.0F, 0.7F);
                    WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), p.x, p.y, p.z, 10.0F, 1.3F);
                }
                double burn = now < start + 14 ? 1.0 : 0.35;
                for (int i = 0; i < (int) (14 * burn) + 2; i++) {
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, p.x + rnd.nextGaussian() * 0.4, p.y + rnd.nextGaussian() * 0.4, p.z + rnd.nextGaussian() * 0.4,
                            out.x * 0.9 + rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15, out.z * 0.9 + rnd.nextGaussian() * 0.15);
                }
                for (int i = 0; i < 4; i++)
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.x + out.x * 2.0, p.y, p.z + out.z * 2.0, out.x * 0.3 + rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1, out.z * 0.3 + rnd.nextGaussian() * 0.1);
                VeilBridge.glow(0x6200_0000_0000_0000L ^ f.id(), p.x + out.x * 3.0, p.y, p.z + out.z * 3.0, 1.0F, 0.7F, 0.4F, 26.0F, 3.0F * (float) burn, 0.4F, now);
            }
        }
    }

    public static void counter(CounterPayload p) {
        COUNTERS.put(p.shot(), new Counter(p));
        Shot s = SHOTS.get(p.shot());
        Flight f = ClientFlights.FLIGHTS.get(p.flight());
        if (p.fooled() && s != null && f != null) DECOYS.put(p.shot(), Interceptors.decoyOf(f, p.shot(), p.arrive(), p.kind()).subtract(s.to()));
    }

    static final Map<Long, Shot> SHOTS = new ConcurrentHashMap<>();
    static final List<Burst> BURSTS = new CopyOnWriteArrayList<>();

    static final long BURST_KEEP = 600;

    private static final float SCALE = (float) LauncherGeometry.SCALE;
    private static final double DRAW_RANGE = 1200.0;

    public static void add(InterceptPayload p) {
        SHOTS.put(p.id(), new Shot(p.id(), new Vec3(p.fx(), p.fy(), p.fz()), new Vec3(p.tx(), p.ty(), p.tz()), p.start(), p.duration(), p.kind()));
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        RandomSource rnd = level.random;
        Vec3 up = new Vec3(p.tx() - p.fx(), p.ty() - p.fy(), p.tz() - p.fz()).normalize();
        for (int i = 0; !silo(p.kind()) && i < 60; i++) {
            double sp = 0.1 + rnd.nextDouble() * 0.5;
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.fx() + rnd.nextGaussian() * 0.6, p.fy() + rnd.nextGaussian() * 0.6, p.fz() + rnd.nextGaussian() * 0.6,
                    -up.x * sp + rnd.nextGaussian() * 0.12, -up.y * sp * 0.3 + 0.03, -up.z * sp + rnd.nextGaussian() * 0.12);
        }
        for (int i = 0; !silo(p.kind()) && i < 40; i++) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.fx(), p.fy() - 1.0, p.fz(), rnd.nextGaussian() * 0.2, 0.05 + rnd.nextDouble() * 0.15, rnd.nextGaussian() * 0.2);
        boolean eject = silo(p.kind());
        if (eject) {
            Vec3 tail = new Vec3(p.fx(), p.fy() - dropOf(p.kind()) - MissileParts.length(buildOf(p.kind()).calibre()) * scaleOf(p.kind()) * 0.5, p.fz());
            for (int i = 0; i < 90; i++) level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + rnd.nextGaussian() * 0.5, tail.y + 0.3 + rnd.nextDouble(), tail.z + rnd.nextGaussian() * 0.5,
                    rnd.nextGaussian() * 0.25, -0.1 - rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.25);
            for (int i = 0; i < 60; i++) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, tail.x + rnd.nextGaussian() * 0.8, tail.y + rnd.nextDouble() * 2.0, tail.z + rnd.nextGaussian() * 0.8,
                    rnd.nextGaussian() * 0.12, 0.5 + rnd.nextDouble() * 0.9, rnd.nextGaussian() * 0.12);
            for (int i = 0; i < 90; i++) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, p.fx() + rnd.nextGaussian() * 1.2, p.fy() - 7.0 + rnd.nextDouble() * 7.0, p.fz() + rnd.nextGaussian() * 1.2,
                    rnd.nextGaussian() * 0.2, 0.3 + rnd.nextDouble() * 0.7, rnd.nextGaussian() * 0.2);
            VeilBridge.flash(tail.x, tail.y + 3.0, tail.z, 1.0F, 0.7F, 0.3F, 60.0F, 4.0F, 14);
            LaunchSounds.interceptor(p.kind(), p.fx(), p.fy(), p.fz());
            VeilBridge.bloomNear(p.fx(), p.fy(), p.fz(), 300.0, 0.9F);
            return;
        }
        for (int i = 0; !eject && i < 40; i++) {
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, p.fx() + rnd.nextGaussian() * 0.4, p.fy() + rnd.nextGaussian() * 0.4, p.fz() + rnd.nextGaussian() * 0.4,
                    -up.x * 0.3 + rnd.nextGaussian() * 0.1, -up.y * 0.3 - 0.05, -up.z * 0.3 + rnd.nextGaussian() * 0.1);
        }
        VeilBridge.flash(p.fx(), p.fy(), p.fz(), 1.0F, 0.75F, 0.4F, 90.0F, 4.5F, 14);
        VeilBridge.bloomNear(p.fx(), p.fy(), p.fz(), 300.0, 0.9F);
        LaunchSounds.interceptor(p.kind(), p.fx(), p.fy(), p.fz());
    }

    static boolean silo(int kind) { return kind == 1 || kind == 2; }

    static int riseOf(int kind) { return kind == 1 ? Balance.SILO3_RISE_TICKS : kind == 2 ? Balance.SILO4_RISE_TICKS : 0; }

    static double dropOf(int kind) {
        double mouthPx = kind == 1 ? 119.0 : 72.0, tailPx = kind == 1 ? -318.0 : -198.0;
        return Math.max(2.0, (mouthPx - tailPx) / 16.0 - MissileParts.length(buildOf(kind).calibre()) * scaleOf(kind) * 0.5);
    }

    static Vec3 position(Shot s, double t) {
        Vec3 b = basePosition(s, t);
        Vec3 off = DECOYS.get(s.id());
        if (off == null) return b;
        double k = Mth.clamp((t - (s.start() + s.duration() - Interceptors.DEPLOY)) / Interceptors.DEPLOY, 0.0, 1.0);
        return b.add(off.scale(k * k));
    }

    static final Map<Long, Vec3> STEER = new ConcurrentHashMap<>(), STEER_DRAWN = new ConcurrentHashMap<>();

    public static void steer(InterceptSteerPayload p) {
        Shot s = SHOTS.get(p.id());
        if (s == null) return;
        STEER.put(p.id(), new Vec3(p.tx(), p.ty(), p.tz()).subtract(s.to()));
        STEER_DRAWN.putIfAbsent(p.id(), Vec3.ZERO);
    }

    private static Vec3 steerOffset(Shot s, double t) {
        Vec3 d = STEER_DRAWN.get(s.id());
        if (d == null) return Vec3.ZERO;
        double w = Mth.clamp((t - s.start()) / Math.max(1.0, s.duration()), 0.0, 1.0);
        return d.scale(w * w * (3.0 - 2.0 * w));
    }

    static Vec3 basePosition(Shot s, double t) {
        return curvePosition(s, t).add(steerOffset(s, t));
    }

    static Vec3 curvePosition(Shot s, double t) {
        if (silo(s.kind()) && t < s.start()) {
            double rise = riseOf(s.kind()), u = Mth.clamp((t - (s.start() - rise)) / rise, 0.0, 1.0);
            return s.from().subtract(0.0, dropOf(s.kind()) * (1.0 - u * u), 0.0);
        }
        double e = ease(Mth.clamp(t - s.start(), 0.0, s.duration()), s);
        Vec3 c = control(s);
        double a = (1 - e) * (1 - e), b = 2 * (1 - e) * e, d = e * e;
        return s.from().scale(a).add(c.scale(b)).add(s.to().scale(d));
    }

    static double boostAt(double d, int kind) { return switch (kind) { case 1, 2 -> 0.0; case 3 -> Math.min(3.0, d * 0.1); default -> Math.min(40.0, d * 0.3); }; }
    private static double rampOf(int kind) { return switch (kind) { case 1, 2 -> 14.0; case 3 -> 8.0; default -> 4.0; }; }

    private static double climbOf(int kind) { return switch (kind) { case 1, 2 -> 3.4; case 3 -> 4.0; default -> 2.2; }; }

    private static float scaleOf(int kind) { return switch (kind) { case 1 -> 0.85F; case 2 -> 0.62F; case 3 -> (float) (BatteryGeometry.SCALE * BatteryGeometry.F); default -> SCALE; }; }
    private static MissileBuild buildOf(int kind) { return MissileBuild.interceptor(switch (kind) { case 1, 2 -> 3; case 3 -> 2; default -> 1; }, false); }

    private static double ease(double tau, Shot s) {
        double d = s.duration(), t1 = boostAt(d, s.kind()), ramp = rampOf(s.kind());
        double pathLen = s.from().distanceTo(s.to()) * 1.12;
        double ip = d - t1 <= ramp ? 0.5 * ramp : 0.5 * ramp + (d - t1 - ramp);
        double k = Math.max(0.5, (pathLen / climbOf(s.kind()) - d) / ip);
        return prof(tau, t1, ramp, k) / prof(d, t1, ramp, k);
    }

    private static double prof(double tau, double t1, double ramp, double k) {
        double t2 = Math.max(0.0, tau - t1), x = Math.min(1.0, t2 / ramp);
        double integral = t2 <= ramp ? ramp * (x * x * x - x * x * x * x * 0.5) : ramp * 0.5 + (t2 - ramp);
        return tau + k * integral;
    }

    private static Vec3 control(Shot s) {
        Vec3 d = s.to().subtract(s.from());
        double len = d.length();
        Vec3 dir = len < 1.0E-6 ? new Vec3(0, 1, 0) : d.scale(1.0 / len);
        Vec3 start = silo(s.kind()) ? new Vec3(dir.x * 0.25, 1.0, dir.z * 0.25).normalize() : dir.add(0, 1.1, 0).normalize();
        return s.from().add(start.scale(Mth.clamp(len * 0.4, 20.0, 260.0)));
    }

    static Vec3 heading(Shot s, double t) {
        if (DECOYS.containsKey(s.id()) || STEER_DRAWN.containsKey(s.id())) {
            Vec3 v = position(s, t + 0.5).subtract(position(s, t - 0.5));
            return v.lengthSqr() < 1.0E-9 ? baseHeading(s, t) : v.normalize();
        }
        return baseHeading(s, t);
    }

    static Vec3 baseHeading(Shot s, double t) {
        if (silo(s.kind()) && t < s.start()) return new Vec3(0, 1, 0);
        double e = ease(Mth.clamp(t - s.start(), 0.0, s.duration()), s);
        Vec3 c = control(s);
        Vec3 v = c.subtract(s.from()).scale(2 * (1 - e)).add(s.to().subtract(c).scale(2 * e));
        return v.lengthSqr() < 1.0E-9 ? new Vec3(0, 1, 0) : v.normalize();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { SHOTS.clear(); BURSTS.clear(); return; }
        long now = level.getGameTime();
        double t = now;
        RandomSource rnd = level.random;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BURSTS.removeIf(b -> now - b.tick() > BURST_KEEP);
        counters(level, now);
        evadeFx(level, now);
        for (Shot s : SHOTS.values()) {
            if (t > s.start() + s.duration() + 4) { SHOTS.remove(s.id()); STEER.remove(s.id()); STEER_DRAWN.remove(s.id()); continue; }
            Vec3 st = STEER.get(s.id());
            if (st != null) STEER_DRAWN.compute(s.id(), (k, v) -> v == null ? st : v.add(st.subtract(v).scale(0.2)));
            if (t < s.start() - riseOf(s.kind())) continue;
            Vec3 p = position(s, t);
            double dist = p.distanceTo(cam);
            double len = p.subtract(position(s, t - 1.0)).length();
            Vec3 back = heading(s, t).scale(-1.0);
            Vec3 tail = p.add(back.scale(1.1));

            float flick = 0.85F + rnd.nextFloat() * 0.3F;
            VeilBridge.glow(0x5C00_0000_0000_0000L ^ s.id(), tail.x, tail.y, tail.z, 1.0F, 0.7F, 0.38F, 20.0F, 2.6F * flick, 0.35F, now);
            VeilBridge.bloomNear(tail.x, tail.y, tail.z, 350.0, 0.8F);
            Vec3 hz = p.add(back.scale(4.5));
            VeilBridge.haze(hz.x, hz.y, hz.z, 2.5, 220.0, 1.0F);
            if (!silo(s.kind()) && now - s.start() == (long) boostAt(s.duration(), s.kind())) boost(level, p, heading(s, t), s.kind());
            if (silo(s.kind()) && now - s.start() < 10) {
                for (int i = 0; i < 5; i++) level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + rnd.nextGaussian() * 0.3, tail.y - rnd.nextDouble() * 1.5, tail.z + rnd.nextGaussian() * 0.3,
                        rnd.nextGaussian() * 0.06, -0.2 - rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.06);
                if (now - s.start() > -4) for (int i = 0; i < 4; i++) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, s.from().x + rnd.nextGaussian() * 1.0, s.from().y - 1.0, s.from().z + rnd.nextGaussian() * 1.0,
                        rnd.nextGaussian() * 0.15, 0.2 + rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.15);
            }
            if (now - s.start() < 25 && s.from().distanceTo(cam) < ClientConfig.trailRange()) {
                for (int i = 0; i < 6; i++) {
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, s.from().x + rnd.nextGaussian() * 1.2, s.from().y + rnd.nextGaussian() * 0.6, s.from().z + rnd.nextGaussian() * 1.2,
                            rnd.nextGaussian() * 0.15, 0.02 + rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.15);
                }
            }
            if (dist < ClientConfig.trailRange()) {
                int n = Math.max(1, Math.min(ClientConfig.count(10, rnd), (int) (len / 1.2) + 1));
                for (int i = 0; i < n; i++) {
                    double k = (i + rnd.nextDouble()) / n;
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true,
                            tail.x + back.x * len * k + rnd.nextGaussian() * 0.25, tail.y + back.y * len * k + rnd.nextGaussian() * 0.25,
                            tail.z + back.z * len * k + rnd.nextGaussian() * 0.25, 0, -0.01, 0);
                }
                boolean burning = s.kind() == 0 || s.kind() == 3 || silo(s.kind()) || now - s.start() >= boostAt(s.duration(), s.kind());
                if (!burning) for (int i = 0; i < 3; i++)
                    level.addParticle(ParticleTypes.CLOUD, tail.x + rnd.nextGaussian() * 0.3, tail.y + rnd.nextGaussian() * 0.3, tail.z + rnd.nextGaussian() * 0.3, back.x * 0.05, back.y * 0.05, back.z * 0.05);
                for (int i = 0; burning && i < 3; i++) {
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + rnd.nextGaussian() * 0.15, tail.y + rnd.nextGaussian() * 0.15, tail.z + rnd.nextGaussian() * 0.15,
                            back.x * 0.05 + rnd.nextGaussian() * 0.03, back.y * 0.05, back.z * 0.05 + rnd.nextGaussian() * 0.03);
                }
                if (t - s.start() < 12) level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, rnd.nextGaussian() * 0.08, -0.05, rnd.nextGaussian() * 0.08);
            }

            long arriveAt = s.start() + s.duration();
            if (now >= arriveAt - 70 && now <= arriveAt && dist < DRAW_RANGE) {
                Vec3 hd = heading(s, t), nose = p.add(hd.scale(1.2));
                if (s.kind() == 0 || s.kind() == 3) {
                    VeilBridge.glow(0x5E00_0000_0000_0000L ^ s.id(), nose.x, nose.y, nose.z, 1.0F, 0.45F, 0.15F, 14.0F, 2.2F * (0.8F + rnd.nextFloat() * 0.4F), 0.3F, now);
                } else if (now % 7 == 0) {
                    Vec3 u = Math.abs(hd.y) > 0.95 ? new Vec3(1, 0, 0) : hd.cross(new Vec3(0, 1, 0)).normalize(), w = hd.cross(u).normalize();
                    var dust = new net.minecraft.core.particles.DustParticleOptions(new Vector3f(0.25F, 0.9F, 1.0F), 1.4F);
                    for (int i = 0; i < 28; i++) {
                        double a = Math.PI * 2.0 * i / 28.0, ca = Math.cos(a), sa = Math.sin(a);
                        level.addAlwaysVisibleParticle(dust, true, nose.x, nose.y, nose.z, (u.x * ca + w.x * sa) * 0.9 + hd.x * 0.5, (u.y * ca + w.y * sa) * 0.9 + hd.y * 0.5, (u.z * ca + w.z * sa) * 0.9 + hd.z * 0.5);
                    }
                    VeilBridge.glow(0x5F00_0000_0000_0000L ^ s.id(), nose.x, nose.y, nose.z, 0.3F, 0.9F, 1.0F, 12.0F, 1.6F, 0.3F, now);
                }
            }

            if (!LaunchSounds.flyby(s.id(), p, dist, now)) {
                if ((now + s.id()) % 20 == 0) WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, p.x, p.y, p.z, 8.0F, 0.5F);
                if ((now + s.id()) % 12 == 0) WmdSound.play(SoundEvents.ELYTRA_FLYING, p.x, p.y, p.z, 8.0F, 0.55F);
            }
        }
    }

    private static void counters(ClientLevel level, long now) {
        RandomSource rnd = level.random;
        for (Counter c : COUNTERS.values()) {
            long dep = c.arrive - Interceptors.DEPLOY;
            if (now > c.arrive + 90) { COUNTERS.remove(c.shot); DECOYS.remove(c.shot); continue; }
            if (now < dep) continue;
            if (!c.started) {
                Flight f = ClientFlights.FLIGHTS.get(c.flight);
                if (f == null) { COUNTERS.remove(c.shot); continue; }
                c.m0 = f.position(dep); c.m1 = f.position(c.arrive); c.started = true;
                WmdSound.play(c.type == 1 ? SoundEvents.FIREWORK_ROCKET_LAUNCH : SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, c.m0.x, c.m0.y, c.m0.z, 14.0F, c.type == 1 ? 0.7F : 1.2F);
                VeilBridge.flash(c.m0.x, c.m0.y, c.m0.z, c.type == 1 ? 1.0F : 0.8F, c.type == 1 ? 0.8F : 0.9F, c.type == 1 ? 0.5F : 1.0F, 90.0F, 2.5F, 10);
            }
            double dt = now - dep;
            if (c.type == 1) {
                if (dt > 110) continue;
                for (int i = 0; i < 4; i++) {
                    Vec3 fp = Interceptors.flarePos(c.m0, c.shot, i, dt);
                    float burn = dt < 85 ? 1.0F : (float) Math.max(0.0, 1.0 - (dt - 85) / 25.0);
                    level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, fp.x, fp.y, fp.z, rnd.nextGaussian() * 0.03, 0.02, rnd.nextGaussian() * 0.03);
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, fp.x + rnd.nextGaussian() * 0.2, fp.y + rnd.nextGaussian() * 0.2, fp.z + rnd.nextGaussian() * 0.2, 0.0, -0.01, 0.0);
                    if (rnd.nextInt(3) == 0) level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, fp.x, fp.y, fp.z, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15 - 0.05, rnd.nextGaussian() * 0.15);
                    VeilBridge.glow(0x6000_0000_0000_0000L ^ (c.shot * 8 + i), fp.x, fp.y, fp.z, 1.0F, 0.85F, 0.55F, 30.0F, 3.4F * burn * (0.8F + rnd.nextFloat() * 0.4F), 0.4F, now);
                    VeilBridge.bloomNear(fp.x, fp.y, fp.z, 400.0, 0.6F * burn);
                }
            } else {
                if (dt > Interceptors.DEPLOY + 70) continue;
                Vec3 at = c.m0.add(c.m1.subtract(c.m0).scale(Math.min(1.0, dt / Interceptors.DEPLOY)));
                double spread = 1.0 + 0.45 * dt;
                for (int i = 0; i < 14; i++)
                    level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, true, at.x + rnd.nextGaussian() * spread, at.y + rnd.nextGaussian() * spread, at.z + rnd.nextGaussian() * spread, rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.04 - 0.01, rnd.nextGaussian() * 0.04);
                if (rnd.nextInt(2) == 0) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, at.x + rnd.nextGaussian() * spread * 0.6, at.y + rnd.nextGaussian() * spread * 0.6, at.z + rnd.nextGaussian() * spread * 0.6, 0.0, 0.0, 0.0);
                VeilBridge.glow(0x6100_0000_0000_0000L ^ c.shot, at.x, at.y, at.z, 0.8F, 0.9F, 1.0F, 26.0F + (float) spread, 1.4F, 0.4F, now);
            }
        }
    }

    private static void boost(ClientLevel level, Vec3 p, Vec3 dir, int kind) {
        RandomSource rnd = level.random;
        for (int i = 0; i < 50; i++) {
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, p.x + rnd.nextGaussian() * 0.5, p.y + rnd.nextGaussian() * 0.5, p.z + rnd.nextGaussian() * 0.5,
                    -dir.x * 0.5 + rnd.nextGaussian() * 0.15, -dir.y * 0.5 + rnd.nextGaussian() * 0.15, -dir.z * 0.5 + rnd.nextGaussian() * 0.15);
        }
        for (int i = 0; i < 40; i++) {
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.x + rnd.nextGaussian() * 0.8, p.y + rnd.nextGaussian() * 0.8, p.z + rnd.nextGaussian() * 0.8,
                    rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25);
        }
        VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.8F, 0.5F, 70.0F, 4.0F, 10);
        if (kind == 0) LaunchSounds.interceptorBoost(p.x, p.y, p.z);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SHOTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Shot s : SHOTS.values()) {
            if (t < s.start() - riseOf(s.kind()) || t > s.start() + s.duration()) continue;
            Vec3 p = position(s, t);
            if (p.distanceTo(cam) > DRAW_RANGE) continue;
            Vec3 d = heading(s, t);
            Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z);
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z));
            float sc = scaleOf(s.kind());
            MissileBuild build = buildOf(s.kind());
            ps.scale(sc, sc, sc);
            ps.translate(0.0F, -MissileParts.length(build.calibre()) * 0.5F, 0.0F);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            MissileParts.render(build, true, ps, buffers, light, pt);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    public static void fx(InterceptFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource rnd = level.random;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        BURSTS.add(new Burst(p.x(), p.y(), p.z(), p.kill(), level.getGameTime()));
        long now = level.getGameTime();
        SHOTS.values().removeIf(s -> s.to().distanceTo(at) < 64.0 && now >= s.start() + s.duration() - 2);
        if (p.kill()) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y(), p.z(), 0.0, 0.0, 0.0);
            for (int i = 0; i < 160; i++) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 1.1, rnd.nextGaussian() * 1.1, rnd.nextGaussian() * 1.1);
            for (int i = 0; i < 60; i++) level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6);
            for (int i = 0; i < 80; i++) {
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.x() + rnd.nextGaussian() * 2, p.y() + rnd.nextGaussian() * 2, p.z() + rnd.nextGaussian() * 2,
                        rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.2 - 0.02, rnd.nextGaussian() * 0.2);
            }
            if (!LaunchSounds.interceptKill(p.x(), p.y(), p.z())) {
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), p.x(), p.y(), p.z(), 60.0F, 0.9F);
                WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), p.x(), p.y(), p.z(), 60.0F, 0.6F);
                WmdSound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, p.x(), p.y(), p.z(), 40.0F, 0.5F);
                WmdSound.play(SoundEvents.LIGHTNING_BOLT_THUNDER, p.x(), p.y(), p.z(), 30.0F, 1.2F);
            }
            VeilBridge.flash(p.x(), p.y(), p.z(), 1.0F, 0.85F, 0.55F, 220.0F, 4.0F, 24);
            VeilBridge.bloomNear(p.x(), p.y(), p.z(), 1500.0, 1.0F);
            VeilBridge.shock(p.x(), p.y(), p.z());
        } else if (p.fooled() == 1) {
            for (int i = 0; i < 90; i++) level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.9 - 0.15, rnd.nextGaussian() * 0.9);
            for (int i = 0; i < 70; i++) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 1.2, rnd.nextGaussian() * 1.2, rnd.nextGaussian() * 1.2);
            for (int i = 0; i < 30; i++) level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.4 - 0.05, rnd.nextGaussian() * 0.4);
            WmdSound.play(WmdSounds.FLARE_BURST.get(), p.x(), p.y(), p.z(), 40.0F, 1.0F);
            VeilBridge.flash(p.x(), p.y(), p.z(), 1.0F, 0.75F, 0.35F, 120.0F, 3.0F, 18);
        } else if (p.fooled() == 2) {
            for (int i = 0; i < 160; i++) level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, p.x() + rnd.nextGaussian() * 3.0, p.y() + rnd.nextGaussian() * 3.0, p.z() + rnd.nextGaussian() * 3.0, rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12 - 0.03, rnd.nextGaussian() * 0.12);
            for (int i = 0; i < 60; i++) level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 0.7, rnd.nextGaussian() * 0.7, rnd.nextGaussian() * 0.7);
            for (int i = 0; i < 40; i++) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, p.x() + rnd.nextGaussian() * 2.0, p.y() + rnd.nextGaussian() * 2.0, p.z() + rnd.nextGaussian() * 2.0, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1);
            WmdSound.play(WmdSounds.CHAFF_BURST.get(), p.x(), p.y(), p.z(), 40.0F, 1.0F);
            VeilBridge.flash(p.x(), p.y(), p.z(), 0.8F, 0.9F, 1.0F, 120.0F, 2.5F, 18);
        } else {
            level.addParticle(ParticleTypes.EXPLOSION, p.x(), p.y(), p.z(), 0.0, 0.0, 0.0);
            for (int i = 0; i < 30; i++) level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, p.x(), p.y(), p.z(), rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3);
            if (!LaunchSounds.interceptMiss(p.x(), p.y(), p.z())) WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), p.x(), p.y(), p.z(), 25.0F, 1.3F);
            VeilBridge.flash(p.x(), p.y(), p.z(), 1.0F, 0.8F, 0.55F, 60.0F, 1.6F, 10);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(at) < 2500.0) {
            mc.player.displayClientMessage(Component.translatable(p.kill() ? "msg.wmd.intercept.kill" : "msg.wmd.intercept.miss")
                    .withStyle(p.kill() ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        }
    }
}
