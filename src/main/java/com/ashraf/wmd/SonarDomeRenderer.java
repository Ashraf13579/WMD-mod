package com.ashraf.wmd;

import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class SonarDomeRenderer extends StagedUnitRenderer<SonarDomeBlockEntity> {

    static final float CX = 8.0F, CY = 16.0F, DC = 5.4F, R = 13.0F;
    static final int SWEEP_T = 8, WAVE_FROM = 6, WAVE_TO = 40, ECHO_FROM = 12, ECHO_STEP = 5, ECHO_MAX = 5;
    static final float WAVE_REACH = 56.0F;
    private static final int SEG = 32;
    private static final java.util.Set<String> BONES = java.util.Set.of("base");

    public SonarDomeRenderer() { super("sonar_dome", 2, 3); }

    @Override protected java.util.Set<String> attachBones() { return BONES; }

    private float pt;

    @Override
    public void render(SonarDomeBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        pt = partialTick;
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    static double since(SonarDomeBlockEntity be, double tt) { return tt - Math.max(be.lastPing(), be.lastSurvey()); }

    static boolean pingLit(SonarDomeBlockEntity be, double tt) {
        double s = since(be, tt);
        if (s < 0.0) return false;
        if (s < SWEEP_T) return true;
        if (be.lastSurvey() > be.lastPing()) return s >= 16.0 && s < 26.0;
        int n = Math.min(ECHO_MAX, be.lastPingShips());
        for (int i = 0; i < n; i++) { double e = ECHO_FROM + ECHO_STEP * i; if (s >= e && s < e + 2.0) return true; }
        return false;
    }

    @Override
    protected void drawAttached(SonarDomeBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        Matrix4f m = poses.get("base");
        if (m == null || be.getLevel() == null) return;
        double tt = be.getLevel().getGameTime() + pt;
        double s = since(be, tt);
        if (s < 0.0 || s >= WAVE_TO || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        if (s < SWEEP_T) {
            float k = smooth((float) (s / SWEEP_T));
            float a = (float) Math.toRadians(90.0 - 84.0 * k), w = (float) Math.toRadians(6.0);
            int alpha = (int) (150 * (1.0F - 0.4F * k));
            band(vc, m, Math.max(0.0F, a - w), a + w, R + 0.35F, alpha);
        }
        if (s >= WAVE_FROM) {
            float p = (float) ((s - WAVE_FROM) / (WAVE_TO - WAVE_FROM));
            float d = WAVE_REACH * (1.0F - (1.0F - p) * (1.0F - p));
            float z = DC - R - 0.4F - d, r = 4.0F + d * 0.9F;
            int alpha = (int) (110 * Math.pow(1.0F - p, 1.5));
            if (alpha > 2) ring(vc, m, z, r, r + 1.2F + d * 0.03F, alpha);
        }
    }

    private static void band(VertexConsumer vc, Matrix4f m, float a0, float a1, float rr, int alpha) {
        int col = (alpha << 24) | 0x60D8FF;
        float z0 = DC - rr * (float) Math.cos(a0), r0 = rr * (float) Math.sin(a0), z1 = DC - rr * (float) Math.cos(a1), r1 = rr * (float) Math.sin(a1);
        for (int i = 0; i < SEG; i++) {
            float t0 = (float) (Math.PI * 2.0 * i / SEG), t1 = (float) (Math.PI * 2.0 * (i + 1) / SEG);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            CcRenderers.quad(vc, m, px(CX + r0 * c0), px(CY + r0 * s0), px(z0), px(CX + r0 * c1), px(CY + r0 * s1), px(z0),
                    px(CX + r1 * c1), px(CY + r1 * s1), px(z1), px(CX + r1 * c0), px(CY + r1 * s0), px(z1), col);
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float z, float ri, float ro, int alpha) {
        int col = (alpha << 24) | 0x60D8FF;
        for (int i = 0; i < SEG; i++) {
            float t0 = (float) (Math.PI * 2.0 * i / SEG), t1 = (float) (Math.PI * 2.0 * (i + 1) / SEG);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            CcRenderers.quad(vc, m, px(CX + ri * c0), px(CY + ri * s0), px(z), px(CX + ri * c1), px(CY + ri * s1), px(z),
                    px(CX + ro * c1), px(CY + ro * s1), px(z), px(CX + ro * c0), px(CY + ro * s0), px(z), col);
        }
    }

    private static float px(float v) { return v / 16.0F; }

    private static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }
}
