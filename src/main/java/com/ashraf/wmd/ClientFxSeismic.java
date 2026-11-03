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
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFxSeismic {

    private ClientFxSeismic() {}

    static final double R = WarheadFx.SEISMIC_RADIUS;
    private static final double REACH = R * 1.08;
    private static final int LIFE = 1400;
    private static final int RINGS = 5, RING_GAP = 16, RING_TICKS = 100, RING_SEG = 80;
    private static final int CRACK_TICKS = 60, PUFF_LIFE = 55;
    private static final int COLLAPSE_LIFE = 700, RUB = 7;
    private static final int ANG = 128;
    private static final double KICK_RANGE = 500.0;

    private static final class Crack { double[] x, z, g, s, w; }

    private static final class Show {
        final double x, y, z;
        final long start, seed;
        boolean quake;
        double y0, cell;
        float[][] polarH;
        final double[] ringSide = { Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN };
        List<Crack> cracks;
        double sMax;
        double[] px, pz, pg, pt, ph;
        double[] cx, cz, cg, ct, cs;
        double[] ra, rs;
        long cStart = -1;
        double[] kx, kz, kg, kt, ks, kh;
        double[][] rub;
        Show(double x, double y, double z, long start) {
            this.x = x; this.y = y; this.z = z; this.start = start;
            this.seed = Double.doubleToLongBits(x * 43.0 + z) ^ start;
        }
        long end() { return Math.max(quake ? start + LIFE : start, cStart >= 0 ? cStart + COLLAPSE_LIFE : start); }
    }

    private static final List<Show> SHOWS = new ArrayList<>();
    private static float kick, sway, tremble;
    private static double kickAt;

    private static double density() { return ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0; }

    public static void quake(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Show s = new Show(x, y, z, now);
        s.quake = true;
        terrain(level, s);
        cracks(level, s);
        curtains(s);
        SHOWS.add(s);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.hypot(cam.x - x, cam.z - z);
        if (d < KICK_RANGE) {
            kick = Math.max(kick, (float) Math.pow(1.0 - d / KICK_RANGE, 1.2));
            kickAt = now + d / 34.0;
        }
    }

    public static void collapse(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Show s = null;
        for (Show o : SHOWS) {
            if (o.quake && o.cStart < 0 && Math.hypot(o.x - x, o.z - z) < 8.0 && now - o.start < LIFE) { s = o; break; }
        }
        if (s == null) {
            s = new Show(x, y, z, now);
            s.y0 = ClientBlastGeo.ground(level, x, z, y);
            SHOWS.add(s);
        }
        s.cStart = now;
        sinks(level, s);
    }

    private static void terrain(ClientLevel level, Show s) {
        s.y0 = ClientBlastGeo.ground(level, s.x, s.z, s.y);
        s.cell = Math.max(2.0, R / 75.0);
        int nr = (int) Math.ceil(REACH * 1.05 / s.cell) + 2;
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

    private static double polar(Show s, double a, double d) {
        double fa = (a / (Math.PI * 2)) % 1.0; if (fa < 0) fa += 1.0;
        int i = (int) Math.round(fa * ANG) % ANG;
        double fj = Math.max(0.0, d) / s.cell; int j = Math.min(s.polarH[i].length - 2, (int) fj); double t = Math.min(1.0, fj - j);
        return s.polarH[i][j] * (1 - t) + s.polarH[i][j + 1] * t;
    }

    private static void cracks(ClientLevel level, Show s) {
        double den = density();
        RandomSource rnd = RandomSource.create(s.seed ^ 0xC4AC4L);
        s.cracks = new ArrayList<>();
        List<Crack> mains = new ArrayList<>();
        int nMain = 9;
        for (int m = 0; m < nMain; m++) {
            double a0 = Math.PI * 2 * (m + rnd.nextDouble() * 0.7) / nMain, len = R * (0.35 + 0.5 * rnd.nextDouble());
            mains.add(walk(level, s, rnd, s.x + Math.cos(a0) * 2.0, s.z + Math.sin(a0) * 2.0, 2.0, a0, len, 1.7, 4.0));
        }
        s.cracks.addAll(mains);
        double bp = 0.13 * Math.min(1.5, den);
        for (Crack c : mains) {
            for (int i = 2; i < c.x.length - 2; i++) {
                double r1 = rnd.nextDouble(), r2 = rnd.nextDouble(), r3 = rnd.nextDouble();
                boolean leftSide = rnd.nextBoolean();
                if (r1 > bp) continue;
                double dir = Math.atan2(c.z[i + 1] - c.z[i - 1], c.x[i + 1] - c.x[i - 1]) + (leftSide ? 1 : -1) * (0.45 + 0.5 * r2);
                double from = Math.hypot(c.x[i] - s.x, c.z[i] - s.z), len = Math.min(10.0 + 28.0 * r3, R * 1.02 - from);
                if (len < 6.0) continue;
                s.cracks.add(walk(level, s, rnd, c.x[i], c.z[i], c.s[i], dir, len, c.w[i] * 0.55, 3.5));
            }
        }
        s.sMax = 1.0;
        for (Crack c : s.cracks) s.sMax = Math.max(s.sMax, c.s[c.s.length - 1]);
        List<double[]> puffs = new ArrayList<>();
        int stride = den >= 1.0 ? 3 : 4;
        for (Crack c : s.cracks) {
            for (int i = 0; i < c.x.length; i++) {
                double jit = rnd.nextDouble(), hr = rnd.nextDouble();
                if (i % stride != 0) continue;
                double near = 1.0 - Math.min(1.0, Math.hypot(c.x[i] - s.x, c.z[i] - s.z) / R);
                puffs.add(new double[] { c.x[i], c.z[i], c.g[i], 2.0 + crackBorn(s, c.s[i]) + jit * 3.0, (2.5 + 4.0 * hr) * (0.65 + 0.5 * near) });
            }
        }
        int n = puffs.size();
        s.px = new double[n]; s.pz = new double[n]; s.pg = new double[n]; s.pt = new double[n]; s.ph = new double[n];
        for (int i = 0; i < n; i++) { double[] p = puffs.get(i); s.px[i] = p[0]; s.pz[i] = p[1]; s.pg[i] = p[2]; s.pt[i] = p[3]; s.ph[i] = p[4]; }
    }

    private static Crack walk(ClientLevel level, Show s, RandomSource rnd, double x0, double z0, double s0, double head, double len, double w0, double step) {
        int n = Math.max(2, (int) Math.ceil(len / step) + 1);
        Crack c = new Crack();
        c.x = new double[n]; c.z = new double[n]; c.g = new double[n]; c.s = new double[n]; c.w = new double[n];
        double x = x0, z = z0, h = head;
        for (int i = 0; i < n; i++) {
            double jag = rnd.nextGaussian();
            if (i > 0) { h = h * 0.75 + head * 0.25 + jag * 0.32; x += Math.cos(h) * step; z += Math.sin(h) * step; }
            double f = i / (double) (n - 1);
            c.x[i] = x; c.z[i] = z; c.s[i] = s0 + i * step;
            c.w[i] = 0.18 + w0 * Math.pow(1.0 - f, 0.7);
            c.g[i] = ClientBlastGeo.ground(level, x, z, s.y0);
        }
        return c;
    }

    private static double crackTip(Show s, double t) {
        if (t < 0) return -1.0;
        return s.sMax * (1.0 - Math.pow(1.0 - Math.min(1.0, t / CRACK_TICKS), 1.6));
    }

    private static double crackBorn(Show s, double sv) {
        return CRACK_TICKS * (1.0 - Math.pow(Math.max(0.0, 1.0 - sv / s.sMax), 1.0 / 1.6));
    }

    private static double ringR(int i, double age) {
        double u = (age - 2 - i * RING_GAP) / RING_TICKS;
        if (u < 0 || u > 1) return -1.0;
        return REACH * (1.0 - Math.pow(1.0 - u, 1.3));
    }

    private static double ringBorn(double d) {
        double f = Math.min(0.999, d / REACH);
        return 2.0 + RING_TICKS * (1.0 - Math.pow(1.0 - f, 1.0 / 1.3));
    }

    private static void curtains(Show s) {
        double den = density();
        RandomSource rnd = RandomSource.create(s.seed ^ 0xD057L);
        List<double[]> cs = new ArrayList<>();
        int capCrack = (int) Math.max(30, 90 * den), k = 0;
        for (Crack c : s.cracks) {
            for (int i = 1; i < c.x.length; i++) {
                double jit = rnd.nextDouble(), sz = rnd.nextDouble();
                if (k++ % 3 != 0 || cs.size() >= capCrack) continue;
                cs.add(new double[] { c.x[i], c.z[i], c.g[i], 2.0 + crackBorn(s, c.s[i]) + 6.0 + jit * 10.0, sz });
            }
        }
        int m = (int) Math.max(30, 80 * den);
        for (int i = 0; i < m; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = R * (0.12 + 0.9 * Math.sqrt(rnd.nextDouble())), jit = rnd.nextDouble(), sz = rnd.nextDouble();
            cs.add(new double[] { s.x + Math.cos(a) * d, s.z + Math.sin(a) * d, polar(s, a, d), ringBorn(d) + 2.0 + jit * 6.0, sz });
        }
        int n = cs.size();
        s.cx = new double[n]; s.cz = new double[n]; s.cg = new double[n]; s.ct = new double[n]; s.cs = new double[n];
        for (int i = 0; i < n; i++) { double[] p = cs.get(i); s.cx[i] = p[0]; s.cz[i] = p[1]; s.cg[i] = p[2]; s.ct[i] = p[3]; s.cs[i] = p[4]; }
        int nr = (int) Math.max(12, 24 * den);
        s.ra = new double[nr]; s.rs = new double[nr];
        for (int i = 0; i < nr; i++) { s.ra[i] = Math.PI * 2 * (i + rnd.nextDouble() * 0.8) / nr; s.rs[i] = rnd.nextDouble(); }
    }

    private static void sinks(ClientLevel level, Show s) {
        double den = density();
        int n = (int) Math.max(10, 26 * den);
        RandomSource rnd = RandomSource.create(s.seed ^ 0x51A0L);
        s.kx = new double[n]; s.kz = new double[n]; s.kg = new double[n]; s.kt = new double[n]; s.ks = new double[n]; s.kh = new double[n];
        s.rub = new double[n * RUB][];
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = R * (0.08 + 0.92 * Math.sqrt(rnd.nextDouble()));
            s.kx[i] = s.x + Math.cos(a) * d; s.kz[i] = s.z + Math.sin(a) * d;
            s.kg[i] = ClientBlastGeo.ground(level, s.kx[i], s.kz[i], s.y0);
            s.kt[i] = Math.pow(rnd.nextDouble(), 1.3) * 60.0;
            s.ks[i] = 1.5 + 2.5 * rnd.nextDouble();
            s.kh[i] = 6.0 + 9.0 * rnd.nextDouble();
            for (int j = 0; j < RUB; j++) {
                double ra = rnd.nextDouble() * Math.PI * 2, out = s.ks[i] * (0.3 + 1.0 * rnd.nextDouble()), upH = 2.0 + 5.0 * rnd.nextDouble();
                double T = Math.max(10.0, Math.min(30.0, 2.0 * Math.sqrt(2.0 * upH / 0.09)));
                s.rub[i * RUB + j] = new double[] { Math.cos(ra), Math.sin(ra), out, upH, T, 0.25 + 0.35 * rnd.nextDouble() };
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SHOWS.clear(); kick = sway = tremble = 0; return; }
        long now = level.getGameTime();
        tremble *= 0.85F;
        if (now - kickAt > 40) kick = 0;
        SHOWS.removeIf(s -> now > s.end() + 10 || now < s.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float sw = 0;
        for (Show s : SHOWS) {
            double d = Math.hypot(cam.x - s.x, cam.z - s.z);
            if (s.quake) {
                double age = now - s.start;

                double env = age < 0 ? 0.0 : Math.min(1.0, age / 12.0) * (age < 110 ? 1.0 : Math.exp(-(age - 110) / 45.0));
                double dk = d < R ? 1.0 : Math.max(0.0, 1.0 - (d - R) / 550.0);
                sw = Math.max(sw, (float) (env * dk * dk));
                for (int i = 0; i < RINGS; i++) {
                    double rr = ringR(i, age);
                    if (rr < 0) { s.ringSide[i] = Double.NaN; continue; }
                    double side = d - rr;
                    if (!Double.isNaN(s.ringSide[i]) && s.ringSide[i] > 0 && side <= 0) {
                        float k = (float) (1.0 - rr / REACH);
                        tremble = Math.max(tremble, (0.6F + 1.2F * k) * (1.0F - 0.15F * i));
                        if (i == 0) WmdSound.play(WmdSounds.BLAST_WHUMP.get(), cam.x, cam.y, cam.z, 0.6F, 0.55F);
                    }
                    s.ringSide[i] = side;
                }
            }
            if (s.cStart >= 0 && s.kt != null) {
                long ac = now - s.cStart;
                double dk = d < R ? 1.0 : Math.max(0.0, 1.0 - (d - R) / 400.0);
                if (ac >= 0 && ac < 80) sw = Math.max(sw, (float) (0.55 * dk * Math.min(1.0, ac / 6.0) * Math.min(1.0, (80 - ac) / 20.0)));
                for (int i = 0; i < s.kt.length; i++) {
                    if ((long) Math.floor(s.kt[i]) != ac) continue;
                    double dd = Math.hypot(cam.x - s.kx[i], cam.z - s.kz[i]);
                    if (dd < 120.0) tremble = Math.max(tremble, (float) (1.6 * (1.0 - dd / 120.0)));
                }
            }
        }
        sway = sw;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || (kick < 0.01F && sway < 0.01F && tremble < 0.05F)) return;
        double now = level.getGameTime() + event.getPartialTick();
        float dp = 0, dy = 0, dr = 0;
        double tk = now - kickAt;
        if (kick >= 0.01F && tk >= 0 && tk < 40) {
            double e = kick * Math.exp(-tk / 4.0) * Math.sin(tk * 1.15);
            dp += (float) (e * 6.0); dr += (float) (e * 1.2);
        }
        if (sway >= 0.01F) {
            float a = sway * 1.5F;
            dp += a * (float) (0.6 * Math.sin(now * 1.7) + 0.4 * Math.sin(now * 4.3 + 1.3));
            dy += a * 0.6F * (float) Math.sin(now * 1.2 + 0.5);
            dr += a * 0.9F * (float) (0.7 * Math.sin(now * 0.9) + 0.3 * Math.sin(now * 3.1 + 2.0));
        }
        if (tremble >= 0.05F) {
            RandomSource rnd = RandomSource.create();
            dp += (rnd.nextFloat() - 0.5F) * tremble * 1.6F;
            dy += (rnd.nextFloat() - 0.5F) * tremble * 1.2F;
            dr += (rnd.nextFloat() - 0.5F) * tremble * 0.9F;
        }
        event.setPitch(event.getPitch() + dp);
        event.setYaw(event.getYaw() + dy);
        event.setRoll(event.getRoll() + dr);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientBlastGeo.beginFrame();

        BufferBuilder marks = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (Math.hypot(cam.x - s.x, cam.z - s.z) > ClientBlastGeo.FAR_VIEW + R) continue;
            any |= cracks(marks, cam, s, now - s.start);
            if (s.cStart >= 0) any |= holes(marks, cam, s, now - s.cStart);
        }
        ClientBlastGeo.draw(event, marks, any, false);

        BufferBuilder dust = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Show s : SHOWS) {
            if (Math.hypot(cam.x - s.x, cam.z - s.z) > ClientBlastGeo.FAR_VIEW + R) continue;
            double age = now - s.start;
            any |= rings(dust, cam, left, up, s, age);
            any |= puffs(dust, cam, left, up, s, age);
            any |= curtainDust(dust, cam, left, up, s, age);
            if (s.cStart >= 0) any |= sinkDust(dust, cam, left, up, s, now - s.cStart);
        }
        ClientBlastGeo.draw(event, dust, any, false);
    }

    private static boolean far(Vec3 cam, double x, double z) {
        double dx = x - cam.x, dz = z - cam.z;
        return dx * dx + dz * dz > ClientBlastGeo.FAR_VIEW * ClientBlastGeo.FAR_VIEW;
    }

    private static boolean cracks(BufferBuilder bb, Vec3 cam, Show s, double age) {
        if (!s.quake || s.cracks == null || age < 2 || age > LIFE) return false;
        double tip = crackTip(s, age - 2);
        float A = 0.85F * (float) Math.min(1.0, (LIFE - age) / 300.0);
        boolean any = false;
        for (Crack c : s.cracks) {
            if (c.s[0] >= tip) continue;
            for (int i = 0; i + 1 < c.x.length; i++) {
                if (c.s[i] >= tip) break;
                if (far(cam, c.x[i], c.z[i])) continue;
                double f = Math.min(1.0, (tip - c.s[i]) / (c.s[i + 1] - c.s[i]));
                double bx = c.x[i] + (c.x[i + 1] - c.x[i]) * f, bz = c.z[i] + (c.z[i + 1] - c.z[i]) * f, bg = c.g[i] + (c.g[i + 1] - c.g[i]) * f;
                double sb = c.s[i] + (c.s[i + 1] - c.s[i]) * f, wb = c.w[i] + (c.w[i + 1] - c.w[i]) * f;
                double dx = bx - c.x[i], dz = bz - c.z[i], l = Math.hypot(dx, dz);
                if (l < 1e-4) continue;
                double nx = -dz / l, nz = dx / l;
                double wa = c.w[i] * Math.min(1.0, 0.3 + (tip - c.s[i]) / 12.0), ww = wb * Math.min(1.0, 0.3 + (tip - sb) / 12.0);
                double ya = c.g[i] + 0.08, yb = bg + 0.08;
                ClientBlastGeo.v(bb, cam, c.x[i] - nx * wa, ya, c.z[i] - nz * wa, 0.08F, 0.065F, 0.05F, A);
                ClientBlastGeo.v(bb, cam, c.x[i] + nx * wa, ya, c.z[i] + nz * wa, 0.08F, 0.065F, 0.05F, A);
                ClientBlastGeo.v(bb, cam, bx + nx * ww, yb, bz + nz * ww, 0.08F, 0.065F, 0.05F, A);
                ClientBlastGeo.v(bb, cam, bx - nx * ww, yb, bz - nz * ww, 0.08F, 0.065F, 0.05F, A);
                any = true;
            }
        }
        return any;
    }

    private static boolean holes(BufferBuilder bb, Vec3 cam, Show s, double ac) {
        if (s.kt == null || ac < 0 || ac > COLLAPSE_LIFE) return false;
        float out = (float) Math.min(1.0, (COLLAPSE_LIFE - ac) / 200.0);
        boolean any = false;
        for (int i = 0; i < s.kt.length; i++) {
            double t = ac - s.kt[i];
            if (t < 0 || far(cam, s.kx[i], s.kz[i])) continue;
            double rad = s.ks[i] * Math.min(1.0, t / 5.0);
            flat(bb, cam, s.kx[i], s.kg[i] + 0.1, s.kz[i], rad, 0.06F, 0.05F, 0.04F, 0.85F * out, 10);
            any = true;
        }
        return any;
    }

    private static void flat(BufferBuilder bb, Vec3 cam, double x, double y, double z, double rad, float r, float g, float b, float a, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            ClientBlastGeo.v(bb, cam, x, y, z, r, g, b, a);
            ClientBlastGeo.v(bb, cam, x, y, z, r, g, b, a);
            ClientBlastGeo.v(bb, cam, x + Math.cos(a1) * rad, y, z + Math.sin(a1) * rad, r, g, b, 0.0F);
            ClientBlastGeo.v(bb, cam, x + Math.cos(a0) * rad, y, z + Math.sin(a0) * rad, r, g, b, 0.0F);
        }
    }

    private static boolean rings(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (!s.quake || s.polarH == null) return false;
        boolean any = false;
        for (int i = 0; i < RINGS; i++) {
            double rr = ringR(i, age);
            if (rr < 2.0) continue;
            double t = age - 2 - i * RING_GAP, u = t / RING_TICKS;
            float weak = 1.0F - 0.13F * i;
            float k = (float) (0.55 * Math.pow(1.0 - u, 0.7) * Math.min(1.0, t / 4.0)) * weak;
            double h = (2.5 + 3.5 * (1.0 - u)) * weak, hc = h * 0.35, rb = Math.max(0.5, rr - (7.0 + 5.0 * u));
            double px = 0, pz = 0, pg = 0, qx = 0, qz = 0, qg = 0;
            for (int j = 0; j <= RING_SEG; j++) {
                double a = Math.PI * 2 * j / RING_SEG, ca = Math.cos(a), sa = Math.sin(a);
                double x = s.x + ca * rr, z = s.z + sa * rr, g = polar(s, a, rr);
                double bx = s.x + ca * rb, bz = s.z + sa * rb, bg = polar(s, a, rb);
                if (j > 0) {

                    ClientBlastGeo.v(bb, cam, qx, qg + 0.3, qz, 0.53F, 0.47F, 0.39F, 0.0F); ClientBlastGeo.v(bb, cam, bx, bg + 0.3, bz, 0.53F, 0.47F, 0.39F, 0.0F);
                    ClientBlastGeo.v(bb, cam, x, g + hc, z, 0.56F, 0.5F, 0.42F, k * 0.7F); ClientBlastGeo.v(bb, cam, px, pg + hc, pz, 0.56F, 0.5F, 0.42F, k * 0.7F);

                    ClientBlastGeo.v(bb, cam, px, pg - 0.3, pz, 0.53F, 0.47F, 0.39F, k); ClientBlastGeo.v(bb, cam, x, g - 0.3, z, 0.53F, 0.47F, 0.39F, k);
                    ClientBlastGeo.v(bb, cam, x, g + h, z, 0.64F, 0.59F, 0.52F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + h, pz, 0.64F, 0.59F, 0.52F, 0.0F);
                }
                px = x; pz = z; pg = g; qx = bx; qz = bz; qg = bg;
            }
            for (int j = 0; j < s.ra.length; j++) {
                double a = s.ra[j] + i * 0.37, d = rr - 1.5;
                double x = s.x + Math.cos(a) * d, z = s.z + Math.sin(a) * d;
                if (far(cam, x, z)) continue;
                double rad = (2.0 + 2.0 * s.rs[j]) * (1.0 - 0.4 * u) * weak;
                float c = 0.5F + 0.08F * (float) s.rs[j];
                ClientBlastGeo.disc(bb, cam, left, up, x, polar(s, a, d) + rad * 0.5, z, rad, c * 1.08F, c * 0.97F, c * 0.82F, k * 0.6F, 8);
            }
            any = true;
        }
        return any;
    }

    private static boolean puffs(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (s.pt == null || age < 2 || age > CRACK_TICKS + PUFF_LIFE + 10) return false;
        boolean any = false;
        for (int p = 0; p < s.pt.length; p++) {
            double t = age - s.pt[p];
            if (t < 0 || t > PUFF_LIFE || far(cam, s.px[p], s.pz[p])) continue;
            float a = (float) (0.5 * Math.pow(1.0 - t / PUFF_LIFE, 1.3) * Math.min(1.0, t / 1.5));
            float c = 0.36F + 0.015F * (p % 5);
            double rise = 1.0 - Math.exp(-t / 3.5);
            for (int q = 0; q < 3; q++) {
                double fr = (q + 1) / 3.0, hh = s.ph[p] * fr * rise + t * 0.03, rad = (0.7 + 0.5 * q) * (1.0 + t * 0.035);
                ClientBlastGeo.disc(bb, cam, left, up, s.px[p] + t * 0.01 * q, s.pg[p] + 0.3 + hh, s.pz[p], rad, c * 1.12F, c * 0.95F, c * 0.76F, a * (1.0F - 0.15F * q), 8);
            }
            any = true;
        }
        return any;
    }

    private static boolean curtainDust(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (s.ct == null || age < 2 || age > LIFE) return false;
        float out = (float) Math.min(1.0, (LIFE - age) / 400.0);
        double u = age / LIFE;
        boolean any = false;
        for (int i = 0; i < s.ct.length; i++) {
            double t = age - s.ct[i];
            if (t < 0) continue;
            double x = s.cx[i] + t * 0.008, z = s.cz[i];
            if (far(cam, x, z)) continue;
            double cs = s.cs[i], rise = 1.0 - Math.exp(-t / 25.0);
            double rad = (2.5 + (2.5 + 6.0 * cs) * rise) * (1.0 + 0.3 * u);
            double y = s.cg[i] + 1.0 + (2.5 + 5.5 * cs) * rise + Math.sin(age * 0.02 + i) * 0.3;
            float c = 0.44F + 0.08F * (float) cs;
            float a = (float) (0.3 * Math.min(1.0, t / 30.0)) * out;
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, c * 1.08F, c * 0.98F, c * 0.86F, a, 10);
            float lit = Math.min(1.0F, c + 0.1F);
            ClientBlastGeo.disc(bb, cam, left, up, x + up.x() * rad * 0.2, y + up.y() * rad * 0.2, z + up.z() * rad * 0.2, rad * 0.6, lit * 1.05F, lit * 0.97F, lit * 0.86F, a * 0.45F, 8);
            any = true;
        }
        return any;
    }

    private static boolean sinkDust(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double ac) {
        if (s.kt == null || ac < 0 || ac > COLLAPSE_LIFE) return false;
        boolean any = false;
        for (int i = 0; i < s.kt.length; i++) {
            double t = ac - s.kt[i];
            if (t < 0 || far(cam, s.kx[i], s.kz[i])) continue;
            double x0 = s.kx[i], z0 = s.kz[i], g = s.kg[i], ks = s.ks[i];
            if (t < 110) {
                float fade = (float) (Math.pow(1.0 - t / 110.0, 1.3) * Math.min(1.0, t / 1.5));
                double rise = 1.0 - Math.exp(-t / 4.0);
                for (int q = 0; q < 6; q++) {
                    double hf = (q + 1) / 6.0;
                    double y = g + 0.5 + s.kh[i] * hf * rise - Math.max(0.0, t - 30) * 0.02 * hf;
                    double ox = Math.sin(q * 2.1 + i) * ks * 0.4 * hf, oz = Math.cos(q * 1.7 + i * 0.6) * ks * 0.4 * hf;
                    double rad = ks * (0.7 + 0.6 * hf) * (0.6 + 0.4 * rise) + t * 0.03;
                    float c = 0.30F + 0.06F * (float) hf;
                    ClientBlastGeo.disc(bb, cam, left, up, x0 + ox + t * 0.01, y, z0 + oz, rad, c * 1.15F, c * 0.93F, c * 0.7F, 0.55F * fade, 10);
                }
            }
            if (t < 32) {
                for (int j = 0; j < RUB; j++) {
                    double[] rb = s.rub[i * RUB + j];
                    if (t > rb[4]) continue;
                    double f = t / rb[4], fq = Math.max(0.0, t - 1.5) / rb[4];
                    double x = x0 + rb[0] * rb[2] * f, z = z0 + rb[1] * rb[2] * f, y = g + 0.5 + rb[3] * 4.0 * f * (1.0 - f) - 2.0 * f;
                    double xq = x0 + rb[0] * rb[2] * fq, zq = z0 + rb[1] * rb[2] * fq, yq = g + 0.5 + rb[3] * 4.0 * fq * (1.0 - fq) - 2.0 * fq;
                    ClientBlastGeo.disc(bb, cam, left, up, xq, yq, zq, rb[5] * 1.7, 0.45F, 0.38F, 0.29F, 0.22F, 6);
                    ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rb[5], 0.2F, 0.16F, 0.12F, 0.9F, 6);
                }
            }
            if (t > 20) {
                double k = Math.min(1.0, (t - 20) / 200.0);
                float a = (float) (0.25 * Math.min(1.0, (t - 20) / 30.0) * Math.min(1.0, (COLLAPSE_LIFE - ac) / 200.0));
                for (int q = 0; q < 2; q++) {
                    double rad = ks * (1.5 + 1.5 * Math.sqrt(k)) * (1.0 + 0.3 * q);
                    double ang = i * 2.4 + q * 3.1, off = ks * 0.8 * q;
                    float c = 0.42F + 0.04F * q;
                    ClientBlastGeo.disc(bb, cam, left, up, x0 + Math.cos(ang) * off + t * 0.008, g + rad * 0.4, z0 + Math.sin(ang) * off, rad, c * 1.1F, c * 0.96F, c * 0.8F, a, 8);
                }
            }
            any = true;
        }
        return any;
    }
}
