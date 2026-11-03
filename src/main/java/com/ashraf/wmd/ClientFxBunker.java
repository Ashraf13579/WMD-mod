package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Arrays;
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
public final class ClientFxBunker {

    private ClientFxBunker() {}

    private static final int ENTRY_LIFE = 220, DEEP_LIFE = 430;
    private static final int HA = 40, HR = 9;

    private static final class Entry {
        final Vec3 pos;
        final double r, colH, w;
        final long start, seed;

        double[] cUx, cUz, cD, cT, cG, cH, cS;

        double[] sx, sz, sg;
        Entry(Vec3 pos, double r, long start) {
            this.pos = pos; this.r = r; this.start = start;
            this.seed = Double.doubleToLongBits(pos.x * 37 + pos.z) ^ start;
            this.colH = columnHeight(r);
            this.w = Math.max(1.8, Math.min(5.0, r * 0.22));
        }
    }

    private static final class Deep {
        final Vec3 pos;
        final double r, R, sc, reach, plumeH;
        final int rippleTicks;
        final long start, seed;
        double rim, bottom;
        float[][] hv;
        double[][] jets;
        double[] hx, hz, hg, hs;
        double camSide = Double.NaN;
        Deep(Vec3 pos, double r, long start) {
            this.pos = pos; this.r = r; this.start = start;
            this.seed = Double.doubleToLongBits(pos.x * 41 + pos.z) ^ start;
            this.R = r * 1.9;
            this.sc = Math.max(0.7, Math.min(1.6, r / 14.0));
            this.reach = Math.max(64.0, r * 6.0);
            this.rippleTicks = (int) Math.round(20 + reach / 4.0);
            this.plumeH = Math.max(20.0, Math.min(45.0, r * 2.2));
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final List<Deep> DEEPS = new ArrayList<>();
    private static float tremble;

    public static double columnHeight(double r) { return Math.max(15.0, Math.min(25.0, r * 1.4)); }

    private static double density() { return ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0; }

    public static void entry(double x, double y, double z, int tier, double r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entry e = new Entry(new Vec3(x, y, z), r, level.getGameTime());
        double den = density();
        int n = (int) Math.max(24, 70 * den), m = (int) Math.max(10, 20 * den);
        RandomSource rnd = RandomSource.create(e.seed ^ 0xC10DL);
        e.cUx = new double[n]; e.cUz = new double[n]; e.cD = new double[n]; e.cT = new double[n]; e.cG = new double[n]; e.cH = new double[n]; e.cS = new double[n];
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.9 + 1.1 * Math.sqrt(rnd.nextDouble())), h = 4.0 + 12.0 * rnd.nextDouble() * (0.4 + 0.6 * rnd.nextDouble());
            e.cUx[i] = Math.cos(a); e.cUz[i] = Math.sin(a); e.cD[i] = d; e.cH[i] = h;
            e.cT[i] = Math.max(14.0, Math.min(44.0, 2.0 * Math.sqrt(2.0 * h / 0.09)));
            e.cS[i] = 0.25 + 0.3 * rnd.nextDouble();
            e.cG[i] = ClientBlastGeo.ground(level, x + e.cUx[i] * d, z + e.cUz[i] * d, y);
        }
        e.sx = new double[m]; e.sz = new double[m]; e.sg = new double[m];
        for (int i = 0; i < m; i++) {
            double a = Math.PI * 2 * (i + rnd.nextDouble() * 0.7) / m, d = r * (0.55 + 0.6 * rnd.nextDouble());
            e.sx[i] = x + Math.cos(a) * d; e.sz[i] = z + Math.sin(a) * d;
            e.sg[i] = Math.max(y - 2.0, ClientBlastGeo.ground(level, e.sx[i], e.sz[i], y));
        }
        ENTRIES.add(e);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dc = cam.distanceTo(e.pos);
        if (dc < r * 4) tremble = Math.max(tremble, (float) (1.0 - dc / (r * 4)) * 1.1F);
    }

