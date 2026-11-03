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
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFxChaff {

    private ClientFxChaff() {}

    static final int LIFE = 1800, FADE = 300;
    private static final double R = WarheadFx.CHAFF_RADIUS;
    private static final double TOP = 18.0, RIM = 4.0;
    private static final double TAU = 60.0;
    private static final double DRIFT = 0.004;
    private static final int CANS = 16;

    private static final class Show {
        final Vec3 pos;
        final int tier;
        final double r, g0, grow;
        final long start, seed;
        final int n, m;
        final double[] pa, pd, pl, ps, pp;
        final double[] ga, gd, gh, gp, gw;
        final double[] ca, cd, ct, ch;
        final double[] bx, bz, bg;
        final boolean[] bok;
        int missing;

        Show(ClientLevel level, Vec3 pos, int tier, double r, long start) {
            this.pos = pos; this.tier = tier; this.r = r; this.start = start;
            this.seed = Double.doubleToLongBits(pos.x * 37 + pos.z) ^ start ^ 0xC4AFL;
            double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
            n = (int) Math.max(90, Math.round(320 * density));
            m = (int) Math.max(40, Math.round(160 * density));
            grow = Math.max(1.0, Math.min(1.8, Math.sqrt(320.0 / n)));
            RandomSource rnd = RandomSource.create(seed);
            pa = new double[n]; pd = new double[n]; pl = new double[n]; ps = new double[n]; pp = new double[n];
            ga = new double[m]; gd = new double[m]; gh = new double[m]; gp = new double[m]; gw = new double[m];
            ca = new double[CANS]; cd = new double[CANS]; ct = new double[CANS]; ch = new double[CANS];
            int all = n + m + CANS;
            bx = new double[all]; bz = new double[all]; bg = new double[all]; bok = new boolean[all];
            for (int i = 0; i < n; i++) {
                double d;
                do { d = 0.97 * Math.sqrt(rnd.nextDouble()); } while (rnd.nextDouble() > domeH(d) / TOP);
                pa[i] = rnd.nextDouble() * Math.PI * 2; pd[i] = d; pl[i] = rnd.nextDouble(); ps[i] = rnd.nextDouble(); pp[i] = rnd.nextDouble() * Math.PI * 2;
                bx[i] = pos.x + Math.cos(pa[i]) * d * R; bz[i] = pos.z + Math.sin(pa[i]) * d * R;
            }
            for (int j = 0; j < m; j++) {
                ga[j] = rnd.nextDouble() * Math.PI * 2; gd[j] = 0.95 * Math.sqrt(rnd.nextDouble()); gh[j] = rnd.nextDouble();
                gp[j] = rnd.nextDouble() * Math.PI * 2; gw[j] = 0.12 + 0.25 * rnd.nextDouble();
                bx[n + j] = pos.x + Math.cos(ga[j]) * gd[j] * R; bz[n + j] = pos.z + Math.sin(ga[j]) * gd[j] * R;
            }
            for (int i = 0; i < CANS; i++) {
                ca[i] = Math.PI * 2 * (i + rnd.nextDouble() * 0.8) / CANS;
                cd[i] = R * (0.12 + 0.45 * Math.sqrt(rnd.nextDouble()));
                ct[i] = 2.0 + 16.0 * cd[i] / (0.57 * R) + rnd.nextDouble() * 3.0;
                ch[i] = 3.0 + rnd.nextDouble() * 8.0;
                bx[n + m + i] = pos.x + Math.cos(ca[i]) * cd[i]; bz[n + m + i] = pos.z + Math.sin(ca[i]) * cd[i];
            }
            g0 = ClientBlastGeo.ground(level, pos.x, pos.z, pos.y);
            missing = all;
            fillGround(level, this);
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();

    public static void start(double x, double y, double z, int tier, double r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        SHOWS.add(new Show(level, new Vec3(x, y, z), tier, r, level.getGameTime()));
    }

    private static double domeH(double d) { return RIM + (TOP - RIM) * Math.sqrt(Math.max(0.0, 1.0 - d * d)); }

    private static double front(double age) { return age <= 0 ? 0.0 : Math.min(1.0, 1.05 * (1.0 - Math.exp(-age / TAU))); }

    private static double arrive(double d) { return -TAU * Math.log(1.0 - Math.min(0.999, d / 1.05)); }

    private static double smooth(double x) { x = Math.max(0.0, Math.min(1.0, x)); return x * x * (3.0 - 2.0 * x); }

    private static double fade(double age) { return Math.max(0.0, Math.min(1.0, (LIFE - age) / FADE)); }

    private static void fillGround(ClientLevel level, Show s) {
        if (s.missing == 0) return;
        int miss = 0;
        for (int i = 0; i < s.bx.length; i++) {
            if (s.bok[i]) continue;
            int cx = (int) Math.floor(s.bx[i]) >> 4, cz = (int) Math.floor(s.bz[i]) >> 4;
            s.bok[i] = level.hasChunk(cx, cz);
            s.bg[i] = ClientBlastGeo.ground(level, s.bx[i], s.bz[i], s.g0);
            if (!s.bok[i]) miss++;
        }
        s.missing = miss;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { SHOWS.clear(); return; }
        long now = level.getGameTime();
        SHOWS.removeIf(s -> now - s.start > LIFE + 20 || now < s.start - 100);
        if (now % 40 == 0) for (Show s : SHOWS) fillGround(level, s);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        float sky = level.getSkyDarken(pt);
        float lk = 0.3F + 0.7F * sky, glk = 0.35F + 0.65F * sky;
        ClientBlastGeo.beginFrame();
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= canSmoke(smoke, cam, left, up, s, age, lk);
            any |= dome(smoke, cam, left, up, s, age, lk);
        }
        any |= camFog(smoke, cam, left, up, insideK(level, cam, now), lk, now);
        ClientBlastGeo.draw(event, smoke, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Show s : SHOWS) {
            if (cam.distanceTo(s.pos) > ClientBlastGeo.FAR_VIEW) continue;
            double age = now - s.start;
            any |= flashes(glow, cam, left, up, s, age);
            any |= glitter(glow, cam, left, up, s, age, glk);
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static double[] canAt(Show s, int i, double f) {
        double sx = s.pos.x, sy = s.g0 + 3.0, sz = s.pos.z;
        double ex = s.bx[s.n + s.m + i], ey = s.bg[s.n + s.m + i] + s.ch[i], ez = s.bz[s.n + s.m + i];
        double d = 1.0 - Math.pow(1.0 - f, 1.5);
        return new double[] { sx + (ex - sx) * d, sy + (ey - sy) * d + (2.0 + s.ch[i] * 0.5) * 4.0 * f * (1.0 - f), sz + (ez - sz) * d };
    }

    private static boolean canSmoke(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age, float lk) {
        if (age < 0 || age > 260) return false;
        double k0 = age / 260.0, r0 = 3.0 + 7.0 * Math.sqrt(Math.min(1.0, age / 40.0));
        float c0 = 0.85F * lk;
        ClientBlastGeo.disc(bb, cam, left, up, s.pos.x + age * DRIFT, s.g0 + 4.0 + age * 0.01, s.pos.z, r0, c0, c0, Math.min(1.0F, c0 * 1.03F), (float) (0.6 * Math.pow(1.0 - k0, 1.2)), 8);
        for (int i = 0; i < CANS; i++) {
            double bt = s.ct[i];
            float trail = (float) (0.35 * Math.max(0.0, Math.min(1.0, 1.0 - (age - bt) / 60.0)));
            if (trail > 0) {
                double f = Math.min(1.0, age / bt);
                double[] p = canAt(s, i, 0.0);
                for (int q = 1; q <= 3; q++) {
                    double[] p2 = canAt(s, i, f * q / 3.0);
                    ClientBlastGeo.streak(bb, cam, p[0], p[1], p[2], p2[0], p2[1], p2[2], 0.35, 0.92F * lk, 0.92F * lk, 0.94F * lk, trail * 0.6F, trail);
                    p = p2;
                }
            }
            double k = age - bt;
            if (k < 0) continue;
            double rad = 1.0 + 5.0 * Math.sqrt(Math.min(1.0, k / 30.0));
            float a = (float) (0.55 * Math.min(1.0, k / 3.0) * (1.0 - smooth((k - 60.0) / 140.0)));
            if (a <= 0.005F) continue;
            double[] e = canAt(s, i, 1.0);
            float c = 0.82F * lk, lit = Math.min(1.0F, c + 0.14F * lk);
            ClientBlastGeo.disc(bb, cam, left, up, e[0] + k * DRIFT, e[1] + k * 0.01, e[2], rad, c, c, Math.min(1.0F, c * 1.03F), a, 8);
            ClientBlastGeo.disc(bb, cam, left, up, e[0] + k * DRIFT + up.x() * rad * 0.22, e[1] + k * 0.01 + up.y() * rad * 0.22, e[2] + up.z() * rad * 0.22, rad * 0.62, lit, lit, lit, a * 0.6F, 8);
        }
        return true;
    }

    private static boolean dome(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age, float lk) {
        if (age < 4 || age > LIFE) return false;
        double u = age / LIFE, fd = fade(age);
        for (int i = 0; i < s.n; i++) {
            double d = s.pd[i], arr = arrive(d);
            if (age < arr) continue;
            double sw = smooth((age - arr) / 40.0), ph = s.pp[i], roll = Math.max(0.0, (d - 0.6) / 0.4), pl = s.pl[i];
            double rb = (6.5 + 4.0 * s.ps[i]) * (1.0 - 0.25 * d) * (1.15 - 0.3 * pl) * s.grow * (1.0 + 0.12 * u + 0.25 * (1.0 - fd));
            double rad = rb * (0.35 + 0.65 * sw);
            double dd = d * R * (0.82 + 0.18 * sw) + Math.cos(age * 0.018 + ph) * rb * 0.3 * roll;
            double a = s.pa[i] + Math.sin(age * 0.003 + ph) * 0.03;
            double y = s.bg[i] + rb * 0.35 + pl * Math.max(0.0, domeH(d) - rb * 0.6) + Math.sin(age * 0.018 + ph) * rb * 0.22 * roll + Math.sin(age * 0.01 + ph * 2) * 0.6;
            double x = s.pos.x + Math.cos(a) * dd + age * DRIFT * (0.6 + 0.6 * pl), z = s.pos.z + Math.sin(a) * dd;
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < rad * 0.45) continue;
            float near = (float) Math.min(1.0, (dist - rad * 0.45) / (rad * 0.8));
            float edge = (float) (1.0 - 0.55 * smooth((d - 0.7) / 0.3));
            float a0 = (float) (fd * sw) * edge * near;
            float c = (0.6F + 0.2F * (float) pl) * lk, lit = Math.min(1.0F, c + 0.16F * lk);
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, rad, c, c, Math.min(1.0F, c * 1.03F), 0.5F * a0, 8);
            double hx = up.x() * rad * 0.22, hy = up.y() * rad * 0.22, hz = up.z() * rad * 0.22;
            ClientBlastGeo.disc(bb, cam, left, up, x + hx, y + hy, z + hz, rad * 0.62, lit, lit, Math.min(1.0F, lit * 1.02F), 0.3F * a0, 8);
        }
        return true;
    }

    private static boolean flashes(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > 30) return false;
        if (age < 5) {
            float k = (float) (1.0 - age / 5.0);
            double size = 7.0 * (0.6 + 0.4 * age / 5.0);
            ClientBlastGeo.disc(bb, cam, left, up, s.pos.x, s.g0 + 4.0, s.pos.z, size * 2.3, 1.0F, 0.97F, 0.9F, 0.35F * k);
            ClientBlastGeo.disc(bb, cam, left, up, s.pos.x, s.g0 + 4.0, s.pos.z, size, 1.0F, 1.0F, 0.97F, 0.95F * k);
        }
        for (int i = 0; i < CANS; i++) {
            double bt = s.ct[i];
            if (age < bt) {
                double[] p = canAt(s, i, age / bt);
                ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], 0.45, 1.0F, 1.0F, 0.95F, 0.7F, 6);
                continue;
            }
            double k = (age - bt) / 4.0;
            if (k > 1.0) continue;
            double[] e = canAt(s, i, 1.0);
            ClientBlastGeo.disc(bb, cam, left, up, e[0], e[1], e[2], 6.0, 1.0F, 0.97F, 0.92F, (float) (0.3 * (1.0 - k)), 8);
            ClientBlastGeo.disc(bb, cam, left, up, e[0], e[1], e[2], 2.6, 1.0F, 1.0F, 1.0F, (float) (0.9 * (1.0 - k)), 8);
        }
        return true;
    }

    private static boolean glitter(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age, float glk) {
        if (age < 15 || age > LIFE) return false;
        double fr = front(age), fd = fade(age);
        for (int j = 0; j < s.m; j++) {
            double d = s.gd[j];
            if (d > fr) continue;
            double sn = Math.sin(age * s.gw[j] + s.gp[j]);
            if (sn < 0.72) continue;
            double tw = Math.pow((sn - 0.72) / 0.28, 2.0) * (0.6 + 0.4 * Math.sin(age * s.gw[j] * 3.7 + s.gp[j] * 2.0));
            double h = domeH(d) * 0.95, fall = ((s.gh[j] * h - (age - 15) * 0.015) % h + h) % h;
            double x = s.pos.x + Math.cos(s.ga[j]) * d * R + age * DRIFT + Math.sin(age * 0.05 + s.gp[j]) * 0.4;
            double z = s.pos.z + Math.sin(s.ga[j]) * d * R + Math.cos(age * 0.043 + s.gp[j]) * 0.4;
            double y = s.bg[s.n + j] + 1.0 + fall;
            double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
            ClientBlastGeo.disc(bb, cam, left, up, x, y, z, 0.2 + dist * 0.0035, 0.92F, 0.95F, 1.0F, (float) (0.95 * tw * fd) * glk, 4);
        }
        return true;
    }

    private static float insideK(ClientLevel level, Vec3 cam, double now) {
        float best = 0.0F;
        for (Show s : SHOWS) {
            double age = now - s.start;
            if (age < 4 || age > LIFE) continue;
            double cx = s.pos.x + age * DRIFT * 0.9, cz = s.pos.z;
            double dh = Math.hypot(cam.x - cx, cam.z - cz) / R, fr = front(age);
            if (dh > fr) continue;
            double h = domeH(dh) * 0.9, above = cam.y - ClientBlastGeo.ground(level, cam.x, cam.z, s.g0);
            double kv = Math.max(0.0, Math.min(1.0, (h - above) / (h * 0.35)));
            double ke = Math.max(0.0, Math.min(1.0, (fr - dh) / 0.2));
            double core = 1.0 - 0.5 * smooth((dh - 0.6) / 0.4);
            best = Math.max(best, (float) (kv * ke * core * fade(age)));
        }
        return best;
    }

    private static boolean camFog(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, float k, float lk, double now) {
        if (k < 0.03F) return false;
        float c = 0.72F * lk;
        for (int j = 0; j < 6; j++) {
            double a = j * Math.PI / 3.0 + now * 0.004;
            ClientBlastGeo.disc(bb, cam, left, up, cam.x + Math.cos(a) * 5.0, cam.y + Math.sin(now * 0.02 + j) * 1.5, cam.z + Math.sin(a) * 5.0, 7.0, c, c, Math.min(1.0F, c * 1.03F), 0.22F * k, 8);
        }
        ClientBlastGeo.disc(bb, cam, left, up, cam.x, cam.y + 4.0, cam.z, 8.0, c, c, Math.min(1.0F, c * 1.03F), 0.22F * k, 8);
        ClientBlastGeo.disc(bb, cam, left, up, cam.x, cam.y - 3.0, cam.z, 8.0, c, c, Math.min(1.0F, c * 1.03F), 0.22F * k, 8);
        return true;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (SHOWS.isEmpty() || event.getType() != FogType.NONE) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float k = insideK(level, event.getCamera().getPosition(), level.getGameTime() + event.getPartialTick());
        if (k < 0.01F) return;
        float far = event.getFarPlaneDistance(), near = event.getNearPlaneDistance(), target = 12.0F;
        if (far <= target) return;
        event.setFarPlaneDistance(far + (target - far) * k);
        event.setNearPlaneDistance(near * (1.0F - k));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (SHOWS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float k = insideK(level, event.getCamera().getPosition(), level.getGameTime() + event.getPartialTick());
        if (k < 0.01F) return;
        float c = 0.72F * (0.3F + 0.7F * level.getSkyDarken((float) event.getPartialTick()));
        event.setRed(event.getRed() + (c - event.getRed()) * k);
        event.setGreen(event.getGreen() + (c - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (Math.min(1.0F, c * 1.03F) - event.getBlue()) * k);
    }
}
