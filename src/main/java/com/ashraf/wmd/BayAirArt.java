package com.ashraf.wmd;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class BayAirArt {

    private BayAirArt() {}

    private static final String MESH = "bay_air";

    private static final float PAD_X = 40F, CLAMP_X = 60F, CAS_X = 80F;

    private static final float HINGE = 9F;

    private static String mesh(int k) {
        String m = AamItem.mesh(k);
        return m != null && MeshModel.named(m).has("part") ? m : "aam_heat";
    }

    private static void meshFrame(PoseStack ps, float xPx, float yPx) {
        ps.translate(xPx / 16.0F, yPx / 16.0F, 0.0F);
        Vector3f up = new Vector3f(0.0F, 0.0F, 1.0F);
        ps.last().normal().transform(up);
        float u = up.y() >= 0.0F ? 1.0F : -1.0F;

        Matrix4f m = new Matrix4f(u, 0, 0, 0, 0, 0, u, 0, 0, -1, 0, 0, 0, 0, 0, 1);
        ps.last().pose().mul(m);
        ps.last().normal().mul(new Matrix3f(m));
    }

    static void jig(PoseStack ps, MultiBufferSource buffers, int light, int k, int sections, double clamp, double roll, float coat) {
        MeshModel jm = MeshModel.named(MESH);
        String rm = mesh(k);
        float r = AamMeshes.radiusPx(rm), len = AamMeshes.lengthPx(rm);
        int ov = OverlayTexture.NO_OVERLAY;
        if (jm.has("jig")) {
            ps.pushPose();
            meshFrame(ps, 0.0F, (float) BayAnim.AIR_JIG);
            jm.draw("jig", ps.last(), buffers, light, ov);
            for (int side = -1; side <= 1; side += 2) {

                for (int dz = -1; dz <= 1; dz += 2) {
                    ps.pushPose();
                    ps.translate((side * (float) BayAnim.AIR_DX - PAD_X) / 16.0F, -r / 16.0F, dz * 4.5F / 16.0F);
                    jm.draw("jpad", ps.last(), buffers, light, ov);
                    ps.popPose();
                }

                float dx = (float) BayAnim.AIR_DX - HINGE, dy = HINGE - (r + 0.9F);
                float closed = (float) Math.atan2(dy, dx), open = (float) Math.toRadians(-70.0);
                float a = open + (closed - open) * (float) clamp;
                ps.pushPose();
                ps.translate(side * HINGE / 16.0F, HINGE / 16.0F, 0.0F);
                if (side < 0) ps.scale(-1.0F, 1.0F, 1.0F);
                ps.mulPose(new org.joml.Quaternionf().rotationZ(-a));
                ps.translate(-CLAMP_X / 16.0F, 0.0F, 0.0F);
                jm.draw("jclamp", ps.last(), buffers, light, ov);
                ps.popPose();
            }
            ps.popPose();
        }
        for (int side = -1; side <= 1; side += 2)
            round(ps, buffers, light, k, rm, side * (float) BayAnim.AIR_DX, (float) BayAnim.AIR_RT, sections, roll, coat, r, len);
    }

    private static void round(PoseStack ps, MultiBufferSource buffers, int light, int k, String rm, float x, float tailY, int sections, double roll, float coat,
                              float r, float len) {
        MeshModel mm = MeshModel.named(rm);
        if (!mm.has("part")) return;
        ps.pushPose();
        meshFrame(ps, x, tailY);
        ps.mulPose(new org.joml.Quaternionf().rotationZ((float) roll));
        float hl = len / 2.0F;
        ps.translate(0.0F, -r / 16.0F, -hl / 16.0F);
        int[] t = MissileParts.tint(AamItem.paint(k));
        int tr = Math.round(255 + (t[0] - 255) * coat), tg = Math.round(255 + (t[1] - 255) * coat), tb = Math.round(255 + (t[2] - 255) * coat);
        float n = (float) BayAnim.airNose(k), g = (float) BayAnim.airGuid(k), mo = (float) BayAnim.AIR_MOTOR;
        float[] cut = { -hl - 1.0F, -hl + n, -hl + g, hl - mo, hl + 2.0F };
        int[] secOf = { 3, 2, 0, 1 };
        for (int i = 0; i < 4; i++)
            if ((sections >> secOf[i] & 1) != 0)
                mm.drawTintedZ("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, tr, tg, tb, cut[i] / 16.0F, cut[i + 1] / 16.0F);
        ps.popPose();
    }

    static void cassette(PoseStack ps, MultiBufferSource buffers, int light, int k, int sec) {
        MeshModel jm = MeshModel.named(MESH);
        String rm = mesh(k);
        float r = AamMeshes.radiusPx(rm), len = AamMeshes.lengthPx(rm);
        float c = (float) BayAnim.SEG_C[Math.max(1, Math.min(3, sec))];
        if (jm.has("cas")) {
            ps.pushPose();
            meshFrame(ps, 0.0F, c);
            ps.translate(-CAS_X / 16.0F, 0.0F, 0.0F);
            jm.draw("cas", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }

        float tail = c - (float) (BayAnim.airCentre(k, sec) - BayAnim.AIR_RT);
        for (int side = -1; side <= 1; side += 2) round(ps, buffers, light, k, rm, side * (float) BayAnim.AIR_DX, tail, 1 << sec, 0.0, 0.0F, r, len);
    }
}
