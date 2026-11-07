package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
final class ShaderFx {

    private ShaderFx() {}

    static final ResourceLocation PUFF = tex("puff"), GLOW = tex("glow"), FADE = tex("fade"), FADE_ADD = tex("fade_add");
    static final double REACH = 0.8;
    static final float SMOKE_ALPHA = 1.0F;
    private static final int ROUND = 0, PLAIN = 1, N = 37;
    private static final int LIGHT = LightTexture.pack(0, 15);

    static boolean on;
    static double LIM;
    private static final ByteBufferBuilder SMOKE_BUF = new ByteBufferBuilder(1 << 18);
    private static final Map<BufferBuilder, Pending> PENDING = new IdentityHashMap<>();

    private static final class Pending {
        final List<float[]> quads = new ArrayList<>();
        float[] open;
        int n;
    }

    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/fx/" + name + ".png"); }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        PENDING.clear();
        Minecraft mc = Minecraft.getInstance();
        on = mc.level != null && enabled() && VeilBridge.shaderPackOn();
        LIM = mc.options.getEffectiveRenderDistance() * 16.0 * REACH;
    }

    static boolean enabled() { return !ClientConfig.SPEC.isLoaded() || ClientConfig.SHADER_SAFE_FX.get(); }

    private static Pending of(BufferBuilder bb) { return PENDING.computeIfAbsent(bb, k -> new Pending()); }

    private static void put(float[] q, int o, double x, double y, double z, float u, float v, float r, float g, float b, float a) {
        q[o] = (float) x; q[o + 1] = (float) y; q[o + 2] = (float) z; q[o + 3] = u; q[o + 4] = v;
        q[o + 5] = r; q[o + 6] = g; q[o + 7] = b; q[o + 8] = a;
    }

    static void vert(BufferBuilder bb, Vec3 cam, double x, double y, double z, float r, float g, float b, float a) {
        Pending p = of(bb);
        if (p.open == null) { p.open = new float[N]; p.open[0] = PLAIN; p.n = 0; }
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, l = Math.sqrt(dx * dx + dy * dy + dz * dz), k = l > LIM ? LIM / l : 1.0;
        put(p.open, 1 + p.n * 9, dx * k, dy * k, dz * k, 0.5F, 0.5F, r, g, b, a);
        if (++p.n == 4) { p.quads.add(fadeInU(p.open)); p.open = null; }
    }

    static float[] fadeInU(float[] q) {
        float r = 0, g = 0, b = 0;
        for (int j = 0; j < 4; j++) { int o = 1 + j * 9; r += q[o + 5]; g += q[o + 6]; b += q[o + 7]; }
        for (int j = 0; j < 4; j++) {
            int o = 1 + j * 9;
            q[o + 3] = Math.max(0.0F, Math.min(1.0F, q[o + 8]));
            q[o + 4] = 0.5F;
            q[o + 5] = r * 0.25F; q[o + 6] = g * 0.25F; q[o + 7] = b * 0.25F; q[o + 8] = 1.0F;
        }
        return q;
    }

    static void disc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double rad, float r, float g, float b, float a) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, l = Math.sqrt(dx * dx + dy * dy + dz * dz), k = l > LIM ? LIM / l : 1.0;
        double cx = dx * k, cy = dy * k, cz = dz * k, s = rad * k;
        double t = x * 0.37 + y * 0.11 + z * 0.23 + rad * 0.5;
        double c = Math.cos(t) * s, sn = Math.sin(t) * s;
        double lx = left.x() * c + up.x() * sn, ly = left.y() * c + up.y() * sn, lz = left.z() * c + up.z() * sn;
        double ux = up.x() * c - left.x() * sn, uy = up.y() * c - left.y() * sn, uz = up.z() * c - left.z() * sn;
        float[] q = new float[N];
        q[0] = ROUND;
        put(q, 1, cx - lx - ux, cy - ly - uy, cz - lz - uz, 0.0F, 1.0F, r, g, b, a);
        put(q, 10, cx + lx - ux, cy + ly - uy, cz + lz - uz, 1.0F, 1.0F, r, g, b, a);
        put(q, 19, cx + lx + ux, cy + ly + uy, cz + lz + uz, 1.0F, 0.0F, r, g, b, a);
        put(q, 28, cx - lx + ux, cy - ly + uy, cz - lz + uz, 0.0F, 0.0F, r, g, b, a);
        of(bb).quads.add(q);
    }

    static void flush(RenderLevelStageEvent event, BufferBuilder bb, boolean additive) {
        Pending p = PENDING.remove(bb);
        if (p == null || p.quads.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack.Pose pose = new PoseStack().last();
        if (!additive) smoke(p);
        else for (int shape = ROUND; shape <= PLAIN; shape++) {
            RenderType rt = RenderType.eyes(shape == ROUND ? GLOW : FADE_ADD);
            float am = 1.0F;
            VertexConsumer vc = null;
            for (float[] q : p.quads) {
                if ((int) q[0] != shape) continue;
                if (vc == null) vc = buffers.getBuffer(rt);
                for (int j = 0; j < 4; j++) vertex(vc, pose, q, 1 + j * 9, additive, am);
                if (additive) for (int j = 3; j >= 0; j--) vertex(vc, pose, q, 1 + j * 9, true, am);
            }
            if (vc != null) buffers.endBatch(rt);
        }
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void smoke(Pending p) {
        Minecraft mc = Minecraft.getInstance();
        for (int shape = ROUND; shape <= PLAIN; shape++) {
            BufferBuilder b = new BufferBuilder(SMOKE_BUF, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            float am = shape == ROUND ? SMOKE_ALPHA : 1.0F;
            for (float[] q : p.quads) {
                if ((int) q[0] != shape) continue;
                for (int j = 0; j < 4; j++) {
                    int o = 1 + j * 9;
                    b.addVertex(q[o], q[o + 1], q[o + 2]).setUv(q[o + 3], q[o + 4]).setColor(q[o + 5], q[o + 6], q[o + 7], Math.min(1.0F, q[o + 8] * am)).setLight(LIGHT);
                }
            }
            MeshData md = b.build();
            if (md == null) continue;
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, shape == ROUND ? PUFF : FADE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            BufferUploader.drawWithShader(md);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            mc.gameRenderer.lightTexture().turnOffLightLayer();
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float[] q, int o, boolean additive, float am) {
        float a = Math.min(1.0F, q[o + 8] * am), r = q[o + 5], g = q[o + 6], b = q[o + 7];
        if (additive) { r *= a; g *= a; b *= a; }
        vc.addVertex(pose, q[o], q[o + 1], q[o + 2]).setColor(r, g, b, a).setUv(q[o + 3], q[o + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    static int pending(BufferBuilder bb) { Pending p = PENDING.get(bb); return p == null ? 0 : p.quads.size(); }
}
