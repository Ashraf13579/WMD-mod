package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFxEmp {

    private ClientFxEmp() {}

    static final int WAVE_TICKS = 110, ARC_TICKS = 60, FIELD_TICKS = 800, FLASH_TICKS = 14, ANG = 128;
    private static final long KEY = 0x3E4D_0000_0000_0000L;

    private static final class Show {
        final double x, y, z, r;
        final int tier;
        final long start, seed;
        double y0, cell;
        float[][] polarH;
        double camSide = Double.NaN;
        Show(double x, double y, double z, int tier, double r, long start) {
            this.x = x; this.y = y; this.z = z; this.tier = tier; this.r = r; this.start = start;
            this.seed = Double.doubleToLongBits(x * 31.0 + z) ^ start;
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();
    private static float wash, blip;

    public static void start(double x, double y, double z, int tier, double fieldRadius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Show s = new Show(x, y, z, tier, Math.max(8.0, fieldRadius), level.getGameTime());
        terrain(level, s);
        SHOWS.add(s);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.hypot(cam.x - x, cam.z - z), near = s.r * 0.75;
        if (d < ClientBlastGeo.FAR_VIEW) level.setSkyFlashTime(4);
        if (d < near && ClientConfig.explosionScreen()) wash = Math.max(wash, (float) (1.0 - d / near) * 0.7F);
    }

    public static boolean any() { return !SHOWS.isEmpty(); }

    public static boolean darkAt(double x, double z) {
        if (SHOWS.isEmpty()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        long now = level.getGameTime();
        for (Show s : SHOWS) {
            double dx = x - s.x, dz = z - s.z, age = now - s.start;
            if (dx * dx + dz * dz > s.r * s.r || age < 0) continue;
            if (waveR(age, s.r) < Math.sqrt(dx * dx + dz * dz)) continue;
            if (age < FIELD_TICKS) return true;
            long a = (long) age - FIELD_TICKS;
            if (a < 4 || (a >= 10 && a < 13) || (a >= 20 && a < 22)) return true;
        }
        return false;
    }

    static double waveR(double age, double R) {
        if (age < 0) return -1.0;
        double u = Math.min(1.0, age / WAVE_TICKS);
        return R * (1.0 - Math.pow(1.0 - u, 2.2));
    }

    private static void terrain(ClientLevel level, Show s) {
        s.y0 = ClientBlastGeo.ground(level, s.x, s.z, s.y);
        s.cell = Math.max(2.0, s.r / 64.0);
        int nr = (int) Math.ceil(s.r / s.cell) + 2;
        s.polarH = new float[ANG][nr];
        for (int i = 0; i < ANG; i++) {
            double a = Math.PI * 2 * i / ANG, ca = Math.cos(a), sa = Math.sin(a), last = s.y0;
            for (int j = 0; j < nr; j++) {
                double d = j * s.cell;
                last = ClientBlastGeo.ground(level, s.x + ca * d, s.z + sa * d, last);
                s.polarH[i][j] = (float) last;
            }
        }
    }

    private static double gIdx(Show s, int i, double d) {
        float[] row = s.polarH[Math.floorMod(i, ANG)];
        double fj = Math.max(0.0, d / s.cell);
        int j = Math.min(row.length - 2, (int) fj);
        double t = Math.min(1.0, fj - j);
        return row[j] * (1 - t) + row[j + 1] * t;
    }

    private static double gAt(Show s, double a, double d) {
        double fa = (a / (Math.PI * 2)) % 1.0; if (fa < 0) fa += 1.0;
        return gIdx(s, (int) Math.round(fa * ANG), d);
    }

    private static double gXZ(Show s, double x, double z) { return gAt(s, Math.atan2(z - s.z, x - s.x), Math.hypot(x - s.x, z - s.z)); }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SHOWS.clear(); wash = blip = 0; return; }
        long now = level.getGameTime();
        wash *= 0.8F; blip *= 0.86F;
        SHOWS.removeIf(s -> now - s.start > FIELD_TICKS + 40 || now < s.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (Show s : SHOWS) {
            double age = now - s.start, dc = Math.hypot(cam.x - s.x, cam.z - s.z), ac = Math.atan2(cam.z - s.z, cam.x - s.x);
            if (dc > ClientBlastGeo.FAR_VIEW) { s.camSide = Double.NaN; continue; }
            if (age <= WAVE_TICKS) {
                double r = waveR(age, s.r), side = dc - r;

                if (!Double.isNaN(s.camSide) && s.camSide > 0 && side <= 0 && dc <= s.r) {
                    if (ClientConfig.explosionScreen()) blip = Math.max(blip, 0.55F);
                    VeilBridge.flash(cam.x, cam.y, cam.z, 0.55F, 0.8F, 1.0F, 28.0F, 4.0F, 0.6F, 10);
                    WmdSound.play(SoundEvents.LIGHTNING_BOLT_IMPACT, cam.x, cam.y, cam.z, 0.8F, 1.5F);
                }
                s.camSide = side;
                float f = (float) Math.min(1.0, (WAVE_TICKS - age) / (WAVE_TICKS * 0.3));

                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0 + (s.seed & 7);
                    VeilBridge.glow(KEY ^ (s.seed * 32 + i), s.x + Math.cos(a) * r, gAt(s, a, r) + 2.0, s.z + Math.sin(a) * r, 0.5F, 0.75F, 1.0F, 22.0F, 3.0F * f + 0.3F, 0.5F, now);
                }
                if (Math.abs(side) < 120.0 && r > 2.0) {
                    for (int i = -2; i <= 2; i++) {
                        double a = ac + i * 14.0 / Math.max(20.0, r);
                        float flick = 0.7F + 0.3F * rnd.nextFloat();
                        VeilBridge.glow(KEY ^ (s.seed * 32 + 10 + i), s.x + Math.cos(a) * r, gAt(s, a, r) + 1.5, s.z + Math.sin(a) * r, 0.5F, 0.75F, 1.0F, 14.0F, 2.6F * f * flick, 0.5F, now);
                    }

                    double span = 100.0 / Math.max(20.0, r);
                    int n = ClientConfig.count(30.0F * f, rnd);
                    for (int i = 0; i < n; i++) {
                        double a = ac + (rnd.nextDouble() - 0.5) * 2 * span, rr = r + rnd.nextGaussian() * 1.2;
                        double px = s.x + Math.cos(a) * rr, pz = s.z + Math.sin(a) * rr;
                        level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, px, gAt(s, a, rr) + 0.2 + rnd.nextDouble() * 2.0, pz,
                                rnd.nextGaussian() * 0.12, 0.12 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.12);
                    }
                }
            } else s.camSide = Double.NaN;

            if (age >= 0 && age < ARC_TICKS && now % 4 == 0) {
                long bucket = now / 2;
                RandomSource ar = arcRnd(s, bucket);
                double a = ar.nextDouble() * Math.PI * 2, d = Math.sqrt(ar.nextDouble()) * arcReach(s, bucket), h = (0.08 + 0.17 * ar.nextDouble()) * s.r;
                VeilBridge.flash(s.x + Math.cos(a) * d, gAt(s, a, d) + h * 0.5, s.z + Math.sin(a) * d, 0.6F, 0.8F, 1.0F, 30.0F, 5.0F, 0.5F, 4);
            }

            if (age >= ARC_TICKS && age < FIELD_TICKS && now % 3 == 0) {
                RandomSource cr = crackleRnd(s, now / 3, 0);
                float roll = cr.nextFloat();
                double a = cr.nextDouble() * Math.PI * 2, d = Math.sqrt(cr.nextDouble()) * s.r * 0.95;
                if (roll < crackleP(age)) VeilBridge.flash(s.x + Math.cos(a) * d, gAt(s, a, d) + 1.5, s.z + Math.sin(a) * d, 0.55F, 0.8F, 1.0F, 10.0F, 2.0F, 0.3F, 3);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        long tick = level.getGameTime();
        double now = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (Math.hypot(cam.x - s.x, cam.z - s.z) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            if (age < 0) continue;
            any |= haze(bb, cam, s, age, tick);
            any |= edge(bb, cam, s, age, tick);
            any |= wave(bb, cam, s, age, tick);
            any |= veins(bb, cam, s, age, tick, density);
            any |= sparks(bb, cam, s, age, tick, density);
            any |= arcs(bb, cam, left, up, s, age, tick, density);
            any |= crackles(bb, cam, s, age, tick, density);
            any |= flash(bb, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static double wid(Vec3 cam, double x, double y, double z, double base) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return base * Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / 48.0);
    }

    private static void bolt(BufferBuilder bb, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double w, float a) {
        double ww = wid(cam, (x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5, w);
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, ww * 3.5, 0.35F, 0.6F, 1.0F, a * 0.35F, a * 0.35F);
        ClientBlastGeo.streak(bb, cam, x0, y0, z0, x1, y1, z1, ww, 0.85F, 0.95F, 1.0F, a, a);
    }

    private static boolean flash(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age > FLASH_TICKS) return false;
        float k = (float) (1.0 - age / FLASH_TICKS);
        double y = s.y0 + 3.0, sc = s.r / 160.0;
        ClientBlastGeo.disc(bb, cam, left, up, s.x, y, s.z, 70.0 * sc * (0.6 + 0.4 * age / FLASH_TICKS), 0.45F, 0.65F, 1.0F, 0.5F * k);
        ClientBlastGeo.disc(bb, cam, left, up, s.x, y, s.z, 26.0 * sc, 0.75F, 0.88F, 1.0F, 0.8F * k);
        ClientBlastGeo.disc(bb, cam, left, up, s.x, y, s.z, 9.0 * sc, 1.0F, 1.0F, 1.0F, 0.95F * k * k + 0.05F * k);
        double top = s.y0 + 90.0 * sc;
        ClientBlastGeo.streak(bb, cam, s.x, s.y0, s.z, s.x, top, s.z, wid(cam, s.x, s.y0 + 20, s.z, 2.5 * sc), 0.5F, 0.7F, 1.0F, 0.45F * k, 0.0F);
        ClientBlastGeo.streak(bb, cam, s.x, s.y0, s.z, s.x, top, s.z, wid(cam, s.x, s.y0 + 20, s.z, 0.6 * sc), 0.9F, 0.95F, 1.0F, 0.95F * k, 0.0F);
        return true;
    }

    private static boolean wave(BufferBuilder bb, Vec3 cam, Show s, double age, long tick) {
        if (age > WAVE_TICKS) return false;
        double r = waveR(age, s.r);
        if (r < 1.0) return false;
        double u = age / WAVE_TICKS;
        float fade = (float) Math.min(1.0, (WAVE_TICKS - age) / (WAVE_TICKS * 0.3));
        float pulse = 0.8F + 0.2F * (float) Math.sin(tick * 0.9);
        double h = (4.0 + s.r * 0.045) * (1.0 - 0.4 * u);
        double px = 0, pz = 0, pg = 0;
        for (int i = 0; i <= ANG; i++) {
            double a = Math.PI * 2 * i / ANG, x = s.x + Math.cos(a) * r, z = s.z + Math.sin(a) * r, gy = gIdx(s, i, r);
            if (i > 0) {
                float k = 0.7F * fade * pulse;
                ClientBlastGeo.v(bb, cam, px, pg - 0.3, pz, 0.55F, 0.8F, 1.0F, k); ClientBlastGeo.v(bb, cam, x, gy - 0.3, z, 0.55F, 0.8F, 1.0F, k);
                ClientBlastGeo.v(bb, cam, x, gy + h, z, 0.3F, 0.5F, 1.0F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + h, pz, 0.3F, 0.5F, 1.0F, 0.0F);
                float c = 0.9F * fade;
                ClientBlastGeo.v(bb, cam, px, pg + 0.05, pz, 1.0F, 1.0F, 1.0F, c); ClientBlastGeo.v(bb, cam, x, gy + 0.05, z, 1.0F, 1.0F, 1.0F, c);
                ClientBlastGeo.v(bb, cam, x, gy + 1.2, z, 0.7F, 0.9F, 1.0F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + 1.2, pz, 0.7F, 0.9F, 1.0F, 0.0F);
            }
            px = x; pz = z; pg = gy;
        }
        return true;
    }

    private static boolean veins(BufferBuilder bb, Vec3 cam, Show s, double age, long tick, double density) {
        double end = WAVE_TICKS * 0.85, r = waveR(age, s.r);
        if (age > end || r < 6.0) return false;
        float fade = (float) Math.min(1.0, (end - age) / 25.0);
        double sc = s.r / 160.0;
        RandomSource rnd = RandomSource.create(s.seed ^ (tick / 2) * 0x9E3779B97F4A7C15L);
        int n = (int) Math.max(12, 36 * density);
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d0 = r - rnd.nextDouble() * 2.0;
            double x = s.x + Math.cos(a) * d0, z = s.z + Math.sin(a) * d0, y = gAt(s, a, d0) + 0.3 + rnd.nextDouble() * 0.6;
            double dir = a + Math.PI + (rnd.nextDouble() - 0.5) * 1.6, len = (2.5 + rnd.nextDouble() * 3.0) * sc;
            float al = fade * (0.5F + 0.5F * rnd.nextFloat());
            for (int seg = 0; seg < 6; seg++) {
                dir += (rnd.nextDouble() - 0.5) * 1.4;
                double nx = x + Math.cos(dir) * len, nz = z + Math.sin(dir) * len, ny = gXZ(s, nx, nz) + 0.25 + rnd.nextDouble() * 0.9;
                bolt(bb, cam, x, y, z, nx, ny, nz, 0.12, al);
                x = nx; y = ny; z = nz;
                if (rnd.nextInt(4) == 0) {
                    double bdir = dir + (rnd.nextBoolean() ? 1 : -1) * (0.6 + rnd.nextDouble()), bl = len * 0.6;
                    double bx = x + Math.cos(bdir) * bl, bz = z + Math.sin(bdir) * bl;
                    bolt(bb, cam, x, y, z, bx, gXZ(s, bx, bz) + 0.3, bz, 0.08, al * 0.7F);
                }
                al *= 0.88F;
            }
        }
        return true;
    }

    private static boolean sparks(BufferBuilder bb, Vec3 cam, Show s, double age, long tick, double density) {
        double r = waveR(age, s.r);
        if (age > WAVE_TICKS || r < 3.0) return false;
        float fade = (float) Math.min(1.0, (WAVE_TICKS - age) / (WAVE_TICKS * 0.3));
        RandomSource rnd = RandomSource.create(s.seed ^ tick * 0x2545F4914F6CDD1DL);
        int n = (int) Math.max(30, 120 * density);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = r + (rnd.nextDouble() - 0.5) * 3.0, h = 0.5 + rnd.nextDouble() * 3.0;
            double lx = (rnd.nextDouble() - 0.5) * 1.2, lz = (rnd.nextDouble() - 0.5) * 1.2;
            float al = fade * (0.6F + 0.4F * rnd.nextFloat());
            double x = s.x + Math.cos(a) * rr, z = s.z + Math.sin(a) * rr, g = gAt(s, a, rr) + 0.1;
            ClientBlastGeo.streak(bb, cam, x, g, z, x + lx, g + h, z + lz, wid(cam, x, g, z, 0.06), 0.8F, 0.92F, 1.0F, al, 0.0F);
        }
        return true;
    }

    private static RandomSource arcRnd(Show s, long bucket) { return arcRnd(s, bucket, 0); }
    private static RandomSource arcRnd(Show s, long bucket, int k) { return RandomSource.create(s.seed ^ (bucket * 0x5DEECE66DL + k * 7919L)); }

    private static double arcReach(Show s, long bucket) { return Math.min(s.r * 0.9, Math.max(20.0, waveR(bucket * 2 - s.start, s.r))); }

    private static boolean arcs(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age, long tick, double density) {
        if (age > ARC_TICKS) return false;
        long bucket = tick / 2;
        double reach = arcReach(s, bucket), sc = s.r / 160.0;
        float env = (float) Math.min(1.0, (ARC_TICKS - age) / 15.0);
        int n = (int) Math.max(2, Math.round((4 + 8 * (1.0 - age / ARC_TICKS)) * density));
        for (int k = 0; k < n; k++) {
            RandomSource rnd = arcRnd(s, bucket, k);
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * reach, H = (0.08 + 0.17 * rnd.nextDouble()) * s.r, jit = H * 0.14;
            float al = env * (0.55F + 0.45F * rnd.nextFloat());
            double x = s.x + Math.cos(a) * d, z = s.z + Math.sin(a) * d, y = gAt(s, a, d);
            ClientBlastGeo.disc(bb, cam, left, up, x, y + 0.8, z, 1.0 + 3.0 * sc, 0.5F, 0.75F, 1.0F, 0.6F * al, 6);
            for (int seg = 0; seg < 8; seg++) {
                double nx = x + (rnd.nextDouble() - 0.5) * 2 * jit, ny = y + H / 8 * (0.6 + 0.8 * rnd.nextDouble()), nz = z + (rnd.nextDouble() - 0.5) * 2 * jit;
                bolt(bb, cam, x, y, z, nx, ny, nz, 0.18, al);
                if ((seg == 2 || seg == 5) && rnd.nextFloat() < 0.6F) {
                    double fx = nx, fy = ny, fz = nz, fdx = rnd.nextDouble() - 0.5, fdz = rnd.nextDouble() - 0.5;
                    for (int f = 0; f < 3; f++) {
                        double gx = fx + fdx * H * 0.25 + (rnd.nextDouble() - 0.5) * jit, gy = fy + H / 12 * rnd.nextDouble(), gz = fz + fdz * H * 0.25 + (rnd.nextDouble() - 0.5) * jit;
                        bolt(bb, cam, fx, fy, fz, gx, gy, gz, 0.1, al * 0.6F);
                        fx = gx; fy = gy; fz = gz;
                    }
                }
                x = nx; y = ny; z = nz;
                al *= 0.94F;
            }
        }
        return true;
    }

    private static float hazeEnv(double age) {
        if (age < 5 || age > FIELD_TICKS) return 0.0F;
        double in = Math.min(1.0, (age - 5) / 25.0), out = age < 300 ? 1.0 : Math.pow(Math.max(0.0, (FIELD_TICKS - age) / (FIELD_TICKS - 300.0)), 1.4);
        return (float) (in * out);
    }

    private static boolean haze(BufferBuilder bb, Vec3 cam, Show s, double age, long tick) {
        float env = hazeEnv(age);
        if (env <= 0.0F) return false;
        RandomSource rnd = RandomSource.create(s.seed ^ tick * 0x632BE59BD9B4E019L);
        float flick = env * (0.7F + 0.3F * rnd.nextFloat());
        int rings = 6, segs = 32, step = ANG / segs;
        double hd = s.r * 0.4;
        for (int i = 0; i < rings; i++) {
            double e0 = Math.PI / 2 * i / rings, e1 = Math.PI / 2 * (i + 1) / rings;
            float a0 = 0.055F * (1.0F - 0.6F * i / rings), a1 = 0.055F * (1.0F - 0.6F * (i + 1) / rings);
            for (int j = 0; j < segs; j++) {
                float boost = rnd.nextFloat() < 0.05F ? 0.06F : 0.0F;
                double[] p00 = dome(s, j * step, e0, hd), p01 = dome(s, (j + 1) * step, e0, hd), p11 = dome(s, (j + 1) * step, e1, hd), p10 = dome(s, j * step, e1, hd);
                float b0 = (a0 + boost) * flick, b1 = (a1 + boost) * flick;
                ClientBlastGeo.v(bb, cam, p00[0], p00[1], p00[2], 0.35F, 0.55F, 1.0F, b0); ClientBlastGeo.v(bb, cam, p01[0], p01[1], p01[2], 0.35F, 0.55F, 1.0F, b0);
                ClientBlastGeo.v(bb, cam, p11[0], p11[1], p11[2], 0.35F, 0.55F, 1.0F, b1); ClientBlastGeo.v(bb, cam, p10[0], p10[1], p10[2], 0.35F, 0.55F, 1.0F, b1);
            }
        }
        return true;
    }

    private static double[] dome(Show s, int i, double e, double hd) {
        double a = Math.PI * 2 * i / ANG, c = Math.cos(e), edge = gIdx(s, i, s.r);
        return new double[] { s.x + Math.cos(a) * s.r * c, edge * c + s.y0 * (1.0 - c) + hd * Math.sin(e), s.z + Math.sin(a) * s.r * c };
    }

    private static boolean edge(BufferBuilder bb, Vec3 cam, Show s, double age, long tick) {
        if (age < WAVE_TICKS * 0.8) return false;
        float env = hazeEnv(age) * (float) Math.min(1.0, (age - WAVE_TICKS * 0.8) / (WAVE_TICKS * 0.2));
        if (env <= 0.0F) return false;
        RandomSource rnd = RandomSource.create(s.seed ^ tick * 0x1B873593L);
        double px = 0, pz = 0, pg = 0;
        for (int i = 0; i <= ANG; i++) {
            float fl = 0.6F + 0.4F * rnd.nextFloat();
            double a = Math.PI * 2 * i / ANG, x = s.x + Math.cos(a) * s.r, z = s.z + Math.sin(a) * s.r, gy = gIdx(s, i, s.r);
            double h = 3.0 + 1.5 * Math.sin(tick * 0.3 + i * 0.7);
            if (i > 0) {
                float k = 0.3F * env * fl;
                ClientBlastGeo.v(bb, cam, px, pg - 0.2, pz, 0.45F, 0.7F, 1.0F, k); ClientBlastGeo.v(bb, cam, x, gy - 0.2, z, 0.45F, 0.7F, 1.0F, k);
                ClientBlastGeo.v(bb, cam, x, gy + h, z, 0.3F, 0.5F, 1.0F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + h, pz, 0.3F, 0.5F, 1.0F, 0.0F);
            }
            px = x; pz = z; pg = gy;
        }
        return true;
    }

    private static RandomSource crackleRnd(Show s, long bucket, int k) { return RandomSource.create(s.seed ^ (bucket * 0x2545F4914F6CDD1DL + k * 104729L + 0xC4L)); }

    private static float crackleP(double age) { return (float) (0.08 + 0.35 * Math.max(0.0, 1.0 - age / FIELD_TICKS)); }

    private static boolean crackles(BufferBuilder bb, Vec3 cam, Show s, double age, long tick, double density) {
        if (age < ARC_TICKS || age > FIELD_TICKS) return false;
        long bucket = tick / 3;
        float p = crackleP(age), env = Math.max(0.3F, hazeEnv(age));
        int n = (int) Math.max(1, Math.round(3 * density));
        double[] px = new double[6], py = new double[6], pz = new double[6];
        boolean any = false;
        for (int k = 0; k < n; k++) {
            RandomSource rnd = crackleRnd(s, bucket, k);
            float roll = rnd.nextFloat();
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * s.r * 0.95;
            boolean upward = rnd.nextFloat() < 0.3F;
            double len = 1.5 + rnd.nextDouble() * 2.0, dir = rnd.nextDouble() * Math.PI * 2, H = 4.0 + rnd.nextDouble() * 5.0;
            float al = env * (0.6F + 0.4F * rnd.nextFloat());
            px[0] = s.x + Math.cos(a) * d; pz[0] = s.z + Math.sin(a) * d; py[0] = gAt(s, a, d) + 0.3 + rnd.nextDouble() * 1.2;
            for (int i = 1; i < 6; i++) {
                if (upward) {
                    px[i] = px[i - 1] + (rnd.nextDouble() - 0.5) * 1.4; pz[i] = pz[i - 1] + (rnd.nextDouble() - 0.5) * 1.4; py[i] = py[i - 1] + H / 5 * (0.6 + 0.8 * rnd.nextDouble());
                } else {
                    dir += (rnd.nextDouble() - 0.5) * 1.6;
                    px[i] = px[i - 1] + Math.cos(dir) * len; pz[i] = pz[i - 1] + Math.sin(dir) * len; py[i] = gXZ(s, px[i], pz[i]) + 0.3 + rnd.nextDouble() * 1.8;
                }
            }
            if (roll >= p) continue;
            for (int i = 1; i < 6; i++) bolt(bb, cam, px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i], 0.1, al);
            any = true;
        }
        return any;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if ((wash < 0.02F && blip < 0.02F) || !ClientConfig.explosionScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (wash >= 0.02F) g.fill(0, 0, w, h, ((int) (210 * Math.min(1.0F, wash)) << 24) | 0xDCEBFF);
        if (blip >= 0.02F) {
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, ((int) (35 * blip)) << 24);
            RandomSource rnd = RandomSource.create();
            int bars = (int) (10 * blip) + 1;
            for (int i = 0; i < bars; i++) {
                int y = rnd.nextInt(h), bh = 1 + rnd.nextInt(Math.max(1, (int) (8 * blip) + 1)), x = rnd.nextInt(Math.max(1, w / 3));
                int grey = 120 + rnd.nextInt(136), alpha = (int) ((60 + rnd.nextInt(120)) * blip);
                int col = switch (rnd.nextInt(4)) { case 0 -> 0x00FFFF; case 1 -> 0x7AA0FF; default -> (grey << 16) | (grey << 8) | grey; };
                g.fill(x, y, w - rnd.nextInt(Math.max(1, w / 4)), y + bh, (alpha << 24) | col);
            }
        }
    }
}
