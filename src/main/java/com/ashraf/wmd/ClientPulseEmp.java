package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPulseEmp {

    private ClientPulseEmp() {}

    private static final int SHOCK_TICKS = 22, RIPPLE_TICKS = 24;
    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 16), SOFT = new ByteBufferBuilder(1 << 16);

    private record Shock(Vec3 from, Vec3 axis, long start) {}
    private record Hit(Vec3 at, long start, long seed) {}
    private record Arc(Vec3 c, Vec3 axis, double r, long seen) {}

    private static final List<Shock> SHOCKS = Statics.client(new ArrayList<>());
    private static final List<Hit> HITS = Statics.client(new ArrayList<>());
    private static final Map<Long, Arc> ARCS = Statics.client(new HashMap<>());
    private static long rippleUntil = Long.MIN_VALUE / 2, rippleStart;

    static void pulse(PulseEmpPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        if (SHOCKS.size() > 8) SHOCKS.remove(0);
        SHOCKS.add(new Shock(p.from(), p.axis(), now));
        for (Vec3 h : p.hits()) { if (HITS.size() > 64) HITS.remove(0); HITS.add(new Hit(h, now, level.random.nextLong())); }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 d = cam.subtract(p.from());
        double l = d.length();
        if (l <= Balance.PEMP_RANGE && l > 0.5 && d.scale(1.0 / l).dot(p.axis()) >= Math.cos(Math.toRadians(Balance.PEMP_HALF_ANGLE))) {
            rippleStart = now + (long) (l / Balance.PEMP_RANGE * SHOCK_TICKS);
            rippleUntil = rippleStart + RIPPLE_TICKS;
        }
    }

    static void chargeArc(BlockPos pos, int i, Vec3 c, Vec3 axis, double r, long now) {
        if (ARCS.size() > 96) ARCS.clear();
        ARCS.put(pos.asLong() * 4L + i, new Arc(c, axis, r, now));
    }

    static float shake(Vec3 cam) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0.0F;
        long now = level.getGameTime();
        if (now < rippleStart || now > rippleUntil) return 0.0F;
        return 0.9F * (1.0F - (now - rippleStart) / (float) RIPPLE_TICKS);
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ClientConfig.explosionScreen()) return;
        long now = level.getGameTime();
        if (now < rippleStart || now > rippleUntil) return;
        float k = 1.0F - (now - rippleStart) / (float) RIPPLE_TICKS;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) (70 * k) << 24) | 0x3A78FF);
        RandomSource r = RandomSource.create(now * 31L);
        for (int i = 0; i < 18; i++) {
            int y = r.nextInt(Math.max(1, h)), lh = 1 + r.nextInt(3);
            g.fill(0, y, w, y + lh, ((int) (110 * k * (0.4F + r.nextFloat() * 0.6F)) << 24) | 0xCFE4FF);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || (SHOCKS.isEmpty() && HITS.isEmpty() && ARCS.isEmpty())) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder soft = new BufferBuilder(SOFT, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyG = false, anyS = false;
        double tanA = Math.tan(Math.toRadians(Balance.PEMP_HALF_ANGLE));
        for (Iterator<Shock> it = SHOCKS.iterator(); it.hasNext();) {
            Shock s = it.next();
            double age = now - s.start();
            if (age > SHOCK_TICKS + 10) { it.remove(); continue; }
            Vec3[] ax = axes(s.axis());
            for (int k = 0; k < 3; k++) {
                double u = (age - k * 3.0) / SHOCK_TICKS;
                if (u <= 0.0 || u >= 1.0) continue;
                double d = Math.sqrt(u) * Balance.PEMP_RANGE, R = Math.max(1.0, d * tanA);
                float fade = (float) ((1.0 - u) * (1.0 - u)) * (k == 0 ? 1.0F : 0.6F);
                Vec3 c = s.from().add(s.axis().scale(d));
                ring(glow, cam, c, ax[0], ax[1], R, 0.6 + R * 0.04, 0.55F, 0.8F, 1.0F, 0.75F * fade, 64);
                ring(soft, cam, c.subtract(s.axis().scale(R * 0.12)), ax[0], ax[1], R * 0.97, R * 0.12, 0.75F, 0.88F, 1.0F, 0.22F * fade, 48);
                if (k == 0) {
                    int n = 32;
                    for (int i = 0; i < n; i++) {
                        double a0 = Math.PI * 2.0 * i / n, a1 = Math.PI * 2.0 * (i + 1) / n;
                        Vec3 p0 = c.add(ax[0].scale(Math.cos(a0) * R)).add(ax[1].scale(Math.sin(a0) * R)), p1 = c.add(ax[0].scale(Math.cos(a1) * R)).add(ax[1].scale(Math.sin(a1) * R));
                        Vec3 b = s.from().add(s.axis().scale(d * 0.55)), b0 = b.add(ax[0].scale(Math.cos(a0) * R * 0.55)).add(ax[1].scale(Math.sin(a0) * R * 0.55)),
                                b1 = b.add(ax[0].scale(Math.cos(a1) * R * 0.55)).add(ax[1].scale(Math.sin(a1) * R * 0.55));
                        ClientBlastGeo.quad(soft, cam, p0, p1, b1, b0, 0.7F, 0.85F, 1.0F, 0.12F * fade, 0.7F, 0.85F, 1.0F, 0.0F);
                    }
                }
                anyG = true; anyS = true;
            }
            if (age < 6.0) {
                ClientBlastGeo.disc(glow, cam, lv, uv, s.from().x, s.from().y, s.from().z, 3.0 + age * 1.5, 0.75F, 0.9F, 1.0F, (float) (0.9 * (1.0 - age / 6.0)), 18);
                VeilBridge.glow(0x50454D50L ^ s.start(), s.from().x, s.from().y, s.from().z, 0.55F, 0.75F, 1.0F, 60.0F, (float) (4.0 * (1.0 - age / 6.0)), level.getGameTime());
            }
        }
        for (Iterator<Hit> it = HITS.iterator(); it.hasNext();) {
            Hit h = it.next();
            double age = now - h.start();
            if (age > Balance.PEMP_ARC_TICKS) { it.remove(); continue; }
            if (age < 0.0) continue;
            RandomSource r = RandomSource.create(h.seed() + tick / 2);
            float al = (float) (1.0 - age / Balance.PEMP_ARC_TICKS);
            for (int k = 0; k < 3; k++) {
                Vec3 a = h.at().add(r.nextGaussian() * 2.0, r.nextGaussian() * 1.5, r.nextGaussian() * 2.0), b = h.at().add(r.nextGaussian() * 2.5, r.nextGaussian() * 1.5, r.nextGaussian() * 2.5);
                bolt(glow, cam, a, b, r, 0.07, al);
            }
            anyG = true;
        }
        for (Iterator<Arc> it = ARCS.values().iterator(); it.hasNext();) {
            Arc a = it.next();
            if (tick - a.seen() > 2) { it.remove(); continue; }
            if (a.c().distanceToSqr(cam) > 160.0 * 160.0) continue;
            Vec3[] ax = axes(a.axis());
            RandomSource r = RandomSource.create(Double.doubleToLongBits(a.c().x * 13.0 + a.c().z) + tick / 2);
            for (int k = 0; k < 2; k++) {
                double t0 = r.nextDouble() * Math.PI * 2.0, t1 = t0 + 0.4 + r.nextDouble() * 0.6;
                Vec3 p0 = a.c().add(ax[0].scale(Math.cos(t0) * a.r())).add(ax[1].scale(Math.sin(t0) * a.r()));
                Vec3 p1 = a.c().add(ax[0].scale(Math.cos(t1) * a.r())).add(ax[1].scale(Math.sin(t1) * a.r()));
                bolt(glow, cam, p0, p1, r, 0.035, 0.9F);
            }
            anyG = true;
        }
        ClientBlastGeo.draw(event, soft, anyS, false);
        ClientBlastGeo.draw(event, glow, anyG, true);
    }

    private static void bolt(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 b, RandomSource r, double w, float al) {
        Vec3 prev = a;
        double len = a.distanceTo(b);
        for (int i = 1; i <= 6; i++) {
            double t = i / 6.0;
            Vec3 p = i == 6 ? b : a.add(b.subtract(a).scale(t)).add(r.nextGaussian() * len * 0.12, r.nextGaussian() * len * 0.12, r.nextGaussian() * len * 0.12);
            ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, p.x, p.y, p.z, w, 0.6F, 0.85F, 1.0F, al, al);
            prev = p;
        }
    }

    private static Vec3[] axes(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        return new Vec3[] { u, n.cross(u).normalize() };
    }

    private static void ring(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 e1, Vec3 e2, double R, double w, float r, float g, float b, float a, int n) {
        double da = Math.PI * 2.0 / n;
        for (int i = 0; i < n; i++) {
            double t0 = i * da, t1 = t0 + da;
            Vec3 d0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), d1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 i0 = c.add(d0.scale(R - w)), i1 = c.add(d1.scale(R - w)), m0 = c.add(d0.scale(R)), m1 = c.add(d1.scale(R)), o0 = c.add(d0.scale(R + w)), o1 = c.add(d1.scale(R + w));
            ClientBlastGeo.quad(bb, cam, i0, i1, m1, m0, r, g, b, 0.0F, r, g, b, a);
            ClientBlastGeo.quad(bb, cam, m0, m1, o1, o0, r, g, b, a, r, g, b, 0.0F);
        }
    }
}