    public static void deep(double x, double y, double z, int tier, double r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Deep dp = new Deep(new Vec3(x, y, z), r, level.getGameTime());
        double den = density();
        double[] ring = new double[16];
        for (int i = 0; i < 16; i++) { double a = Math.PI * 2 * i / 16; ring[i] = ClientBlastGeo.ground(level, x + Math.cos(a) * r * 1.3, z + Math.sin(a) * r * 1.3, y); }
        Arrays.sort(ring);
        dp.rim = Math.max(y, (ring[7] + ring[8]) * 0.5);
        dp.bottom = Math.min(y, dp.rim);
        double inner = r * 1.15;
        dp.hv = new float[HA][HR];
        for (int i = 0; i < HA; i++) {
            double a = Math.PI * 2 * i / HA;
            for (int j = 0; j < HR; j++) {
                double d = dp.R * j / (HR - 1);
                dp.hv[i][j] = (float) (d < inner ? dp.rim : ClientBlastGeo.ground(level, x + Math.cos(a) * d, z + Math.sin(a) * d, dp.rim));
            }
        }
        RandomSource rnd = RandomSource.create(dp.seed ^ 0xB0B0L);
        List<double[]> jets = new ArrayList<>();
        int cap = (int) Math.max(60, 220 * den);
        double far = dp.R * 1.1;
        for (double fr : new double[] { 0.62, 0.78, 0.92, 1.06 }) {
            double d0 = dp.R * fr;
            int k = (int) Math.max(6, 2 * Math.PI * d0 * den / 3.2);
            double off = rnd.nextDouble() * Math.PI * 2;
            for (int i = 0; i < k; i++) {
                double a = off + Math.PI * 2 * (i + rnd.nextDouble() * 0.6) / k, d = d0 + rnd.nextGaussian() * 0.8, h = (3.0 + 5.0 * rnd.nextDouble()) * dp.sc;
                double tj = 3.0 + 12.0 * (d - r) / (far - r) + rnd.nextDouble() * 3.0, tone = rnd.nextDouble();
                double jx = x + Math.cos(a) * d, jz = z + Math.sin(a) * d;
                jets.add(new double[] { jx, jz, ClientBlastGeo.ground(level, jx, jz, dp.rim), tj, h, tone });
            }
        }
        for (int l = 0; l < 7; l++) {
            double a0 = Math.PI * 2 * (l + rnd.nextDouble() * 0.6) / 7;
            for (double d = r * 1.1; d < dp.R * 1.15; d += 2.2) {
                double a = a0 + rnd.nextGaussian() * 0.6 / d, h = (2.5 + 4.0 * rnd.nextDouble()) * dp.sc, tone = rnd.nextDouble();
                double tj = 2.0 + 11.0 * (d - r) / (far - r) + rnd.nextDouble() * 1.5;
                double jx = x + Math.cos(a) * d, jz = z + Math.sin(a) * d;
                jets.add(new double[] { jx, jz, ClientBlastGeo.ground(level, jx, jz, dp.rim), tj, h, tone });
            }
        }
        if (jets.size() > cap) {
            List<double[]> kept = new ArrayList<>();
            for (int i = 0; i < cap; i++) kept.add(jets.get((int) ((long) i * jets.size() / cap)));
            jets = kept;
        }
        dp.jets = jets.toArray(new double[0][]);
        int hn = (int) Math.max(20, 50 * den);
        dp.hx = new double[hn]; dp.hz = new double[hn]; dp.hg = new double[hn]; dp.hs = new double[hn];
        for (int i = 0; i < hn; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = dp.R * 1.35 * Math.sqrt(rnd.nextDouble());
            dp.hx[i] = x + Math.cos(a) * d; dp.hz[i] = z + Math.sin(a) * d;
            dp.hg[i] = d < inner ? dp.rim : ClientBlastGeo.ground(level, dp.hx[i], dp.hz[i], dp.rim);
            dp.hs[i] = rnd.nextDouble();
        }
        DEEPS.add(dp);
    }

    private static double heaveEnv(double age) {
        if (age < 0) return 0.0;
        if (age < 8) return Math.sin(Math.PI * 0.5 * age / 8.0);
        return Math.exp(-(age - 8) / 18.0) * Math.cos(0.075 * (age - 8));
    }

