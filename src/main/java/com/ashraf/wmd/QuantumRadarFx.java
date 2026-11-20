package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class QuantumRadarFx {

    private QuantumRadarFx() {}
    private static VertexBuffer reusedVb;

    private record Pair(Vec3 from, Vec3 to, long start, boolean back) {}

    private static final List<Pair> PAIRS = Statics.client(new ArrayList<>());

    static void pair(Vec3 from, Vec3 to, long now, boolean back) {
        if (from == null || to == null) return;
        if (PAIRS.size() >= 32) PAIRS.remove(0);
        PAIRS.add(new Pair(from, to, now, back));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PAIRS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { PAIRS.clear(); return; }
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PAIRS.removeIf(p -> now - p.start() > Balance.QR_PULSE_TICKS || now < p.start() - 1);
        if (PAIRS.isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = 0;
        for (Pair p : PAIRS) {
            double u = (now - p.start()) / Balance.QR_PULSE_TICKS;
            double f = p.back() ? (u < 0.5 ? u * 2.0 : 2.0 - u * 2.0) : u;
            float a = p.back() ? 0.95F : (float) (0.8 * (1.0 - u));
            Vec3 d = p.to().subtract(p.from());
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
            for (int k = -1; k <= 1; k += 2) {
                double spread = 0.6 * (1.0 - f * 0.7);
                Vec3 head = p.from().add(d.scale(f)).add(side.scale(k * spread));
                Vec3 tail = p.from().add(d.scale(Math.max(0.0, f - (u < 0.5 || !p.back() ? 0.06 : -0.06)))).add(side.scale(k * spread));
                glow(bb, cam, left, up, head, 0.55, 0.5F, 0.95F, 1.0F, a);
                streak(bb, cam, tail, head, 0.18, 0.4F, 0.85F, 1.0F, a * 0.7F);
                n++;
            }
        }
        MeshData md = bb.build();
        if (md == null || n == 0) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (reusedVb == null) reusedVb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        reusedVb.bind();
        reusedVb.upload(md);
        reusedVb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void vtx(BufferBuilder bb, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        bb.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
    }

    private static void glow(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, double rad, float r, float g, float b, float a) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(a0) * rad, s0 = Math.sin(a0) * rad, c1 = Math.cos(a1) * rad, s1 = Math.sin(a1) * rad;
            vtx(bb, cam, c.x, c.y, c.z, r, g, b, a);
            vtx(bb, cam, c.x, c.y, c.z, r, g, b, a);
            vtx(bb, cam, c.x + left.x() * c1 + up.x() * s1, c.y + left.y() * c1 + up.y() * s1, c.z + left.z() * c1 + up.z() * s1, r, g, b, 0.0F);
            vtx(bb, cam, c.x + left.x() * c0 + up.x() * s0, c.y + left.y() * c0 + up.y() * s0, c.z + left.z() * c0 + up.z() * s0, r, g, b, 0.0F);
        }
    }

    private static void streak(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 b, double w, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a), m = a.add(b).scale(0.5).subtract(cam);
        Vec3 s = d.cross(m);
        if (s.lengthSqr() < 1.0E-9) return;
        s = s.normalize().scale(w);
        vtx(bb, cam, a.x - s.x, a.y - s.y, a.z - s.z, r, g, bl, 0.0F);
        vtx(bb, cam, a.x + s.x, a.y + s.y, a.z + s.z, r, g, bl, 0.0F);
        vtx(bb, cam, b.x + s.x, b.y + s.y, b.z + s.z, r, g, bl, al);
        vtx(bb, cam, b.x - s.x, b.y - s.y, b.z - s.z, r, g, bl, al);
    }
}
