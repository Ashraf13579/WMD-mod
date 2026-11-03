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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFxRod {

    private ClientFxRod() {}

    private static final double G_JET = 0.25, G_DROP = 0.09;

    private static final int LIFE = 6200, SHOCK_TICKS = 120, FLASH_TICKS = 6, CROWN_TICKS = 18, SPOT_TICKS = 400, RIM_TICKS = 6000, MOUTH_TICKS = 6000, SHIMMER_TICKS = 800, RIVER_TICKS = 3600;
    private static final int ANG = 128;
    private static final float SHADE = 0.3F;

    private static final class Show {
        final Vec3 pos;
        final double y0;
        final long start, seed;
        double camSide = Double.NaN;

        float[][] polarH, polarS;
        double cell;

        double[] pL, pA, pS, pW, pU, pVt, pR, pLife, pTone;

        double[] dL, dUx, dUz, dD, dT, dV, dG, dS;

        double[] cA, cD, cS, cPh;
        Show(Vec3 pos, long start) {
            this.pos = pos; this.start = start; this.y0 = pos.y;
            this.seed = Double.doubleToLongBits(pos.x * 43 + pos.z) ^ start ^ 0x60DL;
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();
    private static float jolt, wash, dusk;

    public static void impact(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Show s = new Show(new Vec3(x, y, z), level.getGameTime());
        terrain(level, s);
        build(level, s);
        SHOWS.add(s);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(s.pos);
        if (d < ClientBlastGeo.FAR_VIEW * 2) level.setSkyFlashTime(6);
        double flashR = 260.0;
        if (d < flashR && ClientConfig.explosionScreen()) wash = Math.max(wash, (float) (1.0 - d / flashR) * 0.95F);
    }

    private static void terrain(ClientLevel level, Show s) {
        double reach = WarheadFx.ROD_SHOCK;
        s.cell = 3.0;
        int nr = (int) Math.ceil(reach / s.cell) + 2;
        double y0 = ClientBlastGeo.ground(level, s.pos.x, s.pos.z, s.y0);
        s.polarH = new float[ANG][nr]; s.polarS = new float[ANG][nr];
        for (int i = 0; i < ANG; i++) {
            double a = Math.PI * 2 * i / ANG, ca = Math.cos(a), sa = Math.sin(a), horizon = -1e9, last = y0;
            for (int j = 0; j < nr; j++) {
                double d = Math.max(0.5, j * s.cell), h = ClientBlastGeo.ground(level, s.pos.x + ca * d, s.pos.z + sa * d, last);
                last = h;
                s.polarH[i][j] = (float) h;
                s.polarS[i][j] = j < 4 || (h + 2 - (y0 + 3)) / d >= horizon ? 1.0F : SHADE;
                horizon = Math.max(horizon, (h - (y0 + 3)) / d);
            }
        }
    }

    private static float[] polar(Show s, double a, double d) {
        double fa = (a / (Math.PI * 2)) % 1.0; if (fa < 0) fa += 1.0;
        int i = (int) Math.round(fa * ANG) % ANG;
        double fj = Math.max(0.0, d / s.cell); int j = Math.min(s.polarH[i].length - 2, (int) fj); double t = Math.min(1.0, fj - j);
        return new float[] { (float) (s.polarH[i][j] * (1 - t) + s.polarH[i][j + 1] * t), (float) (s.polarS[i][j] * (1 - t) + s.polarS[i][j + 1] * t) };
    }

    private static void build(ClientLevel level, Show s) {
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        RandomSource rnd = RandomSource.create(s.seed);
        int n = (int) Math.max(60, 180 * density);
        s.pL = new double[n]; s.pA = new double[n]; s.pS = new double[n]; s.pW = new double[n]; s.pU = new double[n]; s.pVt = new double[n]; s.pR = new double[n]; s.pLife = new double[n]; s.pTone = new double[n];
        for (int i = 0; i < n; i++) {
            double q = rnd.nextDouble(), late = rnd.nextDouble();
            s.pL[i] = late < 0.15 ? 6.0 + 14.0 * q : 6.0 * q * q;
            s.pA[i] = rnd.nextDouble() * Math.PI * 2;
            s.pS[i] = rnd.nextDouble() * 3.0;
            s.pW[i] = 0.02 + rnd.nextDouble() * 0.06;
            s.pU[i] = 25.0 + 75.0 * Math.sqrt(rnd.nextDouble());
            s.pVt[i] = 0.004 + rnd.nextDouble() * 0.02;
            s.pR[i] = 2.2 + rnd.nextDouble() * 1.8;
            s.pLife[i] = 1000 + rnd.nextDouble() * 300;
            s.pTone[i] = rnd.nextDouble();
        }
        int m = (int) Math.max(90, 280 * density);
        s.dL = new double[m]; s.dUx = new double[m]; s.dUz = new double[m]; s.dD = new double[m]; s.dT = new double[m]; s.dV = new double[m]; s.dG = new double[m]; s.dS = new double[m];
        double top = s.y0 + 1.0;
        for (int i = 0; i < m; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, dd = rnd.nextDouble(), hh = rnd.nextDouble(), ss = rnd.nextDouble(), q = rnd.nextDouble();
            s.dL[i] = 5.0 * q * q;
            s.dUx[i] = Math.cos(a); s.dUz[i] = Math.sin(a);
            s.dD[i] = 35.0 + 110.0 * Math.pow(dd, 1.6);
            s.dG[i] = ClientBlastGeo.ground(level, s.pos.x + s.dUx[i] * s.dD[i], s.pos.z + s.dUz[i] * s.dD[i], s.y0);
            double apex = Math.max(8.0 + 40.0 * hh, s.dG[i] - top + 2.0);
            double tUp = Math.sqrt(2.0 * apex / G_DROP);
            s.dV[i] = G_DROP * tUp;
            s.dT[i] = tUp + Math.sqrt(2.0 * Math.max(0.0, apex + top - s.dG[i]) / G_DROP);
            s.dS[i] = 0.5 + 0.7 * ss;
        }
        int c = 64;
        s.cA = new double[c]; s.cD = new double[c]; s.cS = new double[c]; s.cPh = new double[c];
        double zone = WarheadFx.ROD_ZONE;
        for (int i = 0; i < c; i++) {
            boolean rim = i < 44;
            s.cA[i] = Math.PI * 2 * (i + rnd.nextDouble() * 0.8) / (rim ? 44 : 20);
            s.cD[i] = rim ? zone * (0.82 + 0.22 * rnd.nextDouble()) : zone * (0.4 + 0.35 * rnd.nextDouble());
            s.cS[i] = 3.0 + 3.0 * rnd.nextDouble();
            s.cPh[i] = rnd.nextDouble() * 6.3;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SHOWS.clear(); jolt = wash = dusk = 0; return; }
        long now = level.getGameTime();
        jolt *= 0.8F; wash *= 0.7F; dusk *= 0.85F;
        SHOWS.removeIf(s -> now - s.start > LIFE || now < s.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Show s : SHOWS) {
            double r = shockR(now - s.start);
            if (r < 0) { s.camSide = Double.NaN; continue; }
            double side = Math.hypot(cam.x - s.pos.x, cam.z - s.pos.z) - r;
            if (!Double.isNaN(s.camSide) && s.camSide > 0 && side <= 0) {
                float k = (float) Math.max(0.25, 1.0 - r / WarheadFx.ROD_SHOCK);
                k *= Math.max(0.4F, polar(s, Math.atan2(cam.z - s.pos.z, cam.x - s.pos.x), r)[1]);
                jolt = Math.max(jolt, 2.4F * k);
                if (ClientConfig.explosionScreen()) dusk = Math.max(dusk, 0.4F * k);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), cam.x, cam.y, cam.z, 1.0F, 0.7F + 0.25F * (1.0F - k));
            }
            s.camSide = side;
        }
    }

    private static double shockR(double age) {
        if (age < 0 || age > SHOCK_TICKS) return -1.0;
        return WarheadFx.ROD_SHOCK * age / SHOCK_TICKS;
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
        BufferBuilder dust = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= shock(dust, cam, s, age);
            any |= jetDust(dust, cam, left, up, s, age);
            any |= steam(dust, cam, left, up, s, age);
            any |= shimmer(dust, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, dust, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= crater(glow, level, cam, left, up, s, age);
            any |= jetFire(glow, cam, left, up, s, age);
            any |= crown(glow, cam, s, age);
            any |= droplets(glow, cam, left, up, s, age);
            any |= flash(glow, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static double wid(Vec3 cam, double x, double y, double z, double base) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return base * Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / 64.0);
    }

    private static boolean flash(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > FLASH_TICKS) return false;
        float k = (float) (1.0 - age / FLASH_TICKS);
        double x = s.pos.x, z = s.pos.z, y = s.y0 + 3.0;
        ClientBlastGeo.disc(bb, cam, left, up, x, y, z, 70.0 * (0.6 + 0.4 * age / FLASH_TICKS), 1.0F, 0.85F, 0.65F, 0.55F * k);
        ClientBlastGeo.disc(bb, cam, left, up, x, y, z, 28.0, 1.0F, 0.97F, 0.9F, 0.85F * k);
        ClientBlastGeo.disc(bb, cam, left, up, x, y, z, 10.0, 1.0F, 1.0F, 1.0F, 0.95F * k * k + 0.05F * k);
        ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + 150.0, z, wid(cam, x, s.y0 + 30, z, 3.0), 1.0F, 0.9F, 0.75F, 0.5F * k, 0.0F);
        ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + 150.0, z, wid(cam, x, s.y0 + 30, z, 0.9), 1.0F, 1.0F, 1.0F, 0.95F * k, 0.0F);
        return true;
    }

    private static double[] puffAt(Show s, int i, double ta) {
        double apex = s.pU[i], v0 = Math.sqrt(2.0 * G_JET * apex), tUp = v0 / G_JET, y, out, rad;
        if (ta <= tUp) {
            y = s.y0 + 0.5 + v0 * ta - 0.5 * G_JET * ta * ta;
            out = s.pS[i] + s.pW[i] * ta;
            rad = s.pR[i] * (1.0 + 0.02 * ta);
        } else {
            double tf = ta - tUp;
            y = s.y0 + 0.5 + apex - s.pVt[i] * tf;
            out = Math.min(28.0, s.pS[i] + s.pW[i] * tUp + 0.012 * tf);
            rad = s.pR[i] * (1.0 + 0.02 * tUp) + 0.007 * tf;
        }
        y = Math.max(y, s.y0 + 1.0 + rad * 0.4);
        return new double[] { s.pos.x + Math.cos(s.pA[i]) * out + ta * 0.01, y, s.pos.z + Math.sin(s.pA[i]) * out, rad };
    }

    private static boolean jetDust(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 2 || age > LIFE) return false;
        boolean any = false;
        for (int i = 0; i < s.pL.length; i++) {
            double ta = age - s.pL[i];
            if (ta < 4 || ta > s.pLife[i]) continue;
            double[] p = puffAt(s, i, ta);
            float in = (float) Math.min(1.0, (ta - 4) / 16.0), fade = (float) Math.min(1.0, (s.pLife[i] - ta) / (s.pLife[i] * 0.35));
            float h = (float) Math.min(1.0, (p[1] - s.y0) / 100.0);
            float c = 0.18F + 0.08F * h + 0.06F * (float) s.pTone[i];
            ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], p[3], c * 1.06F, c, c * 0.92F, 0.6F * in * fade, 10);
            float lit = Math.min(1.0F, c + 0.14F);
            ClientBlastGeo.disc(bb, cam, left, up, p[0] + up.x() * p[3] * 0.22, p[1] + up.y() * p[3] * 0.22, p[2] + up.z() * p[3] * 0.22, p[3] * 0.6, lit, lit * 0.96F, lit * 0.9F, 0.3F * in * fade, 8);
            any = true;
        }
        return any;
    }

    private static boolean jetFire(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 300) return false;
        double x = s.pos.x, z = s.pos.z;
        if (age <= 14) {
            float k = (float) Math.pow(1.0 - age / 14.0, 0.8);
            double front = 420.0 * (1.0 - Math.pow(1.0 - Math.min(1.0, age / 5.0), 2.0));
            ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + front, z, wid(cam, x, s.y0 + front * 0.5, z, 12.0), 0.7F, 0.85F, 1.0F, 0.35F * k, 0.0F);
            ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + front, z, wid(cam, x, s.y0 + front * 0.5, z, 3.2), 1.0F, 1.0F, 1.0F, 1.0F * k, 0.15F * k);
        }
        if (age <= 50) {
            float k = (float) Math.pow(1.0 - age / 50.0, 1.3);
            double front = 100.0 * (1.0 - Math.pow(1.0 - Math.min(1.0, age / 30.0), 2.0));
            ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + front, z, wid(cam, x, s.y0 + front * 0.5, z, 5.0), 1.0F, 0.55F, 0.18F, 0.5F * k, 0.05F * k);
            ClientBlastGeo.streak(bb, cam, x, s.y0, z, x, s.y0 + front * 0.85, z, wid(cam, x, s.y0 + front * 0.5, z, 1.6), 1.0F, 0.95F, 0.8F, 0.9F * k, 0.1F * k);
        }
        for (int i = 0; i < s.pL.length; i++) {
            double ta = age - s.pL[i];
            if (ta < 0 || ta > 24) continue;
            double[] p = puffAt(s, i, ta);
            float heat = (float) (1.0 - ta / 24.0);
            ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], p[3] * 0.9, 1.0F, 0.35F + 0.6F * heat * heat, 0.08F + 0.8F * heat * heat * heat, 0.65F * heat, 8);
        }
        float under = (float) Math.pow(1.0 - age / 300.0, 1.4) * (float) Math.min(1.0, age / 3.0);
        for (int j = 0; j < 5; j++) {
            float flick = 0.8F + 0.2F * (float) Math.sin(age * 0.4 + j * 1.7);
            ClientBlastGeo.disc(bb, cam, left, up, x, s.y0 + 2.0 + j * 4.0, z, 6.0 + j * 0.8, 1.0F, 0.42F, 0.1F, 0.32F * under * flick * (1.0F - 0.15F * j), 10);
        }
        return true;
    }

    private static boolean crown(BufferBuilder bb, Vec3 cam, Show s, double age) {
        if (age < 0 || age > CROWN_TICKS) return false;
        double u = age / CROWN_TICKS, rf = 5.0 + 32.0 * Math.sqrt(u), rt = rf * 1.35 + 4.0, h = 28.0 * Math.pow(Math.sin(Math.PI * Math.min(1.0, u * 1.15)), 0.7);
        float k = (float) Math.pow(1.0 - u, 1.2);
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg, c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            float rag = 0.75F + 0.25F * (float) Math.sin(i * 2.3 + s.seed % 7);
            ClientBlastGeo.v(bb, cam, s.pos.x + c0 * rf, s.y0 + 0.5, s.pos.z + s0 * rf, 1.0F, 0.8F, 0.5F, 0.6F * k);
            ClientBlastGeo.v(bb, cam, s.pos.x + c1 * rf, s.y0 + 0.5, s.pos.z + s1 * rf, 1.0F, 0.8F, 0.5F, 0.6F * k);
            ClientBlastGeo.v(bb, cam, s.pos.x + c1 * rt, s.y0 + 0.5 + h * rag, s.pos.z + s1 * rt, 1.0F, 0.4F, 0.1F, 0.0F);
            ClientBlastGeo.v(bb, cam, s.pos.x + c0 * rt, s.y0 + 0.5 + h * rag, s.pos.z + s0 * rt, 1.0F, 0.4F, 0.1F, 0.0F);
        }
        return true;
    }

    private static double[] dropAt(Show s, int i, double t) {
        double f = Math.max(0.0, Math.min(1.0, t / s.dT[i])), tt = f * s.dT[i], d = 4.0 + (s.dD[i] - 4.0) * f;
        return new double[] { s.pos.x + s.dUx[i] * d, s.y0 + 1.0 + s.dV[i] * tt - 0.5 * G_DROP * tt * tt, s.pos.z + s.dUz[i] * d };
    }

    private static boolean droplets(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 5 + 80 + SPOT_TICKS) return false;
        boolean any = false;
        for (int i = 0; i < s.dL.length; i++) {
            double t = age - s.dL[i];
            if (t < 0) continue;
            if (t <= s.dT[i]) {
                double[] p = dropAt(s, i, t), q = dropAt(s, i, Math.max(0.0, t - 2.0));
                float heat = (float) Math.max(0.0, 1.0 - t / s.dT[i] * 0.7);
                double w = wid(cam, p[0], p[1], p[2], 0.18 * s.dS[i]);
                ClientBlastGeo.streak(bb, cam, q[0], q[1], q[2], p[0], p[1], p[2], w, 1.0F, 0.45F + 0.5F * heat, 0.12F + 0.6F * heat * heat, 0.0F, 0.95F);
                ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], w * 3.0, 1.0F, 0.55F + 0.35F * heat, 0.2F + 0.4F * heat, 0.5F, 6);
                any = true;
            } else {
                double k = (t - s.dT[i]) / SPOT_TICKS;
                if (k > 1) continue;
                double x = s.pos.x + s.dUx[i] * s.dD[i], z = s.pos.z + s.dUz[i] * s.dD[i];
                float cool = (float) Math.min(1.0, k * 1.4), pop = (float) Math.max(0.0, 1.0 - (t - s.dT[i]) / 4.0);
                float r = 1.0F - 0.5F * cool, g = 0.55F - 0.48F * cool, b = 0.15F - 0.13F * cool;
                ClientBlastGeo.disc(bb, cam, left, up, x, s.dG[i] + 0.25, z, (0.7 + 0.8 * s.dS[i]) * (1.0 + 1.5 * pop), r, g, b, (float) (0.8 * Math.pow(1.0 - k, 1.3)) + 0.3F * pop, 6);
                any = true;
            }
        }
        return any;
    }

    private static boolean steam(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 5 || age > 5 + 80 + 30) return false;
        boolean any = false;
        for (int i = 0; i < s.dL.length; i += 2) {
            double k = (age - s.dL[i] - s.dT[i]) / 30.0;
            if (k < 0 || k > 1) continue;
            double x = s.pos.x + s.dUx[i] * s.dD[i], z = s.pos.z + s.dUz[i] * s.dD[i];
            ClientBlastGeo.disc(bb, cam, left, up, x, s.dG[i] + 0.4 + k * 2.0, z, 0.5 + 1.8 * Math.sqrt(k), 0.72F, 0.69F, 0.65F, (float) (0.45 * (1 - k)), 6);
            any = true;
        }
        return any;
    }

    private static boolean shock(BufferBuilder bb, Vec3 cam, Show s, double age) {
        double r = shockR(age);
        if (r < 2.0) return false;
        double u = age / SHOCK_TICKS;
        float fade = (float) Math.min(1.0, (SHOCK_TICKS - age) / (SHOCK_TICKS * 0.35));
        double h = 10.0 + 16.0 * (1.0 - u);
        crest(bb, cam, s, r, h, 0.6F * fade, 0.55F, 0.49F, 0.41F, 0.68F, 0.64F, 0.58F);
        crest(bb, cam, s, r + 1.5, 2.2, 0.5F * fade, 0.42F, 0.34F, 0.25F, 0.5F, 0.42F, 0.32F);
        if (r > 12.0) crest(bb, cam, s, r - 9.0, 1.6, 0.4F * fade, 0.42F, 0.34F, 0.25F, 0.5F, 0.42F, 0.32F);
        if (r > 25.0) crest(bb, cam, s, r * 0.82, h * 0.55, 0.25F * fade, 0.58F, 0.53F, 0.46F, 0.66F, 0.62F, 0.57F);
        return true;
    }

    private static void crest(BufferBuilder bb, Vec3 cam, Show s, double rr, double h, float k, float r0, float g0, float b0, float r1, float g1, float b1) {
        double px = 0, pz = 0, pg = 0, ph = 0; float ps = 1.0F;
        for (int i = 0; i <= ANG; i++) {
            double a = Math.PI * 2 * i / ANG, x = s.pos.x + Math.cos(a) * rr, z = s.pos.z + Math.sin(a) * rr;
            float[] pp = polar(s, a, rr);
            double gy = pp[0], hh = h * (0.4 + 0.6 * pp[1]);
            if (i > 0) {
                ClientBlastGeo.v(bb, cam, px, pg - 0.3, pz, r0, g0, b0, k * ps); ClientBlastGeo.v(bb, cam, x, gy - 0.3, z, r0, g0, b0, k * pp[1]);
                ClientBlastGeo.v(bb, cam, x, gy + hh, z, r1, g1, b1, 0.0F); ClientBlastGeo.v(bb, cam, px, pg + ph, pz, r1, g1, b1, 0.0F);
            }
            px = x; pz = z; pg = gy; ph = hh; ps = pp[1];
        }
    }

    private static boolean crater(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 1 || age > MOUTH_TICKS) return false;
        double x = s.pos.x, z = s.pos.z;
        if (age <= RIM_TICKS) {
            double k = age / RIM_TICKS;
            float cool = (float) Math.min(1.0, k * 1.3), in = (float) Math.min(1.0, age / 6.0);
            float r = 1.0F - 0.45F * cool, g = 0.6F - 0.52F * cool, b = 0.2F - 0.18F * cool;
            for (int i = 0; i < s.cA.length; i++) {
                double cx = x + Math.cos(s.cA[i]) * s.cD[i], cz = z + Math.sin(s.cA[i]) * s.cD[i];
                float flick = 0.75F + 0.25F * (float) Math.sin(age * 0.21 + s.cPh[i]) * (float) Math.sin(age * 0.07 + s.cPh[i] * 2.0);
                ClientBlastGeo.disc(bb, cam, left, up, cx, ClientBlastGeo.ground(level, cx, cz, s.y0) + 0.6, cz, s.cS[i], r, g, b, (float) (0.5 * Math.pow(1.0 - k, 1.2)) * in * flick, 8);
            }
        }
        if (age >= 8 && age <= RIVER_TICKS) {
            double k = age / RIVER_TICKS, run = Math.min(1.0, (age - 8) / 60.0);
            float cool = (float) Math.min(1.0, k * 1.2), r = 1.0F - 0.4F * cool, g = 0.55F - 0.47F * cool, b = 0.15F - 0.13F * cool;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2.0 / 14 + Math.sin(i * 7.3) * 0.15, r0 = WarheadFx.ROD_ZONE * (0.95 + 0.1 * Math.sin(i * 3.1)), r1 = r0 + (6.0 - r0) * run;
                double px = x + Math.cos(a) * r0, pz = z + Math.sin(a) * r0, py = ClientBlastGeo.ground(level, px, pz, s.y0) + 0.3;
                for (int seg = 1; seg <= 6; seg++) {
                    double rr = r0 + (r1 - r0) * seg / 6.0, aa = a + Math.sin(seg * 1.7 + i) * 0.06;
                    double qx = x + Math.cos(aa) * rr, qz = z + Math.sin(aa) * rr, qy = ClientBlastGeo.ground(level, qx, qz, s.y0) + 0.3;
                    float flick = 0.8F + 0.2F * (float) Math.sin(age * 0.3 + i + seg);
                    ClientBlastGeo.streak(bb, cam, px, py, pz, qx, qy, qz, 0.9, r, g, b, 0.75F * (float) (1.0 - k) * flick, 0.75F * (float) (1.0 - k) * flick);
                    px = qx; py = qy; pz = qz;
                }
            }
        }
        float m = (float) Math.pow(1.0 - age / MOUTH_TICKS, 1.5) * (float) Math.min(1.0, age / 4.0);
        double floor = Math.min(s.y0, ClientBlastGeo.ground(level, x, z, s.y0));
        ClientBlastGeo.disc(bb, cam, left, up, x, floor - 2.0, z, 13.0, 1.0F, 0.5F, 0.14F, 0.6F * m, 12);
        ClientBlastGeo.disc(bb, cam, left, up, x, floor + 3.0, z, 22.0, 1.0F, 0.38F, 0.08F, 0.2F * m, 12);
        return true;
    }

    private static boolean shimmer(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 20 || age > SHIMMER_TICKS) return false;
        float env = (float) (Math.min(1.0, (age - 20) / 40.0) * Math.pow(1.0 - age / SHIMMER_TICKS, 1.2));
        for (int j = 0; j < 14; j++) {
            double ta = (age + j * 9.0) % 120.0, k = ta / 120.0;
            double sway = Math.sin(ta * 0.09 + j * 1.3) * (1.5 + 3.0 * k), a = j * 2.4;
            double px = s.pos.x + Math.cos(a) * (4.0 + j * 0.9) + sway, pz = s.pos.z + Math.sin(a) * (4.0 + j * 0.9) + Math.cos(ta * 0.07 + j) * 1.5;
            float w = 0.9F - 0.15F * (float) k;
            ClientBlastGeo.disc(bb, cam, left, up, px, s.y0 + 1.0 + ta * 0.25, pz, 3.0 + 5.0 * k, w, w * 0.92F, w * 0.82F, (float) (0.08 * Math.sin(Math.PI * k)) * env, 8);
        }
        return true;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 1.8F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 1.8F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * jolt * 1.2F);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if ((wash < 0.02F && dusk < 0.02F) || !ClientConfig.explosionScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (wash >= 0.02F) g.fill(0, 0, w, h, ((int) (235 * Math.min(1.0F, wash)) << 24) | 0xFFF8EE);
        if (dusk >= 0.02F) g.fill(0, 0, w, h, ((int) (200 * Math.min(1.0F, dusk)) << 24) | 0x9C8A74);
    }
}