    private static double rippleR(Deep dp, double age) {
        double u = (age - 2) / dp.rippleTicks;
        if (u < 0 || u > 1) return -1.0;
        return dp.reach * (1.0 - Math.pow(1.0 - u, 1.4));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { ENTRIES.clear(); DEEPS.clear(); tremble = 0; return; }
        long now = level.getGameTime();
        tremble *= 0.85F;
        ENTRIES.removeIf(e -> now - e.start > ENTRY_LIFE + 10 || now < e.start - 100);
        DEEPS.removeIf(d -> now - d.start > DEEP_LIFE + 10 || now < d.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Deep dp : DEEPS) {
            double age = now - dp.start, dh = Math.hypot(cam.x - dp.pos.x, cam.z - dp.pos.z);
            if (age < 70) {
                double k = Math.max(0.0, 1.0 - dh / (dp.R * 4.0));
                tremble = Math.max(tremble, (float) (1.3 * k * k * Math.abs(heaveEnv(age))));
            }
            double rr = rippleR(dp, age);
            if (rr < 0) { dp.camSide = Double.NaN; continue; }
            double side = dh - rr;
            if (!Double.isNaN(dp.camSide) && dp.camSide > 0 && side <= 0) tremble = Math.max(tremble, (float) (0.5 + 1.3 * (1.0 - rr / dp.reach)));
            dp.camSide = side;
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (tremble < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * tremble * 1.2F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * tremble * 1.2F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * tremble * 0.9F);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (ENTRIES.isEmpty() && DEEPS.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientBlastGeo.beginFrame();
        BufferBuilder dust = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Entry e : ENTRIES) {
            if (cam.distanceTo(e.pos) > ClientBlastGeo.FAR_VIEW) continue;
            any |= entryDust(dust, cam, left, up, e, now - e.start);
        }
        for (Deep dp : DEEPS) {
            if (cam.distanceTo(dp.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - dp.start;
            any |= heave(dust, cam, left, up, dp, age);
            any |= jets(dust, cam, left, up, dp, age, false);
            any |= plume(dust, cam, left, up, dp, age);
            any |= ripple(dust, level, cam, dp, age);
            any |= haze(dust, cam, left, up, dp, age);
        }
        ClientBlastGeo.draw(event, dust, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Entry e : ENTRIES) {
            if (cam.distanceTo(e.pos) > ClientBlastGeo.FAR_VIEW) continue;
            any |= smack(glow, cam, left, up, e, now - e.start);
        }
        for (Deep dp : DEEPS) {
            if (cam.distanceTo(dp.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - dp.start;
            any |= holeGlow(glow, cam, left, up, dp, age);
            any |= jets(glow, cam, left, up, dp, age, true);
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static boolean smack(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Entry e, double age) {
        if (age < 0 || age > 4) return false;
        float k = (float) (1.0 - age / 4.0);
        ClientBlastGeo.disc(bb, cam, left, up, e.pos.x, e.pos.y + 1.0, e.pos.z, e.w * 2.2, 1.0F, 0.5F, 0.2F, 0.4F * k);
        ClientBlastGeo.disc(bb, cam, left, up, e.pos.x, e.pos.y + 0.8, e.pos.z, e.w, 1.0F, 0.72F, 0.42F, 0.55F * k);
        return true;
    }

    private static boolean entryDust(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Entry e, double age) {
        if (age < 0 || age > ENTRY_LIFE) return false;
        double x0 = e.pos.x, y0 = e.pos.y, z0 = e.pos.z;
        RandomSource rnd = RandomSource.create(e.seed);
        double life = 90, rise = 1.0 - Math.exp(-age / 3.5);
        if (age < life) {
            float fade = (float) (Math.pow(1.0 - age / life, 1.2) * Math.min(1.0, age / 1.5));
            int n = 36;
            for (int i = 0; i < n; i++) {
                double hf = (i + rnd.nextDouble()) / n, gx = rnd.nextGaussian(), gz = rnd.nextGaussian(), sz = rnd.nextDouble(), tone = rnd.nextDouble();
                double h = e.colH * hf * rise - Math.max(0.0, age - 12) * 0.06 * hf;
                double spread = e.w * (0.25 + 0.5 * hf) * (1.0 + Math.max(0.0, age - 6) * 0.02);
                double x = x0 + gx * spread, z = z0 + gz * spread, y = y0 + 0.5 + h;
                double rad = e.w * (0.55 + 0.5 * hf + 0.3 * sz) * (0.5 + 0.5 * rise) + Math.max(0.0, age - 8) * 0.04;
                float c = 0.30F + 0.08F * (float) tone + 0.06F * (float) hf;
                ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, c * 1.15F, c * 0.92F, c * 0.68F, 0.62F * fade, 10);
                float lit = Math.min(1.0F, c + 0.14F);
                ClientBlastGeo.disc(bb, cam, left, up, x + up.x() * rad * 0.2, y + up.y() * rad * 0.2, z + up.z() * rad * 0.2, rad * 0.6, lit * 1.1F, lit * 0.95F, lit * 0.78F, 0.3F * fade, 8);
            }
        }
        if (e.cT != null && age < 70) {
            for (int i = 0; i < e.cT.length; i++) {
                double T = e.cT[i];
                if (age <= T) {
                    double[] p = clodAt(e, i, age), q = clodAt(e, i, Math.max(0.0, age - 1.5));
                    ClientBlastGeo.disc(bb, cam, left, up, q[0], q[1], q[2], e.cS[i] * 1.7, 0.45F, 0.38F, 0.29F, 0.22F, 6);
                    ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], e.cS[i], 0.21F, 0.16F, 0.11F, 0.9F, 6);
                } else {
                    double k = (age - T) / 20.0;
                    if (k > 1) continue;
                    double x = x0 + e.cUx[i] * e.cD[i], z = z0 + e.cUz[i] * e.cD[i], rad = (0.4 + 1.5 * Math.sqrt(k)) * e.cS[i] / 0.4;
                    ClientBlastGeo.disc(bb, cam, left, up, x, e.cG[i] + 0.3 + k * 1.4, z, rad, 0.5F, 0.42F, 0.33F, (float) (0.55 * (1 - k)), 8);
                }
            }
        }
        if (age > 6 && age < 150) {
            double k = (age - 6) / 144.0;
            float a = (float) (0.32 * Math.min(1.0, (age - 6) / 10.0) * Math.pow(1.0 - k, 1.3));
            double sc = Math.max(0.7, Math.min(1.6, e.r / 14.0));
            for (int i = 0; i < e.sx.length; i++) {
                double rad = (1.5 + 2.0 * Math.sqrt(k)) * sc * (0.85 + 0.3 * ((i * 7) % 5) / 4.0);
                float c = 0.42F + 0.05F * ((i * 3) % 4) / 3.0F;
                ClientBlastGeo.disc(bb, cam, left, up, e.sx[i] + age * 0.01, e.sg[i] + 0.6 + rad * 0.35, e.sz[i], rad, c * 1.1F, c * 0.96F, c * 0.8F, a, 8);
            }
        }
        if (age > 25 && age < 200) {
            float env = (float) (Math.min(1.0, (age - 25) / 15.0) * Math.min(1.0, (200 - age) / 40.0));
            for (int j = 0; j < 10; j++) {
                double tt = (age - 25 + j * 6.0) % 60.0;
                double x = x0 + tt * 0.03 + Math.sin(tt * 0.12 + j) * 0.3, z = z0 + Math.cos(tt * 0.1 + j * 2) * 0.3, y = y0 + 0.5 + tt * 0.15;
                ClientBlastGeo.disc(bb, cam, left, up, x, y, z, 0.4 + tt * 0.025, 0.66F, 0.60F, 0.52F, (float) (0.28 * (1.0 - tt / 60.0)) * env, 8);
            }
        }
        return true;
    }

    private static double[] clodAt(Entry e, int i, double t) {
        double f = Math.max(0.0, Math.min(1.0, t / e.cT[i])), d = e.cD[i] * f, y0 = e.pos.y + 1.0;
        return new double[] { e.pos.x + e.cUx[i] * d, y0 + e.cH[i] * 4.0 * f * (1.0 - f) + (e.cG[i] + 0.3 - y0) * f, e.pos.z + e.cUz[i] * d };
    }

    private static boolean heave(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Deep dp, double age) {
        if (age < 0 || age > 90) return false;
        double env = heaveEnv(age), A = Math.min(7.0, 1.5 + dp.r * 0.15);
        double lift0 = A * Math.max(-0.3, env);
        float sheetA = (float) (Math.min(1.0, age / 2.0) * Math.max(0.0, Math.min(1.0, 1.0 - (age - 10) / 70.0)));
        double grow = 0.75 + 0.25 * Math.min(1.0, age / 8.0);
        if (sheetA > 0.01) {
            for (int i = 0; i < HA; i++) {
                int i1 = (i + 1) % HA;
                double a0 = Math.PI * 2 * i / HA, a1 = Math.PI * 2 * (i + 1) / HA;
                for (int j = 0; j < HR - 1; j++) {
                    double f0 = j / (double) (HR - 1), f1 = (j + 1) / (double) (HR - 1);
                    double d0 = dp.R * f0 * grow, d1 = dp.R * f1 * grow;
                    heaveV(bb, cam, dp, a0, d0, f0, dp.hv[i][j], lift0, sheetA);
                    heaveV(bb, cam, dp, a1, d0, f0, dp.hv[i1][j], lift0, sheetA);
                    heaveV(bb, cam, dp, a1, d1, f1, dp.hv[i1][j + 1], lift0, sheetA);
                    heaveV(bb, cam, dp, a0, d1, f1, dp.hv[i][j + 1], lift0, sheetA);
                }
            }
        }
        RandomSource rnd = RandomSource.create(dp.seed ^ 0x4EA7L);
        int n = (int) Math.max(14, 36 * density());
        float fade = (float) (Math.min(1.0, age / 3.0) * Math.max(0.0, 1.0 - age / 90.0));
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, f = Math.sqrt(rnd.nextDouble()) * 0.95, sz = rnd.nextDouble(), tone = rnd.nextDouble();
            double d = dp.R * f * grow;
            int i = (int) Math.round(a / (Math.PI * 2) * HA) % HA, j = (int) Math.min(HR - 1, Math.round(f * (HR - 1)));
            double c2 = Math.cos(Math.PI * 0.5 * f), lift = lift0 * c2 * c2;
            double rad = (1.5 + dp.r * 0.1) * (0.7 + 0.6 * sz) * (1.0 + 0.3 * Math.min(1.0, age / 30.0));
            double x = dp.pos.x + Math.cos(a) * d + age * 0.01, z = dp.pos.z + Math.sin(a) * d, y = dp.hv[i][j] + Math.max(0.0, lift) + rad * 0.4;
            float c = 0.33F + 0.08F * (float) tone;
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, c * 1.15F, c * 0.93F, c * 0.7F, 0.42F * fade * (float) (1.0 - 0.5 * f), 8);
            float lit = Math.min(1.0F, c + 0.13F);
            ClientBlastGeo.disc(bb, cam, left, up, x + up.x() * rad * 0.2, y + up.y() * rad * 0.2, z + up.z() * rad * 0.2, rad * 0.6, lit * 1.1F, lit * 0.95F, lit * 0.78F, 0.22F * fade, 8);
        }
        return true;
    }

    private static void heaveV(BufferBuilder bb, Vec3 cam, Deep dp, double a, double d, double f, double g, double lift0, float sheetA) {
        double c2 = Math.cos(Math.PI * 0.5 * f), lift = lift0 * c2 * c2;
        float c = 0.33F + 0.1F * (float) Math.max(0.0, lift / Math.max(1.0, Math.abs(lift0)));
        float a0 = sheetA * 0.5F * (float) (1.0 - f * f);
        ClientBlastGeo.v(bb, cam, dp.pos.x + Math.cos(a) * d, g + 0.4 + lift, dp.pos.z + Math.sin(a) * d, c * 1.15F, c * 0.93F, c * 0.7F, a0);
    }

    private static boolean jets(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Deep dp, double age, boolean glow) {
        if (dp.jets == null || age < 0 || age > 90) return false;
        boolean any = false;
        for (double[] jt : dp.jets) {
            double t = age - jt[3];
            if (t < 0 || t > 60) continue;
            if (glow) {
                if (t > 4) continue;
                ClientBlastGeo.disc(bb, cam, left, up, jt[0], jt[2] + 0.4, jt[1], 0.7 * dp.sc, 1.0F, 0.5F, 0.18F, (float) (0.35 * (1.0 - t / 4.0)), 6);
            } else {
                float a = (float) (0.55 * Math.pow(1.0 - t / 60.0, 1.4) * Math.min(1.0, t / 1.5));
                float c = 0.32F + 0.1F * (float) jt[5];
                double rise = 1.0 - Math.exp(-t / 3.5);
                for (int k = 0; k < 4; k++) {
                    double fr = (k + 1) / 4.0, hh = jt[4] * fr * rise + t * 0.03;
                    double rad = (0.5 + 0.35 * k) * (1.0 + t * 0.03) * dp.sc;
                    ClientBlastGeo.disc(bb, cam, left, up, jt[0] + t * 0.01 * k, jt[2] + 0.3 + hh, jt[1], rad, c * 1.15F, c * 0.93F, c * 0.7F, a * (1.0F - 0.15F * k), 8);
                }
            }
            any = true;
        }
        return any;
    }

    private static boolean plume(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Deep dp, double age) {
        if (age < 0 || age > 260) return false;
        RandomSource rnd = RandomSource.create(dp.seed ^ 0x9F1L);
        double rise = 1.0 - Math.exp(-age / 6.0), top = dp.rim + dp.plumeH * rise;
        float fade = (float) (Math.min(1.0, age / 2.0) * (age > 150 ? Math.max(0.0, 1.0 - (age - 150) / 110.0) : 1.0));
        float fire = (float) Math.max(0.0, 1.0 - age / 25.0);
        int n = 30;
        for (int i = 0; i < n; i++) {
            double hf = (i + rnd.nextDouble()) / n, a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble(), sz = rnd.nextDouble(), tone = rnd.nextDouble();
            double y = dp.bottom + (top - dp.bottom) * Math.pow(hf, 0.8) - Math.max(0.0, age - 40) * 0.015 * hf;
            double spread = (1.0 + dp.r * 0.08) * (0.3 + 1.2 * hf * hf) * (0.4 + 0.6 * rise) + Math.max(0.0, age - 20) * 0.03;
            double x = dp.pos.x + Math.cos(a) * spread * rr + age * 0.015 * hf, z = dp.pos.z + Math.sin(a) * spread * rr;
            double rad = (1.4 + dp.r * 0.11) * (0.6 + 0.7 * hf + 0.3 * sz) * (0.5 + 0.5 * rise) + age * 0.015;
            float c = 0.24F + 0.1F * (float) tone + 0.08F * (float) hf;
            float gl = fire * (float) (1.0 - hf) * 0.8F;
            float r = c * 1.15F + (0.85F - c * 1.15F) * gl, g = c * 0.92F + (0.38F - c * 0.92F) * gl, b = c * 0.68F + (0.12F - c * 0.68F) * gl;
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, r, g, b, 0.62F * fade, 10);
            float lit = Math.min(1.0F, c + 0.13F);
            ClientBlastGeo.disc(bb, cam, left, up, x + up.x() * rad * 0.22, y + up.y() * rad * 0.22, z + up.z() * rad * 0.22, rad * 0.6, lit * 1.1F, lit * 0.95F, lit * 0.78F, 0.3F * fade, 8);
        }
        return true;
    }

    private static boolean ripple(BufferBuilder bb, ClientLevel level, Vec3 cam, Deep dp, double age) {
        double rr = rippleR(dp, age);
        if (rr < 1.0) return false;
        double u = (age - 2) / dp.rippleTicks;
        float k = (float) (0.5 * Math.pow(1.0 - u, 0.8) * Math.min(1.0, (age - 2) / 3.0));
        double h = (1.0 + dp.r * 0.06) * (1.0 - 0.5 * u);
        crest(bb, level, cam, dp, rr, h, k);
        double r2 = rr - (3.0 + dp.r * 0.25);
        if (r2 > 2.0) crest(bb, level, cam, dp, r2, h * 0.6, k * 0.6F);
        return true;
    }

    private static void crest(BufferBuilder bb, ClientLevel level, Vec3 cam, Deep dp, double rr, double h, float k) {
        int seg = (int) Math.min(256, Math.max(48, rr * 1.5));
        double px = 0, pz = 0, pg = 0;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg, x = dp.pos.x + Math.cos(a) * rr, z = dp.pos.z + Math.sin(a) * rr;
            double gy = ClientBlastGeo.ground(level, x, z, dp.rim);
            if (i > 0) {
                ClientBlastGeo.v(bb, cam, px, pg - 0.2, pz, 0.5F, 0.43F, 0.34F, k); ClientBlastGeo.v(bb, cam, x, gy - 0.2, z, 0.5F, 0.43F, 0.34F, k);
                ClientBlastGeo.v(bb, cam, x, gy + h, z, 0.6F, 0.54F, 0.46F, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + h, pz, 0.6F, 0.54F, 0.46F, 0.0F);
            }
            px = x; pz = z; pg = gy;
        }
    }

