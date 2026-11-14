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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ServiceArmRenderer extends StagedUnitRenderer<ServiceArmBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing", "hatch_l", "hatch_r", "tele2", "head");
    private static final ResourceLocation HOSE = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
    private static final float W = 1.4F;
    private static final float[] KN = { -84F, -78F, -35F, -29F, 14F, 20F };

    static final float[][] HOUSING = {
            { -24F, 0F, -88F, -24F + W, 48F - W, 24F }, { 24F - W, 0F, -88F, 24F, 48F - W, 24F }, { -24F + W, 0F, -88F, 24F - W, 48F - W, -88F + W },
            { -24F + W, 14.2F, 24F - W, 24F - W, 48F - W, 24F }, { -24F + W, 0F, 24F - W, 24F - W, 1.8F, 24F },
            { -24F + W, 1.8F, 24F - W, -22.2F, 14.2F, 24F }, { -9.8F, 1.8F, 24F - W, -6.0F, 14.2F, 24F }, { 6.0F, 1.8F, 24F - W, 9.8F, 14.2F, 24F }, { 22.2F, 1.8F, 24F - W, 24F - W, 14.2F, 24F },
            { -24F, 48F - W, -84F, -22.6F, 48F, -78F }, { -24F, 48F - W, -35F, -22.6F, 48F, -29F }, { -24F, 48F - W, 14F, -22.6F, 48F, 20F },
            { 22.6F, 48F - W, -84F, 24F, 48F, -78F }, { 22.6F, 48F - W, -35F, 24F, 48F, -29F }, { 22.6F, 48F - W, 14F, 24F, 48F, 20F } };

    static final float[][] LEAF_L = leaf(-24F, -22.6F, -0.1F), LEAF_R = leaf(22.6F, 24F, 0.1F);

    private static float[][] leaf(float s0, float s1, float inner) {
        float[][] b = new float[5][];
        b[0] = inner < 0 ? new float[] { s1, 48F - W, -88F, inner, 48F, 24F } : new float[] { inner, 48F - W, -88F, s0, 48F, 24F };
        float z = -88F;
        int k = 1;
        for (int i = 0; i <= KN.length; i += 2) {
            float z1 = i < KN.length ? KN[i] : 24F;
            b[k++] = new float[] { s0, 48F - W, z, s1, 48F, z1 };
            z = i < KN.length ? KN[i + 1] : 24F;
        }
        return b;
    }

    private BlockState skin;

    public ServiceArmRenderer() { super("service_arm", 10, 17); }

    @Override protected Set<String> attachBones() { return ATTACH; }

    @Override
    protected boolean drawsItself(String bone) { return skin != null && ("housing".equals(bone) || "hatch_l".equals(bone) || "hatch_r".equals(bone)); }

    @Override
    public void render(ServiceArmBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(ServiceArmBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin != null) {
            draw(be, poses, normals, buffers, "housing", HOUSING, light, overlay);
            draw(be, poses, normals, buffers, "hatch_l", LEAF_L, light, overlay);
            draw(be, poses, normals, buffers, "hatch_r", LEAF_R, light, overlay);
        }
        Matrix4f ta = poses.get("tele2"), tb = poses.get("head");
        if (ta == null || tb == null) return;
        Vector3f a = ta.transformPosition(new Vector3f(ServiceArmBlockEntity.HOSE_A[0] / 16F, ServiceArmBlockEntity.HOSE_A[1] / 16F, ServiceArmBlockEntity.HOSE_A[2] / 16F));
        Vector3f b = tb.transformPosition(new Vector3f(ServiceArmBlockEntity.HOSE_B[0] / 16F, ServiceArmBlockEntity.HOSE_B[1] / 16F, ServiceArmBlockEntity.HOSE_B[2] / 16F));
        hose(buffers.getBuffer(RenderType.entitySolid(HOSE)), a, b, light);
    }

    private void draw(ServiceArmBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, String bone, float[][] boxes, int light, int overlay) {
        if (poses.containsKey(bone)) AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get(bone), normals.get(bone), skin, be, boxes, light, overlay);
    }

    private static void hose(VertexConsumer vc, Vector3f a, Vector3f b, int light) {
        float len = a.distance(b);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5F).add(0.0F, -0.15F - 0.25F * len, 0.0F);
        Vector3f prev = new Vector3f(a);
        for (int i = 1; i <= 8; i++) {
            float t = i / 8.0F, u = 1.0F - t;
            Vector3f p = new Vector3f(a).mul(u * u).add(new Vector3f(mid).mul(2.0F * u * t)).add(new Vector3f(b).mul(t * t));
            tube(vc, prev, p, 0.05F, light);
            prev = p;
        }
    }

    private static void tube(VertexConsumer vc, Vector3f a, Vector3f b, float r, int light) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-4F) return;
        d.div(len);
        Vector3f up = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s = new Vector3f(d).cross(up).normalize().mul(r), t = new Vector3f(d).cross(s).normalize().mul(r);
        Vector3f[] ring = { new Vector3f(s).add(t), new Vector3f(s).sub(t), new Vector3f(s).negate().sub(t), new Vector3f(s).negate().add(t) };
        for (int i = 0; i < 4; i++) {
            Vector3f r0 = ring[i], r1 = ring[(i + 1) % 4], n = new Vector3f(r0).add(r1).normalize();
            vert(vc, new Vector3f(a).add(r0), n, 0.0F, 0.0F, light);
            vert(vc, new Vector3f(a).add(r1), n, 1.0F, 0.0F, light);
            vert(vc, new Vector3f(b).add(r1), n, 1.0F, len, light);
            vert(vc, new Vector3f(b).add(r0), n, 0.0F, len, light);
        }
    }

    private static void vert(VertexConsumer vc, Vector3f p, Vector3f n, float u, float v, int light) {
        vc.addVertex(p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }

    @Override
    public AABB getRenderBoundingBox(ServiceArmBlockEntity be) { return new AABB(be.getBlockPos()).inflate(17.0, 0.0, 17.0).expandTowards(0.0, 11.0, 0.0).expandTowards(0.0, -4.0, 0.0); }

    @Override public boolean shouldRenderOffScreen(ServiceArmBlockEntity be) { return true; }
}
