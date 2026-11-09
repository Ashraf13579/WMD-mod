package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SeabedCrawlerRenderer extends EntityRenderer<SeabedCrawlerEntity> {

    private static final ResourceLocation HOSE = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/entity/riser_hose.png");
    private static final float PX = 1.0F / 16.0F, HEAD_DEG = 8.0F;
    static final double RISER_FOOT_UP = 4.0, JUMPER_R = 0.14, RISER_R = 0.2;
    private static final double[] HOSE_TIP = { 0.0, 43.0, 31.6 };

    public SeabedCrawlerRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SeabedCrawlerEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
        MeshModel mesh = MeshModel.named("seabed_crawler");
        int ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (mesh.has("body")) {
            mesh.draw("body", ps.last(), buffers, light, ov);
            if (mesh.has("hose")) mesh.draw("hose", ps.last(), buffers, light, ov);
            if (mesh.has("glow")) mesh.draw("glow", ps.last(), buffers, light, ov);
            if (mesh.has("head")) {
                float h = Mth.lerp(pt, e.headShownO, e.headShown);
                ps.pushPose();
                ps.translate(0.0F, 12.0F * PX, -23.0F * PX);
                ps.mulPose(Axis.XP.rotationDegrees(HEAD_DEG * h));
                ps.translate(0.0F, -12.0F * PX, 23.0F * PX);
                mesh.draw("head", ps.last(), buffers, light, ov);
                ps.popPose();
            }
        }
        ps.popPose();

        double th = Math.toRadians(180.0 - yaw);
        double tx = HOSE_TIP[0] * PX * Math.cos(th) + HOSE_TIP[2] * PX * Math.sin(th), tz = -HOSE_TIP[0] * PX * Math.sin(th) + HOSE_TIP[2] * PX * Math.cos(th);
        Vec3 a = new Vec3(ex + tx, ey + HOSE_TIP[1] * PX, ez + tz), back = new Vec3(tx, 0.0, tz).normalize();
        Vec3 foot = e.foot(), riser = e.riser();
        if (riser.lengthSqr() > 1.0E-6) {
            Vec3 r0 = foot.add(0.0, RISER_FOOT_UP, 0.0);
            List<Vec3> jumper = new ArrayList<>();
            Vec3 c1 = a.add(back.scale(2.5)).add(0.0, 2.5, 0.0), c2 = r0.add(0.0, -2.5, 0.0);
            for (int i = 0; i <= 16; i++) jumper.add(bezier(a, c1, c2, r0, i / 16.0));
            List<Vec3> up = new ArrayList<>();
            int n = Math.max(1, (int) Math.ceil(Math.abs(riser.y - r0.y) / 4.0));
            for (int i = 0; i <= n; i++) up.add(r0.lerp(riser, i / (double) n));
            VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(HOSE));
            tube(vc, ps.last(), e.level(), jumper, JUMPER_R, ex, ey, ez);
            tube(vc, ps.last(), e.level(), up, RISER_R, ex, ey, ez);
        }
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static Vec3 bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        return p0.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(p3.scale(t * t * t));
    }

    static void tube(VertexConsumer vc, PoseStack.Pose pose, Level level, List<Vec3> pts, double r, double ox, double oy, double oz) {
        if (pts.size() < 2) return;
        int n = pts.size();
        Vec3[][] ring = new Vec3[n][4];
        Vec3[][] nrm = new Vec3[n][4];
        int[] lit = new int[n];
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = pts.get(Math.min(n - 1, i + 1)).subtract(pts.get(Math.max(0, i - 1)));
            t = t.lengthSqr() < 1.0E-9 ? new Vec3(0, 1, 0) : t.normalize();
            Vec3 side = Math.abs(t.y) > 0.95 ? new Vec3(1, 0, 0).cross(t).normalize() : t.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 upv = side.cross(t).normalize();
            Vec3[] dirs = { side, upv, side.scale(-1), upv.scale(-1) };
            for (int k = 0; k < 4; k++) { nrm[i][k] = dirs[k]; ring[i][k] = pts.get(i).add(dirs[k].add(dirs[(k + 1) % 4]).scale(r * 0.7071)); }
            lit[i] = LevelRenderer.getLightColor(level, BlockPos.containing(pts.get(i)));
            v[i] = i == 0 ? 0.0 : v[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        }
        for (int i = 0; i + 1 < n; i++)
            for (int k = 0; k < 4; k++) {
                int k2 = (k + 1) % 4;
                Vec3 nm = nrm[i][k2];
                float u0 = k * 0.25F, u1 = u0 + 0.25F;
                vert(vc, pose, ring[i][k], ox, oy, oz, u0, (float) v[i], lit[i], nm);
                vert(vc, pose, ring[i + 1][k], ox, oy, oz, u0, (float) v[i + 1], lit[i + 1], nm);
                vert(vc, pose, ring[i + 1][k2], ox, oy, oz, u1, (float) v[i + 1], lit[i + 1], nm);
                vert(vc, pose, ring[i][k2], ox, oy, oz, u1, (float) v[i], lit[i], nm);
            }
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, double ox, double oy, double oz, float u, float v, int light, Vec3 n) {
        vc.addVertex(pose, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz)).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    @Override
    public ResourceLocation getTextureLocation(SeabedCrawlerEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
