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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFxBreacher {

    private ClientFxBreacher() {}

    private static final double G_DUST = 0.12, G_ROCK = 0.08;
    private static final int LIFE = 600, RING_LIFE = 40, GLOW_TICKS = 200, WISP_START = 30, WISP_END = 400, WISP_LIFE = 140;

    private static final class Show {
        final Vec3 pos;
        final int tier;
        final double r, boreR, depth, colH, y0;
        final long start, seed;

        double[] pL, pA, pS, pW, pU, pVt, pR, pLife;

        double[] kL, kUx, kUz, kD, kT, kV, kG, kW;
        boolean[] kIn;

        double[] wB, wA, wS, wR;

        Show(Vec3 pos, int tier, double r, long start) {
            this.pos = pos; this.tier = tier; this.r = r; this.start = start;
            this.seed = Double.doubleToLongBits(pos.x * 37 + pos.z) ^ start ^ 0xB4EAL;
            this.boreR = 2.0 + tier * 0.8;
            this.depth = 24.0 * tier;
            this.colH = Math.max(25.0, Math.min(80.0, 20.0 + r * 2.5));
            this.y0 = pos.y;
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();

    public static void start(double x, double y, double z, int tier, double r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Show s = new Show(new Vec3(x, y, z), Math.max(1, Math.min(4, tier)), r, level.getGameTime());
        build(level, s);
        SHOWS.add(s);
    }

    private static void build(ClientLevel level, Show s) {
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        RandomSource rnd = RandomSource.create(s.seed);
        int n = (int) Math.max(60, 180 * density);
        s.pL = new double[n]; s.pA = new double[n]; s.pS = new double[n]; s.pW = new double[n]; s.pU = new double[n]; s.pVt = new double[n]; s.pR = new double[n]; s.pLife = new double[n];
        for (int i = 0; i < n; i++) {
            double q = rnd.nextDouble();
            s.pL[i] = 8.0 * q * q;
            s.pA[i] = rnd.nextDouble() * Math.PI * 2;
            s.pS[i] = rnd.nextDouble() * s.boreR * 0.5;
            s.pW[i] = 0.01 + rnd.nextDouble() * 0.03;
            s.pU[i] = s.colH * (0.2 + 0.8 * Math.sqrt(rnd.nextDouble()));
            s.pVt[i] = 0.18 + rnd.nextDouble() * 0.2;
            s.pR[i] = s.boreR * (0.45 + 0.3 * rnd.nextDouble());
            s.pLife[i] = 150 + rnd.nextDouble() * 90;
        }
        int m = (int) Math.max(20, 56 * density);
        s.kL = new double[m]; s.kUx = new double[m]; s.kUz = new double[m]; s.kD = new double[m]; s.kT = new double[m]; s.kV = new double[m]; s.kG = new double[m]; s.kW = new double[m];
        s.kIn = new boolean[m];
        double top = s.y0 + 1.0;
        for (int i = 0; i < m; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, back = rnd.nextDouble(), dd = rnd.nextDouble(), hh = rnd.nextDouble(), ww = rnd.nextDouble();
            s.kL[i] = 1.0 + rnd.nextDouble() * 10.0;
            s.kUx[i] = Math.cos(a); s.kUz[i] = Math.sin(a);
            s.kIn[i] = back < 0.15;
            s.kD[i] = s.kIn[i] ? dd * s.boreR * 0.6 : s.r * (1.0 + 2.0 * dd);
            s.kG[i] = s.kIn[i] ? s.y0 - 12.0 : ClientBlastGeo.ground(level, s.pos.x + s.kUx[i] * s.kD[i], s.pos.z + s.kUz[i] * s.kD[i], s.y0);
            double apex = Math.max(s.colH * (0.3 + 0.8 * hh), s.kG[i] - top + 2.0);
            double tUp = Math.sqrt(2.0 * apex / G_ROCK);
            s.kV[i] = G_ROCK * tUp;
            s.kT[i] = tUp + Math.sqrt(2.0 * (apex + top - s.kG[i]) / G_ROCK);
            s.kW[i] = 0.12 + 0.18 * ww;
        }
        int w = (int) Math.max(30, 74 * density);
        s.wB = new double[w]; s.wA = new double[w]; s.wS = new double[w]; s.wR = new double[w];
        for (int i = 0; i < w; i++) {
            s.wB[i] = WISP_START + (WISP_END - WISP_START) * (i + rnd.nextDouble() * 0.8) / w;
            s.wA[i] = rnd.nextDouble() * Math.PI * 2;
            s.wS[i] = rnd.nextDouble() * 6.3;
            s.wR[i] = 0.8 + rnd.nextDouble() * 0.6;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { SHOWS.clear(); return; }
        long now = level.getGameTime();
        SHOWS.removeIf(s -> now - s.start > LIFE || now < s.start - 100);
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
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= ring(smoke, level, cam, s, age);
            any |= columnDust(smoke, cam, left, up, s, age);
            any |= rocks(smoke, cam, left, up, s, age, false);
            any |= wisps(smoke, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, smoke, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= snap(glow, cam, left, up, s, age);
            any |= jet(glow, cam, left, up, s, age);
            any |= columnFire(glow, cam, left, up, s, age);
            any |= rocks(glow, cam, left, up, s, age, true);
            any |= boreGlow(glow, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static boolean snap(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 5) return false;
        float k = (float) (1.0 - age / 5.0);
        double x = s.pos.x, z = s.pos.z, y = s.y0 + 1.2, size = (1.2 + s.boreR * 0.6) * (0.7 + 0.3 * age / 5.0);
        disc(bb, cam, left, up, x, y, z, size * 2.0, 1.0F, 0.8F, 0.5F, 0.4F * k);
        disc(bb, cam, left, up, x, y, z, size, 1.0F, 0.98F, 0.92F, 0.95F * k);
        double spike = s.colH * 0.4 * Math.min(1.0, 0.4 + age / 2.0);
        ClientBlastGeo.streak(bb, cam, x, s.y0 + 0.5, z, x, s.y0 + spike, z, 0.5, 1.0F, 0.97F, 0.9F, 0.9F * k, 0.0F);
        return true;
    }

    private static boolean jet(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 22) return false;
        double x = s.pos.x, z = s.pos.z;
        if (age <= 9) {
            double front = s.depth * Math.min(1.0, age / 6.0);
            float f = (float) (age <= 6 ? 1.0 : 1.0 - (age - 6) / 3.0);
            for (double d = 0; d <= front; d += 2.5) {
                float heat = (float) (1.0 - 0.3 * d / s.depth);
                disc(bb, cam, left, up, x, s.y0 - d, z, 0.8, 1.0F, 0.85F + 0.15F * heat, 0.55F + 0.4F * heat, 0.7F * f, 8);
            }
            ClientBlastGeo.streak(bb, cam, x, s.y0 + 0.5, z, x, s.y0 - front, z, 0.45, 1.0F, 0.97F, 0.9F, 0.85F * f, 0.95F * f);
            disc(bb, cam, left, up, x, s.y0 - front, z, 1.8, 1.0F, 0.95F, 0.8F, 0.95F * f);
            disc(bb, cam, left, up, x, s.y0 - front, z, 3.4, 1.0F, 0.6F, 0.25F, 0.35F * f);
        }
        if (age >= 5) {
            float k = (float) Math.max(0.0, 1.0 - (age - 5) / 17.0);
            disc(bb, cam, left, up, x, s.y0 - s.depth + 1.0, z, s.boreR * 1.6, 1.0F, 0.75F, 0.4F, 0.8F * k);
        }
        return true;
    }

    private static double[] puffAt(Show s, int i, double ta) {
        double apex = s.pU[i], v0 = Math.sqrt(2.0 * G_DUST * apex), tUp = v0 / G_DUST, y, out;
        double r0 = s.pR[i], rad;
        if (ta <= tUp) {
            y = s.y0 + 0.5 + v0 * ta - 0.5 * G_DUST * ta * ta;
            out = s.pS[i] + s.pW[i] * ta;
            rad = r0 * (1.0 + 0.025 * ta);
        } else {
            double tf = ta - tUp, vt = s.pVt[i], tv = vt / G_DUST;
            double fall = tf < tv ? 0.5 * G_DUST * tf * tf : 0.5 * vt * tv + vt * (tf - tv);
            y = s.y0 + 0.5 + apex - fall;
            out = Math.min(s.r * 2.5, s.pS[i] + s.pW[i] * ta + 0.035 * tf);
            rad = r0 * (1.0 + 0.025 * tUp + 0.012 * tf);
        }
        y = Math.max(y, s.y0 + 0.6 + rad * 0.35);
        return new double[] { s.pos.x + Math.cos(s.pA[i]) * out + ta * 0.012, y, s.pos.z + Math.sin(s.pA[i]) * out, rad };
    }

    private static boolean columnDust(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 1 || age > 260) return false;
        boolean any = false;
        for (int i = 0; i < s.pL.length; i++) {
            double ta = age - s.pL[i];
            if (ta < 1 || ta > s.pLife[i]) continue;
            double[] p = puffAt(s, i, ta);
            float in = (float) Math.min(1.0, (ta - 1) / 6.0), fade = (float) Math.min(1.0, (s.pLife[i] - ta) / (s.pLife[i] * 0.4));
            float h = (float) Math.min(1.0, (p[1] - s.y0) / s.colH);
            float c = 0.30F + 0.08F * h + 0.06F * (float) ((i * 7919) % 11) / 11.0F;
            disc(bb, cam, left, up, p[0], p[1], p[2], p[3], c * 1.06F, c, c * 0.9F, 0.55F * in * fade, 10);
            float lit = Math.min(1.0F, c + 0.15F);
            disc(bb, cam, left, up, p[0] + up.x() * p[3] * 0.22, p[1] + up.y() * p[3] * 0.22, p[2] + up.z() * p[3] * 0.22, p[3] * 0.6, lit, lit * 0.96F, lit * 0.88F, 0.3F * in * fade, 8);
            any = true;
        }
        return any;
    }

    private static boolean columnFire(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 20) return false;
        if (age <= 10) {
            float k = (float) (1.0 - age / 10.0);
            ClientBlastGeo.streak(bb, cam, s.pos.x, s.y0 + 0.5, s.pos.z, s.pos.x, s.y0 + Math.min(s.colH * 0.8, 1.0 + age * 4.0), s.pos.z, s.boreR * 0.35, 1.0F, 0.85F, 0.55F, 0.7F * k, 0.1F * k);
        }
        for (int i = 0; i < s.pL.length; i++) {
            double ta = age - s.pL[i];
            if (ta < 0 || ta > 9) continue;
            double[] p = puffAt(s, i, ta);
            float heat = (float) (1.0 - ta / 9.0);
            disc(bb, cam, left, up, p[0], p[1], p[2], p[3] * 0.85, 1.0F, 0.55F + 0.45F * heat * heat, 0.15F + 0.75F * heat * heat * heat, 0.6F * heat, 8);
        }
        return true;
    }

    private static double[] rockAt(Show s, int i, double t) {
        double f = Math.max(0.0, Math.min(1.0, t / s.kT[i])), tt = f * s.kT[i], d = s.kD[i] * f;
        return new double[] { s.pos.x + s.kUx[i] * d, s.y0 + 1.0 + s.kV[i] * tt - 0.5 * G_ROCK * tt * tt, s.pos.z + s.kUz[i] * d };
    }

    private static boolean rocks(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age, boolean glow) {
        if (age < 1 || age > 160) return false;
        boolean any = false;
        for (int i = 0; i < s.kL.length; i++) {
            double t = age - s.kL[i];
            if (t < 0) continue;
            if (t <= s.kT[i]) {
                double[] p = rockAt(s, i, t), q = rockAt(s, i, Math.max(0.0, t - 1.2));
                if (glow) {
                    if (t > 8) continue;
                    float heat = (float) (1.0 - t / 8.0);
                    ClientBlastGeo.streak(bb, cam, q[0], q[1], q[2], p[0], p[1], p[2], s.kW[i] * 0.8, 1.0F, 0.5F + 0.45F * heat, 0.15F + 0.6F * heat * heat, 0.0F, 0.9F * heat);
                } else {
                    ClientBlastGeo.streak(bb, cam, q[0], q[1], q[2], p[0], p[1], p[2], s.kW[i], 0.2F, 0.18F, 0.16F, 0.0F, 0.85F);
                    disc(bb, cam, left, up, p[0], p[1], p[2], s.kW[i] * 1.6, 0.17F, 0.15F, 0.13F, 0.9F, 6);
                }
                any = true;
            } else if (!glow && !s.kIn[i]) {
                double k = (t - s.kT[i]) / 18.0;
                if (k > 1) continue;
                double x = s.pos.x + s.kUx[i] * s.kD[i], z = s.pos.z + s.kUz[i] * s.kD[i];
                disc(bb, cam, left, up, x, s.kG[i] + 0.3 + k * 1.2, z, 0.35 + 1.4 * Math.sqrt(k), 0.52F, 0.47F, 0.40F, (float) (0.55 * (1 - k)), 6);
                any = true;
            }
        }
        return any;
    }

    private static boolean boreGlow(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > GLOW_TICKS) return false;
        float k = (float) Math.pow(1.0 - age / GLOW_TICKS, 1.6);
        double x = s.pos.x, z = s.pos.z, front = s.depth * Math.min(1.0, age / 6.0);
        int j = 0;
        for (double d = 1.0; d <= front; d += 3.0, j++) {
            double fd = d / s.depth;
            float on = (float) Math.min(1.0, (front - d) / 6.0 + (age > 6 ? 1.0 : 0.0));
            float flick = 0.8F + 0.2F * (float) Math.sin(age * 0.37 + j * 1.9);
            disc(bb, cam, left, up, x, s.y0 - d, z, s.boreR * 0.95, 1.0F, 0.42F + 0.2F * (float) (1.0 - fd), 0.1F, 0.3F * k * (0.5F + 0.5F * (float) fd) * flick * on, 10);
        }
        if (age > 6) disc(bb, cam, left, up, x, s.y0 - s.depth + 1.5, z, s.boreR * 1.3, 1.0F, 0.5F, 0.15F, 0.6F * k, 10);
        disc(bb, cam, left, up, x, s.y0 + 0.6, z, s.boreR * 1.6, 1.0F, 0.45F, 0.12F, 0.22F * k * (float) Math.min(1.0, age / 4.0), 10);
        return true;
    }

    private static boolean ring(BufferBuilder bb, ClientLevel level, Vec3 cam, Show s, double age) {
        if (age < 1 || age > RING_LIFE) return false;
        double u = Math.min(1.0, age / 22.0), rr = s.r * (0.6 + 2.2 * (1.0 - Math.pow(1.0 - u, 2.0)));
        float k = 0.42F * (float) Math.pow(1.0 - age / RING_LIFE, 1.2);
        double h = 0.8 + s.r * 0.12 * (1.0 + 0.5 * u);
        int seg = 56;
        double px = 0, pz = 0, pg = 0;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg, x = s.pos.x + Math.cos(a) * rr, z = s.pos.z + Math.sin(a) * rr, gy = ClientBlastGeo.ground(level, x, z, s.y0);
            if (i > 0) {
                ClientBlastGeo.v(bb, cam, px, pg - 0.3, pz, 0.55F, 0.5F, 0.43F, k); ClientBlastGeo.v(bb, cam, x, gy - 0.3, z, 0.55F, 0.5F, 0.43F, k);
                ClientBlastGeo.v(bb, cam, x, gy + h, z, 0.66F, 0.62F, 0.57F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + h, pz, 0.66F, 0.62F, 0.57F, 0.0F);
            }
            px = x; pz = z; pg = gy;
        }
        return true;
    }

    private static boolean wisps(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < WISP_START || age > WISP_END + WISP_LIFE) return false;
        boolean any = false;
        for (int i = 0; i < s.wB.length; i++) {
            double ta = age - s.wB[i];
            if (ta < 0 || ta > WISP_LIFE) continue;
            double k = ta / WISP_LIFE, rise = 0.08 * ta + 0.0004 * ta * ta;
            double sway = Math.sin(ta * 0.05 + s.wS[i]) * (0.3 + 1.2 * k);
            double x = s.pos.x + Math.cos(s.wA[i]) * (s.boreR * 0.3 + sway) + ta * 0.015, z = s.pos.z + Math.sin(s.wA[i]) * (s.boreR * 0.3 + sway);
            float later = (float) (1.0 - 0.6 * (s.wB[i] - WISP_START) / (WISP_END - WISP_START));
            float a = 0.3F * (float) ((1.0 - k) * Math.min(1.0, k * 8.0)) * later;
            disc(bb, cam, left, up, x, s.y0 - 3.0 + rise, z, s.wR[i] * (0.6 + 2.6 * Math.sqrt(k)), 0.6F, 0.58F, 0.55F, a, 8);
            any = true;
        }
        return any;
    }

    private static void disc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double rad, float r, float g, float b, float a) {
        ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, r, g, b, a);
    }

    private static void disc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double rad, float r, float g, float b, float a, int n) {
        ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, r, g, b, a, n);
    }
}
