package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class AnchorWindlassRenderer extends CopycatUnitRenderer<AnchorWindlassBlockEntity> {

    private static final ResourceLocation STEEL = ResourceLocation.withDefaultNamespace("textures/block/gray_concrete.png");
    private static final Set<String> ATTACH = Set.of("housing", "anchor");
    private static final float LINK_PITCH = AnchorWindlassBlockEntity.PITCH / 16.0F, LINK_LEN = 0.3F, LINK_W = 0.06F, BAR = 0.02F;
    private float partial;

    AnchorWindlassRenderer() { super("anchor_windlass", 1, 2, null); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return "anchor".equals(bone) || super.drawsItself(bone); }

    @Override
    public void render(AnchorWindlassBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        partial = partialTick;
        super.render(be, partialTick, ps, buffers, light, overlay);
    }

    @Override
    protected void drawAttached(AnchorWindlassBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        super.drawAttached(be, poses, normals, buffers, light, overlay);
        Matrix4f root = poses.get("housing");
        Matrix3f rn = normals.get("housing");
        Matrix4f am = poses.get("anchor");
        Matrix3f an = normals.get("anchor");
        if (root == null || rn == null || be.getLevel() == null) return;
        Direction f = be.getBlockState().getValue(MachineBlock.FACING);
        float pt = partial;
        float n = be.pipes();
        float[] throat = { 0.0F, AnchorWindlassBlockEntity.HAWSE_Y / 16.0F, 0.0F }, exit = { 0.0F, -n, 0.0F };
        float shackle = AnchorWindlassBlockEntity.SHACKLE_Y / 16.0F;
        float[] shift, end;
        boolean bottom = be.onBottom() && be.anchorPoint() != null;
        if (bottom) {
            float[] s = AnchorClient.model(be.getBlockPos(), f, AnchorClient.toPlot(be.getLevel(), be.getBlockPos(), be.anchorPoint(), pt));
            float lift = AnchorWindlassBlockEntity.ANCHOR_DROP_PX / 16.0F - shackle;
            shift = new float[] { s[0], s[1] + lift, s[2] };
        } else {
            float c = be.shownChain();
            float[] dn = AnchorClient.down(be.getLevel(), be.getBlockPos(), f, pt);
            shift = new float[] { exit[0] + dn[0] * c, exit[1] + dn[1] * c, exit[2] + dn[2] * c };
        }
        end = new float[] { shift[0], shift[1] + shackle, shift[2] };

        if (am != null && an != null && MeshModel.named("anchor_windlass").has("anchor")) {
            Matrix4f inv = new Matrix4f(root).invert();
            Matrix4f mm = new Matrix4f(root).translate(shift[0], shift[1], shift[2]).mul(inv).mul(am);
            PoseStack p = new PoseStack();
            p.last().pose().set(mm);
            p.last().normal().set(an);
            MeshModel.named("anchor_windlass").draw("anchor", p.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(STEEL));
        int[] k = { 0 };
        links(vc, root, rn, throat, exit, 0.0F, null, light, k);
        float sag = 0.0F;
        float[] sagDir = null;
        if (bottom && be.state() == AnchorWindlassBlockEntity.S_HOLDING) {
            float straight = dist(exit, end);
            sag = Math.max(0.0F, Math.min(4.0F, (be.chainOut() - straight) * 0.5F + 0.06F * straight * (1.0F - be.tension())));
            sagDir = AnchorClient.down(be.getLevel(), be.getBlockPos(), f, pt);
        }
        links(vc, root, rn, exit, end, sag, sagDir, light, k);
    }

    private static float dist(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static void links(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] a, float[] b, float sag, float[] sagDir, int light, int[] k) {
        float len = dist(a, b);
        if (len < 1.0E-3F) return;
        int steps = Math.max(1, (int) Math.ceil(len / LINK_PITCH));
        if (steps > 800) steps = 800;
        Vector3f prev = point(a, b, 0.0F, sag, sagDir);
        for (int i = 1; i <= steps; i++) {
            Vector3f p = point(a, b, i / (float) steps, sag, sagDir);
            Vector3f d = new Vector3f(p).sub(prev);
            float l = d.length();
            if (l > 1.0E-4F) {
                d.div(l);
                Vector3f mid = new Vector3f(prev).add(p).mul(0.5F);
                Vector3f u = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
                Vector3f s1 = new Vector3f(d).cross(u).normalize();
                Vector3f s2 = new Vector3f(d).cross(s1).normalize();
                Vector3f side = (k[0]++ % 2 == 0) ? s1 : s2;
                Vector3f half = new Vector3f(d).mul(LINK_LEN * 0.5F);
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vector3f off = new Vector3f(side).mul(sgn * LINK_W);
                    Vector3f p0 = new Vector3f(mid).add(off).sub(half), p1 = new Vector3f(mid).add(off).add(half);
                    bar(vc, m, nm, p0, p1, light);
                }
            }
            prev = p;
        }
    }

    private static Vector3f point(float[] a, float[] b, float t, float sag, float[] dir) {
        Vector3f p = new Vector3f(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
        if (sag > 0.0F && dir != null) p.add(dir[0] * sag * 4.0F * t * (1.0F - t), dir[1] * sag * 4.0F * t * (1.0F - t), dir[2] * sag * 4.0F * t * (1.0F - t));
        return p;
    }

    private static void bar(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vector3f a, Vector3f b, int light) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-4F) return;
        d.div(len);
        Vector3f u = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s = new Vector3f(d).cross(u).normalize().mul(BAR);
        Vector3f t = new Vector3f(d).cross(s).normalize().mul(BAR);
        Vector3f[] ring = { new Vector3f(s).add(t), new Vector3f(s).sub(t), new Vector3f(s).negate().sub(t), new Vector3f(s).negate().add(t) };
        for (int i = 0; i < 4; i++) {
            Vector3f r0 = ring[i], r1 = ring[(i + 1) % 4];
            Vector3f n = nm.transform(new Vector3f(r0).add(r1).normalize());
            vert(vc, m, n, new Vector3f(a).add(r0), 0.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(a).add(r1), 1.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(b).add(r1), 1.0F, 1.0F, light);
            vert(vc, m, n, new Vector3f(b).add(r0), 0.0F, 1.0F, light);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vector3f n, Vector3f p, float u, float v, int light) {
        vc.addVertex(m, p.x, p.y, p.z).setColor(92, 96, 102, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }

    @Override
    public AABB getRenderBoundingBox(AnchorWindlassBlockEntity be) {
        return super.getRenderBoundingBox(be).expandTowards(0, -(Balance.ANCHOR_CHAIN_MAX + Balance.ANCHOR_PIPES_MAX + 4), 0).inflate(16, 0, 16);
    }

    @Override public boolean shouldRenderOffScreen(AnchorWindlassBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 128; }

    static Vec3 anchorWorld(AnchorWindlassBlockEntity be) { return be.anchorPoint(); }
}
