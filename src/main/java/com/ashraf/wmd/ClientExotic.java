package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientExotic {

    private ClientExotic() {}

    private static final int MAX_BOTS = 4_000, BITE_BOTS = 12;
    private static final double LOD_NEAR = 40.0, LOD_FAR = 160.0;

    private static final class Bite {
        final BlockPos pos;
        final long t0;
        final int breaker;
        int stage = -1;
        Bite(BlockPos pos, long t0, int breaker) { this.pos = pos; this.t0 = t0; this.breaker = breaker; }
    }

    private static final class Swarm {
        double x, y, z, r;
        long seen;
        final List<Bite> bites = new ArrayList<>();
    }

    private static final class Hole {
        double x, y, z, r, yaw;
        long start, seen, throwAt = -1;
    }

    private static final Map<Integer, Swarm> SWARMS = new HashMap<>();
    private static final Map<Integer, Hole> HOLES = new HashMap<>();
    private static ClientLevel seenLevel;
    private static int nextBreaker = -1_900_000_000;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { SWARMS.clear(); HOLES.clear(); ExoticFxPayload.PENDING.clear(); seenLevel = null; return; }
        if (level != seenLevel) { seenLevel = level; SWARMS.clear(); HOLES.clear(); }
        long now = ClientClock.ticks();
        for (ExoticFxPayload p; (p = ExoticFxPayload.PENDING.poll()) != null; ) arrive(level, p, now);
        for (Iterator<Map.Entry<Integer, Swarm>> it = SWARMS.entrySet().iterator(); it.hasNext(); ) {
            Swarm s = it.next().getValue();
            for (Iterator<Bite> bi = s.bites.iterator(); bi.hasNext(); ) {
                Bite b = bi.next();
                long age = now - b.t0;
                int stage = (int) Math.min(9, age * 10 / Balance.NANITE_DISSOLVE);
                if (age > Balance.NANITE_DISSOLVE + 6 || level.getBlockState(b.pos).isAir()) { level.destroyBlockProgress(b.breaker, b.pos, -1); bi.remove(); continue; }
                if (stage != b.stage) { b.stage = stage; level.destroyBlockProgress(b.breaker, b.pos, stage); }
            }
            if (now - s.seen > 60) { clearBites(level, s); it.remove(); }
        }
        for (Iterator<Map.Entry<Integer, Hole>> it = HOLES.entrySet().iterator(); it.hasNext(); ) {
            Hole h = it.next().getValue();
            if (h.throwAt >= 0 ? now - h.throwAt > 14 : now - h.seen > 60) { it.remove(); continue; }
            if (h.throwAt < 0) {
                double grow = Math.min(1.0, (now - h.start) / 20.0);
                VeilBridge.glow(0x6B00_0000_0000_0000L ^ h.hashCode(), h.x, h.y, h.z, 1.0F, 0.55F, 0.22F, 14.0F, (float) (3.0 * grow), now);
                VeilBridge.lens(h.x, h.y, h.z, holeR(h, now), (float) grow);
            }
        }
    }

    private static void arrive(ClientLevel level, ExoticFxPayload p, long now) {
        switch (p.kind()) {
            case ExoticFxPayload.NANITE_STATE -> {
                Swarm s = SWARMS.computeIfAbsent(p.id(), k -> new Swarm());
                s.x = p.x(); s.y = p.y(); s.z = p.z(); s.r = p.r(); s.seen = now;
                for (long e : p.eat()) s.bites.add(new Bite(BlockPos.of(e), now, nextBreaker++));
            }
            case ExoticFxPayload.NANITE_END -> {
                Swarm s = SWARMS.remove(p.id());
                if (s != null) clearBites(level, s);
                if (p.left() < 0) {
                    var rnd = level.random;
                    for (int i = 0; i < 160; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = p.r() * Math.sqrt(rnd.nextDouble());
                        double x = p.x() + Math.cos(a) * d, z = p.z() + Math.sin(a) * d;
                        double y = level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) + 0.2 : p.y();
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, -0.05, 0.0);
                    }
                }
            }
            case ExoticFxPayload.GRAV_STATE -> {
                Hole h = HOLES.get(p.id());
                if (h == null) {
                    h = new Hole();
                    h.start = now - Math.max(0, p.total() - p.left());
                    h.yaw = (p.id() & 0xFFFF) / 65536.0 * Math.PI * 2.0;
                    HOLES.put(p.id(), h);
                }
                h.x = p.x(); h.y = p.y(); h.z = p.z(); h.r = p.r(); h.seen = now;
            }
            case ExoticFxPayload.GRAV_THROW -> {
                Hole h = HOLES.get(p.id());
                if (h == null) { h = new Hole(); h.x = p.x(); h.y = p.y(); h.z = p.z(); h.start = now - 40; h.seen = now; HOLES.put(p.id(), h); }
                h.throwAt = now;
            }
            default -> { }
        }
    }

    private static void clearBites(ClientLevel level, Swarm s) {
        for (Bite b : s.bites) level.destroyBlockProgress(b.breaker, b.pos, -1);
        s.bites.clear();
    }

    private static double holeR(Hole h, double t) {
        double grow = Math.min(1.0, (t - h.start) / 20.0);
        double r = 1.2 * grow * grow * (3.0 - 2.0 * grow);
        if (h.throwAt >= 0) r *= Math.max(0.0, 1.0 - (t - h.throwAt) / 6.0);
        return r;
    }

    private static double hash(int i, int salt) {
        long v = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        v ^= v >>> 31; v *= 0xBF58476D1CE4E5B9L; v ^= v >>> 29;
        return (v & 0xFFFFFF) / (double) 0x1000000;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SWARMS.isEmpty() && HOLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double t = ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        ClientBlastGeo.beginFrame();
        if (!SWARMS.isEmpty()) {
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean any = false;
            int[] budget = { MAX_BOTS };
            for (Swarm s : SWARMS.values()) any |= swarm(bb, level, cam, left, up, s, t, budget);
            ClientBlastGeo.draw(event, bb, any, false);
        }
        for (Hole h : HOLES.values()) {
            double R = holeR(h, t);
            if (cam.distanceTo(new Vec3(h.x, h.y, h.z)) > 512.0) continue;
            BufferBuilder back = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean b1 = R > 0.02 && disk(back, cam, h, R, t, true);
            ClientBlastGeo.draw(event, back, b1, true);
            BufferBuilder black = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean b2 = R > 0.02;
            if (b2) solidDisc(black, cam, left, up, h.x, h.y, h.z, R, 48);
            ClientBlastGeo.draw(event, black, b2, false);
            BufferBuilder front = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean b3 = false;
            if (R > 0.02) { b3 = disk(front, cam, h, R, t, false); rings(front, cam, left, up, h, R, t); infall(front, cam, h, R, t); b3 = true; }
            if (h.throwAt >= 0) {
                double u = (t - h.throwAt) / 10.0;
                if (u < 1.0) { ClientBlastGeo.disc(front, cam, left, up, h.x, h.y, h.z, 1.0 + u * 26.0, 1.0F, 0.96F, 0.9F, (float) (0.95 * (1.0 - u))); b3 = true; }
            }
            ClientBlastGeo.draw(event, front, b3, true);
        }
    }

    private static boolean swarm(BufferBuilder bb, ClientLevel level, Vec3 cam, Vector3f l, Vector3f u, Swarm s, double t, int[] budget) {
        if (cam.distanceTo(new Vec3(s.x, s.y, s.z)) > LOD_FAR + s.r) return false;
        float light = Math.max(0.3F, level.getMaxLocalRawBrightness(BlockPos.containing(s.x, s.y + 2, s.z)) / 15.0F);
        long tick = (long) t;
        boolean any = false;
        int i = 0;

        for (Bite b : s.bites) {
            double bx = b.pos.getX() + 0.5, by = b.pos.getY() + 0.5, bz = b.pos.getZ() + 0.5;
            if (cam.distanceToSqr(bx, by, bz) > LOD_NEAR * LOD_NEAR * 4) continue;
            for (int k = 0; k < BITE_BOTS && budget[0] > 0; k++, i++) {
                int f = k % 5;
                double pu = 0.44 * Math.sin(t * 0.07 * (1 + k % 3) + k * 1.7 + b.breaker), pv = 0.44 * Math.cos(t * 0.05 * (1 + k % 2) + k * 2.3 + b.breaker);
                double x = bx, y = by, z = bz;
                switch (f) {
                    case 0 -> { y += 0.53; x += pu; z += pv; }
                    case 1 -> { x += 0.53; y += pu; z += pv; }
                    case 2 -> { x -= 0.53; y += pu; z += pv; }
                    case 3 -> { z += 0.53; x += pu; y += pv; }
                    default -> { z -= 0.53; x += pu; y += pv; }
                }
                any |= bot(bb, cam, l, u, x, y, z, i, tick, light, budget);
            }
        }

        int ground = (int) Math.min(700, s.r * s.r * 1.0);
        for (int k = 0; k < ground && budget[0] > 0; k++, i++) {
            double a = hash(k, 1) * Math.PI * 2.0 + Math.sin(t * 0.004 * (1 + hash(k, 2)) + k) * 0.35, d = s.r * Math.sqrt(hash(k, 3));
            double x = s.x + Math.cos(a) * d + Math.sin(t * 0.03 + k) * 0.8, z = s.z + Math.sin(a) * d + Math.cos(t * 0.027 + k * 1.3) * 0.8;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            if (!level.hasChunk(ix >> 4, iz >> 4)) continue;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz) + 0.035;
            any |= bot(bb, cam, l, u, x, y, z, i, tick, light, budget);
        }

        int ring = (int) Math.min(260, s.r * 8.0);
        for (int k = 0; k < ring && budget[0] > 0; k++, i++) {
            double a = hash(k, 4) * Math.PI * 2.0 + t * 0.012 * (0.6 + hash(k, 5));
            double d = s.r * (0.78 + 0.28 * hash(k, 6));
            double y = s.y + 0.5 + 4.0 * hash(k, 7) + Math.sin(t * 0.05 + k) * 0.8;
            any |= bot(bb, cam, l, u, s.x + Math.cos(a) * d, y, s.z + Math.sin(a) * d, i, tick, light, budget);
        }
        return any;
    }

    private static boolean bot(BufferBuilder bb, Vec3 cam, Vector3f l, Vector3f u, double x, double y, double z, int i, long tick, float light, int[] budget) {
        double d2 = cam.distanceToSqr(x, y, z);
        if (d2 > LOD_FAR * LOD_FAR) return false;
        budget[0]--;
        boolean glint = (tick / 2 + i * 7) % 173 == 0;
        float br = glint ? 1.0F : 0.80F * light, bg = glint ? 1.0F : 0.83F * light, bb2 = glint ? 1.0F : 0.88F * light;
        if (d2 > LOD_NEAR * LOD_NEAR) {
            if (i % 3 != 0) return false;
            quad(bb, cam, l, u, x, y, z, 0.07, 0.07, br, bg, bb2, 0.85F);
            return true;
        }
        quad(bb, cam, l, u, x, y, z, 0.045, 0.03, br, bg, bb2, 1.0F);
        quad(bb, cam, l, u, x + l.x() * -0.025 + u.x() * 0.012, y + l.y() * -0.025 + u.y() * 0.012, z + l.z() * -0.025 + u.z() * 0.012, 0.011, 0.011, 0.31F, 0.85F, 1.0F, 1.0F);
        double leg = ((tick + i) & 1) == 0 ? 0.008 : -0.008;
        float lr = 0.42F * light, lg = 0.47F * light, lb = 0.53F * light;
        for (int s = -1; s <= 1; s += 2) {
            double ox = s * 0.03 + leg * s, oy = -0.036;
            quad(bb, cam, l, u, x + l.x() * ox + u.x() * oy, y + l.y() * ox + u.y() * oy, z + l.z() * ox + u.z() * oy, 0.009, 0.009, lr, lg, lb, 1.0F);
        }
        return true;
    }

    private static void quad(BufferBuilder bb, Vec3 cam, Vector3f l, Vector3f u, double x, double y, double z, double hw, double hh, float r, float g, float b, float a) {
        ClientBlastGeo.v(bb, cam, x - l.x() * hw - u.x() * hh, y - l.y() * hw - u.y() * hh, z - l.z() * hw - u.z() * hh, r, g, b, a);
        ClientBlastGeo.v(bb, cam, x + l.x() * hw - u.x() * hh, y + l.y() * hw - u.y() * hh, z + l.z() * hw - u.z() * hh, r, g, b, a);
        ClientBlastGeo.v(bb, cam, x + l.x() * hw + u.x() * hh, y + l.y() * hw + u.y() * hh, z + l.z() * hw + u.z() * hh, r, g, b, a);
        ClientBlastGeo.v(bb, cam, x - l.x() * hw + u.x() * hh, y - l.y() * hw + u.y() * hh, z - l.z() * hw + u.z() * hh, r, g, b, a);
    }

    private static Vec3[] basis(Hole h) {
        double tilt = Math.toRadians(16.0);
        Vec3 n = new Vec3(Math.sin(tilt) * Math.cos(h.yaw), Math.cos(tilt), Math.sin(tilt) * Math.sin(h.yaw));
        Vec3 e1 = n.cross(new Vec3(1.0, 0.0, 0.0)).normalize(), e2 = n.cross(e1).normalize();
        return new Vec3[] { n, e1, e2 };
    }

    private static final double[] DCOS = new double[73], DSIN = new double[73];

    private static boolean disk(BufferBuilder bb, Vec3 cam, Hole h, double R, double t, boolean back) {
        Vec3[] bs = basis(h);
        double e1x = bs[1].x, e1y = bs[1].y, e1z = bs[1].z, e2x = bs[2].x, e2y = bs[2].y, e2z = bs[2].z;
        double cx = h.x, cy = h.y, cz = h.z;
        double tcx = cam.x - cx, tcy = cam.y - cy, tcz = cam.z - cz, dist = Math.sqrt(tcx * tcx + tcy * tcy + tcz * tcz), il = dist < 1.0E-9 ? 0.0 : 1.0 / dist;
        tcx *= il; tcy *= il; tcz *= il;
        int M = 12, S = dist > 160.0 ? 24 : dist > 64.0 ? 36 : 72;
        for (int s = 0; s <= S; s++) { double a = Math.PI * 2 * s / S; DCOS[s] = Math.cos(a); DSIN[s] = Math.sin(a); }
        for (int j = 0; j < M; j++) {
            double f0 = 1.5 + 3.1 * j / M, f1 = 1.5 + 3.1 * (j + 1) / M, r0 = R * f0, r1 = R * f1, tj = j / (double) (M - 1), rm = (r0 + r1) * 0.5;
            double phase = t * 0.9 * Math.pow(1.5 / ((f0 + f1) * 0.5), 1.5);
            float cr, cg, cb;
            if (tj < 0.5) { double k = tj / 0.5; cr = 1.0F; cg = (float) (0.93 - 0.38 * k); cb = (float) (0.80 - 0.65 * k); }
            else { double k = (tj - 0.5) / 0.5; cr = (float) (1.0 - 0.5 * k); cg = (float) (0.55 - 0.45 * k); cb = (float) (0.15 - 0.12 * k); }
            for (int s = 0; s < S; s++) {
                double a0 = Math.PI * 2 * s / S, a1 = Math.PI * 2 * (s + 1) / S, am = (a0 + a1) * 0.5;
                double cm = Math.cos(am), sm = Math.sin(am);
                double mdot = ((e1x * cm + e2x * sm) * tcx + (e1y * cm + e2y * sm) * tcy + (e1z * cm + e2z * sm) * tcz) * rm;
                if ((mdot < 0.0) != back) continue;
                double m = am - phase;
                double b = 0.55 + 0.3 * Math.sin(5.0 * m + j * 1.3) + 0.15 * Math.sin(13.0 * m + j * 2.1);
                double tang = (-sm * e1x + cm * e2x) * tcx + (-sm * e1y + cm * e2y) * tcy + (-sm * e1z + cm * e2z) * tcz;
                b *= 1.0 + 0.6 * tang;
                float a = (float) (Math.max(0.0, Math.min(1.0, b)) * (1.0 - 0.55 * tj));
                double c0 = DCOS[s], s0 = DSIN[s], c1 = DCOS[s + 1], s1 = DSIN[s + 1];
                double d0x = e1x * c0 + e2x * s0, d0y = e1y * c0 + e2y * s0, d0z = e1z * c0 + e2z * s0, d1x = e1x * c1 + e2x * s1, d1y = e1y * c1 + e2y * s1, d1z = e1z * c1 + e2z * s1;
                ClientBlastGeo.v(bb, cam, cx + d0x * r0, cy + d0y * r0, cz + d0z * r0, cr, cg, cb, a);
                ClientBlastGeo.v(bb, cam, cx + d0x * r1, cy + d0y * r1, cz + d0z * r1, cr, cg, cb, a * 0.8F);
                ClientBlastGeo.v(bb, cam, cx + d1x * r1, cy + d1y * r1, cz + d1z * r1, cr, cg, cb, a * 0.8F);
                ClientBlastGeo.v(bb, cam, cx + d1x * r0, cy + d1y * r0, cz + d1z * r0, cr, cg, cb, a);
            }
        }
        return true;
    }

    private static void rings(BufferBuilder bb, Vec3 cam, Vector3f l, Vector3f u, Hole h, double R, double t) {
        Vec3 n = basis(h)[0];
        double nx = n.x * l.x() + n.y * l.y() + n.z * l.z(), ny = n.x * u.x() + n.y * u.y() + n.z * u.z(), nl = Math.max(1.0E-4, Math.hypot(nx, ny));
        nx /= nl; ny /= nl;
        int S = 64;
        for (int s = 0; s < S; s++) {
            double a0 = Math.PI * 2 * s / S, a1 = Math.PI * 2 * (s + 1) / S, am = (a0 + a1) * 0.5;
            annulus(bb, cam, l, u, h, a0, a1, R * 1.0, R * 1.09, 1.0F, 0.9F, 0.75F, 0.9F);
            double k = Math.abs(Math.cos(am) * nx + Math.sin(am) * ny);
            k = k * k * (0.6 + 0.4 * Math.sin(6.0 * (am - t * 0.4)));
            annulus(bb, cam, l, u, h, a0, a1, R * 1.12, R * 1.65, 1.0F, 0.62F, 0.22F, (float) (0.75 * k));
        }
    }

    private static void annulus(BufferBuilder bb, Vec3 cam, Vector3f l, Vector3f u, Hole h, double a0, double a1, double r0, double r1, float r, float g, float b, float a) {
        double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
        ClientBlastGeo.v(bb, cam, h.x + (l.x() * c0 + u.x() * s0) * r0, h.y + (l.y() * c0 + u.y() * s0) * r0, h.z + (l.z() * c0 + u.z() * s0) * r0, r, g, b, a);
        ClientBlastGeo.v(bb, cam, h.x + (l.x() * c0 + u.x() * s0) * r1, h.y + (l.y() * c0 + u.y() * s0) * r1, h.z + (l.z() * c0 + u.z() * s0) * r1, r, g, b, 0.0F);
        ClientBlastGeo.v(bb, cam, h.x + (l.x() * c1 + u.x() * s1) * r1, h.y + (l.y() * c1 + u.y() * s1) * r1, h.z + (l.z() * c1 + u.z() * s1) * r1, r, g, b, 0.0F);
        ClientBlastGeo.v(bb, cam, h.x + (l.x() * c1 + u.x() * s1) * r0, h.y + (l.y() * c1 + u.y() * s1) * r0, h.z + (l.z() * c1 + u.z() * s1) * r0, r, g, b, a);
    }

    private static void solidDisc(BufferBuilder bb, Vec3 cam, Vector3f l, Vector3f u, double x, double y, double z, double rad, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(a0) * rad, s0 = Math.sin(a0) * rad, c1 = Math.cos(a1) * rad, s1 = Math.sin(a1) * rad;
            ClientBlastGeo.v(bb, cam, x, y, z, 0.0F, 0.0F, 0.0F, 1.0F);
            ClientBlastGeo.v(bb, cam, x, y, z, 0.0F, 0.0F, 0.0F, 1.0F);
            ClientBlastGeo.v(bb, cam, x + l.x() * c1 + u.x() * s1, y + l.y() * c1 + u.y() * s1, z + l.z() * c1 + u.z() * s1, 0.0F, 0.0F, 0.0F, 1.0F);
            ClientBlastGeo.v(bb, cam, x + l.x() * c0 + u.x() * s0, y + l.y() * c0 + u.y() * s0, z + l.z() * c0 + u.z() * s0, 0.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    private static void infall(BufferBuilder bb, Vec3 cam, Hole h, double R, double t) {
        Vec3[] bs = basis(h);
        Vec3 n = bs[0], e1 = bs[1], e2 = bs[2];
        Minecraft mc = Minecraft.getInstance();
        Vector3f l = mc.gameRenderer.getMainCamera().getLeftVector(), u = mc.gameRenderer.getMainCamera().getUpVector();
        for (int k = 0; k < 60; k++) {
            double f = (t * (0.006 + 0.01 * hash(k, 11)) + hash(k, 12)) % 1.0;
            double r = R * (9.0 - 7.5 * f), a = hash(k, 13) * Math.PI * 2.0 + 7.0 * f * f + t * 0.05, hy = (1.0 - f) * (hash(k, 14) - 0.5) * 3.0 * R;
            Vec3 p = new Vec3(h.x, h.y, h.z).add(e1.scale(Math.cos(a) * r)).add(e2.scale(Math.sin(a) * r)).add(n.scale(hy));
            float al = (float) (0.75 * f);
            quad(bb, cam, l, u, p.x, p.y, p.z, 0.05 + 0.05 * f, 0.05 + 0.05 * f, 0.95F, 0.6F, 0.35F, al);
        }
    }
}
