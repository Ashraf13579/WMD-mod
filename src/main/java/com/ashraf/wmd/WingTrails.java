package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class WingTrails {

    private WingTrails() {}

    static final double GRAV = 11.0;

    private static final class Craft {
        final ArrayDeque<double[]> left = new ArrayDeque<>(), right = new ArrayDeque<>();
        Vec3 lastC, lastV, acc = Vec3.ZERO;
        double k;
        long seen;
    }

    private static final Map<Long, Craft> CRAFTS = Statics.client(new HashMap<>());

    static void feed(Level lv, BlockPos pos, double[] port, double[] stbd) {
        if (lv == null || !lv.isClientSide || port == null || stbd == null || !AirGate.on()) return;
        long now = lv.getGameTime();
        Craft c = CRAFTS.computeIfAbsent(pos.asLong(), p -> new Craft());
        if (c.seen == now) return;
        c.seen = now;
        Vec3 o = Vec3.atCenterOf(pos);
        Vec3 w = ShipFrame.drawnWorld(lv, pos, o, Float.NaN), wu = ShipFrame.drawnWorld(lv, pos, o.add(0.0, 1.0, 0.0), Float.NaN);
        if (w == null || wu == null || w == o) { c.lastC = null; return; }
        Vec3 up = wu.subtract(w).normalize();
        if (c.lastC != null) {
            Vec3 v = w.subtract(c.lastC).scale(20.0);
            if (c.lastV != null) c.acc = c.acc.scale(0.6).add(v.subtract(c.lastV).scale(20.0).scale(0.4));
            c.lastV = c.lastV == null ? v : c.lastV.scale(0.5).add(v.scale(0.5));
        }
        c.lastC = w;
        double speed = c.lastV == null ? 0.0 : c.lastV.length();
        double g = c.acc.add(0.0, GRAV, 0.0).dot(up) / GRAV;
        double target = speed < Balance.TRAIL_SPEED ? 0.0 : clamp((g - Balance.TRAIL_G0) / Balance.TRAIL_G_SPAN);
        c.k += (target - c.k) * 0.25;
        push(c.left, lv, pos, port, c.k, now);
        push(c.right, lv, pos, stbd, c.k, now);
    }

    private static void push(ArrayDeque<double[]> q, Level lv, BlockPos pos, double[] tip, double k, long now) {
        while (!q.isEmpty() && now - q.peekLast()[4] > Balance.TRAIL_LIFE) q.pollLast();
        if (k < 0.03) { if (!q.isEmpty() && q.peekFirst()[3] > 0.0) q.addFirst(new double[] { 0, 0, 0, 0.0, now }); return; }
        Vec3 w = ShipFrame.drawnWorld(lv, pos, new Vec3(tip[0], tip[1], tip[2]), Float.NaN);
        if (w != null) q.addFirst(new double[] { w.x, w.y, w.z, k, now });
    }

    private static double clamp(double x) { return x < 0.0 ? 0.0 : x > 1.0 ? 1.0 : x; }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) { if (!CRAFTS.isEmpty()) CRAFTS.clear(); return; }
        long now = lv.getGameTime();
        for (Iterator<Craft> it = CRAFTS.values().iterator(); it.hasNext(); ) {
            Craft c = it.next();
            if (now - c.seen > Balance.TRAIL_FORGET || now < c.seen - 40) it.remove();
        }
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CRAFTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        double now = lv.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        boolean packOn = ShaderFx.on;
        VertexConsumer vc = packOn ? null : buf.getBuffer(RenderType.debugQuads());
        boolean drew = false;
        for (Craft c : CRAFTS.values()) {
            drew |= ribbon(vc, m, c.left, cam, now, packOn);
            drew |= ribbon(vc, m, c.right, cam, now, packOn);
        }
        ps.popPose();
        if (packOn) ShaderFx.flush(event, PACK_KEY, false);
        else if (drew) buf.endBatch(RenderType.debugQuads());
    }

    private static final com.mojang.blaze3d.vertex.BufferBuilder PACK_KEY = new com.mojang.blaze3d.vertex.BufferBuilder(
            new com.mojang.blaze3d.vertex.ByteBufferBuilder(64), com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);

    private static boolean ribbon(VertexConsumer vc, Matrix4f m, ArrayDeque<double[]> q, Vec3 cam, double now, boolean packOn) {
        if (q.size() < 2) return false;
        boolean drew = false;
        double[] prev = null;
        for (double[] p : q) {
            if (prev != null && prev[3] > 0.0 && p[3] > 0.0) {
                Vec3 a = new Vec3(prev[0], prev[1], prev[2]), b = new Vec3(p[0], p[1], p[2]);
                Vec3 along = b.subtract(a);
                if (along.lengthSqr() > 1.0E-6 && a.distanceToSqr(cam) < Balance.TRAIL_RANGE * Balance.TRAIL_RANGE) {
                    Vec3 side = along.cross(a.subtract(cam)).normalize();
                    double ageA = (now - prev[4]) / Balance.TRAIL_LIFE, ageB = (now - p[4]) / Balance.TRAIL_LIFE;
                    double wa = width(prev[3], ageA), wb = width(p[3], ageB);
                    int aa = alpha(prev[3], ageA, now - prev[4]), ab = alpha(p[3], ageB, now - p[4]);
                    if (aa > 0 || ab > 0) {
                        Vec3 a0 = a.add(side.scale(wa)), a1 = a.subtract(side.scale(wa)), b0 = b.add(side.scale(wb)), b1 = b.subtract(side.scale(wb));
                        if (packOn) {
                            ShaderFx.vert(PACK_KEY, cam, a0.x, a0.y, a0.z, 1.0F, 1.0F, 1.0F, aa / 255.0F); ShaderFx.vert(PACK_KEY, cam, a1.x, a1.y, a1.z, 1.0F, 1.0F, 1.0F, aa / 255.0F);
                            ShaderFx.vert(PACK_KEY, cam, b1.x, b1.y, b1.z, 1.0F, 1.0F, 1.0F, ab / 255.0F); ShaderFx.vert(PACK_KEY, cam, b0.x, b0.y, b0.z, 1.0F, 1.0F, 1.0F, ab / 255.0F);
                        } else {
                            vtx(vc, m, a0, aa); vtx(vc, m, a1, aa); vtx(vc, m, b1, ab); vtx(vc, m, b0, ab);
                            vtx(vc, m, b0, ab); vtx(vc, m, b1, ab); vtx(vc, m, a1, aa); vtx(vc, m, a0, aa);
                        }
                        drew = true;
                    }
                }
            }
            prev = p;
        }
        return drew;
    }

    private static double width(double k, double age) { return (Balance.TRAIL_W0 + Balance.TRAIL_W1 * k) * (1.0 + 1.5 * Math.max(0.0, age)); }

    private static int alpha(double k, double age, double ticks) {
        double fadeIn = Math.min(1.0, ticks / 2.0);
        return (int) Math.round(255 * Balance.TRAIL_ALPHA * k * Math.max(0.0, 1.0 - age) * fadeIn);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, Math.max(0, Math.min(255, a)));
    }
}
