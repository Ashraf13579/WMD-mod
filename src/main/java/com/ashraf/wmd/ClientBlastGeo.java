package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
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
public final class ClientBlastGeo {

    private ClientBlastGeo() {}

    public static final double FAR_VIEW = 1000.0;

    private static final class Blast {
        final Vec3 pos;
        final WarheadType type;
        final double r, reach, fire;
        final int waveTicks, colLife;
        final long start, seed;
        final boolean show, far;
        double camSide = Double.NaN;

        float[][] polarH, polarS;
        double cell, y0;

        double[] sx, sz, sg, sd;
        double sCell, sTop, sR;

        double[] fUx, fUz, fD, fT, fG, fH;
        int fragN;

        double incH, incR;
        double[] iUx, iUz, iD, iT, iG, iK;
        final int tier;
        Blast(Vec3 pos, WarheadType type, int tier, double r, long start) {
            this.pos = pos; this.type = type; this.r = r; this.start = start; this.tier = tier;
            this.seed = Double.doubleToLongBits(pos.x * 31 + pos.z) ^ start;
            this.reach = WarheadFx.waveReach(type, tier, r);
            this.waveTicks = WarheadFx.waveTicks(type, reach);
            this.show = WarheadFx.spectacle(type);
            this.far = WarheadFx.farWave(type, tier);
            this.fire = r * 1.6;
            this.colLife = switch (tier) { case 1 -> 600; case 2 -> 900; case 3 -> 1500; default -> 2400; };
        }
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static float jolt, wash;
    private static final int ANG = 256;
    private static final float SHADE = 0.3F;

    public static void start(double x, double y, double z, int tier, WarheadType type, double r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Blast b = new Blast(new Vec3(x, y, z), type, tier, r, level.getGameTime());
        BLASTS.add(b);
        if (b.far) terrain(level, b);
        if (type == WarheadType.FRAG) fragments(level, b);
        if (type == WarheadType.INCENDIARY) streamers(level, b);
        double[] sg = WarheadFx.surge(type, tier);
        if (sg != null) valley(level, b, sg[0], sg[1]);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(b.pos);
        if (d < b.reach * 3 || tier >= 3) level.setSkyFlashTime(tier >= 3 ? 6 : 3);
        double flashR = Math.min(b.reach, WarheadFx.waveReach(r) * 1.5);
        if (d < flashR && ClientConfig.explosionScreen() && !type.mushroom()) wash = Math.max(wash, (float) (1.0 - d / flashR) * 0.85F);
    }

