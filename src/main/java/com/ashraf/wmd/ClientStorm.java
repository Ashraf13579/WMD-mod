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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientStorm {

    private ClientStorm() {}

    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 17), DARK = new ByteBufferBuilder(1 << 17);

    private static final class S {
        Vec3 tip, eye;
        float r;
        long t0, t1, t2, cut = -1L, lastStrike = Long.MIN_VALUE / 2;
    }

    private record Bolt(int id, Vec3 hit, Vec3 from, long at, long seed) {}
    private record Steam(Vec3 p, long seen) {}

    private static final Map<Integer, S> STORMS = Statics.client(new HashMap<>());
    private static final List<Bolt> BOLTS = Statics.client(new ArrayList<>());
    private static final Map<Long, Steam> STEAM = Statics.client(new HashMap<>());

    static void message(StormPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (p.kind()) {
            case StormPayload.START -> {
                if (STORMS.containsKey(p.id())) return;
                S s = new S();
                s.tip = p.a(); s.eye = p.b(); s.r = p.r(); s.t0 = p.t0(); s.t1 = p.t1(); s.t2 = p.t2();
                if (STORMS.size() > 16) STORMS.clear();
                STORMS.put(p.id(), s);
            }
            case StormPayload.STRIKE -> {
                S s = STORMS.get(p.id());
                if (s != null) s.lastStrike = level.getGameTime();
                if (BOLTS.size() > 24) BOLTS.remove(0);
                BOLTS.add(new Bolt(p.id(), p.a(), p.b(), level.getGameTime(), level.random.nextLong()));
            }
            case StormPayload.CUT -> { S s = STORMS.get(p.id()); if (s != null) s.cut = p.t0(); }
            default -> { }
        }
    }

    static long lastStrike(int id) { S s = STORMS.get(id); return s == null ? Long.MIN_VALUE / 2 : s.lastStrike; }

    static void steam(long key, Vec3 p, long now) {
        if (STEAM.size() > 64) STEAM.clear();
        STEAM.put(key, new Steam(p, now));
    }

    private static long rainEnd(S s) { return s.cut >= 0 ? Math.min(s.t2, Math.max(s.cut, s.t0)) : s.t2; }

    private static double cellK(S s, double now) {
        double seed0 = s.t1 - Balance.STGEN_SEED, end = rainEnd(s);
        if (now < seed0) return 0.0;
        if (now < s.t1) return (now - seed0) / Balance.STGEN_SEED;
        if (now < end) return 1.0;
        return Math.max(0.0, 1.0 - (now - end) / Balance.STGEN_FADE);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (STORMS.isEmpty() && STEAM.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder dark = new BufferBuilder(DARK, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyD = false, anyG = false;
        for (Iterator<Map.Entry<Integer, S>> it = STORMS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, S> en = it.next();
            S s = en.getValue();
            long end = rainEnd(s);
            if (now > end + Balance.STGEN_FADE + 20) { it.remove(); continue; }
            double k = cellK(s, now);
            boolean beam = now >= s.t1 - Balance.STGEN_SEED && now < end;
            if (k > 0.01) { cell(dark, glow, cam, lv, uv, en.getKey(), s, now, k); anyD = anyG = true; }
            if (beam) { beam(glow, cam, lv, uv, s, now, tick); anyG = true; }
            if (now >= s.t1 && now < end + 40 && Math.hypot(cam.x - s.eye.x, cam.z - s.eye.z) < s.r + 96.0) { rain(dark, cam, s, now, end); anyD = true; }
        }
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            double age = now - b.at();
            if (age > Balance.STGEN_BOLT_TICKS) { it.remove(); continue; }
            bolt(glow, cam, lv, uv, b, age, tick);
            anyG = true;
        }
        for (Iterator<Steam> it = STEAM.values().iterator(); it.hasNext(); ) {
            Steam st = it.next();
            if (tick - st.seen() > 3) { it.remove(); continue; }
            if (st.p().distanceToSqr(cam) > 96.0 * 96.0) continue;
            for (int i = 0; i < 4; i++) {
                double f = ((now * 0.03 + i * 0.25) % 1.0);
                ClientBlastGeo.disc(dark, cam, lv, uv, st.p().x + Math.sin(i * 2.1 + now * 0.05) * 0.3 * f, st.p().y + f * 3.0, st.p().z + Math.cos(i * 1.7) * 0.3 * f,
                        0.35 + f * 1.1, 0.85F, 0.87F, 0.9F, (float) (0.35 * (1.0 - f)), 10);
            }
            anyD = true;
        }
        ClientBlastGeo.draw(event, dark, anyD, false);
        ClientBlastGeo.draw(event, glow, anyG, true);
    }

    private static void cell(BufferBuilder dark, BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, int id, S s, double now, double k) {
        RandomSource r = RandomSource.create(id * 7919L);
        float a = (float) Math.min(1.0, k * 1.2);
        int n = 34;
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0, d = Math.sqrt(r.nextDouble()) * s.r * 1.15, dy = r.nextDouble() * 22.0 + 4.0 * (1.0 - d / (s.r * 1.15));
            double spin = now * 0.003 * (1.3 - d / (s.r * 1.15)), rad = (10.0 + r.nextDouble() * 14.0) * (0.35 + 0.65 * k);
            float tone = 0.14F + r.nextFloat() * 0.12F;
            ClientBlastGeo.disc(dark, cam, lv, uv, s.eye.x + Math.cos(ang + spin) * d * (0.5 + 0.5 * k), s.eye.y + dy, s.eye.z + Math.sin(ang + spin) * d * (0.5 + 0.5 * k),
                    rad, tone, tone, tone + 0.03F, 0.85F * a, 12);
        }
        for (int ring = 0; ring < 5; ring++) {
            double r0 = s.r * (0.12 + ring * 0.22), r1 = r0 + s.r * 0.26, spin = now * (0.02 - ring * 0.003), y = s.eye.y - 1.0 - ring * 0.8;
            float al = (float) (a * (0.55 - ring * 0.07));
            int m = 40;
            for (int i = 0; i < m; i++) {
                double a0 = spin + Math.PI * 2 * i / m, a1 = spin + Math.PI * 2 * (i + 1) / m;
                float ai = (float) (al * (0.6 + 0.4 * Math.sin(i * 1.7 + ring * 2.3 + now * 0.02)));
                float t = 0.12F + 0.03F * ring;
                ClientBlastGeo.v(dark, cam, s.eye.x + Math.cos(a0) * r0, y, s.eye.z + Math.sin(a0) * r0, t, t, t + 0.04F, ai);
                ClientBlastGeo.v(dark, cam, s.eye.x + Math.cos(a1) * r0, y, s.eye.z + Math.sin(a1) * r0, t, t, t + 0.04F, ai);
                ClientBlastGeo.v(dark, cam, s.eye.x + Math.cos(a1) * r1, y + 1.5, s.eye.z + Math.sin(a1) * r1, t, t, t + 0.04F, ai * 0.4F);
                ClientBlastGeo.v(dark, cam, s.eye.x + Math.cos(a0) * r1, y + 1.5, s.eye.z + Math.sin(a0) * r1, t, t, t + 0.04F, ai * 0.4F);
            }
        }
        float pulse = (float) (0.7 + 0.3 * Math.sin(now * 0.3));
        ClientBlastGeo.disc(glow, cam, lv, uv, s.eye.x, s.eye.y - 1.5, s.eye.z, 5.0 * k, 0.45F, 0.85F, 1.0F, 0.8F * a * pulse, 16);
        ClientBlastGeo.flatDisc(glow, cam, s.eye.x, s.eye.y - 2.0, s.eye.z, s.r * 0.3 * k, 0.3F, 0.6F, 1.0F, 0.35F * a * pulse, 24);
    }

    private static void beam(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, S s, double now, long tick) {
        Vec3 a = s.tip, b = s.eye.add(0.0, -2.0, 0.0);
        double rise = Math.min(1.0, (now - (s.t1 - Balance.STGEN_SEED)) / 20.0);
        Vec3 e = a.add(b.subtract(a).scale(rise));
        double len = Math.max(0.01, a.distanceTo(e));
        Vec3 d = e.subtract(a).scale(1.0 / len);
        Vec3 u = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0).cross(d).normalize() : d.cross(new Vec3(0.0, 1.0, 0.0)).normalize(), v = d.cross(u);
        float flare = (float) Math.max(0.0, 1.0 - (now - s.lastStrike) / 10.0);

        ClientBlastGeo.streak(glow, cam, a.x, a.y, a.z, e.x, e.y, e.z, 0.12 + 0.12 * flare, 0.95F, 0.98F, 1.0F, 0.95F, 0.85F);
        ClientBlastGeo.streak(glow, cam, a.x, a.y, a.z, e.x, e.y, e.z, 0.55 + 0.5 * flare, 0.45F + 0.4F * flare, 0.4F + 0.5F * flare, 1.0F, 0.32F + 0.3F * flare, 0.2F);

        helix(glow, cam, a, d, u, v, len, now, 1.0, 0.0, 0.35F, 0.9F, 1.0F, flare);
        helix(glow, cam, a, d, u, v, len, now, -1.0, Math.PI, 0.75F, 0.5F, 1.0F, flare);

        for (int i = 0; i < 4; i++) {
            double f = (now * 0.035 + i / 4.0) % 1.0, f0 = Math.max(0.0, f - 0.06);
            if (f > rise) continue;
            Vec3 p0 = a.add(d.scale(f0 * len)), p1 = a.add(d.scale(f * len));
            ClientBlastGeo.streak(glow, cam, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.32, 0.85F, 0.95F, 1.0F, 0.15F, 0.95F);
            ClientBlastGeo.disc(glow, cam, lv, uv, p1.x, p1.y, p1.z, 1.1, 0.7F, 0.9F, 1.0F, 0.85F, 12);
            if (i == 0) VeilBridge.glow(0x50414B54L ^ Double.doubleToLongBits(a.z), p1.x, p1.y, p1.z, 0.45F, 0.75F, 1.0F, 10.0F, 1.2F, tick);
        }

        for (int i = 0; i < 6; i++) {
            double f = (now * 0.012 + i / 6.0) % 1.0;
            if (f > rise) continue;
            Vec3 c = a.add(d.scale(f * len));
            ring(glow, cam, c, u, v, 0.7 + 2.2 * f, 0.09, 0.4F, 0.8F, 1.0F, (float) (0.75 * (1.0 - f)), 20);
        }

        RandomSource r = RandomSource.create(tick * 31L + Double.doubleToLongBits(a.x));
        for (int i = 0; i < 3 + (int) (flare * 4); i++) {
            Vec3 p = a.add(d.scale(r.nextDouble() * len));
            double ang = r.nextDouble() * Math.PI * 2.0, out = 1.2 + r.nextDouble() * 2.2;
            Vec3 q = p.add(u.scale(Math.cos(ang) * out)).add(v.scale(Math.sin(ang) * out)).add(d.scale((r.nextDouble() - 0.3) * 2.0));
            jag(glow, cam, p, q, r, 0.03, 0.85F, 6, 0.18);
        }

        for (int k = 0; k < 3; k++) {
            double spin = now * 0.35 + k * Math.PI * 2.0 / 3.0, tilt = 0.9;
            Vec3 lu = u.scale(Math.cos(spin)).add(v.scale(Math.sin(spin)));
            Vec3 lw = d.scale(Math.cos(tilt)).add(lu.cross(d).scale(Math.sin(tilt)));
            ring(glow, cam, a, lu, lw, 0.75, 0.05, 0.6F, 0.9F, 1.0F, 0.8F, 18);
        }
        ClientBlastGeo.disc(glow, cam, lv, uv, a.x, a.y, a.z, 1.3 + 0.8 * flare, 0.8F, 0.95F, 1.0F, 0.95F, 12);

        if (rise >= 1.0) {
            for (int i = 0; i < 3; i++) {
                double f = (now * 0.02 + i / 3.0) % 1.0;
                ring(glow, cam, e, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), 2.0 + 14.0 * f, 0.35, 0.45F, 0.8F, 1.0F, (float) (0.55 * (1.0 - f)), 32);
            }
            ClientBlastGeo.disc(glow, cam, lv, uv, e.x, e.y, e.z, 4.0 + 3.0 * flare, 0.55F, 0.85F, 1.0F, 0.7F, 16);
        }
        VeilBridge.glow(0x53544F524DL ^ Double.doubleToLongBits(a.x), a.x, a.y, a.z, 0.5F, 0.8F, 1.0F, 12.0F, 1.6F + 1.5F * flare, tick);
    }

    private static void helix(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 d, Vec3 u, Vec3 v, double len, double now, double dir, double ph, float r, float g, float bl, float flare) {
        int n = Math.max(12, Math.min(160, (int) (len * 1.5)));
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, s = t * len;
            double ang = ph + dir * (s * 0.9 - now * 0.22), rad = (0.45 + 0.18 * Math.sin(s * 0.7 + now * 0.15)) * (1.0 + 0.6 * flare);
            Vec3 p = a.add(d.scale(s)).add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad));
            if (prev != null) {
                float al = (float) (0.85 * (1.0 - 0.5 * t));
                ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.07, r, g, bl, al, al);
            }
            prev = p;
        }
    }

    private static void ring(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 x, Vec3 y, double R, double w, float r, float g, float bl, float al, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 o0 = x.scale(Math.cos(a0)).add(y.scale(Math.sin(a0))), o1 = x.scale(Math.cos(a1)).add(y.scale(Math.sin(a1)));
            Vec3 p0 = c.add(o0.scale(R - w)), p1 = c.add(o1.scale(R - w)), q1 = c.add(o1.scale(R + w)), q0 = c.add(o0.scale(R + w));
            ClientBlastGeo.v(bb, cam, p0.x, p0.y, p0.z, r, g, bl, al);
            ClientBlastGeo.v(bb, cam, p1.x, p1.y, p1.z, r, g, bl, al);
            ClientBlastGeo.v(bb, cam, q1.x, q1.y, q1.z, r, g, bl, al * 0.3F);
            ClientBlastGeo.v(bb, cam, q0.x, q0.y, q0.z, r, g, bl, al * 0.3F);
        }
    }

    private static void rain(BufferBuilder dark, Vec3 cam, S s, double now, long end) {
        float fade = now < end ? 1.0F : (float) Math.max(0.0, 1.0 - (now - end) / 40.0);
        double top = s.eye.y - 3.0, h = Math.max(20.0, top - (s.eye.y - Balance.STGEN_CLOUD_H - 6.0));
        boolean inside = Math.hypot(cam.x - s.eye.x, cam.z - s.eye.z) < s.r;
        for (int pass = 0; pass < (inside ? 2 : 1); pass++) {
            int n = pass == 0 ? 260 : 320;
            RandomSource r = RandomSource.create(pass * 104729L + (long) s.eye.x);
            for (int i = 0; i < n; i++) {
                double x, z;
                if (pass == 0) { double a = r.nextDouble() * Math.PI * 2.0, d = Math.sqrt(r.nextDouble()) * s.r; x = s.eye.x + Math.cos(a) * d; z = s.eye.z + Math.sin(a) * d; }
                else { x = cam.x + (r.nextDouble() - 0.5) * 32.0; z = cam.z + (r.nextDouble() - 0.5) * 32.0; if (Math.hypot(x - s.eye.x, z - s.eye.z) > s.r) continue; }
                double ph = r.nextDouble(), sp = 1.1 + r.nextDouble() * 0.4;
                double hgt = pass == 0 ? h : 24.0, base = pass == 0 ? top - h : cam.y - 10.0;
                double y = base + hgt - ((now * sp + ph * hgt) % hgt);
                if (y > top) continue;
                ClientBlastGeo.streak(dark, cam, x, y, z, x + 0.25, y - 1.6, z + 0.1, 0.035, 0.62F, 0.68F, 0.78F, 0.45F * fade, 0.1F * fade);
            }
        }
    }

    private static void bolt(BufferBuilder glow, Vec3 cam, Vector3f lv, Vector3f uv, Bolt b, double age, long tick) {
        float al = (float) (1.0 - age / Balance.STGEN_BOLT_TICKS);
        if (age > 2 && age < 4) al *= 0.35F;
        RandomSource r = RandomSource.create(b.seed());
        List<Vec3> pts = jag(glow, cam, b.from(), b.hit(), r, 0.32, al, 18, 0.07);
        jag(glow, cam, b.from(), b.hit(), RandomSource.create(b.seed() ^ 0x5DEECE66DL), 0.9, al * 0.25F, 18, 0.07);
        for (int j = 4; j < pts.size() - 3; j += 5) {
            Vec3 q = pts.get(j);
            Vec3 t = q.add(r.nextGaussian() * 9.0, -6.0 - r.nextDouble() * 14.0, r.nextGaussian() * 9.0);
            jag(glow, cam, q, t, r, 0.12, al * 0.7F, 8, 0.12);
        }
        ClientBlastGeo.disc(glow, cam, lv, uv, b.hit().x, b.hit().y + 0.5, b.hit().z, 2.5 + 2.0 * al, 0.85F, 0.92F, 1.0F, 0.95F * al, 16);
        ClientBlastGeo.disc(glow, cam, lv, uv, b.from().x, b.from().y + 4.0, b.from().z, 22.0, 0.6F, 0.7F, 0.95F, 0.35F * al, 16);
        if (age < 3.0) VeilBridge.glow(0x424F4C54L ^ b.seed(), b.hit().x, b.hit().y + 1.0, b.hit().z, 0.75F, 0.85F, 1.0F, 24.0F, 4.0F * al, tick);
    }

    private static List<Vec3> jag(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 b, RandomSource r, double w, float al, int n, double amp) {
        List<Vec3> pts = new ArrayList<>(n + 1);
        pts.add(a);
        Vec3 prev = a;
        double len = a.distanceTo(b);
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, k = len * amp * (1.0 - Math.abs(2.0 * t - 1.0));
            Vec3 p = i == n ? b : a.add(b.subtract(a).scale(t)).add(r.nextGaussian() * k, r.nextGaussian() * k * 0.4, r.nextGaussian() * k);
            ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, p.x, p.y, p.z, w, 0.8F, 0.9F, 1.0F, al, al);
            pts.add(p);
            prev = p;
        }
        return pts;
    }
}
