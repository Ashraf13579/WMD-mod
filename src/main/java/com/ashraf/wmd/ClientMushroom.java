package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientMushroom {

    private ClientMushroom() {}

    static final int LIFE = 2_400, DRIFT_AT = 1_200, CAP_PUFFS = 380, STEM_PUFFS = 200, COLLAR_PUFFS = 36, SKIRT_PUFFS = 40, SURGE_PUFFS = 90;

    private record Show(double x, double y, double z, double g0, double r, long start, float[] seed) {}

    private static final List<Show> SHOWS = new ArrayList<>();

    static void start(double x, double y, double z, double g0, double r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float[] seed = new float[(CAP_PUFFS + STEM_PUFFS + COLLAR_PUFFS + SKIRT_PUFFS + SURGE_PUFFS) * 4];
        var rnd = mc.level.random;
        for (int i = 0; i < seed.length; i++) seed[i] = rnd.nextFloat();
        SHOWS.add(new Show(x, y, z, g0, r, mc.level.getGameTime(), seed));
        while (SHOWS.size() > 3) SHOWS.remove(0);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { SHOWS.clear(); }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v; }

    private record Puff(double x, double y, double z, double rad, float r, float g, float b, float a, float lr, float lg, float lb, double d2) {}

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SHOWS.clear(); return; }
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float sky = 0.25F + 0.75F * level.getSkyDarken(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        List<Puff> puffs = new ArrayList<>(1600);
        for (Iterator<Show> it = SHOWS.iterator(); it.hasNext(); ) {
            Show s = it.next();
            double age = now - s.start();
            if (age > LIFE) { it.remove(); continue; }
            if (age < 0.0) continue;
            if (DhBridge.ready && Math.hypot(cam.x - s.x(), cam.z - s.z()) > ClientBlastGeo.FAR_VIEW) continue;
            build(s, age, sky, cam, puffs);
        }
        if (puffs.isEmpty()) return;
        puffs.sort((p, q) -> Double.compare(q.d2(), p.d2()));
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Puff p : puffs) {
            ClientBlastGeo.disc(bb, cam, left, up, p.x(), p.y(), p.z(), p.rad(), p.r(), p.g(), p.b(), p.a(), 10);
            double o = p.rad() * 0.28;
            ClientBlastGeo.disc(bb, cam, left, up, p.x() + up.x() * o, p.y() + up.y() * o, p.z() + up.z() * o, p.rad() * 0.62, p.lr(), p.lg(), p.lb(), p.a() * 0.75F, 10);
        }
        ClientBlastGeo.draw(event, bb, true, false);
    }

    private static void build(Show s, double age, float sky, Vec3 cam, List<Puff> out) {
        float[] sd = s.seed();
        double R = s.r();
        double stem = Math.min(age * 1.1, R * 2.6), grow = clamp01(age / 90.0);
        double drift = clamp01((age - DRIFT_AT) / (double) (LIFE - DRIFT_AT));
        double life = Math.pow(1.0 - drift, 1.2);
        double Rc = R * (0.35 + 0.75 * grow) * 1.15 * (1.0 + 0.35 * drift), rc = Rc * 0.45 * (1.0 - 0.25 * drift);
        double capY = s.y() + stem + rc * 0.5, wind = drift * R * 1.5;
        double heat = clamp01(1.0 - age / 400.0);
        int k = 0;

        for (int i = 0; i < CAP_PUFFS; i++, k += 4) {
            double th = sd[k] * Math.PI * 2, ph = sd[k + 1] * Math.PI * 2 - age * 0.012, j = 0.55 + 0.6 * sd[k + 2];
            double rr = Rc + Math.cos(ph) * rc * j, py = capY + Math.sin(ph) * rc * 0.8 * j;
            double px = s.x() + Math.cos(th) * rr + wind, pz = s.z() + Math.sin(th) * rr;
            double top = Math.sin(ph), shade = 0.72 + 0.28 * top;
            double glow = heat * (0.5 + 0.5 * (1.0 - top) * 0.5);
            float br = (float) (0.60 * shade * sky + 0.55 * glow), bg = (float) (0.54 * shade * sky + 0.25 * glow), bb = (float) (0.48 * shade * sky + 0.06 * glow);
            add(out, cam, px, py, pz, rc * (0.42 + 0.35 * sd[k + 3]) * (0.4 + 0.6 * grow), br, bg, bb, (float) (0.92 * life), 1.18F, (float) clamp01(age / 6.0));
        }

        double foot = s.g0(), topY = capY - rc * 0.3;
        for (int i = 0; i < STEM_PUFFS; i++, k += 4) {
            double u = (sd[k] + age * 0.0012) % 1.0, yy = foot + (topY - foot) * u;
            if (yy > s.y() + stem) continue;
            double w = R * (0.34 - 0.2 * u) * (0.5 + 0.6 * sd[k + 1]), a = sd[k + 2] * Math.PI * 2 + age * 0.008 + u * 3.0;
            double px = s.x() + Math.cos(a) * w * 0.7 + wind * u, pz = s.z() + Math.sin(a) * w * 0.7;
            double dust = 1.0 - u, hot = heat * (1.0 - u * 0.5);
            float br = (float) ((0.50 + 0.12 * dust) * sky + 0.5 * hot * 0.6), bg = (float) ((0.46 + 0.10 * dust) * sky + 0.22 * hot * 0.6), bb = (float) ((0.42 + 0.06 * dust) * sky + 0.05 * hot * 0.6);
            add(out, cam, px, yy, pz, R * (0.16 + 0.12 * sd[k + 3]), br, bg, bb, (float) (0.88 * life * Math.sin(Math.PI * Math.max(0.05, u))), 1.2F, 1.0F);
        }

        double ca = clamp01((age - 30.0) / 30.0) * clamp01((400.0 - age) / 120.0);
        for (int i = 0; i < COLLAR_PUFFS; i++, k += 4) {
            if (ca <= 0.0) continue;
            double a = Math.PI * 2 * i / COLLAR_PUFFS + sd[k] * 0.15, cr = R * (0.7 + 0.25 * grow) * (0.9 + 0.2 * sd[k + 1]);
            double yy = foot + stem * (0.42 + 0.08 * clamp01(age / 300.0));
            add(out, cam, s.x() + Math.cos(a) * cr + wind * 0.5, yy, s.z() + Math.sin(a) * cr, R * (0.12 + 0.06 * sd[k + 2]), 0.95F * sky + 0.05F, 0.96F * sky + 0.04F, 0.98F * sky + 0.02F, (float) (0.7 * ca * life), 1.05F, 1.0F);
        }

        for (int i = 0; i < SKIRT_PUFFS; i++, k += 4) {
            if (age < 40.0) continue;
            double a = sd[k] * Math.PI * 2, rr = Rc * (0.55 + 0.3 * sd[k + 1]), yy = capY - rc * (0.8 + 0.6 * sd[k + 2]);
            add(out, cam, s.x() + Math.cos(a) * rr + wind, yy, s.z() + Math.sin(a) * rr, rc * (0.35 + 0.2 * sd[k + 3]), 0.5F * sky, 0.46F * sky, 0.42F * sky, (float) (0.75 * life * clamp01((age - 40.0) / 60.0)), 1.2F, 1.0F);
        }

        double sf = clamp01(age / 600.0), sa = clamp01((900.0 - age) / 400.0) * life;
        for (int i = 0; i < SURGE_PUFFS; i++, k += 4) {
            if (sa <= 0.0) continue;
            double a = Math.PI * 2 * i / SURGE_PUFFS + sd[k] * 0.1, rr = R * (0.4 + 2.2 * (1.0 - (1.0 - sf) * (1.0 - sf))) * (0.92 + 0.16 * sd[k + 1]);
            double h = R * (0.08 + 0.1 * sd[k + 2]);
            add(out, cam, s.x() + Math.cos(a) * rr, foot + h, s.z() + Math.sin(a) * rr, R * (0.14 + 0.1 * sd[k + 3]), 0.62F * sky, 0.56F * sky, 0.48F * sky, (float) (0.7 * sa), 1.2F, 1.0F);
        }
    }

    private static void add(List<Puff> out, Vec3 cam, double x, double y, double z, double rad, float r, float g, float b, float a, float lift, float inK) {
        if (a <= 0.01F || rad <= 0.1) return;
        float lr = Math.min(1.0F, r * lift), lg = Math.min(1.0F, g * lift), lb = Math.min(1.0F, b * lift);
        lr = r + (lr - r) * inK; lg = g + (lg - g) * inK; lb = b + (lb - b) * inK;
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        out.add(new Puff(x, y, z, rad, Math.min(1.0F, r), Math.min(1.0F, g), Math.min(1.0F, b), Math.min(1.0F, a), lr, lg, lb, dx * dx + dy * dy + dz * dz));
    }
}