    static double ground(ClientLevel level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            int top = ClientSnapshot.topAt(bx, bz);
            return top != Integer.MIN_VALUE ? top : fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    private static void terrain(ClientLevel level, Blast b) {
        b.cell = Math.max(2.0, b.reach / 150.0);
        int nr = (int) Math.ceil(b.reach / b.cell) + 2;
        b.y0 = ground(level, b.pos.x, b.pos.z, b.pos.y);
        b.polarH = new float[ANG][nr]; b.polarS = new float[ANG][nr];
        for (int i = 0; i < ANG; i++) {
            double a = Math.PI * 2 * i / ANG, ca = Math.cos(a), sa = Math.sin(a), horizon = -1e9, last = b.y0;
            for (int j = 0; j < nr; j++) {
                double d = Math.max(0.5, j * b.cell), h = ground(level, b.pos.x + ca * d, b.pos.z + sa * d, last);
                last = h;
                b.polarH[i][j] = (float) h;
                b.polarS[i][j] = j < 4 || (h + 2 - (b.y0 + 3)) / d >= horizon ? 1.0F : SHADE;
                horizon = Math.max(horizon, (h - (b.y0 + 3)) / d);
            }
        }
    }

    private static float[] polar(Blast b, double a, double d) {
        double fa = (a / (Math.PI * 2)) % 1.0; if (fa < 0) fa += 1.0;
        int i = (int) Math.round(fa * ANG) % ANG;
        double fj = d / b.cell; int j = Math.min(b.polarH[i].length - 2, (int) fj); double t = Math.min(1.0, fj - j);
        return new float[] { (float) (b.polarH[i][j] * (1 - t) + b.polarH[i][j + 1] * t), (float) (b.polarS[i][j] * (1 - t) + b.polarS[i][j + 1] * t) };
    }

    private static void valley(ClientLevel level, Blast b, double R, double H) {
        double c = Math.max(4.0, R / 24.0);
        int n = (int) Math.ceil(R / c), w = 2 * n + 1;
        double g0 = ground(level, b.pos.x, b.pos.z, b.pos.y), top = g0 + H;
        double[] gh = new double[w * w], dist = new double[w * w];
        java.util.Arrays.fill(dist, Double.MAX_VALUE);
        for (int i = 0; i < w; i++) for (int j = 0; j < w; j++) gh[i * w + j] = ground(level, b.pos.x + (i - n) * c, b.pos.z + (j - n) * c, g0);
        java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((p1, p2) -> Double.compare(p1[0], p2[0]));
        int s0 = n * w + n; dist[s0] = 0; q.add(new double[] { 0, s0 });
        List<Integer> order = new ArrayList<>();
        while (!q.isEmpty()) {
            double[] e = q.poll(); int k = (int) e[1];
            if (e[0] > dist[k]) continue;
            order.add(k);
            int ki = k / w, kj = k % w;
            for (int di = -1; di <= 1; di++) for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) continue;
                int ni = ki + di, nj = kj + dj;
                if (ni < 0 || nj < 0 || ni >= w || nj >= w) continue;
                int m = ni * w + nj;
                if (gh[m] > top - 1.0 || Math.hypot(ni - n, nj - n) * c > R) continue;
                double nd = dist[k] + c * (di != 0 && dj != 0 ? 1.4142 : 1.0);
                if (nd < dist[m]) { dist[m] = nd; q.add(new double[] { nd, m }); }
            }
        }
        int m = order.size();
        b.sx = new double[m]; b.sz = new double[m]; b.sg = new double[m]; b.sd = new double[m];
        for (int i = 0; i < m; i++) {
            int k = order.get(i);
            b.sx[i] = b.pos.x + (k / w - n) * c; b.sz[i] = b.pos.z + (k % w - n) * c; b.sg[i] = gh[k]; b.sd[i] = dist[k];
        }
        b.sCell = c; b.sTop = top; b.sR = R;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { BLASTS.clear(); FUELS.clear(); jolt = wash = 0; return; }
        long now = level.getGameTime();
        jolt *= 0.82F; wash *= 0.8F;
        BLASTS.removeIf(b -> now - b.start > Math.max(b.colLife * (b.type == WarheadType.INCENDIARY ? 1.5 : 1.0) + 30, b.waveTicks) + 20 || now < b.start - 100);
        FUELS.removeIf(f -> now - f.start > WarheadFx.FUEL_TICKS + 10 || now < f.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Blast b : BLASTS) {
            double r = WarheadFx.waveRadius(now - b.start, b.reach, b.waveTicks);
            if (r < 0) { b.camSide = Double.NaN; continue; }
            double side = Math.hypot(cam.x - b.pos.x, cam.z - b.pos.z) - r;
            if (!Double.isNaN(b.camSide) && b.camSide > 0 && side <= 0) {
                float k = (float) Math.max(0.25, 1.0 - r / b.reach);
                if (b.polarS != null) k *= Math.max(0.4F, polar(b, Math.atan2(cam.z - b.pos.z, cam.x - b.pos.x), r)[1]);
                jolt = Math.max(jolt, 3.0F * k);
                if (ClientConfig.explosionScreen()) wash = Math.max(wash, 0.35F * k);
                WmdSound.play(WmdSounds.BLAST_WHUMP.get(), cam.x, cam.y, cam.z, 1.0F, 0.85F + 0.3F * (1.0F - k));
            }
            b.camSide = side;
        }
    }

    static double LIM;

    static boolean RAW;

    static void beginFrame() { LIM = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9; }

    static void v(BufferBuilder bb, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        if (ShaderFx.on && !RAW) { ShaderFx.vert(bb, cam, x, y, z, r, g, b, a); return; }
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double k = l > LIM ? LIM / l : 1.0;
        bb.addVertex((float) (dx * k), (float) (dy * k), (float) (dz * k)).setColor(r, g, b, a);
    }