    private static boolean haze(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Deep dp, double age) {
        if (dp.hx == null || age < 15 || age > DEEP_LIFE) return false;
        float out = (float) Math.min(1.0, (DEEP_LIFE - age) / 150.0);
        double u = age / DEEP_LIFE;
        boolean any = false;
        for (int i = 0; i < dp.hx.length; i++) {
            double born = 15 + (i % 10) * 5;
            if (age < born) continue;
            double s = dp.hs[i];
            double rad = (3.2 + dp.r * 0.12) * (0.8 + 0.4 * s) * (1.0 + 0.3 * u);
            double y = dp.hg[i] + 1.2 + s * 2.5 + Math.sin(age * 0.02 + i) * 0.3;
            float c = 0.40F + 0.06F * (float) s;
            float a = (float) (0.3 * Math.min(1.0, (age - born) / 40.0)) * out;
            double x = dp.hx[i] + age * 0.012, z = dp.hz[i];
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, c * 1.08F, c * 0.97F, c * 0.84F, a, 10);
            float lit = Math.min(1.0F, c + 0.1F);
            ClientBlastGeo.disc(bb, cam, left, up, x + up.x() * rad * 0.2, y + up.y() * rad * 0.2, z + up.z() * rad * 0.2, rad * 0.6, lit * 1.05F, lit * 0.97F, lit * 0.86F, a * 0.45F, 8);
            any = true;
        }
        return any;
    }

    private static boolean holeGlow(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Deep dp, double age) {
        if (age < 0 || age > 70) return false;
        double k = age < 3 ? age / 3.0 : Math.exp(-(age - 3) / 18.0);
        float f = (float) (k * (0.8 + 0.2 * Math.sin(age * 0.9) * Math.sin(age * 0.37 + 1.0)));
        double x = dp.pos.x, z = dp.pos.z;
        ClientBlastGeo.disc(bb, cam, left, up, x, dp.bottom + 2.0, z, dp.r * 0.5, 1.0F, 0.6F, 0.25F, 0.7F * f);
        ClientBlastGeo.disc(bb, cam, left, up, x, dp.rim - 0.5, z, dp.r * 0.7, 1.0F, 0.45F, 0.15F, 0.45F * f);
        ClientBlastGeo.disc(bb, cam, left, up, x, dp.rim + 1.0, z, dp.r * 1.4, 0.9F, 0.35F, 0.1F, 0.18F * f);
        if (age < 20) {
            float g = (float) (1.0 - age / 20.0);
            double rise = 1.0 - Math.exp(-age / 6.0);
            for (int i = 0; i < 3; i++) ClientBlastGeo.disc(bb, cam, left, up, x, dp.rim + (2.0 + i * 4.0) * rise, z, (1.5 + dp.r * 0.12) * (1.0 + 0.3 * i), 1.0F, 0.5F, 0.18F, 0.3F * g * (1.0F - 0.25F * i), 10);
        }
        return true;
    }
}
