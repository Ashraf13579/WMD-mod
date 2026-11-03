package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAsteroid {

    private ClientAsteroid() {}

    private static final float PX = 1.0F / 16.0F, S = 0.66F;
    private static final double FAR = 400.0;

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!Orbit.isOrbit(mc.level)) {
            if (!AsteroidBayPayloads.FALLS.isEmpty() || !WAVES.isEmpty() || !SMOKE.isEmpty() || !AFTER.isEmpty()) ground(event, mc);
            return;
        }
        if (AsteroidBayPayloads.TUGS.isEmpty()) return;
        MeshModel tug = MeshModel.named("asteroid_tug"), rock = MeshModel.named("asteroid_rock");
        if (!tug.has("tug") || !rock.has("rock")) return;
        double t = ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (AsteroidBayPayloads.Tug g : AsteroidBayPayloads.TUGS.values()) {
            Direction f = Direction.from3DDataValue(g.facing());
            if (f.getAxis().isVertical()) continue;
            BlockPos o = new BlockPos(g.ox(), g.oy(), g.oz());
            Vec3 a = AsteroidRedirect.axis(f), doors = AsteroidRedirect.doors(o, f);
            double el = t - g.t0(), size = Math.max(5, g.size()), r = size / 2.0;
            switch (g.state()) {
                case AsteroidBay.LAUNCH -> {
                    if (el < Balance.TUG_LEAVE_AT) break;
                    double u = el - Balance.TUG_LEAVE_AT;
                    Vec3 tail = AsteroidRedirect.launchTail(o, f, el), dir = AsteroidRedirect.launchTail(o, f, el + 2.0).subtract(tail);
                    float w1 = (float) AsteroidRedirect.smooth(u / 120.0), w2 = (float) AsteroidRedirect.smooth((u - 120.0) / 120.0);
                    drawTug(tug, ps, buffers, cam, tail, dir.lengthSqr() < 1e-6 ? a : dir.normalize(), w1, w2, 0.0F, 0.0F, 1.0F, 1.0F);
                    any |= exhaust(bb, cam, tail, dir.lengthSqr() < 1e-6 ? a : dir.normalize(), 1.0);
                }
                case AsteroidBay.CHASE -> {
                    Vec3 p = AsteroidRedirect.chaseTug(o, f, el);
                    any |= glint(bb, cam, p, 0.25F, 0.55F, 1.0F);
                }
                case AsteroidBay.RETURN -> {
                    double u = Math.min(1.0, el / Balance.TUG_RETURN_TICKS);
                    Vec3 far = AsteroidRedirect.chaseTug(o, f, 600.0), p = far.add(doors.subtract(far).scale(AsteroidRedirect.smooth(u)));
                    if (u < 0.98) any |= glint(bb, cam, p, 0.25F, 0.55F, 1.0F);
                }
                case AsteroidBay.CAPTURE -> any |= capture(tug, rock, ps, buffers, bb, cam, AsteroidRedirect.capturePoint(doors, f), a, r, el, t, g.seed());
                case AsteroidBay.FALL -> {
                    if (el > 1_200) break;
                    Vec3 p0 = AsteroidRedirect.parked(doors, f, size), c = p0.add(a.scale(0.002 * el * el * 0.3)).add(0.0, -0.003 * el * el, 0.0);
                    drawRock(rock, ps, buffers, cam, c, size, t * 0.02, g.seed());
                    Vec3 tail = c.add(a.scale(r + 6.0)).add(0.0, r * 0.5, 0.0), dir = c.subtract(tail).normalize();
                    drawTug(tug, ps, buffers, cam, tail, dir, 1.0F, 1.0F, -12.0F, 1.0F, (float) (r * 1.05 / 1.73), 1.0F);
                    any |= exhaust(bb, cam, tail, dir, Math.max(1.0, cam.distanceTo(tail) / FAR) * 1.6);
                }
                case AsteroidBay.PUSH, AsteroidBay.PARKED -> {
                    boolean push = g.state() == AsteroidBay.PUSH;
                    Vec3 c = push ? AsteroidRedirect.pushRock(doors, f, size, el / Balance.TUG_PUSH_TICKS) : AsteroidRedirect.parked(doors, f, size);
                    Vec3 away = push ? AsteroidRedirect.pushStart(doors, f).subtract(AsteroidRedirect.parked(doors, f, size)).normalize() : a;
                    drawRock(rock, ps, buffers, cam, c, size, t * (push ? 0.05 : 0.02), g.seed());
                    Vec3 tail = c.add(away.scale(r + 6.0));
                    drawTug(tug, ps, buffers, cam, tail, away.scale(-1.0), 1.0F, 1.0F, -12.0F, 1.0F, (float) (r * 1.05 / 1.73), push ? 1.0F : 0.2F);
                    if (push) any |= exhaust(bb, cam, tail, away.scale(-1.0), Math.max(1.0, cam.distanceTo(tail) / FAR));
                }
                default -> {}
            }
        }
        buffers.endBatch();
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static boolean capture(MeshModel tug, MeshModel rock, PoseStack ps, MultiBufferSource buffers, BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 a, double r, double el, double t, long seed) {
        double size = r * 2.0, u = el / Balance.TUG_CAPTURE_TICKS, n = Balance.TUG_CAPTURE_TICKS;
        double spun = u < 0.6 ? el : 0.6 * n + 0.3 * n * 0.5 * AsteroidRedirect.smooth((u - 0.6) / 0.3);
        drawRock(rock, ps, buffers, cam, c, size, 0.05 * (t - el + spun), seed);
        double gap = 70.0 * (1.0 - AsteroidRedirect.smooth(u / 0.45));
        Vec3 tail = c.subtract(a.scale(r + 6.0 + gap));
        float claw = u < 0.28 ? 0.0F : u < 0.5 ? 70.0F * (float) AsteroidRedirect.smooth((u - 0.28) / 0.14) : 70.0F - 82.0F * (float) AsteroidRedirect.smooth((u - 0.5) / 0.12);
        float net = (float) AsteroidRedirect.smooth((u - 0.62) / 0.25);
        float glow = u < 0.45 ? 1.0F : 0.2F;
        drawTug(tug, ps, buffers, cam, tail, a, 1.0F, 1.0F, claw, net, (float) (r * 1.05 / 1.73), glow);
        return u < 0.45 && exhaust(bb, cam, tail, a, 1.0);
    }

    private static Object[] place(Vec3 cam, Vec3 p) {
        Vec3 d = p.subtract(cam);
        double dist = d.length();
        if (dist <= FAR) return new Object[] { d, 1.0 };
        return new Object[] { d.scale(FAR / dist), FAR / dist };
    }

    private static void drawRock(MeshModel rock, PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 c, double size, double turn, long seed) {
        Object[] pl = place(cam, c);
        Vec3 d = (Vec3) pl[0];
        float k = (float) ((double) pl[1] * size / 20.0);
        java.util.Random rnd = new java.util.Random(seed);
        Vector3f ax = new Vector3f(rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F);
        if (ax.lengthSquared() < 1e-4F) ax.set(0, 1, 0);
        ax.normalize();
        ps.pushPose();
        ps.translate(d.x, d.y, d.z);
        ps.scale(k, k, k);
        ps.mulPose(new Quaternionf().rotationAxis((float) (turn % (Math.PI * 2.0)) + rnd.nextFloat() * 6.0F, ax));
        rock.draw("rock", ps.last(), buffers, LightTexture.pack(4, 15), OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void drawTug(MeshModel tug, PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 tail, Vec3 dir, float w1, float w2, float claw, float net, float netGrow, float glow) {
        Object[] pl = place(cam, tail);
        Vec3 d = (Vec3) pl[0];
        float k = (float) (double) pl[1];
        int light = LightTexture.pack(4, 15), full = LightTexture.FULL_BRIGHT, ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.translate(d.x, d.y, d.z);
        ps.scale(k, k, k);
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(1, 0, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
        tug.draw("tug", ps.last(), buffers, light, ov);
        if (tug.has("tg_led")) tug.drawColoured("tg_led", ps.last(), buffers, full, ov, 74, 163, 255);
        for (int i = 0; i < 4; i++) {
            if (tug.has("tg_eng" + i)) tug.draw("tg_eng" + i, ps.last(), buffers, light, ov);
            if (tug.has("tg_glow" + i)) tug.drawColoured("tg_glow" + i, ps.last(), buffers, full, ov, (int) (60 + 100 * glow), (int) (110 + 120 * glow), 255);
        }
        for (String side : new String[] { "p", "s" }) {
            float zs = side.equals("p") ? 1.0F : -1.0F;
            ps.pushPose();
            CargoPodRenderer.about(ps, 60.0F * S, -6.0F * S, zs * 50.7F * S, Axis.XP, zs * 90.0F * w1);
            if (tug.has("tg_wing_" + side)) tug.draw("tg_wing_" + side, ps.last(), buffers, light, ov);
            CargoPodRenderer.about(ps, 60.0F * S, 30.5F * S, zs * 52.1F * S, Axis.XP, 180.0F * w2);
            if (tug.has("tg_wing2_" + side)) tug.draw("tg_wing2_" + side, ps.last(), buffers, light, ov);
            ps.popPose();
        }
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(90.0 * i);
            float cy = (float) Math.cos(ang), sz = (float) Math.sin(ang);
            ps.pushPose();
            if (Math.abs(claw) > 0.01F) {
                ps.translate(134.0F * S * PX, 30.0F * S * cy * PX, 30.0F * S * sz * PX);
                ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(claw), 0.0F, -sz, cy));
                ps.translate(-134.0F * S * PX, -30.0F * S * cy * PX, -30.0F * S * sz * PX);
            }
            if (tug.has("tg_claw" + i)) tug.draw("tg_claw" + i, ps.last(), buffers, light, ov);
            ps.popPose();
        }
        if (tug.has("tg_net")) {
            float cx = 115.0F * S * PX, slide = net * (netGrow * 1.73F / 1.05F + 6.0F - cx);
            float g = 1.0F + (netGrow - 1.0F) * net;
            ps.pushPose();
            ps.translate(cx + slide, 0.0F, 0.0F);
            ps.scale(1.0F, g, g);
            ps.translate(-cx, 0.0F, 0.0F);
            tug.draw("tg_net", ps.last(), buffers, light, ov);
            ps.popPose();
        }
        ps.popPose();
    }

    private static boolean exhaust(BufferBuilder bb, Vec3 cam, Vec3 tail, Vec3 dir, double widen) {
        Object[] pl = place(cam, tail);
        double k = (double) pl[1];
        Vec3 p = cam.add((Vec3) pl[0]), back = p.subtract(dir.scale(8.0 * k));
        ClientBlastGeo.streak(bb, cam, p.x, p.y, p.z, back.x, back.y, back.z, 0.9 * k * widen, 0.35F, 0.6F, 1.0F, 0.9F, 0.0F);
        ClientBlastGeo.streak(bb, cam, p.x, p.y, p.z, back.x, back.y, back.z, 0.3 * k * widen, 0.85F, 0.95F, 1.0F, 1.0F, 0.0F);
        return true;
    }

    private static boolean glint(BufferBuilder bb, Vec3 cam, Vec3 p, float r, float g, float b) {
        Object[] pl = place(cam, p);
        Vec3 q = cam.add((Vec3) pl[0]);
        double w = Math.max(0.15, cam.distanceTo(q) * 0.004);
        ClientBlastGeo.streak(bb, cam, q.x - w * 2, q.y, q.z, q.x + w * 2, q.y, q.z, w, r, g, b, 1.0F, 1.0F);
        ClientBlastGeo.streak(bb, cam, q.x, q.y - w * 2, q.z, q.x, q.y + w * 2, q.z, w, r, g, b, 1.0F, 1.0F);
        return true;
    }

    private static final java.util.Map<Long, Integer> HEARD = new java.util.HashMap<>();

    private static double[] dust;
    private static final java.util.List<double[]> WAVES = new java.util.ArrayList<>();

    private static final java.util.ArrayDeque<double[]> SMOKE = new java.util.ArrayDeque<>();
    private static final java.util.Map<Long, Double> PUFFED = new java.util.HashMap<>();

    private static final java.util.List<double[]> AFTER = new java.util.ArrayList<>();

    private static Vec3 at(Vec3 cam, Vec3 v, double k) {
        return k >= 1.0 ? v : cam.add(v.subtract(cam).scale(k));
    }

    private static final int TAIL_SEGS = 32;

    private static void tail(BufferBuilder bb, Vec3 cam, Vec3 q, Vec3 back, double rr, double fire, double t, long seed) {

        Vec3 dir = back.subtract(q);
        double len = dir.length();
        if (len < 1.0E-3) return;
        Vec3 u = dir.scale(1.0 / len);
        Vec3 s1 = u.cross(new Vec3(0.0, 1.0, 0.0));
        if (s1.lengthSqr() < 1.0E-6) s1 = new Vec3(1.0, 0.0, 0.0);
        s1 = s1.normalize();
        Vec3 s2 = u.cross(s1).normalize();
        double ph = (seed & 1023) * 0.01;
        Vec3[] pts = new Vec3[TAIL_SEGS + 1];
        for (int j = 0; j <= TAIL_SEGS; j++) {
            double f = j / (double) TAIL_SEGS, wob = rr * 0.9 * f * f * fire;
            pts[j] = q.add(u.scale(rr * 0.7 + (len - rr * 0.7) * f))
                    .add(s1.scale(Math.sin(t * 0.05 + f * 5.0 + ph) * wob)).add(s2.scale(Math.cos(t * 0.04 + f * 4.0 + ph) * wob * 0.7));
        }
        Vec3 prevL = null, prevR = null;
        float pr = 0, pg = 0, pb = 0, pa = 0;
        for (int j = 0; j <= TAIL_SEGS; j++) {
            double f = j / (double) TAIL_SEGS;
            Vec3 a = pts[Math.max(0, j - 1)], b = pts[Math.min(TAIL_SEGS, j + 1)], tan = b.subtract(a);
            Vec3 toCam = cam.subtract(pts[j]);
            Vec3 side = tan.cross(toCam);
            if (side.lengthSqr() < 1.0E-9) side = s1;
            double w = rr * (0.45 + 1.1 * f) * (1.0 - 0.6 * f * f);
            side = side.normalize().scale(w);
            Vec3 l = pts[j].subtract(side), r = pts[j].add(side);
            float cr, cg, cb;
            if (f < 0.15) { cr = 1.0F; cg = 0.95F; cb = 0.8F; }
            else if (f < 0.45) { cr = 1.0F; cg = 0.72F; cb = 0.3F; }
            else { cr = 0.95F; cg = 0.4F; cb = 0.12F; }
            double near = Math.min(1.0, toCam.length() / (w * 8.0 + 1.0));
            float ca = (float) (Math.pow(1.0 - f, 1.6) * 0.75 * fire * near);
            if (prevL != null) {
                ClientBlastGeo.quad(bb, cam, prevL, prevR, r, l, pr, pg, pb, pa, cr, cg, cb, ca);
            }
            prevL = l; prevR = r; pr = cr; pg = cg; pb = cb; pa = ca;
        }
    }

    private static void entry(BufferBuilder bb, Vec3 cam, Vector3f lv, Vector3f uv, Vec3 q, Vec3 back, double rr, double fire, double t, long seed) {
        Vec3 aft = back.subtract(q);
        if (aft.lengthSqr() < 1.0E-6) return;
        Vec3 bk = aft.normalize(), fwd = bk.scale(-1.0);
        Vec3 s1 = fwd.cross(new Vec3(0.0, 1.0, 0.0));
        s1 = s1.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : s1.normalize();
        Vec3 s2 = fwd.cross(s1).normalize(), bow = q.add(fwd.scale(rr * 0.55));
        ClientBlastGeo.disc(bb, cam, lv, uv, bow.x, bow.y, bow.z, rr * (1.0 + 0.6 * fire), 1.0F, 0.97F, 0.9F, (float) (0.6 * fire), 18);
        ClientBlastGeo.disc(bb, cam, lv, uv, bow.x, bow.y, bow.z, rr * (1.8 + 1.4 * fire), 1.0F, 0.58F, 0.28F, (float) (0.3 * fire), 18);
        if (fire < 0.15) return;
        double len = aft.length();
        for (int f = 0; f < 5; f++) {
            double per = 70.0 + 23.0 * f, run = t + f * 37.0 + (seed & 255), a = (run % per) / per;
            long fs = seed * 131 + f * 7 + (long) (run / per);
            double ang = Math.floorMod(fs * 2654435761L, 628L) * 0.01;
            Vec3 out = s1.scale(Math.cos(ang)).add(s2.scale(Math.sin(ang)));
            Vec3 head = q.add(bk.scale(len * (0.08 + 0.5 * a))).add(out.scale(rr * (1.0 + 7.0 * a)));
            Vec3 end = head.add(bk.scale(rr * (2.0 + 4.0 * a)));
            ClientBlastGeo.streak(bb, cam, head.x, head.y, head.z, end.x, end.y, end.z, Math.max(0.12 * rr, 0.08), 1.0F, 0.86F, 0.55F,
                    (float) (fire * (1.0 - a)), 0.0F);
        }
    }

    private static boolean smoke(BufferBuilder shade, Vec3 cam, Vector3f lv, Vector3f uv, double t) {
        while (!SMOKE.isEmpty() && (t - SMOKE.peekFirst()[3] > Balance.ASTEROID_SMOKE_TICKS || t < SMOKE.peekFirst()[3] - 40.0)) SMOKE.removeFirst();
        boolean any = false;
        for (double[] s : SMOKE) {
            double age = t - s[3];
            if (age < 0.0) continue;
            double u = age / Balance.ASTEROID_SMOKE_TICKS;
            Object[] pl = place(cam, new Vec3(s[0] + age * 0.03, s[1] - age * 0.004, s[2]));
            double k = (double) pl[1];
            Vec3 q = cam.add((Vec3) pl[0]);
            float hot = (float) Math.max(0.0, 1.0 - age / 60.0);
            ClientBlastGeo.disc(shade, cam, lv, uv, q.x, q.y, q.z, s[4] * (0.8 + 2.5 * Math.sqrt(u)) * k, 0.5F + 0.35F * hot, 0.48F + 0.04F * hot,
                    0.46F - 0.18F * hot, (float) (0.32 * (1.0 - u) * Math.min(1.0, age / 6.0)), 10);
            any = true;
        }
        return any;
    }

    private static boolean curtain(BufferBuilder shade, Vec3 cam, double[] a, double age) {
        double u = age / Balance.ASTEROID_CURTAIN_TICKS, R = a[4];
        double foot = R * (0.25 + 0.95 * Math.sqrt(u)), h = R * 0.8 * Math.sin(Math.PI * Math.min(1.0, u * 1.15));
        if (h < 1.0) return false;
        Vec3 c = new Vec3(a[0], a[1], a[2]);
        double k = Math.min(1.0, 400.0 / Math.max(1.0, cam.distanceTo(c)));
        float al = (float) (0.75 * Math.pow(1.0 - u, 0.7));
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double h0 = h * (0.75 + 0.25 * Math.sin(i * 2.7 + a[6] * 1.3)), h1 = h * (0.75 + 0.25 * Math.sin((i + 1) * 2.7 + a[6] * 1.3));
            double t0 = foot + h0 * 0.9, t1 = foot + h1 * 0.9;
            Vec3 b0 = at(cam, c.add(Math.cos(a0) * foot, 0.0, Math.sin(a0) * foot), k), b1 = at(cam, c.add(Math.cos(a1) * foot, 0.0, Math.sin(a1) * foot), k);
            Vec3 u1 = at(cam, c.add(Math.cos(a1) * t1, h1, Math.sin(a1) * t1), k), u0 = at(cam, c.add(Math.cos(a0) * t0, h0, Math.sin(a0) * t0), k);
            ClientBlastGeo.quad(shade, cam, b0, b1, u1, u0, 0.33F, 0.28F, 0.22F, al, 0.45F, 0.4F, 0.33F, 0.0F);
        }
        return true;
    }

    private static boolean ejecta(BufferBuilder bb, Vec3 cam, double[] a, double age) {
        double R = a[4];
        Vec3 c = new Vec3(a[0], a[1], a[2]);
        double k = Math.min(1.0, 400.0 / Math.max(1.0, cam.distanceTo(c)));
        boolean any = false;
        for (int j = 0; j < 48; j++) {
            long rs = (long) a[6] * 977L + j * 7919L + (long) a[0];
            double ang = j * 2.399963 + Math.floorMod(rs, 100L) * 0.01, v = 0.5 + 0.5 * Math.floorMod(rs * 31L, 1000L) / 1000.0;
            double flight = 60.0 + 90.0 * v, s = age / flight;
            if (s > 1.0) continue;
            double s0 = Math.max(0.0, s - 0.05);
            Vec3 p1 = at(cam, c.add(Math.cos(ang) * R * (0.3 + 1.4 * v) * s, R * 0.9 * v * 4.0 * s * (1.0 - s), Math.sin(ang) * R * (0.3 + 1.4 * v) * s), k);
            Vec3 p0 = at(cam, c.add(Math.cos(ang) * R * (0.3 + 1.4 * v) * s0, R * 0.9 * v * 4.0 * s0 * (1.0 - s0), Math.sin(ang) * R * (0.3 + 1.4 * v) * s0), k);
            ClientBlastGeo.streak(bb, cam, p1.x, p1.y, p1.z, p0.x, p0.y, p0.z, Math.max(0.6, R * 0.01) * k, 1.0F, 0.55F, 0.2F, (float) (1.0 - 0.7 * s), 0.0F);
            any = true;
        }
        return any;
    }

    private static boolean dome(BufferBuilder shade, Vec3 cam, double[] a, double age) {
        double u = age / Balance.ASTEROID_DOME_TICKS, rd = a[4] * 0.7 * (1.0 - Math.pow(1.0 - u, 3.0));
        if (rd < 1.0) return false;
        Vec3 c = new Vec3(a[0], a[1], a[2]);
        double k = Math.min(1.0, 400.0 / Math.max(1.0, cam.distanceTo(c)));
        float al = (float) (0.5 * Math.pow(1.0 - u, 1.2) * Math.min(1.0, age / 4.0));
        for (int s = 0; s < 8; s++) {
            double phi = (s + 0.5) / 8.0 * Math.PI / 2.0;
            Vec3 m = at(cam, c.add(0.0, rd * Math.sin(phi) * 0.8 + 1.0, 0.0), k);
            ClientBlastGeo.flatDisc(shade, cam, m.x, m.y, m.z, rd * Math.cos(phi) * k, 0.9F, 0.93F, 0.97F, al / 3.0F, 32);
        }
        return true;
    }

    private static void ground(RenderLevelStageEvent event, Minecraft mc) {
        MeshModel rock = MeshModel.named("asteroid_rock");
        double t = ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        net.minecraft.client.Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder shade = new BufferBuilder(SHADOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false, shaded = false;
        for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            double left = p.impact() - t;
            if (p.stage() != 1 || left <= 0) continue;
            if (left < Balance.ASTEROID_SHADOW_TICKS && mc.level != null) {
                Vec3 tg0 = new Vec3(p.tx() + 0.5, p.ty(), p.tz() + 0.5);
                int n0 = Math.max(1, p.pieces());
                double sl0 = p.split() == 0 ? 0.0 : p.impact() - p.split(), sz0 = Math.max(5, p.size()) / Math.cbrt(n0), u = 1.0 - left / Balance.ASTEROID_SHADOW_TICKS;
                for (int i = 0; i < n0; i++) {
                    Vec3 c = AsteroidDrop.piecePos(tg0, p.seed(), left, i, n0, sl0);
                    double gy = ClientBlastGeo.ground(mc.level, c.x, c.z, tg0.y) + 0.4, rad = sz0 * (1.2 + 4.0 * u);
                    for (int r = 0; r < 3; r++)
                        ClientBlastGeo.flatDisc(shade, cam, c.x, gy + 0.05 * r, c.z, rad * (1.0 - 0.25 * r), 0.03F, 0.02F, 0.02F, (float) (0.22 * u), 24);
                }
                shaded = true;
            }
            Vec3 tgt = new Vec3(p.tx() + 0.5, p.ty(), p.tz() + 0.5);
            int n = Math.max(1, p.pieces());
            double splitLeft = p.split() == 0 ? 0.0 : p.impact() - p.split();
            double size = Math.max(5, p.size()) / Math.cbrt(n), fire = left < Balance.ASTEROID_INT_WINDOW ? 1.0 - left / Balance.ASTEROID_INT_WINDOW : 0.0;
            for (int i = 0; i < n; i++) {
                Vec3 c = AsteroidDrop.piecePos(tgt, p.seed(), left, i, n, splitLeft);
                if (rock.has("rock")) drawRock(rock, ps, buffers, cam, c, size, t * 0.03 + i, p.seed() + i);
                Object[] pl = place(cam, c);
                double k = (double) pl[1];
                Vec3 q = cam.add((Vec3) pl[0]), back = q.add(AsteroidDrop.bearing(p.seed()).add(0.0, 1.0, 0.0).normalize().scale((40.0 + 400.0 * fire) * k));
                double rr = size * 0.5 * k, glowW = Math.max(rr * (1.2 + 1.4 * fire), cam.distanceTo(q) * 0.006);
                if (fire > 0.0) tail(bb, cam, q, back, rr, fire, t, p.seed() + i);
                if (fire > 0.0) entry(bb, cam, lv, uv, q, back, rr, fire, t, p.seed() * 31 + i);
                if (fire > 0.0) {
                    long key = p.seed() * 31 + i;
                    Double last = PUFFED.get(key);
                    if (last == null || t - last >= Balance.ASTEROID_SMOKE_EVERY || t < last) {
                        PUFFED.put(key, t);
                        SMOKE.addLast(new double[] { c.x, c.y, c.z, t, size });
                        while (SMOKE.size() > Balance.ASTEROID_SMOKE_MAX) SMOKE.removeFirst();
                    }
                }
                ClientBlastGeo.disc(bb, cam, lv, uv, q.x, q.y, q.z, glowW, 1.0F, (float) (0.85 - 0.35 * fire), (float) (0.7 - 0.5 * fire), (float) (0.25 + 0.6 * fire), 14);
                any = true;
            }
        }
        shaded |= smoke(shade, cam, lv, uv, t);
        for (java.util.Iterator<double[]> it = AFTER.iterator(); it.hasNext(); ) {
            double[] a = it.next();
            double age = t - a[3];
            if (age > (a[5] > 0.5 ? Balance.ASTEROID_DOME_TICKS : Balance.ASTEROID_CURTAIN_TICKS)) { it.remove(); continue; }
            if (age < 0.0) continue;
            if (a[5] > 0.5) shaded |= dome(shade, cam, a, age);
            else {
                shaded |= curtain(shade, cam, a, age);
                any |= ejecta(bb, cam, a, age);
            }
        }

        for (java.util.Iterator<double[]> it = WAVES.iterator(); it.hasNext(); ) {
            double[] w = it.next();
            double r = (t - w[3]) * Balance.ASTEROID_SEA_WAVE_SPEED;
            if (r > Balance.ASTEROID_SEA_WAVE_R) { it.remove(); continue; }
            if (Math.hypot(cam.x - w[0], cam.z - w[2]) > r + 600.0) continue;
            float a = (float) (0.75 * (1.0 - r / Balance.ASTEROID_SEA_WAVE_R) + 0.15);
            int seg = 96;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                ClientBlastGeo.streak(bb, cam, w[0] + Math.cos(a0) * r, w[1] + 1.5, w[2] + Math.sin(a0) * r, w[0] + Math.cos(a1) * r, w[1] + 1.5, w[2] + Math.sin(a1) * r,
                        2.5 + 3.0 * (1.0 - r / Balance.ASTEROID_SEA_WAVE_R), 0.85F, 0.93F, 1.0F, a, a);
            }
            any = true;
        }
        buffers.endBatch();
        ClientBlastGeo.draw(event, shade, shaded, false);
        ClientBlastGeo.draw(event, bb, any, true);
    }

    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { AsteroidBayPayloads.FALLS.clear(); WAVES.clear(); SMOKE.clear(); PUFFED.clear(); AFTER.clear(); dust = null; return; }
        if (AsteroidBayPayloads.FALLS.isEmpty()) { PUFFED.clear(); return; }
        long now = ClientClock.ticks();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (java.util.Iterator<java.util.Map.Entry<Long, Object[]>> it = AsteroidBayPayloads.FALLS.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<Long, Object[]> en = it.next();
            Object[] e = en.getValue();
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            if ((long) e[1] < 0) e[1] = now;
            int h = HEARD.getOrDefault(en.getKey(), 0);
            double d = Math.hypot(cam.x - p.tx(), cam.z - p.tz());
            if (p.stage() == 3) {
                if (d < Balance.ASTEROID_DUST_R) dust = new double[] { p.tx() + 0.5, p.tz() + 0.5, now + Balance.ASTEROID_DUST_TICKS };
                if (p.sea()) WAVES.add(new double[] { p.tx() + 0.5, p.ty(), p.tz() + 0.5, now });
                if (d < Balance.ASTEROID_DUST_R) {
                    int n = Math.max(1, p.pieces());
                    double sl = p.split() == 0 ? 0.0 : p.impact() - p.split();
                    Vec3 tg = new Vec3(p.tx() + 0.5, p.ty(), p.tz() + 0.5);
                    for (int i = 0; i < n; i++) {
                        Vec3 c = AsteroidDrop.piecePos(tg, p.seed(), 0.0, i, n, sl);
                        double gy = p.sea() ? p.ty() : ClientBlastGeo.ground(mc.level, c.x, c.z, p.ty());
                        AFTER.add(new double[] { c.x, gy, c.z, now, Balance.ASTEROID_CURTAIN_R / Math.cbrt(n), p.sea() ? 1.0 : 0.0, i });
                    }
                }
                it.remove();
                HEARD.remove(en.getKey());
                continue;
            }
            if (now - (long) e[1] > 400) { it.remove(); HEARD.remove(en.getKey()); continue; }
            long left = p.impact() - now;
            float vol = (float) Math.max(0.3, 1.0 - d / 6000.0);
            if (left <= 1_200 && left > 0 && (h & 1) == 0) { h |= 1; ClientRailgun.flat(d < 1500.0 ? WmdSounds.REENTRY.get() : WmdSounds.REENTRY_FAR.get(), vol, 0.6F); }
            if (left <= 300 && left > 0 && (h & 2) == 0) { h |= 2; ClientRailgun.flat(d < 1500.0 ? WmdSounds.SONIC_BOOM.get() : WmdSounds.SONIC_BOOM_FAR.get(), vol, 0.7F); }
            if (left <= 220 && left > 0 && (h & 4) == 0 && !ClientCutscene.active()) { h |= 4; ClientRailgun.flat(d < 1500.0 ? WmdSounds.AST_FALL.get() : WmdSounds.AST_FALL_FAR.get(), vol, 1.0F); }
            HEARD.put(en.getKey(), h);
        }
    }

    @SubscribeEvent
    static void onGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || AsteroidBayPayloads.FALLS.isEmpty() || mc.options.hideGui) return;
        long now = ClientClock.ticks();
        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        int y = 4 + 17 * RailgunEventPayload.EVENTS.size(), w = g.guiWidth();
        for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            if (p.stage() != 1) continue;
            long left = Math.max(0, p.impact() - now);
            net.minecraft.network.chat.Component line = p.pieces() > 1
                    ? net.minecraft.network.chat.Component.translatable("gui.wmd.abay.event_split", p.owner(), p.tx(), p.tz(), OrbitMath.mmss(left * 50L), p.pieces())
                    : net.minecraft.network.chat.Component.translatable("gui.wmd.abay.event", p.owner(), p.tx(), p.tz(), OrbitMath.mmss(left * 50L));
            int col = left < Balance.ASTEROID_INT_WINDOW ? (((now / 10) % 2 == 0) ? WmdUi.BAD : WmdUi.TEXT) : WmdUi.EV_ASTEROID;
            WmdUi.eventBar(g, mc.font, line, y, col, WmdUi.EV_ASTEROID);
            y += 17;
        }
    }

    private static double dustK(Minecraft mc) {
        if (dust == null || mc.level == null) return 0.0;
        long now = ClientClock.ticks();
        if (now > dust[2]) { dust = null; return 0.0; }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = Math.hypot(cam.x - dust[0], cam.z - dust[1]);
        if (d > Balance.ASTEROID_DUST_R) return 0.0;
        double time = Math.min(1.0, (dust[2] - now) / 1_200.0), rise = Math.min(1.0, (now - (dust[2] - Balance.ASTEROID_DUST_TICKS)) / 200.0);
        return time * rise * (1.0 - 0.6 * d / Balance.ASTEROID_DUST_R);
    }

    @SubscribeEvent
    static void onFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        if (dust == null || event.getType() != net.minecraft.world.level.material.FogType.NONE || ClientCutscene.active()) return;
        double k = dustK(Minecraft.getInstance());
        if (k <= 0.01) return;

        float far = (float) Math.min(event.getFarPlaneDistance(), event.getFarPlaneDistance() * (1.0 - k) + 220.0 * k);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(far * 0.35F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onIncomingSky(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || AsteroidBayPayloads.FALLS.isEmpty() || ClientCutscene.active()) return;
        Vec3 cam = event.getCamera().getPosition();
        double now = ClientClock.now(0.0F), best = 0.0;
        for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            double left = p.impact() - now, d = Math.hypot(cam.x - p.tx(), cam.z - p.tz());
            if (p.stage() != 1 || left <= 0 || left > Balance.ASTEROID_SKY_TICKS || d > Balance.ASTEROID_DUST_R) continue;
            best = Math.max(best, (1.0 - left / Balance.ASTEROID_SKY_TICKS) * (1.0 - d / Balance.ASTEROID_DUST_R));
        }
        if (best <= 0.01) return;
        float k = (float) (0.5 * best);
        event.setRed(event.getRed() + (0.72F - event.getRed()) * k);
        event.setGreen(event.getGreen() + (0.34F - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (0.2F - event.getBlue()) * k);
    }

    private static final com.mojang.blaze3d.vertex.ByteBufferBuilder SHADOW = new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 14);

    @SubscribeEvent
    static void onFogColour(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        if (dust == null || ClientCutscene.active()) return;
        float k = (float) dustK(Minecraft.getInstance());
        if (k <= 0.01F) return;
        event.setRed(event.getRed() * (1.0F - k) + 0.40F * k);
        event.setGreen(event.getGreen() * (1.0F - k) + 0.33F * k);
        event.setBlue(event.getBlue() * (1.0F - k) + 0.26F * k);
    }
}
