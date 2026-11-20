package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAirFx {

    private ClientAirFx() {}

    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 16), SOFT = new ByteBufferBuilder(1 << 16);
    private static final int CRASH_FLASH = 6, CRASH_BALL = 26, CRASH_RING = 34, WIRE_LIFE = 28, CAT_LIFE = 120, SLOT_SEGS = 24;

    private static final class Fx {
        final AirFxPayload p; final long start, seed; final boolean ground; final double groundY;
        Vector3d plotHook; double reached; final long[] pass = new long[SLOT_SEGS];
        Fx(AirFxPayload p, long start, long seed, boolean ground, double groundY) { this.p = p; this.start = start; this.seed = seed; this.ground = ground; this.groundY = groundY; }
    }

    private static final List<Fx> FX = Statics.client(new ArrayList<>());
    private static final List<double[]> SHAKES = Statics.client(new ArrayList<>());
    private static final List<long[]> PUNCH = Statics.client(new ArrayList<>());
    private static float flash;

    static void add(AirFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Entity me = Minecraft.getInstance().player;
        boolean ground = false;
        double gy = 0.0;
        if (p.kind() == AirFxPayload.CRASH) {
            gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.a().x), (int) Math.floor(p.a().z));
            ground = Math.abs(p.a().y - gy) <= p.c().x + 4.0 && ShipFrame.craftsNear(level, new Vec3(p.a().x, gy, p.a().z), 1.5).size() <= 1;
            double k = Math.min(1.6, p.power() / 40.0);
            shake(now + (long) (cam.distanceTo(p.a()) / 17.0), 30, (float) (0.6 + k), p.a(), 60.0 + p.c().x * 10.0);
            burst(level, p, ground, gy);
        } else if (p.kind() == AirFxPayload.EJECT) {
            shake(now, 12, 0.5F, p.a(), 14.0);
            Entity veh = me == null ? null : me.getVehicle();
            if (me != null && cam.distanceTo(p.a()) < 6.0 && (veh == null || veh instanceof EjectionSeatEntity || veh instanceof CockpitSeatEntity)) { shake(now, 14, 1.4F, p.a(), 30.0); flash = 1.0F; }
            for (int i = 0; i < 18; i++)
                level.addParticle(ParticleTypes.LARGE_SMOKE, p.a().x, p.a().y + 0.3, p.a().z, (level.random.nextDouble() - 0.5) * 0.5, level.random.nextDouble() * 0.25, (level.random.nextDouble() - 0.5) * 0.5);
        } else if (p.kind() == AirFxPayload.WIRE) {
            if (me != null && me.getVehicle() != null && cam.distanceTo(p.c()) < 16.0) shake(now, 14, (float) Math.min(1.1, p.power() / 45.0), p.c(), 30.0);
        } else if (p.kind() == AirFxPayload.CATAPULT) {
            if (me != null && me.getVehicle() != null && cam.distanceTo(p.c()) < 14.0) { shake(now, 10, 0.9F, p.c(), 30.0); PUNCH.add(new long[] { now, now + 14 }); }
            else shake(now, 10, 0.35F, p.a(), 40.0);
        } else if (p.kind() == AirFxPayload.AIR_BURST) {
            double R = Math.max(3.0, p.c().x);
            shake(now + (long) (cam.distanceTo(p.a()) / 17.0), 10, 0.5F, p.a(), R * 6.0);
        } else if (p.kind() == AirFxPayload.FUEL_FIRE) {
            gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.a().x), (int) Math.floor(p.a().z));
            ground = Math.abs(p.a().y - gy) <= p.c().x + 4.0;
            double d = cam.distanceTo(p.a());
            float pitch = 0.9F + level.random.nextFloat() * 0.15F;
            if (d > Balance.CRASH_FIRE_NEAR_SND) level.playLocalSound(p.a().x, p.a().y, p.a().z, WmdSounds.HE_FAR.get(), SoundSource.BLOCKS, Balance.AAM_FAR_VOLUME, pitch, false);
            else level.playLocalSound(p.a().x, p.a().y, p.a().z, WmdSounds.INCENDIARY_WHOOSH.get(), SoundSource.BLOCKS, Balance.CRASH_FIRE_VOLUME, pitch, false);
            shake(now + (long) (d / 17.0), 24, 0.8F, p.a(), 50.0);
        } else if (p.kind() >= AirFxPayload.TORPEDO) {
            double[] sy = { 0.0 };
            ground = pointBurst(level, p, now, cam, sy);
            gy = sy[0];
        }
        Fx f = new Fx(p, now, level.random.nextLong(), ground, gy);
        if (p.ship() != null && (p.kind() == AirFxPayload.WIRE || p.kind() == AirFxPayload.CATAPULT)) {
            Matrix4d m = ShipFrame.craftMatrix(level, p.ship(), 1.0F);
            if (m != null) f.plotHook = new Matrix4d(m).invert().transformPosition(new Vector3d(p.c().x, p.c().y, p.c().z));
        }
        if (FX.size() > 24) FX.remove(0);
        FX.add(f);
    }

    private static void burst(ClientLevel level, AirFxPayload p, boolean ground, double gy) {
        RandomSource rnd = level.random;
        Vec3 a = p.a();
        double k = Math.min(2.0, p.power() / 30.0);
        BlockState hit = level.getBlockState(BlockPos.containing(a.x, ground ? gy - 1.0 : a.y, a.z));
        int n = (int) (20 + 30 * k);
        for (int i = 0; i < n; i++) {
            double vx = rnd.nextGaussian() * 0.5 * k, vy = 0.3 + rnd.nextDouble() * 0.7 * k, vz = rnd.nextGaussian() * 0.5 * k;
            if (ground && !hit.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, hit), a.x, a.y + 0.5, a.z, vx, vy, vz);
            else level.addParticle(ParticleTypes.LAVA, a.x, a.y, a.z, vx, vy, vz);
            if (i % 2 == 0) level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, a.x, a.y + 0.5, a.z, vx * 0.4, vy * 0.3, vz * 0.4);
        }
    }

    private static void shake(long start, int ticks, float s, Vec3 at, double reach) {
        if (SHAKES.size() > 16) SHAKES.remove(0);
        SHAKES.add(new double[] { start, start + ticks, s, at.x, at.y, at.z, reach });
    }

    static float shake(Vec3 cam) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SHAKES.isEmpty()) return 0.0F;
        long now = level.getGameTime();
        float out = 0.0F;
        for (Iterator<double[]> it = SHAKES.iterator(); it.hasNext();) {
            double[] s = it.next();
            if (now > s[1]) { it.remove(); continue; }
            if (now < s[0]) continue;
            double d = cam.distanceTo(new Vec3(s[3], s[4], s[5]));
            if (d > s[6]) continue;
            double u = (now - s[0]) / (s[1] - s[0]), k = 1.0 - d / s[6];
            out = Math.max(out, (float) (s[2] * k * (1.0 - u) * (1.0 - u)));
        }
        return out;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        flash = Math.max(0.0F, flash - 0.12F);
        long now = level.getGameTime();
        RandomSource rnd = level.random;
        for (Iterator<Fx> it = FX.iterator(); it.hasNext();) {
            Fx f = it.next();
            long age = now - f.start;
            AirFxPayload p = f.p;
            switch (p.kind()) {
                case AirFxPayload.CRASH -> {
                    if (age > Balance.AIRFX_CRASH_SMOKE) { it.remove(); continue; }
                    double fade = 1.0 - age / (double) Balance.AIRFX_CRASH_SMOKE;
                    if (rnd.nextDouble() < 0.35 + 0.6 * fade)
                        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.a().x + rnd.nextGaussian() * 0.8, p.a().y + 0.5, p.a().z + rnd.nextGaussian() * 0.8,
                                0.0, 0.05 + 0.04 * fade, 0.0);
                    if (age < 60 && rnd.nextDouble() < 0.5) level.addParticle(ParticleTypes.FLAME, p.a().x + rnd.nextGaussian(), p.a().y + 0.3, p.a().z + rnd.nextGaussian(), 0.0, 0.04, 0.0);
                }
                case AirFxPayload.WIRE -> {
                    if (age > WIRE_LIFE) { it.remove(); continue; }
                    double[] box = p.ship() == null ? null : ShipFrame.craftBox(level, p.ship());
                    if (box != null && age < 22) {
                        double k = Math.min(1.0, p.power() / 40.0) * (1.0 - age / 22.0);
                        for (int i = 0; i < 1 + (int) (3 * k); i++)
                            level.addParticle(ParticleTypes.CLOUD, box[0] + rnd.nextDouble() * (box[3] - box[0]), box[1] + 0.2, box[2] + rnd.nextDouble() * (box[5] - box[2]), 0.0, 0.03, 0.0);
                    }
                }
                case AirFxPayload.CATAPULT -> {
                    if (age > CAT_LIFE) { it.remove(); continue; }
                    Vec3 back = p.a().subtract(p.b().subtract(p.a()).normalize().scale(5.0));
                    if (age < 30) for (int i = 0; i < 3; i++)
                        level.addParticle(ParticleTypes.CLOUD, back.x + rnd.nextGaussian() * 0.6, back.y + 0.5, back.z + rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.04, 0.25 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.04);
                    if (age > 10 && rnd.nextDouble() < 0.6) {
                        double s = rnd.nextDouble() * f.reached;
                        Vec3 d = p.b().subtract(p.a());
                        double len = Math.max(1.0, d.length());
                        Vec3 q = p.a().add(d.scale(s / len));
                        level.addParticle(ParticleTypes.CLOUD, q.x, q.y, q.z, 0.0, 0.02, 0.0);
                    }
                }
                case AirFxPayload.BREACH -> {
                    if (age > 44) { it.remove(); continue; }
                    if (f.ground && age < 26) for (int i = 0; i < 4; i++)
                        level.addParticle(ParticleTypes.SPLASH, p.a().x + rnd.nextGaussian() * 0.8, f.groundY + 0.2, p.a().z + rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.15, 0.8 + rnd.nextDouble() * 0.8, rnd.nextGaussian() * 0.15);
                    if (age < 20) level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, p.a().x + rnd.nextGaussian(), p.a().y, p.a().z + rnd.nextGaussian(), 0.0, 0.3, 0.0);
                }
                case AirFxPayload.AIR_BURST -> {
                    if (age > Math.max(Balance.AIRBURST_PUFF, Balance.AIRBURST_FRAG_LIFE + Balance.AIRBURST_FRAG_SPREAD)) it.remove();
                }
                case AirFxPayload.ROD_RING -> { if (age > Balance.AAM_ROD_FX_LIFE) it.remove(); }
                case AirFxPayload.FUEL_FIRE -> {
                    if (age > Balance.CRASH_FIRE_SMOKE) { it.remove(); continue; }
                    if (age < Balance.CRASH_FIRE_BURN && rnd.nextDouble() < 0.35) {
                        double y = f.ground ? f.groundY + 0.3 : p.a().y;
                        level.addParticle(ParticleTypes.LAVA, p.a().x + rnd.nextGaussian() * 1.2, y, p.a().z + rnd.nextGaussian() * 1.2, 0.0, 0.0, 0.0);
                    }
                }
                default -> { if (age > 40) it.remove(); }
            }
        }
        for (Iterator<long[]> it = PUNCH.iterator(); it.hasNext();) if (now > it.next()[1]) it.remove();
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || PUNCH.isEmpty()) return;
        double now = level.getGameTime() + event.getPartialTick();
        double kick = 0.0;
        for (long[] p : PUNCH) {
            double u = (now - p[0]) / (double) (p[1] - p[0]);
            if (u >= 0.0 && u <= 1.0) kick = Math.max(kick, u < 0.15 ? u / 0.15 : 1.0 - (u - 0.15) / 0.85);
        }
        if (kick > 0.0) event.setFOV(event.getFOV() + 9.0 * kick);
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        if (flash < 0.02F || !ClientConfig.explosionScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (230 * Math.min(1.0F, flash)) << 24) | 0xFFFFFF);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientBlastGeo.beginFrame();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder soft = new BufferBuilder(SOFT, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyG = false, anyS = false;
        for (Fx f : FX) {
            double age = now - f.start;
            switch (f.p.kind()) {
                case AirFxPayload.CRASH -> { boolean[] r = crash(glow, soft, cam, lv, uv, f, age, level); anyG |= r[0]; anyS |= r[1]; }
                case AirFxPayload.EJECT -> anyG |= eject(glow, cam, lv, uv, f, age, level);
                case AirFxPayload.WIRE -> anyG |= wire(glow, cam, lv, uv, f, age, level, pt);
                case AirFxPayload.CATAPULT -> anyS |= catapult(soft, cam, f, age, level, pt);
                case AirFxPayload.AIR_BURST -> { boolean[] r = airBurst(glow, soft, cam, lv, uv, f, age, level); anyG |= r[0]; anyS |= r[1]; }
                case AirFxPayload.ROD_RING -> anyG |= rodRingFx(glow, cam, lv, uv, f, age, level);
                case AirFxPayload.FUEL_FIRE -> { boolean[] r = fuelFire(glow, soft, cam, lv, uv, f, age, level); anyG |= r[0]; anyS |= r[1]; }
                default -> { boolean[] r = point(glow, soft, cam, lv, uv, f, age, level); anyG |= r[0]; anyS |= r[1]; }
            }
        }
        anyG |= seatPlumes(glow, cam, lv, uv, level, pt, now);
        ClientBlastGeo.draw(event, soft, anyS, false);
        ClientBlastGeo.draw(event, glow, anyG, true);
    }

    private static boolean[] crash(BufferBuilder glow, BufferBuilder soft, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        AirFxPayload p = f.p;
        Vec3 a = p.a();
        double r = Math.max(1.5, p.c().x), k = Math.min(1.6, p.power() / 40.0);
        boolean g = false, s = false;
        if (age < CRASH_FLASH) {
            float u = (float) (1.0 - age / CRASH_FLASH);
            VeilBridge.glow(0x43524153L ^ f.seed, a.x, a.y + 1.0, a.z, 1.0F, 0.85F, 0.6F, (float) (20.0 + r * 6.0), 3.0F * u, level.getGameTime());
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.5, a.z, (2.0 + r) * (1.0 + age * 0.3), 1.0F, 0.95F, 0.85F, 0.9F * u, 16);
            g = true;
        }
        if (age < CRASH_BALL) {
            double u = age / CRASH_BALL, rad = (1.5 + r * 1.6 * k) * (0.4 + 0.9 * Math.sqrt(u));
            float al = (float) ((1.0 - u) * (1.0 - u));
            double rise = u * r * 1.2;
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.6 + rise, a.z, rad, 1.0F, 0.55F, 0.12F, 0.75F * al, 18);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.6 + rise * 0.8, a.z, rad * 0.62, 1.0F, 0.8F, 0.35F, 0.85F * al, 16);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.6 + rise * 0.6, a.z, rad * 0.3, 1.0F, 0.97F, 0.8F, 0.9F * al, 12);
            if (!f.ground && age < 14) {
                RandomSource rnd = RandomSource.create(f.seed + (long) age);
                for (int i = 0; i < 14; i++) {
                    Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6 + 0.3, rnd.nextGaussian()).normalize().scale((2.0 + r * 2.0) * (0.4 + rnd.nextDouble()));
                    ClientBlastGeo.streak(glow, cam, a.x, a.y, a.z, a.x + d.x, a.y + d.y, a.z + d.z, 0.06, 1.0F, 0.8F, 0.35F, 0.9F, 0.0F);
                }
            }
            g = true;
        }
        if (f.ground && age < CRASH_RING) {
            double u = age / CRASH_RING, R = (4.0 + r * 3.0) + (16.0 + r * 8.0) * k * Math.sqrt(u), w = 1.5 + 3.0 * u;
            float al = (float) (0.55 * (1.0 - u));
            ring(soft, cam, a.x, f.groundY + 0.25 + u * 1.2, a.z, R, w, 0.55F, 0.5F, 0.42F, al, 40);
            s = true;
        }
        return new boolean[] { g, s };
    }

    static void airBurst(Vec3 at, Vec3 vel, double radius) {
        int n = radius < Balance.AIRBURST_BIG_R ? Balance.AIRBURST_FRAGS_SMALL : Balance.AIRBURST_FRAGS_BIG;
        add(new AirFxPayload(AirFxPayload.AIR_BURST, null, at, vel == null ? Vec3.ZERO : vel, new Vec3(radius, 0.0, 0.0), n));
    }

    static void rodRing(Vec3 at, Vec3 vel, double radius) {
        add(new AirFxPayload(AirFxPayload.ROD_RING, null, at, vel == null ? Vec3.ZERO : vel, new Vec3(radius, 0.0, 0.0), 0.0F));
    }

    private static double farScale(Vec3 cam, Vec3 p) { return Math.max(1.0, Math.min(Balance.AIRBURST_FAR_MAX, cam.distanceTo(p) / Balance.AIRBURST_NEAR)); }

    private static Vec3 fragAt(Vec3 a, Vec3 v0, double t) {
        double dr = Balance.AIRBURST_DRAG, sum = (1.0 - Math.pow(dr, t)) / (1.0 - dr);
        double fall = Balance.AIRBURST_GRAV / (1.0 - dr) * (t - sum);
        return new Vec3(a.x + v0.x * sum, a.y + v0.y * sum - fall, a.z + v0.z * sum);
    }

    private static boolean[] airBurst(BufferBuilder glow, BufferBuilder soft, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        AirFxPayload p = f.p;
        Vec3 a = p.a();
        double R = Math.max(3.0, p.c().x), sc = farScale(cam, a);
        boolean g = false, s = false;
        if (age < 0) return new boolean[] { false, false };

        if (age < Balance.AIRBURST_PUFF) {
            double u = age / Balance.AIRBURST_PUFF;
            RandomSource rnd = RandomSource.create(f.seed);
            double wa = rnd.nextDouble() * Math.PI * 2.0, drift = age * Balance.AIRBURST_DRIFT;
            float al = (float) (Math.min(1.0, age / 3.0) * Math.pow(1.0 - u, 1.4) * 0.8);
            for (int i = 0; i < 9; i++) {
                double ox = rnd.nextGaussian() * R * 0.28, oy = rnd.nextGaussian() * R * 0.22, oz = rnd.nextGaussian() * R * 0.28;
                double rad = R * (0.35 + rnd.nextDouble() * 0.2) * (0.5 + 0.9 * Math.sqrt(Math.min(1.0, age / 30.0)) + 0.6 * u) * Math.sqrt(sc);
                float grey = 0.12F + rnd.nextFloat() * 0.08F;
                ClientBlastGeo.disc(soft, cam, lv, uv, a.x + ox + Math.cos(wa) * drift, a.y + oy + age * 0.004, a.z + oz + Math.sin(wa) * drift, rad, grey, grey, grey * 1.05F, al, 14);
            }
            s = true;
        }

        if (age < Balance.AIRBURST_FLASH) {
            float u = (float) (1.0 - age / Balance.AIRBURST_FLASH);
            VeilBridge.glow(0x41425253L ^ f.seed, a.x, a.y, a.z, 1.0F, 0.85F, 0.6F, (float) (30.0 + R * 4.0), 4.0F * u, level.getGameTime());
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, R * 1.6 * sc * (1.0 + age * 0.25), 1.0F, 0.97F, 0.9F, 0.9F * u, 16);
            g = true;
        }

        if (age < Balance.AIRBURST_BALL) {
            double u = age / Balance.AIRBURST_BALL, rad = R * (0.35 + 0.55 * Math.sqrt(u)) * sc;
            float al = (float) ((1.0 - u) * (1.0 - u)), heat = (float) Math.max(0.0, 1.0 - u * 1.5);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, rad, 1.0F, 0.5F + 0.2F * heat, 0.12F + 0.15F * heat, 0.8F * al, 18);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, rad * 0.6, 1.0F, 0.78F + 0.2F * heat, 0.35F + 0.4F * heat, 0.85F * al, 16);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, rad * 0.3, 1.0F, 0.97F, 0.8F + 0.15F * heat, 0.95F * al * heat + 0.05F, 12);
            if (age >= Balance.AIRBURST_FLASH) VeilBridge.glow(0x41425253L ^ f.seed, a.x, a.y, a.z, 1.0F, 0.6F, 0.25F, (float) (20.0 + R * 3.0), 2.5F * al, level.getGameTime());
            g = true;
        }

        RandomSource fr = RandomSource.create(f.seed ^ 0x5EED5EEDL);
        Vec3 carry = p.b().lengthSqr() > 1.0E-6 ? p.b().scale(Math.min(0.15, 1.2 / p.b().length())) : Vec3.ZERO;
        int n = (int) p.power();
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(fr.nextGaussian(), fr.nextGaussian() * 0.7 + 0.25, fr.nextGaussian()).normalize();
            Vec3 v0 = d.scale((0.5 + fr.nextDouble() * 0.7) * R / 6.0).add(carry);
            double life = Balance.AIRBURST_FRAG_LIFE + fr.nextDouble() * Balance.AIRBURST_FRAG_SPREAD;
            if (age >= life) continue;
            double k = 1.0 - age / life;
            Vec3 head = fragAt(a, v0, age), prev = head;
            for (int j = 1; j <= 7; j++) {
                double t = age - j * 2.0;
                if (t < 0.0) break;
                Vec3 q = fragAt(a, v0, t);
                ClientBlastGeo.streak(soft, cam, q.x, q.y, q.z, prev.x, prev.y, prev.z, (0.12 + j * 0.05) * Math.sqrt(sc), 0.16F, 0.16F, 0.17F, (float) (0.55 * k * (1.0 - j / 7.0)), (float) (0.55 * k * (1.0 - (j - 1) / 7.0)));
                prev = q;
            }
            float fl = (float) (0.8 + 0.2 * Math.sin(age * 1.7 + i * 2.1));
            ClientBlastGeo.disc(glow, cam, lv, uv, head.x, head.y, head.z, 0.45 * sc * (0.6 + 0.4 * k), 1.0F, 0.6F, 0.2F, 0.85F * fl * (float) k, 8);
            ClientBlastGeo.disc(glow, cam, lv, uv, head.x, head.y, head.z, 0.18 * sc, 1.0F, 0.95F, 0.8F, (float) k, 6);
            g = true;
            s = true;
        }
        return new boolean[] { g, s };
    }

    private static boolean rodRingFx(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        if (age < 0) return false;
        AirFxPayload p = f.p;
        double life = Balance.AAM_ROD_FX_LIFE, u = Math.min(1.0, age / life), sc = farScale(cam, p.a());
        double R = Math.max(2.0, p.c().x) * (1.0 - (1.0 - u) * (1.0 - u)), w = 0.18 * Math.sqrt(sc);
        float al = (float) Math.pow(1.0 - u, 1.2);
        Vec3 axis = p.b().lengthSqr() > 1.0E-9 ? p.b().normalize() : new Vec3(0, -1, 0);
        Vec3 up = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 e1 = axis.cross(up).normalize(), e2 = axis.cross(e1).normalize(), c = p.a();
        int n = 64;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2.0 * i / n, t1 = Math.PI * 2.0 * (i + 1) / n;
            double z0 = (i % 2 == 0 ? 1 : -1) * 0.35, z1 = -z0;
            Vec3 d0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), d1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 m0 = c.add(d0.scale(R)).add(axis.scale(z0)), m1 = c.add(d1.scale(R)).add(axis.scale(z1));
            Vec3 i0 = m0.subtract(d0.scale(w)), i1 = m1.subtract(d1.scale(w)), o0 = m0.add(d0.scale(w)), o1 = m1.add(d1.scale(w));
            ClientBlastGeo.quad(glow, cam, i0, i1, m1, m0, 1.0F, 0.75F, 0.4F, 0.0F, 1.0F, 0.9F, 0.7F, al);
            ClientBlastGeo.quad(glow, cam, m0, m1, o1, o0, 1.0F, 0.9F, 0.7F, al, 1.0F, 0.75F, 0.4F, 0.0F);
        }
        RandomSource rnd = RandomSource.create(f.seed);
        for (int k = 0; k < 14; k++) {
            double t = rnd.nextDouble() * Math.PI * 2.0 + age * 0.15;
            Vec3 q = c.add(e1.scale(Math.cos(t) * R)).add(e2.scale(Math.sin(t) * R));
            ClientBlastGeo.disc(glow, cam, lv, uv, q.x, q.y, q.z, 0.3 * Math.sqrt(sc), 1.0F, 0.7F, 0.3F, al, 6);
        }
        if (age < 4) VeilBridge.glow(0x524F4452L ^ f.seed, c.x, c.y, c.z, 1.0F, 0.85F, 0.6F, (float) (p.c().x * 2.5), 3.0F * (float) (1.0 - age / 4.0), level.getGameTime());
        return true;
    }

    private static boolean[] fuelFire(BufferBuilder glow, BufferBuilder soft, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        AirFxPayload p = f.p;
        if (age < 0) return new boolean[] { false, false };
        double y0 = f.ground ? f.groundY + 0.2 : p.a().y;
        Vec3 a = new Vec3(p.a().x, y0, p.a().z);
        double fuelK = Math.max(0.6, Math.min(1.6, p.c().y / Balance.CRASH_FIRE_FUEL_REF)), size = Balance.CRASH_FIRE_SIZE * fuelK;
        boolean g = false, s = false;
        long gt = level.getGameTime();

        if (age < Balance.CRASH_FIRE_BALL) {
            double u = age / Balance.CRASH_FIRE_BALL, rise = u * size * 2.2, rad = size * (0.5 + 0.8 * Math.sqrt(u));
            float al = (float) Math.pow(1.0 - u, 1.5), heat = (float) Math.max(0.0, 1.0 - u * 1.3);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 1.0 + rise, a.z, rad, 1.0F, 0.42F + 0.15F * heat, 0.08F, 0.75F * al, 20);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 1.0 + rise * 0.85, a.z, rad * 0.62, 1.0F, 0.7F + 0.2F * heat, 0.25F + 0.3F * heat, 0.85F * al, 18);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.8 + rise * 0.6, a.z, rad * 0.32, 1.0F, 0.95F, 0.75F, 0.9F * al * heat + 0.05F, 12);
            ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y + 0.6, a.z, rad * 1.3, 1.0F, 0.5F, 0.12F, 0.3F * al, 18);
            VeilBridge.glow(0x46554C4CL ^ f.seed, a.x, a.y + 1.0 + rise * 0.5, a.z, 1.0F, 0.6F, 0.25F, (float) (24.0 + size * 5.0), 4.0F * al, gt);
            g = true;
        }

        if (f.ground && age < 20) {
            double u = age / 20.0;
            ring(glow, cam, a.x, a.y + 0.15, a.z, size * (0.8 + 2.4 * Math.sqrt(u)), 0.8 + 1.4 * u, 1.0F, 0.5F, 0.15F, (float) (0.7 * (1.0 - u)), 36);
            g = true;
        }

        if (age < Balance.CRASH_FIRE_BURN) {
            float fade = (float) Math.min(1.0, (Balance.CRASH_FIRE_BURN - age) / 60.0) * (float) Math.min(1.0, age / 8.0);
            RandomSource rnd = RandomSource.create(f.seed ^ 0x0F1AE5L);
            for (int i = 0; i < 6; i++) {
                double ox = rnd.nextGaussian() * size * 0.35, oz = rnd.nextGaussian() * size * 0.35, ph = rnd.nextDouble() * 6.28;
                double h = 0.8 + 0.6 * Math.sin(age * 0.45 + ph) + 0.3 * Math.sin(age * 1.3 + ph * 2.0);
                ClientBlastGeo.disc(glow, cam, lv, uv, a.x + ox, a.y + 0.5 + h * 0.5, a.z + oz, 0.7 + h * 0.5, 1.0F, 0.48F, 0.1F, 0.6F * fade, 12);
                ClientBlastGeo.disc(glow, cam, lv, uv, a.x + ox, a.y + 0.35 + h * 0.3, a.z + oz, 0.35 + h * 0.25, 1.0F, 0.85F, 0.45F, 0.8F * fade, 10);
            }
            float fl = (float) (0.8 + 0.2 * Math.sin(age * 0.9));
            VeilBridge.glow(0x5752434BL ^ f.seed, a.x, a.y + 1.2, a.z, 1.0F, 0.55F, 0.2F, (float) (14.0 + size * 3.0), 2.2F * fade * fl, gt);
            g = true;
        }

        RandomSource wr = RandomSource.create(f.seed ^ 0x51A0E5L);
        double wa = wr.nextDouble() * Math.PI * 2.0, wx = Math.cos(wa) * 0.035, wz = Math.sin(wa) * 0.035;
        int life = 160, last = Balance.CRASH_FIRE_SMOKE - life;
        for (int born = Math.max(0, ((int) age - life) / 5 * 5); born <= Math.min(age, last); born += 5) {
            double pa = age - born;
            if (pa < 0.0 || pa > life) continue;
            double u = pa / life, src = 1.0 - Math.max(0.0, (born - Balance.CRASH_FIRE_BURN) / (double) (last - Balance.CRASH_FIRE_BURN));
            RandomSource pr = RandomSource.create(f.seed + born * 31L);
            double rad = (1.0 + size * 0.3) * (0.6 + pa * 0.035) * src;
            float al = (float) (0.7 * Math.min(1.0, pa / 6.0) * (1.0 - u) * src), c = 0.07F + pr.nextFloat() * 0.06F;
            if (al < 0.01F || rad < 0.2) continue;
            ClientBlastGeo.disc(soft, cam, lv, uv, a.x + wx * pa * pa * 0.02 + pr.nextGaussian() * 0.6, a.y + 1.5 + pa * 0.14, a.z + wz * pa * pa * 0.02 + pr.nextGaussian() * 0.6,
                    rad, c, c, c, al, 14);
            s = true;
        }
        return new boolean[] { g, s };
    }

    private static boolean eject(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        if (age > 8) return false;
        Vec3 a = f.p.a();
        float u = (float) (1.0 - age / 8.0);
        VeilBridge.glow(0x454A4543L ^ f.seed, a.x, a.y, a.z, 1.0F, 0.9F, 0.7F, 18.0F, 2.5F * u, level.getGameTime());
        ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 1.2 + age * 0.45, 1.0F, 0.92F, 0.75F, 0.85F * u, 14);
        return true;
    }

    private static boolean seatPlumes(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, ClientLevel level, float pt, double now) {
        boolean any = false;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof EjectionSeatEntity s) || !s.rocketing() || e.distanceToSqr(cam) > 300.0 * 300.0) continue;
            Vec3 up = s.launchUp(), base = e.getPosition(pt).subtract(up.scale(0.25));
            double fl = 0.85 + 0.15 * Math.sin(now * 3.1 + e.getId());
            Vec3 tip = base.subtract(up.scale(2.6 * fl)), mid = base.subtract(up.scale(1.4 * fl));
            ClientBlastGeo.streak(glow, cam, base.x, base.y, base.z, tip.x, tip.y, tip.z, 0.32, 1.0F, 0.5F, 0.12F, 0.9F, 0.0F);
            ClientBlastGeo.streak(glow, cam, base.x, base.y, base.z, mid.x, mid.y, mid.z, 0.16, 1.0F, 0.9F, 0.5F, 1.0F, 0.0F);
            ClientBlastGeo.disc(glow, cam, lv, uv, base.x, base.y, base.z, 0.55, 1.0F, 0.85F, 0.5F, 0.8F, 10);
            VeilBridge.glow(0x53454154L ^ e.getId(), base.x, base.y, base.z, 1.0F, 0.6F, 0.25F, 10.0F, 1.6F, level.getGameTime());
            any = true;
        }
        return any;
    }

    private static boolean wire(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level, float pt) {
        AirFxPayload p = f.p;
        Vec3 hook = hookNow(level, f, pt);
        if (hook == null) return false;
        double k = Math.min(1.4, p.power() / 40.0);
        boolean any = false;
        if (age < 12) {
            float al = (float) (0.9 * (1.0 - age / 12.0));
            ClientBlastGeo.streak(glow, cam, p.a().x, p.a().y, p.a().z, hook.x, hook.y, hook.z, 0.07, 0.85F, 0.92F, 1.0F, al, al);
            ClientBlastGeo.streak(glow, cam, p.b().x, p.b().y, p.b().z, hook.x, hook.y, hook.z, 0.07, 0.85F, 0.92F, 1.0F, al, al);
            any = true;
        }
        if (age < 20) {
            RandomSource rnd = RandomSource.create(f.seed + (long) (age * 2.0));
            Vec3 back = p.a().add(p.b()).scale(0.5).subtract(hook);
            back = back.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 0) : back.normalize();
            int n = (int) (6 + 10 * k * (1.0 - age / 20.0));
            for (int i = 0; i < n; i++) {
                Vec3 d = back.scale(-0.6).add(rnd.nextGaussian() * 0.6, 0.3 + rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.6).normalize().scale((0.6 + rnd.nextDouble() * 1.6) * k);
                ClientBlastGeo.streak(glow, cam, hook.x, hook.y, hook.z, hook.x + d.x, hook.y + d.y, hook.z + d.z, 0.035, 1.0F, 0.78F, 0.3F, 1.0F, 0.0F);
            }
            ClientBlastGeo.disc(glow, cam, lv, uv, hook.x, hook.y, hook.z, 0.4 + 0.3 * k, 1.0F, 0.85F, 0.5F, (float) (0.8 * (1.0 - age / 20.0)), 10);
            any = true;
        }
        return any;
    }

    private static boolean catapult(BufferBuilder soft, Vec3 cam, Fx f, double age, ClientLevel level, float pt) {
        AirFxPayload p = f.p;
        Vec3 d = p.b().subtract(p.a());
        double len = d.length();
        if (len < 1.0) return false;
        Vec3 fw = d.scale(1.0 / len);
        Vec3 nose = hookNow(level, f, pt);
        if (nose != null && age < 80) f.reached = Math.max(f.reached, Math.min(len, nose.subtract(p.a()).dot(fw)));
        long now = level.getGameTime();
        Vec3 side = fw.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        boolean any = false;
        for (int i = 0; i < SLOT_SEGS; i++) {
            double s0 = len * i / SLOT_SEGS, s1 = len * (i + 1) / SLOT_SEGS;
            if (s0 > f.reached) break;
            if (f.pass[i] == 0L) f.pass[i] = now;
            double sa = (now - f.pass[i]) + pt;
            if (sa > 80.0) continue;
            double h = 0.35 + sa * 0.06, wob = Math.sin(sa * 0.3 + i) * 0.15;
            float al = (float) (0.5 * Math.exp(-sa / 26.0));
            Vec3 a0 = p.a().add(fw.scale(s0)).add(side.scale(wob)), a1 = p.a().add(fw.scale(Math.min(s1, f.reached))).add(side.scale(wob));
            Vec3 b0 = a0.add(0, h, 0).add(side.scale(sa * 0.01)), b1 = a1.add(0, h, 0).add(side.scale(sa * 0.01));
            ClientBlastGeo.quad(soft, cam, a0, a1, b1, b0, 0.95F, 0.96F, 0.98F, al, 0.95F, 0.96F, 0.98F, 0.0F);
            any = true;
        }
        return any;
    }

    private static boolean pointBurst(ClientLevel level, AirFxPayload p, long now, Vec3 cam, double[] sy) {
        RandomSource rnd = level.random;
        Vec3 a = p.a();
        boolean wet = !level.getFluidState(BlockPos.containing(a)).isEmpty();
        switch (p.kind()) {
            case AirFxPayload.TORPEDO -> {
                shake(now, 10, 0.4F, a, 24.0);
                Vec3 d = p.b();
                for (int i = 0; i < 30; i++) {
                    double v = 0.2 + rnd.nextDouble() * 0.5;
                    if (wet) level.addParticle(ParticleTypes.BUBBLE, a.x, a.y, a.z, d.x * v + rnd.nextGaussian() * 0.08, d.y * v + rnd.nextGaussian() * 0.08 + 0.05, d.z * v + rnd.nextGaussian() * 0.08);
                    else if (i % 3 == 0) level.addParticle(ParticleTypes.CLOUD, a.x, a.y, a.z, d.x * v * 0.5, 0.02, d.z * v * 0.5);
                }
            }
            case AirFxPayload.BREACH -> {
                shake(now + (long) (cam.distanceTo(a) / 17.0), 16, (float) Math.min(1.3, 0.3 + p.power() / 5.0), a, 40.0 + p.power() * 4.0);
                BlockState iron = net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState();
                for (int i = 0; i < 40; i++) {
                    level.addParticle(ParticleTypes.BUBBLE, a.x, a.y, a.z, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5 + 0.1, rnd.nextGaussian() * 0.5);
                    if (i % 2 == 0) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iron), a.x, a.y, a.z, rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.4);
                }
                for (int dy = 1; dy <= 14; dy++) {
                    BlockPos q = BlockPos.containing(a.x, a.y + dy, a.z);
                    if (level.getFluidState(q).isEmpty()) { sy[0] = q.getY(); return dy > 1; }
                }
            }
            case AirFxPayload.GEAR_SNAP -> {
                shake(now, 8, 0.6F, a, 24.0);
                BlockState iron = net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState();
                for (int i = 0; i < 18; i++) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iron), a.x, a.y, a.z, rnd.nextGaussian() * 0.25, 0.2 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.25);
                for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, a.x, a.y, a.z, rnd.nextGaussian() * 0.05, 0.05, rnd.nextGaussian() * 0.05);
            }
            case AirFxPayload.PYLON -> {
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.CLOUD, a.x, a.y - 0.3, a.z, rnd.nextGaussian() * 0.06, -0.08 - rnd.nextDouble() * 0.06, rnd.nextGaussian() * 0.06);
            }
            case AirFxPayload.ENGINE_HIT -> {
                shake(now, 6, 0.3F, a, 16.0);
                if (p.power() >= 3.0F) for (int i = 0; i < 20; i++) level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, a.x, a.y, a.z, rnd.nextGaussian() * 0.12, 0.05 + rnd.nextDouble() * 0.1, rnd.nextGaussian() * 0.12);
                else for (int i = 0; i < 10; i++) level.addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, a.x, a.y, a.z, rnd.nextGaussian() * 0.15, rnd.nextDouble() * 0.15, rnd.nextGaussian() * 0.15);
            }
            case AirFxPayload.DIESEL -> {
                boolean fire = p.power() >= 1.0F;
                for (int i = 0; i < (fire ? 20 : 12); i++) {
                    if (wet) level.addParticle(ParticleTypes.BUBBLE, a.x, a.y, a.z, rnd.nextGaussian() * 0.1, 0.15 + rnd.nextDouble() * 0.1, rnd.nextGaussian() * 0.1);
                    else level.addAlwaysVisibleParticle(fire && i % 2 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, true, a.x, a.y, a.z,
                            rnd.nextGaussian() * 0.04, 0.08 + rnd.nextDouble() * (fire ? 0.12 : 0.06), rnd.nextGaussian() * 0.04);
                }
            }
            case AirFxPayload.CRANE -> {
                for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.POOF, a.x, a.y, a.z, rnd.nextGaussian() * 0.05, 0.03, rnd.nextGaussian() * 0.05);
                if (p.power() < 1.0F) for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.CRIT, a.x, a.y + 0.3, a.z, rnd.nextGaussian() * 0.3, rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.3);
            }
            default -> {}
        }
        return false;
    }

    private static boolean[] point(BufferBuilder glow, BufferBuilder soft, Vec3 cam, Vector3f lv, Vector3f uv, Fx f, double age, ClientLevel level) {
        AirFxPayload p = f.p;
        Vec3 a = p.a();
        Vec3 L = new Vec3(lv.x(), lv.y(), lv.z()), U = new Vec3(uv.x(), uv.y(), uv.z());
        boolean g = false, s = false;
        switch (p.kind()) {
            case AirFxPayload.TORPEDO -> {
                if (age < 14) {
                    double u = age / 14.0;
                    Vec3 d = p.b().lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : p.b().normalize();
                    Vec3[] ax = axes(d);
                    ringIn(soft, cam, a.add(d.scale(0.5 + u * 2.0)), ax[0], ax[1], 0.6 + 4.5 * Math.sqrt(u), 0.35, 0.85F, 0.93F, 1.0F, (float) (0.5 * (1.0 - u)), 24);
                    for (int i = 0; i < 10; i++) {
                        Vec3 tip = a.add(d.scale(1.0 + 3.5 * u)).add(ax[0].scale(Math.cos(i * 0.63) * 1.4 * u)).add(ax[1].scale(Math.sin(i * 0.63) * 1.4 * u));
                        ClientBlastGeo.streak(soft, cam, a.x, a.y, a.z, tip.x, tip.y, tip.z, 0.12, 0.9F, 0.96F, 1.0F, (float) (0.35 * (1.0 - u)), 0.0F);
                    }
                    s = true;
                }
            }
            case AirFxPayload.BREACH -> {
                if (age < 16) {
                    double u = age / 16.0;
                    ringIn(soft, cam, a, L, U, 0.8 + (2.5 + p.power() * 0.6) * Math.sqrt(u), 0.4, 0.85F, 0.93F, 1.0F, (float) (0.55 * (1.0 - u)), 28);
                    s = true;
                }
                if (f.ground && age < 40) {
                    double u = age / 40.0, h = (5.0 + p.power() * 1.5) * Math.sin(Math.min(1.0, u * 1.6) * Math.PI * 0.5) * (u < 0.6 ? 1.0 : 1.0 - (u - 0.6) / 0.4 * 0.6);
                    float al = (float) (0.6 * (1.0 - u));
                    RandomSource rnd = RandomSource.create(f.seed);
                    for (int i = 0; i < 9; i++) {
                        double ox = rnd.nextGaussian() * 0.7, oz = rnd.nextGaussian() * 0.7, hh = h * (0.6 + 0.4 * rnd.nextDouble());
                        ClientBlastGeo.streak(soft, cam, a.x + ox, f.groundY, a.z + oz, a.x + ox * 1.6, f.groundY + hh, a.z + oz * 1.6, 0.35, 0.93F, 0.96F, 1.0F, al, al * 0.3F);
                    }
                    s = true;
                }
            }
            case AirFxPayload.PING -> {
                double life = p.power() >= 2.0F ? 30.0 : 22.0;
                if (age < life) {
                    double u = age / life, R = 1.5 + (p.power() >= 2.0F ? 34.0 : 18.0) * u;
                    float al = (float) (0.45 * (1.0 - u) * (1.0 - u));
                    ringIn(glow, cam, a, L, U, R, 0.18 + R * 0.01, 0.35F, 0.65F, 1.0F, al, 40);
                    ringIn(glow, cam, a, new Vec3(1, 0, 0), new Vec3(0, 0, 1), R, 0.18 + R * 0.01, 0.35F, 0.65F, 1.0F, al * 0.7F, 40);
                    g = true;
                }
            }
            case AirFxPayload.MINE_ARM -> {
                if (age < 20) {
                    float blink = (float) (age < 4 ? 1.0 - age / 4.0 : age >= 10 && age < 14 ? 1.0 - (age - 10) / 4.0 : 0.0);
                    if (blink > 0.0F) ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 0.45, 1.0F, 0.15F, 0.1F, 0.9F * blink, 10);
                    double u = age / 20.0;
                    ringIn(glow, cam, a, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5 + 3.0 * u, 0.08, 1.0F, 0.25F, 0.2F, (float) (0.5 * (1.0 - u)), 24);
                    g = true;
                }
            }
            case AirFxPayload.GEAR_SNAP -> {
                if (age < 4) ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 0.8 + age * 0.3, 1.0F, 0.95F, 0.85F, (float) (0.9 * (1.0 - age / 4.0)), 12);
                if (age < 12) {
                    RandomSource rnd = RandomSource.create(f.seed + (long) (age * 2.0));
                    for (int i = 0; i < 12; i++) {
                        Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.5, rnd.nextGaussian()).normalize().scale(0.8 + rnd.nextDouble() * 1.8);
                        ClientBlastGeo.streak(glow, cam, a.x, a.y, a.z, a.x + d.x, a.y + d.y, a.z + d.z, 0.035, 1.0F, 0.78F, 0.3F, (float) (1.0 - age / 12.0), 0.0F);
                    }
                    g = true;
                }
            }
            case AirFxPayload.PYLON -> {
                if (age < 3) { ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y - 0.4, a.z, 0.5, 1.0F, 0.85F, 0.55F, (float) (0.9 * (1.0 - age / 3.0)), 10); g = true; }
                if (age < 10) {
                    double u = age / 10.0;
                    ClientBlastGeo.disc(soft, cam, lv, uv, a.x, a.y - 0.5 - u * 0.8, a.z, 0.4 + 1.3 * u, 0.92F, 0.92F, 0.92F, (float) (0.45 * (1.0 - u)), 12);
                    s = true;
                }
            }
            case AirFxPayload.ENGINE_HIT -> {
                boolean out = p.power() >= 3.0F;
                if (age < 7) {
                    double u = age / 7.0;
                    VeilBridge.glow(0x454E4748L ^ f.seed, a.x, a.y, a.z, 1.0F, out ? 0.35F : 0.6F, 0.15F, 14.0F, (float) (2.0 * (1.0 - u)), level.getGameTime());
                    ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 1.0 + 1.6 * u, 1.0F, out ? 0.4F : 0.65F, 0.15F, (float) (0.85 * (1.0 - u)), 14);
                    g = true;
                }
                if (out && age < 22) {
                    double u = age / 22.0;
                    ClientBlastGeo.disc(soft, cam, lv, uv, a.x, a.y + u * 1.5, a.z, 1.0 + 2.2 * u, 0.12F, 0.11F, 0.1F, (float) (0.55 * (1.0 - u)), 14);
                    s = true;
                }
            }
            case AirFxPayload.HACK -> {
                int st = (int) Math.round(p.c().x);
                double x0 = a.x - 0.52, x1 = a.x + 0.52, z0 = a.z - 0.52, z1 = a.z + 0.52;
                if (st == 0 && age < 20) {
                    double y = a.y - 0.5 + age / 20.0;
                    float al = (float) (0.8 * Math.sin(age / 20.0 * Math.PI));
                    square(glow, cam, x0, x1, z0, z1, y, 0.03, 0.3F, 0.9F, 1.0F, al);
                    g = true;
                } else if (st != 0 && age < 12) {
                    double u = age / 12.0, e = 0.52 + 0.4 * u;
                    float r = st == 1 ? 0.3F : 1.0F, gg = st == 1 ? 1.0F : 0.25F, b = st == 1 ? 0.45F : 0.2F, al = (float) (0.9 * (1.0 - u));
                    for (int k = 0; k < 3; k++) square(glow, cam, a.x - e, a.x + e, a.z - e, a.z + e, a.y - 0.5 + k * 0.5, 0.04, r, gg, b, al);
                    ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 0.6 + u, r, gg, b, al * 0.6F, 12);
                    g = true;
                }
            }
            default -> {}
        }
        return new boolean[] { g, s };
    }

    private static Vec3[] axes(Vec3 d) {
        Vec3 ref = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(ref).normalize();
        return new Vec3[] { u, d.cross(u).normalize() };
    }

    private static void ringIn(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 e1, Vec3 e2, double R, double w, float r, float g, float b, float a, int n) {
        double da = Math.PI * 2.0 / n;
        for (int i = 0; i < n; i++) {
            double t0 = i * da, t1 = t0 + da;
            Vec3 d0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), d1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 i0 = c.add(d0.scale(R - w)), i1 = c.add(d1.scale(R - w)), m0 = c.add(d0.scale(R)), m1 = c.add(d1.scale(R)), o0 = c.add(d0.scale(R + w)), o1 = c.add(d1.scale(R + w));
            ClientBlastGeo.quad(bb, cam, i0, i1, m1, m0, r, g, b, 0.0F, r, g, b, a);
            ClientBlastGeo.quad(bb, cam, m0, m1, o1, o0, r, g, b, a, r, g, b, 0.0F);
        }
    }

    private static void square(BufferBuilder bb, Vec3 cam, double x0, double x1, double z0, double z1, double y, double w, float r, float g, float b, float a) {
        ClientBlastGeo.streak(bb, cam, x0, y, z0, x1, y, z0, w, r, g, b, a, a);
        ClientBlastGeo.streak(bb, cam, x1, y, z0, x1, y, z1, w, r, g, b, a, a);
        ClientBlastGeo.streak(bb, cam, x1, y, z1, x0, y, z1, w, r, g, b, a, a);
        ClientBlastGeo.streak(bb, cam, x0, y, z1, x0, y, z0, w, r, g, b, a, a);
    }

    private static Vec3 hookNow(ClientLevel level, Fx f, float pt) {
        if (f.plotHook == null || f.p.ship() == null) return f.p.c();
        Matrix4d m = ShipFrame.craftMatrix(level, f.p.ship(), pt);
        if (m == null) return f.p.c();
        Vector3d w = m.transformPosition(new Vector3d(f.plotHook));
        return new Vec3(w.x, w.y, w.z);
    }

    private static void ring(BufferBuilder bb, Vec3 cam, double x, double y, double z, double R, double w, float r, float g, float b, float a, int n) {
        double da = Math.PI * 2.0 / n;
        for (int i = 0; i < n; i++) {
            double t0 = i * da, t1 = t0 + da;
            Vec3 i0 = new Vec3(x + Math.cos(t0) * (R - w), y, z + Math.sin(t0) * (R - w)), i1 = new Vec3(x + Math.cos(t1) * (R - w), y, z + Math.sin(t1) * (R - w));
            Vec3 m0 = new Vec3(x + Math.cos(t0) * R, y + w * 0.35, z + Math.sin(t0) * R), m1 = new Vec3(x + Math.cos(t1) * R, y + w * 0.35, z + Math.sin(t1) * R);
            Vec3 o0 = new Vec3(x + Math.cos(t0) * (R + w), y, z + Math.sin(t0) * (R + w)), o1 = new Vec3(x + Math.cos(t1) * (R + w), y, z + Math.sin(t1) * (R + w));
            ClientBlastGeo.quad(bb, cam, i0, i1, m1, m0, r, g, b, 0.0F, r, g, b, a);
            ClientBlastGeo.quad(bb, cam, m0, m1, o1, o0, r, g, b, a, r, g, b, 0.0F);
        }
    }
}