    static void disc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double rad, float r, float g, float b, float a) {
        disc(bb, cam, left, up, x, y, z, rad, r, g, b, a, 12);
    }

    static void disc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double rad, float r, float g, float b, float a, int n) {
        if (ShaderFx.on && !RAW) { ShaderFx.disc(bb, cam, left, up, x, y, z, rad, r, g, b, a); return; }
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(a0) * rad, s0 = Math.sin(a0) * rad, c1 = Math.cos(a1) * rad, s1 = Math.sin(a1) * rad;
            v(bb, cam, x, y, z, r, g, b, a);
            v(bb, cam, x, y, z, r, g, b, a);
            v(bb, cam, x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1, r, g, b, 0.0F);
            v(bb, cam, x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0, r, g, b, 0.0F);
        }
    }

    static void flatDisc(BufferBuilder bb, Vec3 cam, double x, double y, double z, double rad, float r, float g, float b, float a, int n) {
        disc(bb, cam, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), x, y, z, rad, r, g, b, a, n);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (BLASTS.isEmpty() && FUELS.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        LIM = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9;
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Fuel f : FUELS) {
            if (cam.distanceTo(f.pos) <= FAR_VIEW) any |= fuelMist(smoke, level, cam, left, up, f, now - f.start);
        }
        for (Blast b : BLASTS) {
            if (cam.distanceTo(b.pos) > FAR_VIEW) continue;
            double age = now - b.start;
            if (b.type == WarheadType.HE) any |= cloud(smoke, level, cam, left, up, b, age, b.r, 1.0F, 45.0, b.colLife);
            else if (b.type == WarheadType.THERMOBARIC) any |= cloud(smoke, level, cam, left, up, b, age - 10, b.r * 0.55, 0.5F, 120.0, b.colLife);
            else if (b.type == WarheadType.INCENDIARY) {
                any |= incTrails(smoke, cam, left, up, b, age);
                any |= cloud(smoke, level, cam, left, up, b, age - 30, b.r * 1.3, 3.0F, 400.0, b.colLife * 1.5);
            }
            else if (b.type == WarheadType.FRAG) {
                any |= cloud(smoke, level, cam, left, up, b, age, b.r * 0.7, 1.8F, 4.0, 160.0);
                any |= fragGround(smoke, cam, left, up, b, age, false);
            }
            any |= surge(smoke, cam, left, up, b, age);
            any |= wave(smoke, level, cam, b, age, false);
        }
        draw(event, smoke, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Blast b : BLASTS) {
            if (cam.distanceTo(b.pos) > FAR_VIEW) continue;
            double age = now - b.start;
            if (b.type == WarheadType.HE) any |= fireball(glow, cam, left, up, b, age);
            else if (b.type == WarheadType.THERMOBARIC) any |= tbxFire(glow, level, cam, left, up, b, age);
            else if (b.type == WarheadType.INCENDIARY) any |= incFire(glow, level, cam, left, up, b, age);
            else if (b.type == WarheadType.FRAG) {
                any |= fragSnap(glow, cam, left, up, b, age);
                any |= fragStreaks(glow, cam, b, age);
                any |= fragGround(glow, cam, left, up, b, age, true);
            }
            any |= wave(glow, level, cam, b, age, true);
        }
        draw(event, glow, any, true);
    }

    public static double incBurstHeight(double r) { return 12.0 + r * 0.5; }

    private static void streamers(ClientLevel level, Blast b) {
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        int n = (int) Math.max(18, 36 * density);
        b.incH = incBurstHeight(b.r);
        b.incR = Missiles.craterRadius(b.tier) * 1.8;
        RandomSource rnd = RandomSource.create(b.seed ^ 0x1AC5L);
        b.iUx = new double[n]; b.iUz = new double[n]; b.iD = new double[n]; b.iT = new double[n]; b.iG = new double[n]; b.iK = new double[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * (i + rnd.nextDouble() * 0.7) / n;
            b.iUx[i] = Math.cos(a); b.iUz[i] = Math.sin(a);
            b.iD[i] = b.incR * (0.25 + 0.75 * Math.sqrt(rnd.nextDouble()));
            b.iT[i] = 18 + rnd.nextDouble() * 14;
            b.iG[i] = ground(level, b.pos.x + b.iUx[i] * b.iD[i], b.pos.z + b.iUz[i] * b.iD[i], b.pos.y);
            b.iK[i] = 2.0 + rnd.nextDouble() * 5.0;
        }
    }

    private static double[] incAt(Blast b, int i, double t) {
        double f = Math.max(0.0, Math.min(1.0, t / b.iT[i])), top = b.pos.y + b.incH;
        double d = b.iD[i] * (1.0 - Math.pow(1.0 - f, 1.8));
        return new double[] { b.pos.x + b.iUx[i] * d, top + b.iK[i] * 4.0 * f * (1.0 - f) + (b.iG[i] + 0.3 - top) * Math.pow(f, 1.6), b.pos.z + b.iUz[i] * d };
    }

    private static boolean incTrails(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        double trailLife = 300;
        if (b.iT == null || age < 1 || age > 32 + trailLife) return false;
        double drift = 0.012;
        for (int i = 0; i < b.iT.length; i++) {
            for (double sT = 0; sT <= Math.min(age, b.iT[i]); sT += 2.0) {
                double old = age - sT;
                if (old > trailLife) continue;
                double[] p = incAt(b, i, sT);
                double k = old / trailLife, rad = 0.5 + 3.0 * Math.sqrt(k) + 0.04 * old;
                float c = 0.9F + 0.06F * (float) (1 - k);
                disc(bb, cam, left, up, p[0] + old * drift, p[1] + old * 0.01, p[2], rad, c, c, c * 0.97F, (float) (0.5 * Math.pow(1 - k, 1.4)), 8);
            }
        }
        if (age < 200) {
            double k = age / 200.0, top = b.pos.y + b.incH;
            for (int j = 0; j < 6; j++) {
                double a = j * Math.PI / 3, rr = 1.5 + 4.0 * Math.sqrt(k);
                disc(bb, cam, left, up, b.pos.x + Math.cos(a) * rr + age * drift, top + Math.sin(a) * rr * 0.6, b.pos.z + Math.sin(a * 2) * rr * 0.3, 2.0 + 5.0 * Math.sqrt(k), 0.95F, 0.95F, 0.92F, (float) (0.55 * (1 - k)), 8);
            }
        }
        return true;
    }

    private static boolean incFire(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        if (b.iT == null || age < 0 || age > 640) return false;
        double top = b.pos.y + b.incH;
        if (age < 6) {
            float k = (float) (1.0 - age / 6.0);
            disc(bb, cam, left, up, b.pos.x, top, b.pos.z, 3.0 + b.r * 0.8, 1.0F, 0.95F, 0.85F, 0.95F * k);
            disc(bb, cam, left, up, b.pos.x, top, b.pos.z, (3.0 + b.r * 0.8) * 2.2, 1.0F, 0.7F, 0.35F, 0.4F * k);
        }
        float burn = (float) Math.min(1.0, (600 - age) / 200.0);
        for (int i = 0; i < b.iT.length; i++) {
            double tt = b.iT[i];
            if (age <= tt) {
                double[] p = incAt(b, i, age);
                disc(bb, cam, left, up, p[0], p[1], p[2], 0.55, 1.0F, 1.0F, 0.95F, 0.95F, 6);
                disc(bb, cam, left, up, p[0], p[1], p[2], 1.4, 1.0F, 0.6F, 0.2F, 0.35F, 6);
            } else if (burn > 0) {
                double x = b.pos.x + b.iUx[i] * b.iD[i], z = b.pos.z + b.iUz[i] * b.iD[i];
                float flick = 0.7F + 0.3F * (float) Math.sin(age * 0.45 + i * 1.7) * (float) Math.sin(age * 0.17 + i);
                disc(bb, cam, left, up, x, b.iG[i] + 0.6, z, 1.8 + 0.8 * Math.sin(i), 1.0F, 0.5F, 0.12F, 0.5F * burn * flick * (float) Math.min(1.0, (age - tt) / 4.0), 8);
            }
        }
        if (burn > 0) {
            RandomSource rnd = RandomSource.create(b.seed ^ 0x2F1L);
            double grow = Math.min(1.0, 0.25 + 1.2 * age / 600.0);
            for (int j = 0; j < 24; j++) {
                double a = rnd.nextDouble() * Math.PI * 2, dd = Math.sqrt(rnd.nextDouble()), born = 20 + rnd.nextDouble() * 300;
                if (age < born || dd > grow) continue;
                double x = b.pos.x + Math.cos(a) * dd * b.incR, z = b.pos.z + Math.sin(a) * dd * b.incR;
                float flick = 0.7F + 0.3F * (float) Math.sin(age * 0.5 + j * 2.3);
                disc(bb, cam, left, up, x, ground(level, x, z, b.pos.y) + 0.6, z, 2.2, 1.0F, 0.45F, 0.1F, 0.4F * burn * flick * (float) Math.min(1.0, (age - born) / 10.0), 8);
            }
        }
        return true;
    }

    private static void fragments(ClientLevel level, Blast b) {
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        int n = (int) Math.max(60, 200 * density), m = (int) Math.max(50, 160 * density);
        double D = b.r * b.type.reach, y0 = b.pos.y + 1.2;
        RandomSource rnd = RandomSource.create(b.seed ^ 0xF2A6L);
        b.fragN = n;
        b.fUx = new double[n + m]; b.fUz = new double[n + m]; b.fD = new double[n + m]; b.fT = new double[n + m]; b.fG = new double[n + m]; b.fH = new double[n + m];
        for (int i = 0; i < n + m; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = i < n ? D * (0.55 + 0.6 * rnd.nextDouble()) : D * 1.05 * Math.sqrt(rnd.nextDouble());
            double el = rnd.nextDouble() < 0.1 ? 0.3 + rnd.nextDouble() * 0.5 : Math.abs(rnd.nextGaussian()) * 0.12 + rnd.nextDouble() * 0.05;
            b.fUx[i] = Math.cos(a); b.fUz[i] = Math.sin(a); b.fD[i] = d;
            b.fT[i] = 2.0 + 9.0 * d / D + rnd.nextDouble() * 1.5;
            b.fG[i] = ground(level, b.pos.x + b.fUx[i] * d, b.pos.z + b.fUz[i] * d, b.pos.y);
            b.fH[i] = d * Math.tan(el) * 0.5;
        }
    }

    private static double[] fragAt(Blast b, int i, double t) {
        double f = Math.max(0.0, Math.min(1.0, t / b.fT[i])), d = b.fD[i] * (1.0 - Math.pow(1.0 - f, 1.6)), y0 = b.pos.y + 1.2;
        return new double[] { b.pos.x + b.fUx[i] * d, y0 + b.fH[i] * 4.0 * f * (1.0 - f) + (b.fG[i] + 0.2 - y0) * f, b.pos.z + b.fUz[i] * d };
    }

    private static boolean fragSnap(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        if (age < 0 || age > 5) return false;
        float k = (float) (1.0 - age / 5.0);
        double y = b.pos.y + 1.5, size = b.r * 1.6 * (0.6 + 0.4 * age / 5.0);
        disc(bb, cam, left, up, b.pos.x, y, b.pos.z, size * 1.6, 1.0F, 0.75F, 0.45F, 0.5F * k);
        disc(bb, cam, left, up, b.pos.x, y, b.pos.z, size, 1.0F, 0.97F, 0.9F, 0.95F * k);
        return true;
    }

    private static boolean fragStreaks(BufferBuilder bb, Vec3 cam, Blast b, double age) {
        if (b.fT == null || age < 0 || age > 14) return false;
        boolean any = false;
        for (int i = 0; i < b.fragN; i++) {
            if (age > b.fT[i]) continue;
            double[] p = fragAt(b, i, age), q = fragAt(b, i, Math.max(0.0, age - 1.5));
            float heat = (float) Math.max(0.0, 1.0 - age / b.fT[i] * 0.8);
            streak(bb, cam, q[0], q[1], q[2], p[0], p[1], p[2], 0.06, 1.0F, 0.55F + 0.4F * heat, 0.2F + 0.6F * heat * heat, 0.0F, 0.9F);
            any = true;
        }
        return any;
    }

    private static boolean fragGround(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age, boolean glow) {
        if (b.fT == null || age < 2 || age > 40) return false;
        boolean any = false;
        for (int i = 0; i < b.fT.length; i++) {
            double k = (age - b.fT[i]) / (glow ? 3.0 : 18.0);
            if (k < 0 || k > 1) continue;
            double x = b.pos.x + b.fUx[i] * b.fD[i], z = b.pos.z + b.fUz[i] * b.fD[i];
            if (glow) disc(bb, cam, left, up, x, b.fG[i] + 0.3, z, 0.35, 1.0F, 0.75F, 0.4F, (float) (0.9 * (1 - k)), 6);
            else {
                double rad = 0.3 + 1.1 * Math.sqrt(k);
                disc(bb, cam, left, up, x, b.fG[i] + 0.3 + k * 1.2, z, rad, 0.52F, 0.47F, 0.40F, (float) (0.55 * (1 - k)), 6);
            }
            any = true;
        }
        return any;
    }

    static void quad(BufferBuilder bb, Vec3 cam, Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1, float r0, float g0, float bl0, float al0, float r1, float g1, float bl1, float al1) {
        v(bb, cam, a0.x, a0.y, a0.z, r0, g0, bl0, al0); v(bb, cam, a1.x, a1.y, a1.z, r0, g0, bl0, al0);
        v(bb, cam, b0.x, b0.y, b0.z, r1, g1, bl1, al1); v(bb, cam, b1.x, b1.y, b1.z, r1, g1, bl1, al1);
    }

    static void streak(BufferBuilder bb, Vec3 cam, double ax, double ay, double az, double bx, double by, double bz, double w, float r, float g, float bl, float a0, float a1) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, mx = (ax + bx) * 0.5 - cam.x, my = (ay + by) * 0.5 - cam.y, mz = (az + bz) * 0.5 - cam.z;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx, l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx *= w / l; sy *= w / l; sz *= w / l;
        v(bb, cam, ax - sx, ay - sy, az - sz, r, g, bl, a0); v(bb, cam, ax + sx, ay + sy, az + sz, r, g, bl, a0);
        v(bb, cam, bx + sx, by + sy, bz + sz, r, g, bl, a1); v(bb, cam, bx - sx, by - sy, bz - sz, r, g, bl, a1);
    }

    private static final class Fuel {
        final Vec3 pos; final double r; final long start, seed;
        Fuel(Vec3 pos, double r, long start) { this.pos = pos; this.r = r; this.start = start; this.seed = Double.doubleToLongBits(pos.x * 17 + pos.z) ^ start; }
    }

    private static final List<Fuel> FUELS = new ArrayList<>();

    public static void fuel(double x, double y, double z, int tier) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        FUELS.add(new Fuel(new Vec3(x, y, z), Missiles.craterRadius(tier) * 1.1, level.getGameTime()));
    }

    private static boolean fuelMist(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f left, Vector3f up, Fuel f, double age) {
        int ft = WarheadFx.FUEL_TICKS, sk = WarheadFx.SUCK_START;
        if (age < 0 || age > ft + 6) return false;
        double grow = 1.0 - Math.pow(1.0 - Math.min(1.0, age / 14.0), 2.0), suck = Math.max(0.0, (age - sk) / (double) (ft - sk));
        double R = f.r * (0.3 + 0.7 * grow) * (1.0 - 0.15 * Math.min(1.0, suck));
        float fade = (float) Math.max(0.0, Math.min(1.0, (ft + 6 - age) / 6.0));
        RandomSource rnd = RandomSource.create(f.seed);
        for (int i = 0; i < 34; i++) {
            double a = rnd.nextDouble() * Math.PI * 2 + age * 0.004 * (rnd.nextBoolean() ? 1 : -1), d = Math.sqrt(rnd.nextDouble()) * R;
            double x = f.pos.x + Math.cos(a) * d, z = f.pos.z + Math.sin(a) * d;
            double y = ground(level, x, z, f.pos.y) + 1.0 + rnd.nextDouble() * f.r * 0.28 * grow;
            double rad = f.r * (0.22 + 0.12 * rnd.nextDouble()) * (0.5 + 0.5 * grow);
            float c = 0.78F + 0.1F * (float) rnd.nextDouble();
            disc(bb, cam, left, up, x, y, z, rad, c, c * 0.97F, c * 0.86F, 0.2F * fade, 8);
        }
        if (suck > 0 && suck < 1.0) {
            double rr = f.r * (1.6 - 0.9 * suck), h = 2.5 + f.r * 0.06;
            float k = (float) (0.45 * Math.sin(Math.PI * suck));
            double px = 0, pz = 0, pg = 0;
            for (int i = 0; i <= 64; i++) {
                double a = Math.PI * 2 * i / 64, x = f.pos.x + Math.cos(a) * rr, z = f.pos.z + Math.sin(a) * rr, gy = ground(level, x, z, f.pos.y);
                if (i > 0) {
                    v(bb, cam, px, pg - 0.3, pz, 0.6F, 0.55F, 0.47F, k); v(bb, cam, x, gy - 0.3, z, 0.6F, 0.55F, 0.47F, k);
                    v(bb, cam, x, gy + h, z, 0.7F, 0.66F, 0.6F, 0.0F); v(bb, cam, px, pg + h, pz, 0.7F, 0.66F, 0.6F, 0.0F);
                }
                px = x; pz = z; pg = gy;
            }
        }
        return true;
    }

    private static boolean tbxFire(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        double fr = b.r * 0.786, life = 60 + b.r * 1.0, glowEnd = life + 200;
        if (age < 0 || age > glowEnd) return false;
        RandomSource rnd = RandomSource.create(b.seed ^ 0x7B1L);
        double g0 = ground(level, b.pos.x, b.pos.z, b.pos.y);
        if (age <= life) {
            double u = age / life;
            float fade = (float) Math.pow(1.0 - u, 1.2), heat = (float) Math.max(0.0, 1.0 - u * 1.3);
            double dome = fr * 1.25 * Math.min(1.0, 0.4 + age / 8.0);
            disc(bb, cam, left, up, b.pos.x, g0 + fr * 0.35 + age * 0.05, b.pos.z, dome, 1.0F, 0.5F + 0.3F * heat, 0.15F + 0.25F * heat, 0.45F * fade);
            for (int i = 0; i < 40; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * fr, lift = 0.5 + rnd.nextDouble(), ry = rnd.nextDouble(), rs = rnd.nextDouble();
                double la = age - (d / fr) * 6.0;
                if (la < 0) continue;
                double lu = Math.min(1.0, la / (life - (d / fr) * 6.0));
                double x = b.pos.x + Math.cos(a) * (d + la * 0.04), z = b.pos.z + Math.sin(a) * (d + la * 0.04);
                double y = ground(level, x, z, g0) + 1.5 + ry * fr * 0.3 + la * 0.07 * lift;
                double size = fr * (0.28 + 0.12 * rs) * Math.min(1.0, 0.3 + la / 5.0) * (1.0 + 0.6 * lu);
                float h = (float) Math.max(0.0, 1.0 - lu * 1.35), f2 = (float) Math.pow(1.0 - lu, 1.1);
                disc(bb, cam, left, up, x, y, z, size, 1.0F, 0.45F + 0.5F * h, 0.12F + 0.6F * h * h, 0.4F * f2, 10);
            }
        }
        if (age > life * 0.5) {
            RandomSource rg = RandomSource.create(b.seed ^ 0x3C5L);
            double ga = age - life * 0.5, gl = glowEnd - life * 0.5;
            float f3 = (float) Math.min(1.0, ga / 20.0) * (float) Math.pow(1.0 - ga / gl, 1.5);
            for (int i = 0; i < 10; i++) {
                double a = rg.nextDouble() * Math.PI * 2, d = Math.sqrt(rg.nextDouble()) * fr * 0.8;
                double x = b.pos.x + Math.cos(a) * d, z = b.pos.z + Math.sin(a) * d;
                float flick = 0.75F + 0.25F * (float) Math.sin(age * (0.3 + rg.nextDouble() * 0.4) + i);
                disc(bb, cam, left, up, x, ground(level, x, z, g0) + 1.0, z, fr * 0.45, 0.95F, 0.32F, 0.08F, 0.28F * f3 * flick, 8);
            }
        }
        return true;
    }

    static void draw(RenderLevelStageEvent event, BufferBuilder bb, boolean any, boolean additive) {
        ShaderFx.flush(event, bb, additive);
        MeshData md = bb.build();
        if (!any || md == null) { if (md != null) md.close(); return; }
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(md);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static boolean fireball(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        double life = 22 + b.r * 0.8;
        if (age < 0 || age > life) return false;
        double u = age / life, size = b.fire * Math.min(1.0, 0.35 + age / 6.0) * (1.0 + 0.25 * u);
        float heat = (float) Math.max(0.0, 1.0 - u * 1.4), fade = (float) Math.pow(1.0 - u, 1.3);
        double y = b.pos.y + size * 0.55;
        disc(bb, cam, left, up, b.pos.x, y, b.pos.z, size * 1.35, 1.0F, 0.45F + 0.25F * heat, 0.12F + 0.2F * heat, 0.55F * fade);
        disc(bb, cam, left, up, b.pos.x, y, b.pos.z, size, 1.0F, 0.75F + 0.25F * heat, 0.35F + 0.55F * heat, 0.85F * fade);
        disc(bb, cam, left, up, b.pos.x, y, b.pos.z, size * 0.55, 1.0F, 1.0F, 0.85F + 0.15F * heat, 0.9F * fade * heat + 0.1F);
        return true;
    }

    private static boolean wave(BufferBuilder bb, ClientLevel level, Vec3 cam, Blast b, double age, boolean hot) {
        double r = WarheadFx.waveRadius(age, b.reach, b.waveTicks);
        if (r < 1.0) return false;
        double u = age / b.waveTicks;
        if (hot && u > 0.4) return false;
        float fade = (float) Math.min(1.0, (b.waveTicks - age) / (b.waveTicks * 0.35));
        double h = b.far ? Math.max(6.0, Math.min(30.0, 4.0 + b.r * 0.3)) * (hot ? 0.35 : 1.0) * (1.0 - 0.55 * u)
                         : (3.0 + b.r * 0.25) * (hot ? 0.35 : 1.0) * (1.0 + 0.6 * u);
        if (b.type == WarheadType.FRAG) h *= 0.5;
        if (b.type == WarheadType.INCENDIARY) h *= 0.4;
        int seg = b.polarH != null ? ANG : (int) Math.min(360, Math.max(48, r * 2.0));
        double px = 0, pz = 0, pg = 0, ph = 0; float ps = 1.0F;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg, x = b.pos.x + Math.cos(a) * r, z = b.pos.z + Math.sin(a) * r, gy;
            float sh = 1.0F;
            if (b.polarH != null) { float[] pp = polar(b, a, r); gy = pp[0]; sh = pp[1]; }
            else gy = ground(level, x, z, b.pos.y);
            double hh = h * (0.4 + 0.6 * sh);
            if (i > 0) {
                if (hot) {
                    float k = (float) (1.0 - u / 0.4) * 0.8F;
                    v(bb, cam, px, pg - 0.2, pz, 1.0F, 0.75F, 0.4F, k * ps); v(bb, cam, x, gy - 0.2, z, 1.0F, 0.75F, 0.4F, k * sh);
                    v(bb, cam, x, gy + hh, z, 1.0F, 0.5F, 0.2F, 0.0F); v(bb, cam, px, pg + ph, pz, 1.0F, 0.5F, 0.2F, 0.0F);
                } else {
                    float k = 0.55F * fade;
                    v(bb, cam, px, pg - 0.3, pz, 0.55F, 0.49F, 0.41F, k * ps); v(bb, cam, x, gy - 0.3, z, 0.55F, 0.49F, 0.41F, k * sh);
                    v(bb, cam, x, gy + hh, z, 0.66F, 0.62F, 0.57F, 0.0F); v(bb, cam, px, pg + ph, pz, 0.66F, 0.62F, 0.57F, 0.0F);
                }
            }
            px = x; pz = z; pg = gy; ph = hh; ps = sh;
        }
        return true;
    }

    private static boolean surge(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age) {
        if (b.sx == null || age < 3 || age > b.colLife) return false;
        double tau = 40.0 + b.sR * 0.3, front = b.sR * 1.15 * (1.0 - Math.exp(-age / tau));
        double u = age / b.colLife;
        float fade = (float) Math.min(1.0, (1.0 - u) / 0.35) * (float) Math.min(1.0, age / 8.0);
        int covered = 0;
        while (covered < b.sd.length && b.sd[covered] <= front) covered++;
        if (covered == 0) return false;
        double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
        int budget = (int) Math.max(120, 700 * density);
        int stride = Math.max(1, (int) Math.ceil(covered * 2.0 / budget));
        double grow = Math.sqrt(stride);
        for (int i = 0; i < covered; i++) {
            long hsh = (b.seed ^ (i * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
            if (stride > 1 && Math.floorMod(hsh >> 17, stride) != 0) continue;
            double depth = b.sTop - b.sg[i];
            int n = (int) Math.max(1, Math.min(3, Math.round(depth / (b.sCell * 1.1))));
            float edge = (float) Math.min(1.0, (front - b.sd[i]) / (b.sCell * 3.0));
            for (int k = 0; k < n; k++) {
                double jx = (((hsh >> (8 + k * 6)) & 63) / 63.0 - 0.5) * b.sCell, jz = (((hsh >> (26 + k * 6)) & 63) / 63.0 - 0.5) * b.sCell;
                double x = b.sx[i] + jx + age * 0.01 * b.sCell, z = b.sz[i] + jz;
                double y = b.sg[i] + depth * (k + 0.5) / n + Math.sin(age * 0.025 + (hsh & 255)) * b.sCell * 0.15;
                double dx = x - cam.x, dz = z - cam.z;
                if (dx * dx + dz * dz > FAR_VIEW * FAR_VIEW) continue;
                double rad = b.sCell * 1.05 * grow * (0.85 + 0.3 * ((hsh >> 40) & 15) / 15.0) * (1.0 + 0.4 * u);
                float hgt = (float) ((k + 0.5) / n), c = 0.24F + 0.14F * hgt + 0.08F * (float) u;
                disc(bb, cam, left, up, x, y, z, rad, c * 1.04F, c, c * 0.9F, 0.5F * fade * edge, 8);
                float lit = Math.min(1.0F, c + 0.14F);
                disc(bb, cam, left, up, x + up.x() * rad * 0.2, y + up.y() * rad * 0.2, z + up.z() * rad * 0.2, rad * 0.6, lit, lit * 0.97F, lit * 0.9F, 0.28F * fade * edge, 8);
            }
        }
        return true;
    }

    private static boolean cloud(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f left, Vector3f up, Blast b, double age, double size, float tone, double fireTicks, double life) {
        if (age < 2 || age > life) return false;
        double grow = 1.0 - Math.exp(-age / 40.0), u = age / life;
        float fade = (float) Math.min(1.0, (1.0 - u) / 0.35) * (float) Math.min(1.0, age / 6.0);
        float fire = (float) Math.max(0.0, 1.0 - age / fireTicks);
        RandomSource rnd = RandomSource.create(b.seed);
        double rc = size * (1.0 + 1.4 * grow) * (1.0 + 0.35 * u);
        double cy = b.pos.y + size * 0.5 + (size * 1.6 + 6.0) * grow + age * 0.012;
        double drift = age * 0.02, spin = age * 0.0025;
        int n = 26;
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2 + spin * (rnd.nextBoolean() ? 1 : -1), e = Math.asin(rnd.nextDouble() * 1.6 - 0.6);
            double reach = 0.35 + 0.6 * Math.sqrt(rnd.nextDouble()), ph = rnd.nextDouble() * 6.3;
            double px = b.pos.x + Math.cos(a) * Math.cos(e) * rc * reach + drift * (0.5 + 0.5 * Math.sin(e)) * size * 0.4;
            double py = cy + Math.sin(e) * rc * 0.7 * reach + Math.sin(age * 0.03 + ph) * rc * 0.05;
            double pz = b.pos.z + Math.sin(a) * Math.cos(e) * rc * reach;
            double rad = rc * (0.38 + 0.25 * rnd.nextDouble());
            float h = (float) (0.5 + 0.5 * Math.sin(e));
            float c = (0.10F + 0.16F * h + 0.12F * (float) u) * tone;
            float glow = fire * (1.0F - h) * 0.9F;
            float r = c + (0.95F - c) * glow, g = c * 0.95F + (0.42F - c * 0.95F) * glow, bl = c * 0.9F + (0.12F - c * 0.9F) * glow;
            disc(bb, cam, left, up, px, py, pz, rad, r, g, bl, 0.58F * fade);
            double hx = up.x() * rad * 0.22, hy = up.y() * rad * 0.22, hz = up.z() * rad * 0.22;
            float lit = Math.min(1.0F, c + (0.16F + 0.1F * h) * tone);
            disc(bb, cam, left, up, px + hx, py + hy, pz + hz, rad * 0.62, lit, lit * 0.96F, lit * 0.9F, 0.34F * fade);
        }
        int m = 12;
        double sr = size * (0.8 + 1.6 * grow);
        for (int k = 0; k < m; k++) {
            double a = Math.PI * 2 * (k + rnd.nextDouble() * 0.6) / m, x = b.pos.x + Math.cos(a) * sr + drift * size * 0.15, z = b.pos.z + Math.sin(a) * sr;
            double rad = size * (0.5 + 0.35 * rnd.nextDouble()) * (0.7 + 0.5 * grow);
            float c = 0.34F + 0.06F * (float) rnd.nextDouble();
            disc(bb, cam, left, up, x, ground(level, x, z, b.pos.y) + rad * 0.45, z, rad, c * 1.05F, c, c * 0.88F, 0.45F * fade * (float) (1.0 - 0.5 * u));
        }
        return true;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * jolt * 1.4F);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (wash < 0.02F || !ClientConfig.explosionScreen()) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (220 * Math.min(1.0F, wash)) << 24) | 0xFFF4E0);
    }
}
