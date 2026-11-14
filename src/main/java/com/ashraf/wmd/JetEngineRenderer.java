package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

public class JetEngineRenderer implements BlockEntityRenderer<JetEngineBlockEntity> {

    private final Map<String, CopycatUnitRenderer<JetEngineBlockEntity>> byMesh = new HashMap<>();

    private CopycatUnitRenderer<JetEngineBlockEntity> of(JetEngineBlockEntity be) {

        return byMesh.computeIfAbsent(be.meshName(), m -> new CopycatUnitRenderer<>(m, 1, 9, null));
    }

    @Override
    public void render(JetEngineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
        float ab = be.abShown(partialTick), n1 = be.n1Shown();
        if ((ab > 0.02F || n1 > 0.5F) && be.getLevel() != null) plume(be, ab, n1, partialTick, poseStack, buffers);
    }

    private static void plume(JetEngineBlockEntity be, float ab, float n1, float pt, PoseStack ps, MultiBufferSource buffers) {

        pack = ShaderFx.on;
        VertexConsumer vc = buffers.getBuffer(pack ? RenderType.eyes(ShaderFx.FADE_ADD) : RenderType.lightning());
        poseNow = ps.last();
        Matrix4f m = ps.last().pose();
        double bx = be.getBlockPos().getX(), by = be.getBlockPos().getY(), bz = be.getBlockPos().getZ();
        double t = be.getLevel().getGameTime() + pt;
        float dry = Math.max(0.0F, (n1 - 0.5F) / 0.5F) * (1.0F - ab);
        double len = Balance.JET_PLUME_LEN_DRY * dry + Balance.JET_PLUME_LEN_AB * ab, r0 = Balance.JET_PLUME_R;
        if (len < 0.05) return;
        int ei = 0;
        for (double[] e : be.exhausts()) {
            double[][] n = be.nozzlePlot(e);
            double cx = n[0][0] - bx, cy = n[0][1] - by, cz = n[0][2] - bz;
            double[] d = n[1], sv = new double[3], uv = new double[3];
            frame(d, sv, uv);
            double seed = ei++ * 2.3 + (be.getBlockPos().asLong() & 15);

            float sheathA = 0.10F * dry + 0.30F * ab;
            for (int shell = 0; shell < 3; shell++) {
                double rs = r0 * (1.0 - shell * 0.28);
                float a = sheathA * (shell == 0 ? 0.55F : shell == 1 ? 0.8F : 1.0F);
                tube(vc, m, cx, cy, cz, d, sv, uv, len, rs, Balance.JET_PLUME_FLARE, true, t, seed + shell, 120 + (int) (40 * ab), 125, 255, a);
            }

            double clen = len * (Balance.JET_CORE_SHARE + 0.1 * ab);
            float coreA = 0.12F * dry + 0.75F * ab;
            tube(vc, m, cx, cy, cz, d, sv, uv, clen, r0 * 0.78, -0.85, false, t * 1.4, seed + 7, 255, 150, 110, coreA * 0.7F);
            tube(vc, m, cx, cy, cz, d, sv, uv, clen * 0.6, r0 * 0.45, -0.9, false, t * 1.7, seed + 9, 255, 225, 200, coreA);

            if (ab > 0.05F)
                for (int i = 0; i < Balance.JET_DIAMONDS; i++) {
                    double along = len * (0.16 + i * 0.12);
                    float fall = 1.0F - i / (float) (Balance.JET_DIAMONDS + 1);
                    float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.9 + i * 1.7 + seed);
                    double rr = r0 * 0.55 * fall;
                    double ox = cx + d[0] * along, oy = cy + d[1] * along, oz = cz + d[2] * along;
                    tube(vc, m, ox, oy, oz, d, sv, uv, 0.22 * fall, rr, -1.0, false, t, seed + 20 + i, 255, 220, 230, 0.45F * ab * fall * pulse);
                }
        }
    }

    private static void frame(double[] d, double[] s, double[] u) {
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(d[1]) > 0.9) { ux = 1; uy = 0; }
        double sx = d[1] * uz - d[2] * uy, sy = d[2] * ux - d[0] * uz, sz = d[0] * uy - d[1] * ux;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        s[0] = sx / sl; s[1] = sy / sl; s[2] = sz / sl;
        u[0] = s[1] * d[2] - s[2] * d[1]; u[1] = s[2] * d[0] - s[0] * d[2]; u[2] = s[0] * d[1] - s[1] * d[0];
    }

    private static void tube(VertexConsumer vc, Matrix4f m, double ox, double oy, double oz, double[] d, double[] s, double[] u, double len, double r, double flare,
                             boolean fadeIn, double t, double seed, int cr, int cg, int cb, float a) {
        if (a < 0.004F || len < 0.02) return;
        int segs = PLUME_SEG, ring = PLUME_RING;
        double[][] prev = null;
        float pa = 0.0F;
        for (int k = 0; k <= segs; k++) {
            double p = k / (double) segs;
            double jit = 1.0 + 0.07 * Math.sin(t * 2.1 + k * 1.9 + seed) + 0.05 * Math.sin(t * 3.7 + k * 0.7 + seed * 1.3);
            double rr = Math.max(0.0, r * (1.0 + flare * p) * jit);
            double ax = ox + d[0] * len * p, ay = oy + d[1] * len * p, az = oz + d[2] * len * p;
            float ka = (float) ((1.0 - p) * (fadeIn && k == 0 ? 0.4 : 1.0) * (0.88 + 0.12 * Math.sin(t * 5.3 + k + seed)));
            double[][] cur = new double[ring][3];
            for (int q = 0; q < ring; q++) {
                double ang = q * Math.PI * 2.0 / ring, c = Math.cos(ang) * rr, sn = Math.sin(ang) * rr;
                cur[q][0] = ax + s[0] * c + u[0] * sn; cur[q][1] = ay + s[1] * c + u[1] * sn; cur[q][2] = az + s[2] * c + u[2] * sn;
            }
            if (prev != null) {
                int a0 = Math.round(255 * a * pa), a1 = Math.round(255 * a * ka);
                for (int q = 0; q < ring; q++) {
                    double[] p0 = prev[q], p1 = prev[(q + 1) % ring], p2 = cur[(q + 1) % ring], p3 = cur[q];
                    quad(vc, m, p0, p1, p2, p3, cr, cg, cb, a0, a1);
                }
            }
            prev = cur;
            pa = ka;
        }
    }

    private static boolean pack;
    private static PoseStack.Pose poseNow;

    private static void quad(VertexConsumer vc, Matrix4f m, double[] p0, double[] p1, double[] p2, double[] p3, int r, int g, int b, int a0, int a1) {
        if (pack) { packQuad(vc, p0, p1, p2, p3, r, g, b, a0, a1); return; }
        vc.addVertex(m, (float) p0[0], (float) p0[1], (float) p0[2]).setColor(r, g, b, a0);
        vc.addVertex(m, (float) p1[0], (float) p1[1], (float) p1[2]).setColor(r, g, b, a0);
        vc.addVertex(m, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, a1);
        vc.addVertex(m, (float) p3[0], (float) p3[1], (float) p3[2]).setColor(r, g, b, a1);
        vc.addVertex(m, (float) p3[0], (float) p3[1], (float) p3[2]).setColor(r, g, b, a1);
        vc.addVertex(m, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, a1);
        vc.addVertex(m, (float) p1[0], (float) p1[1], (float) p1[2]).setColor(r, g, b, a0);
        vc.addVertex(m, (float) p0[0], (float) p0[1], (float) p0[2]).setColor(r, g, b, a0);
    }

    private static void packQuad(VertexConsumer vc, double[] p0, double[] p1, double[] p2, double[] p3, int r, int g, int b, int a0, int a1) {
        float u0 = a0 / 255.0F, u1 = a1 / 255.0F;
        packV(vc, p0, r, g, b, u0); packV(vc, p1, r, g, b, u0); packV(vc, p2, r, g, b, u1); packV(vc, p3, r, g, b, u1);
        packV(vc, p3, r, g, b, u1); packV(vc, p2, r, g, b, u1); packV(vc, p1, r, g, b, u0); packV(vc, p0, r, g, b, u0);
    }

    private static void packV(VertexConsumer vc, double[] p, int r, int g, int b, float u) {
        vc.addVertex(poseNow, (float) p[0], (float) p[1], (float) p[2]).setColor(r, g, b, 255).setUv(Math.max(0.0F, Math.min(1.0F, u)), 0.5F)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(poseNow, 0.0F, 1.0F, 0.0F);
    }

    static final int PLUME_SEG = 12, PLUME_RING = 12;

    @Override public AABB getRenderBoundingBox(JetEngineBlockEntity be) { return of(be).getRenderBoundingBox(be); }
    @Override public boolean shouldRenderOffScreen(JetEngineBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }
}
