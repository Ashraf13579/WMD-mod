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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientBlinkGate {

    private ClientBlinkGate() {}

    private static final int FLASH_TICKS = 14, WAVE_TICKS = 22, REFUSE_TICKS = 16, BANDS = 16;
    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 16), SOFT = new ByteBufferBuilder(1 << 15);

    private record Portal(Vec3 c, Vec3 n, Vec3 r, double a, float open, int state, boolean refused, long seen) {}
    private record Burst(int kind, Vec3 c, Vec3 n, double a, long start) {}

    private static final Map<Long, Portal> PORTALS = Statics.client(new HashMap<>());
    private static final List<Burst> BURSTS = Statics.client(new ArrayList<>());

    static void portal(BlockPos pos, Vec3 c, Vec3 n, Vec3 r, double a, float open, int state, boolean refused, long now) {
        if (PORTALS.size() > 32) PORTALS.clear();
        PORTALS.put(pos.asLong(), new Portal(c, n, r, a, open, state, refused, now));
        if (open > 0.05F) VeilBridge.glow(0x424C4E4BL ^ pos.asLong(), c.x, c.y, c.z, refused ? 1.0F : 0.25F, refused ? 0.2F : 0.55F, 1.0F, (float) (a * 2.5), 2.5F * open, now);
    }

    static void onPayload(BlinkGateFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (BURSTS.size() > 32) BURSTS.clear();
        BURSTS.add(new Burst(p.kind(), p.centre(), p.normal(), p.half(), level.getGameTime()));
    }

    private static double[][] oct(double a, double f) {
        double q = Math.max(0.0, a - 2.0);
        double[][] p = { { q, a }, { -q, a }, { -a, q }, { -a, -q }, { -q, -a }, { q, -a }, { a, -q }, { a, q } };
        for (double[] v : p) { v[0] *= f; v[1] *= f; }
        return p;
    }

    private static Vec3 at(Portal p, double[] v) { return p.c().add(p.r().scale(v[0])).add(0.0, v[1], 0.0); }

    private static Vec3 at(Burst b, Vec3 r, double[] v) { return b.c().add(r.scale(v[0])).add(0.0, v[1], 0.0); }

    private static void band(BufferBuilder bb, Vec3 cam, java.util.function.Function<double[], Vec3> map, double a, double f0, double f1,
                             float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        double[][] in = oct(a, f0), out = oct(a, f1);
        for (int k = 0; k < 8; k++) {
            int j = (k + 1) % 8;
            ClientBlastGeo.quad(bb, cam, map.apply(in[k]), map.apply(in[j]), map.apply(out[j]), map.apply(out[k]), r0, g0, b0, a0, r1, g1, b1, a1);
        }
    }

    private static double[] onRim(double a, double t) {
        double[][] p = oct(a, 1.0);
        double[] len = new double[8];
        double tot = 0.0;
        for (int k = 0; k < 8; k++) { int j = (k + 1) % 8; len[k] = Math.hypot(p[j][0] - p[k][0], p[j][1] - p[k][1]); tot += len[k]; }
        double d = ((t % 1.0) + 1.0) % 1.0 * tot;
        for (int k = 0; k < 8; k++) {
            if (d <= len[k]) { int j = (k + 1) % 8; double u = d / Math.max(1.0E-6, len[k]); return new double[] { p[k][0] + (p[j][0] - p[k][0]) * u, p[k][1] + (p[j][1] - p[k][1]) * u }; }
            d -= len[k];
        }
        return p[0];
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || (PORTALS.isEmpty() && BURSTS.isEmpty())) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder soft = new BufferBuilder(SOFT, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyG = false, anyS = false;
        double far = Balance.BG_FX_RANGE;
        for (Iterator<Portal> it = PORTALS.values().iterator(); it.hasNext();) {
            Portal p = it.next();
            if (tick - p.seen() > 3) { it.remove(); continue; }
            if (p.c().distanceTo(cam) > far) continue;
            double a = p.a() - 0.12;
            java.util.function.Function<double[], Vec3> map = v -> at(p, v);
            float o = p.open();
            if (o > 0.0F) {
                boolean red = p.refused();
                double fIn = Math.max(0.0, 1.0 - o);
                for (int j = 0; j < BANDS; j++) {
                    double f0 = fIn + (1.0 - fIn) * j / BANDS, f1 = fIn + (1.0 - fIn) * (j + 1) / BANDS;

                    float s0 = (float) f0, s1 = (float) f1;
                    float br0 = red ? 0.55F : 0.02F + 0.08F * s0, bg0 = red ? 0.04F : 0.08F + 0.32F * s0, bb0 = red ? 0.06F : 0.40F + 0.55F * s0;
                    float br1 = red ? 0.55F : 0.02F + 0.08F * s1, bg1 = red ? 0.04F : 0.08F + 0.32F * s1, bb1 = red ? 0.06F : 0.40F + 0.55F * s1;
                    band(soft, cam, map, a, f0, f1, br0, bg0, bb0, 0.72F, br1, bg1, bb1, 0.72F);

                    float w0 = ripple(f0, now), w1 = ripple(f1, now);
                    float rim0 = (float) Math.pow(Math.max(0.0, (f0 - 0.86) / 0.14), 3.0), rim1 = (float) Math.pow(Math.max(0.0, (f1 - 0.86) / 0.14), 3.0);
                    float al0 = Math.min(1.0F, 0.55F * w0 + 0.9F * rim0), al1 = Math.min(1.0F, 0.55F * w1 + 0.9F * rim1);
                    if (al0 > 0.01F || al1 > 0.01F)
                        band(glow, cam, map, a, f0, f1, red ? 1.0F : 0.35F, red ? 0.25F : 0.82F, red ? 0.2F : 1.0F, al0, red ? 1.0F : 0.35F, red ? 0.25F : 0.82F, red ? 0.2F : 1.0F, al1);
                }

                if (o < 0.999F) band(glow, cam, map, a, Math.max(0.0, fIn - 0.015), fIn + 0.02, 0.8F, 0.95F, 1.0F, 0.0F, 0.8F, 0.95F, 1.0F, 0.9F);
                anyS = true; anyG = true;
            } else if (p.state() == BlinkGateCoreBlockEntity.S_COOL) {

                for (int k = 0; k < 8; k++) {
                    double[] v = onRim(a, now * 0.004 + k / 8.0);
                    Vec3 q = at(p, v);
                    ClientBlastGeo.disc(glow, cam, lv, uv, q.x, q.y, q.z, 0.22, 1.0F, 0.6F, 0.1F, 0.85F, 10);
                }
                anyG = true;
            }
        }
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext();) {
            Burst b = it.next();
            double age = now - b.start();
            if (age > Math.max(WAVE_TICKS, REFUSE_TICKS) + 2) { it.remove(); continue; }
            if (age < 0.0 || b.c().distanceTo(cam) > far) continue;
            Vec3 r = new Vec3(-b.n().z, 0.0, b.n().x);
            double a = b.a() - 0.12;
            java.util.function.Function<double[], Vec3> map = v -> at(b, r, v);
            if (b.kind() == BlinkGateCoreBlockEntity.FX_REFUSE) {
                double u = age / REFUSE_TICKS;
                if (u < 1.0) {
                    float f = (float) (1.0 - u);
                    band(glow, cam, map, a, 0.9, 1.0, 1.0F, 0.15F, 0.1F, 0.0F, 1.0F, 0.15F, 0.1F, 0.95F * f);
                    band(glow, cam, map, a, 0.0, 0.9, 1.0F, 0.1F, 0.05F, 0.25F * f, 1.0F, 0.1F, 0.05F, 0.35F * f);
                    anyG = true;
                }
                continue;
            }
            double u = age / FLASH_TICKS;
            if (u < 1.0) {
                float f = (float) (1.0 - u);
                band(glow, cam, map, a, 0.0, 1.0, 0.85F, 0.95F, 1.0F, 0.95F * f * f, 0.85F, 0.95F, 1.0F, 0.6F * f);
                ClientBlastGeo.disc(glow, cam, lv, uv, b.c().x, b.c().y, b.c().z, a * (0.5 + 0.8 * u), 0.8F, 0.92F, 1.0F, 0.8F * f, 28);
                VeilBridge.glow(0x424C4E46L ^ b.start() ^ Double.doubleToLongBits(b.c().x), b.c().x, b.c().y, b.c().z, 0.6F, 0.85F, 1.0F, (float) (a * 4.0), 8.0F * f, tick);
            }
            double w = age / WAVE_TICKS;
            if (w < 1.0) {
                double f = 0.3 + 1.6 * Math.sqrt(w);
                float fade = (float) ((1.0 - w) * (1.0 - w));
                band(glow, cam, map, a, f - 0.06, f, 0.5F, 0.85F, 1.0F, 0.0F, 0.5F, 0.85F, 1.0F, 0.85F * fade);
                band(glow, cam, map, a, f, f + 0.04, 0.5F, 0.85F, 1.0F, 0.85F * fade, 0.5F, 0.85F, 1.0F, 0.0F);
            }
            anyG = true;
        }
        ClientBlastGeo.draw(event, soft, anyS, false);
        ClientBlastGeo.draw(event, glow, anyG, true);
    }

    private static float ripple(double f, double now) {
        double s = 0.5 + 0.5 * Math.sin(Math.PI * 2.0 * (f * 3.0 + now * 0.035));
        return (float) (Math.pow(s, 8.0) * (0.35 + 0.65 * f));
    }
}
