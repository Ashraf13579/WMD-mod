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
public final class ClientFxNuke {

    private ClientFxNuke() {}

    static final int DOME_START = 10, FIRE_TICKS = 200, GLOW_TICKS = 600, HAZE_START = 20, HAZE_TICKS = 400, LIFE = 640;
    static final double SKY_R = 3000.0, GLOW_R = 1500.0;
    private static final int RINGS = 12, SEGS = 48;
    private static final int HANG = 48, HRINGS = 8;

    private static final class Show {
        final double x, y, z, r, g0, reach, domeMax, hazeR;
        final int tier, waveTicks, domeLife;
        final boolean thermo;
        final long start, seed;
        final float[][] noise;
        final double[] da, de, ds;
        float[][] hazeH;
        Show(ClientLevel level, double x, double y, double z, int tier, double r, boolean thermo, long start, boolean shell) {
            this.x = x; this.y = y; this.z = z; this.tier = tier; this.r = r; this.thermo = thermo; this.start = start;
            this.seed = Double.doubleToLongBits(x * 43.0 + z) ^ start ^ 0x4E4BL;
            WarheadType type = thermo ? WarheadType.THERMO : WarheadType.NUKE;
            this.reach = WarheadFx.waveReach(type, tier, r);
            this.waveTicks = WarheadFx.waveTicks(type, reach);
            this.domeMax = r * (thermo ? 3.2 : 2.6);
            this.domeLife = thermo ? 60 : 50;
            this.hazeR = r * 2.8;
            this.g0 = shell ? y : ClientBlastGeo.ground(level, x, z, y);
            double density = ClientConfig.SPEC.isLoaded() ? ClientConfig.SMOKE_DENSITY.get() : 1.0;
            int n = (int) Math.max(20, Math.round(60 * density));
            RandomSource rnd = RandomSource.create(seed);
            noise = new float[RINGS + 1][SEGS];
            for (int i = 0; i <= RINGS; i++) for (int j = 0; j < SEGS; j++) noise[i][j] = rnd.nextFloat() * 2.0F - 1.0F;
            da = new double[n]; de = new double[n]; ds = new double[n];
            for (int k = 0; k < n; k++) {
                da[k] = rnd.nextDouble() * Math.PI * 2; de[k] = Math.asin(0.1 + 0.9 * rnd.nextDouble()); ds[k] = 0.8 + 0.4 * rnd.nextDouble();
            }
            if (thermo) {
                hazeH = new float[HANG][HRINGS + 1];
                for (int i = 0; i < HANG; i++) {
                    double a = Math.PI * 2 * i / HANG, last = g0;
                    for (int j = 0; j <= HRINGS; j++) {
                        double d = hazeR * j / HRINGS;
                        last = shell ? g0 : ClientBlastGeo.ground(level, x + Math.cos(a) * d, z + Math.sin(a) * d, last);
                        hazeH[i][j] = (float) last;
                    }
                }
            }
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();

    public static void start(double x, double y, double z, int tier, double r, boolean thermo) { start(x, y, z, tier, r, thermo, false); }

    public static void start(double x, double y, double z, int tier, double r, boolean thermo, boolean shell) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        SHOWS.add(new Show(level, x, y, z, tier, Math.max(8.0, r), thermo, level.getGameTime(), shell));
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double smooth(double v) { v = clamp01(v); return v * v * (3.0 - 2.0 * v); }

    static double flashK(double age, boolean thermo) {
        if (age < 0) return 0.0;
        if (!thermo) return age <= 20 ? 1.0 : Math.pow(clamp01(1.0 - (age - 20) / 70.0), 1.6);
        double first = Math.exp(-age / 2.5) * (age < 8 ? 1.0 : 0.0);
        double a2 = age - 8;
        double second = a2 < 0 ? 0.0 : a2 < 3 ? a2 / 3.0 : a2 <= 24 ? 1.0 : Math.pow(clamp01(1.0 - (a2 - 24) / 80.0), 1.6);
        return Math.max(first, second);
    }

    static double glowEnv(double age) {
        if (age < 0 || age > GLOW_TICKS) return 0.0;
        return clamp01(age / 30.0) * Math.pow(1.0 - age / GLOW_TICKS, 1.3);
    }

    private static double[] mushroom(Show s, double age) {
        double stem = Math.min(Math.max(0.0, age) * 1.1, s.r * 2.6), grow = Math.min(1.0, Math.max(0.0, age) / 90.0);
        double Rc = s.r * (0.35 + 0.75 * grow) * 1.15, rc = Rc * 0.45;
        return new double[] { s.y + stem, s.y + stem + rc * 0.5, Rc, rc };
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SHOWS.clear(); return; }
        long now = level.getGameTime();
        SHOWS.removeIf(s -> now - s.start > LIFE || now < s.start - 100);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Show s : SHOWS) {
            double age = now - s.start;

            if (Math.hypot(cam.x - s.x, cam.z - s.z) < SKY_R && flashK(age, s.thermo) > 0.5) level.setSkyFlashTime(2);
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
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder shell = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Show s : SHOWS) {
            if (Math.hypot(cam.x - s.x, cam.z - s.z) > ClientBlastGeo.FAR_VIEW) continue;
            any |= dome(shell, cam, left, up, s, now - s.start);
        }
        ClientBlastGeo.draw(event, shell, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Show s : SHOWS) {
            double age = now - s.start, dh = Math.hypot(cam.x - s.x, cam.z - s.z);
            any |= sky(glow, cam, s, age, dh);
            if (dh > ClientBlastGeo.FAR_VIEW) continue;
            any |= haze(glow, cam, s, age, now);
            any |= fireball(glow, cam, left, up, s, age);
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static double domeR(Show s, double age) {
        double t = age - DOME_START, u = clamp01(t / s.domeLife);
        double D = s.domeMax * (0.55 + 0.45 * (1.0 - Math.exp(-t / 8.0))) * (1.0 + 0.1 * u);
        double front = WarheadFx.waveRadius(age, s.reach, s.waveTicks);
        return front > 0 ? Math.min(D, front * 0.92) : D;
    }

    private static boolean dome(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        double t = age - DOME_START;
        if (t < 0 || t > s.domeLife) return false;
        double u = t / s.domeLife, D = domeR(s, age), cy = s.g0 - D * 0.1, e0 = Math.asin(0.1);
        double cut = -0.3 + (Math.PI / 2 + 0.6) * smooth((u - 0.2) / 0.8);
        float fadeIn = (float) clamp01(t / 3.0), base = (s.thermo ? 0.6F : 0.55F) * fadeIn;
        float warm = (float) clamp01(1.0 - age / 60.0);
        for (int i = 0; i < RINGS; i++) {
            double ea = e0 + (Math.PI / 2 - e0) * i / RINGS, eb = e0 + (Math.PI / 2 - e0) * (i + 1) / RINGS;
            for (int j = 0; j < SEGS; j++) {
                double a0 = Math.PI * 2 * j / SEGS, a1 = Math.PI * 2 * (j + 1) / SEGS;
                int j1 = (j + 1) % SEGS;
                shellV(bb, cam, s, D, cy, a0, ea, e0, cut, s.noise[i][j], base, warm);
                shellV(bb, cam, s, D, cy, a1, ea, e0, cut, s.noise[i][j1], base, warm);
                shellV(bb, cam, s, D, cy, a1, eb, e0, cut, s.noise[i + 1][j1], base, warm);
                shellV(bb, cam, s, D, cy, a0, eb, e0, cut, s.noise[i + 1][j], base, warm);
            }
        }
        for (int k = 0; k < s.da.length; k++) {
            double e = s.de[k], ce = Math.cos(e);
            float dis = (float) clamp01((e + 0.15 * Math.sin(k * 2.7) - cut) / 0.3);
            if (dis <= 0.01F) continue;
            double px = s.x + Math.cos(s.da[k]) * ce * D, py = cy + Math.sin(e) * D, pz = s.z + Math.sin(s.da[k]) * ce * D;
            float c = 0.96F + 0.04F * (float) Math.sin(e);
            ClientBlastGeo.disc(bb, cam, left, up, px, py, pz, D * 0.2 * s.ds[k], c, c, Math.min(1.0F, c * 1.03F), 0.16F * fadeIn * dis, 8);
        }
        return true;
    }

    private static void shellV(BufferBuilder bb, Vec3 cam, Show s, double D, double cy, double a, double e, double e0, double cut, float noise, float base, float warm) {
        double ce = Math.cos(e), nx = Math.cos(a) * ce, ny = Math.sin(e), nz = Math.sin(a) * ce;
        double px = s.x + nx * D, py = cy + ny * D, pz = s.z + nz * D;
        double vx = px - cam.x, vy = py - cam.y, vz = pz - cam.z, l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
        double limb = 1.0 - Math.abs((nx * vx + ny * vy + nz * vz) / l);
        double dis = clamp01((e + 0.22 * noise - cut) / 0.3), foot = clamp01((e - e0) / 0.12);
        float al = base * (float) (dis * foot * (0.3 + 0.7 * Math.pow(limb, 1.5)) * (0.8 + 0.2 * ny));
        float w = warm * (float) (1.0 - ny) * 0.6F;
        ClientBlastGeo.v(bb, cam, px, py, pz, 0.97F + 0.03F * w, 0.97F - 0.07F * w, 1.0F - 0.18F * w, al);
    }

    private static double[] near(Vec3 cam, double x, double y, double z, double k) {
        double dx = cam.x - x, dy = cam.y - y, dz = cam.z - z, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-6, m = Math.min(k, l * 0.5) / l;
        return new double[] { x + dx * m, y + dy * m, z + dz * m };
    }

    private static boolean fireball(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Show s, double age) {
        if (age < 0 || age > FIRE_TICKS) return false;
        double fk = flashK(age, s.thermo);
        if (fk > 0.01) {
            ClientBlastGeo.disc(bb, cam, left, up, s.x, s.g0 + s.r * 0.3, s.z, s.r * 3.2, 1.0F, 0.98F, 0.92F, (float) (0.5 * fk));
            ClientBlastGeo.disc(bb, cam, left, up, s.x, s.g0 + s.r * 0.3, s.z, s.r * 1.2, 1.0F, 1.0F, 1.0F, (float) (0.95 * fk));
        }
        double[] m = mushroom(s, age);
        double Rc = m[2], rc = m[3];
        float heat = (float) clamp01(1.0 - age / 70.0), fade = (float) Math.pow(1.0 - age / FIRE_TICKS, 1.1);
        double early = s.r * 0.9 * Math.min(1.0, 0.4 + age / 5.0);
        double rad = Math.max(early, Rc * 0.75) * (s.thermo ? 1.1 : 1.0);
        double by = Math.max(s.y + s.r * 0.35 * Math.min(1.0, age / 6.0), m[1]);
        double[] p = age > 12 ? near(cam, s.x, by, s.z, rc * 0.9) : new double[] { s.x, by, s.z };
        ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], rad * 2.2, 1.0F, 0.5F + 0.3F * heat, 0.2F + 0.3F * heat, 0.3F * fade);
        ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], rad, 1.0F, 0.7F + 0.3F * heat, 0.35F + 0.6F * heat, 0.7F * fade);
        ClientBlastGeo.disc(bb, cam, left, up, p[0], p[1], p[2], rad * 0.5, 1.0F, 1.0F, 0.9F + 0.1F * heat, (0.9F * heat + 0.15F) * fade);
        float capIn = (float) clamp01((age - 12) / 20.0) * fade;
        if (capIn > 0.01F) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + age * 0.004;
                double[] q = near(cam, s.x + Math.cos(a) * Rc, m[1], s.z + Math.sin(a) * Rc, rc * 0.8);
                ClientBlastGeo.disc(bb, cam, left, up, q[0], q[1], q[2], rc * 1.3, 1.0F, 0.45F + 0.25F * heat, 0.15F + 0.2F * heat, 0.35F * capIn);
            }
        }
        float stemK = (float) clamp01(1.0 - age / 120.0) * (float) clamp01(age / 6.0);
        if (stemK > 0.01F) {
            double h = m[0] - s.y;
            for (int i = 0; i < 6; i++) {
                double f = (i + 0.5) / 6.0;
                double[] q = near(cam, s.x, s.y + h * f, s.z, s.r * 0.25);
                ClientBlastGeo.disc(bb, cam, left, up, q[0], q[1], q[2], s.r * 0.3 * (1.0 - 0.4 * f), 1.0F, 0.5F + 0.2F * heat, 0.2F, 0.3F * stemK);
            }
        }
        return true;
    }

    private static boolean sky(BufferBuilder bb, Vec3 cam, Show s, double age, double dh) {
        double fW = flashK(age, s.thermo) * Math.sqrt(clamp01(1.0 - dh / SKY_R)) * 0.55;
        double gW = glowEnv(age) * Math.pow(clamp01(1.0 - dh / GLOW_R), 0.7) * (s.thermo ? 0.36 : 0.3);
        if (fW + gW < 0.004) return false;
        double bx = dh < 1.0 ? 0.0 : (s.x - cam.x) / dh, bz = dh < 1.0 ? 0.0 : (s.z - cam.z) / dh;
        double rad = ClientBlastGeo.LIM * 0.96;
        int rings = 7, segs = 32;
        for (int i = 0; i < rings; i++) {
            double ea = -0.12 + (Math.PI / 2 + 0.12) * i / rings, eb = -0.12 + (Math.PI / 2 + 0.12) * (i + 1) / rings;
            for (int j = 0; j < segs; j++) {
                double a0 = Math.PI * 2 * j / segs, a1 = Math.PI * 2 * (j + 1) / segs;
                skyV(bb, cam, rad, a0, ea, bx, bz, fW, gW);
                skyV(bb, cam, rad, a1, ea, bx, bz, fW, gW);
                skyV(bb, cam, rad, a1, eb, bx, bz, fW, gW);
                skyV(bb, cam, rad, a0, eb, bx, bz, fW, gW);
            }
        }
        return true;
    }

    private static void skyV(BufferBuilder bb, Vec3 cam, double rad, double a, double e, double bx, double bz, double fW, double gW) {
        double ce = Math.cos(e), hx = Math.cos(a), hz = Math.sin(a), toward = (1.0 + hx * bx + hz * bz) * 0.5;
        double f = fW * (0.6 + 0.4 * toward), g = gW * (0.25 + 0.75 * toward * toward) * (1.0 - 0.7 * Math.max(0.0, Math.sin(e)));
        double sum = f + g + 1e-6;
        float r = 1.0F, gr = (float) ((f * 0.98 + g * 0.42) / sum), bl = (float) ((f * 0.93 + g * 0.18) / sum);
        ClientBlastGeo.v(bb, cam, cam.x + hx * ce * rad, cam.y + Math.sin(e) * rad, cam.z + hz * ce * rad, r, gr, bl, (float) Math.min(0.85, sum));
    }

    private static boolean haze(BufferBuilder bb, Vec3 cam, Show s, double age, double now) {
        if (s.hazeH == null) return false;
        double t = age - HAZE_START;
        if (t < 0 || t > HAZE_TICKS) return false;
        float env = (float) (clamp01(t / 40.0) * clamp01((HAZE_TICKS - t) / 160.0));
        if (env < 0.01F) return false;
        for (int layer = 0; layer < 2; layer++) {
            double h = layer == 0 ? 2.5 : 7.0;
            for (int i = 0; i < HANG; i++) {
                int i1 = (i + 1) % HANG;
                for (int j = 0; j < HRINGS; j++) {
                    hazeV(bb, cam, s, i, j, h, layer, env, now);
                    hazeV(bb, cam, s, i1, j, h, layer, env, now);
                    hazeV(bb, cam, s, i1, j + 1, h, layer, env, now);
                    hazeV(bb, cam, s, i, j + 1, h, layer, env, now);
                }
            }
        }
        return true;
    }

    private static void hazeV(BufferBuilder bb, Vec3 cam, Show s, int i, int j, double h, int layer, float env, double now) {
        double a = Math.PI * 2 * i / HANG, d = s.hazeR * j / HRINGS;
        double shim = 0.5 + 0.5 * Math.sin(now * 0.4 + i * 1.7 + j * 2.3 + layer * 1.1) * Math.sin(now * 0.23 + i * 0.9 - j * 1.3 + layer);
        double wob = 0.6 * Math.sin(now * 0.5 + i * 1.3 + j * 0.7 + layer * 2.0);
        double edge = 1.0 - Math.pow((double) j / HRINGS, 2.0);
        float al = (float) (0.06 * env * shim * edge) * (layer == 0 ? 1.0F : 0.7F);
        ClientBlastGeo.v(bb, cam, s.x + Math.cos(a) * d, s.hazeH[i][j] + h + wob, s.z + Math.sin(a) * d, 1.0F, 0.6F, 0.3F, al);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (SHOWS.isEmpty() || !ClientConfig.explosionScreen()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double now = level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        double best = 0.0, warmAt = 0.0;
        for (Show s : SHOWS) {
            double age = now - s.start, d = cam.distanceTo(new Vec3(s.x, s.g0, s.z)), washR = s.thermo ? 1800.0 : 1200.0;
            double w = flashK(age, s.thermo) * Math.pow(clamp01(1.0 - d / washR), 0.6);
            if (w > best) { best = w; warmAt = clamp01((age - 20) / 70.0); }
        }
        if (best < 0.02) return;
        int r = 255, g = (int) (255 - 29 * warmAt), b = (int) (255 - 71 * warmAt);
        GuiGraphics gg = event.getGuiGraphics();
        gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), ((int) (250 * Math.min(1.0, best)) << 24) | (r << 16) | (g << 8) | b);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (SHOWS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double now = level.getGameTime() + event.getPartialTick();
        for (Show s : SHOWS) {
            double age = now - s.start, dh = Math.hypot(cam.x - s.x, cam.z - s.z);
            float g = (float) (glowEnv(age) * Math.pow(clamp01(1.0 - dh / GLOW_R), 0.7) * 0.45);
            if (g > 0.005F) {
                event.setRed(event.getRed() + (0.95F - event.getRed()) * g);
                event.setGreen(event.getGreen() + (0.45F - event.getGreen()) * g);
                event.setBlue(event.getBlue() + (0.22F - event.getBlue()) * g);
            }
            float f = (float) (flashK(age, s.thermo) * Math.sqrt(clamp01(1.0 - dh / SKY_R)) * 0.9);
            if (f > 0.005F) {
                event.setRed(event.getRed() + (1.0F - event.getRed()) * f);
                event.setGreen(event.getGreen() + (0.97F - event.getGreen()) * f);
                event.setBlue(event.getBlue() + (0.92F - event.getBlue()) * f);
            }
        }
    }
}
