package com.ashraf.wmd;

import java.util.HashSet;
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
import net.minecraft.world.phys.AABB;

public class P7PartsRenderer<T extends P6DriveBlockEntity & P7Parts> extends CopycatUnitRenderer<T> {

    private static final ResourceLocation ROPE = ResourceLocation.withDefaultNamespace("textures/block/gray_concrete.png");
    private static final ResourceLocation HOSE = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
    private final String name;
    private final double drop, trail;
    private Set<String> attach;
    private T current;
    private float partial;

    public P7PartsRenderer(String name, int up, int reach, double drop, double trail) {
        super(name, up, reach, null);
        this.name = name;
        this.drop = drop;
        this.trail = trail;
    }

    @Override
    protected Set<String> attachBones() {
        if (attach == null) {
            Set<String> s = new HashSet<>(super.attachBones());
            if (current != null) s.addAll(current.movedBones());
            attach = s;
        }
        return attach;
    }

    @Override
    protected boolean drawsItself(String bone) { return (current != null && current.movedBones().contains(bone)) || super.drawsItself(bone); }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        current = be;
        partial = partialTick;
        attachBones();
        try { super.render(be, partialTick, poseStack, buffers, light, overlay); }
        finally { current = null; }
    }

    @Override
    protected void drawAttached(T be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        super.drawAttached(be, poses, normals, buffers, light, overlay);
        float pt = partial;
        MeshModel mesh = MeshModel.named(name);
        Matrix4f root0 = poses.get("housing");
        if (root0 == null) root0 = poses.get("base");
        Matrix4f inv = root0 == null ? null : new Matrix4f(root0).invert();
        for (String b : be.movedBones()) {
            Matrix4f m = poses.get(b);
            Matrix3f n = normals.get(b);
            float[] d = be.boneShift(b, pt);
            if (m == null || n == null || d == null || !mesh.has(b)) continue;

            Matrix4f mm = root0 == null ? new Matrix4f(m).translate(d[0], d[1], d[2]) : new Matrix4f(root0).translate(d[0], d[1], d[2]).mul(inv).mul(m);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(mm);
            ps.last().normal().set(n);
            mesh.draw(b, ps.last(), buffers, be.boneLit(b) ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY);
        }
        Matrix4f root = poses.get("housing");
        Matrix3f rn = normals.get("housing");
        if (root == null) { root = poses.values().stream().findFirst().orElse(null); rn = normals.values().stream().findFirst().orElse(null); }
        if (root == null || rn == null) return;

        for (float[] c : be.cables(pt)) tube(buffers.getBuffer(RenderType.entityCutoutNoCull(c[7] > 0.5F ? HOSE : ROPE)), root, rn, c, light);
    }

    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[] c, int light) {
        Vector3f a = new Vector3f(c[0], c[1], c[2]), b = new Vector3f(c[3], c[4], c[5]);
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3F) return;
        d.div(len);
        Vector3f u = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s = new Vector3f(d).cross(u).normalize().mul(c[6]);
        Vector3f t = new Vector3f(d).cross(s).normalize().mul(c[6]);
        Vector3f[] ring = { new Vector3f(s).add(t), new Vector3f(s).sub(t), new Vector3f(s).negate().sub(t), new Vector3f(s).negate().add(t) };
        for (int i = 0; i < 4; i++) {
            Vector3f r0 = ring[i], r1 = ring[(i + 1) % 4];
            Vector3f nrm = new Vector3f(r0).add(r1).normalize();
            Vector3f n = nm.transform(new Vector3f(nrm));
            vert(vc, m, n, new Vector3f(a).add(r0), 0.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(a).add(r1), 1.0F, 0.0F, light);
            vert(vc, m, n, new Vector3f(b).add(r1), 1.0F, len, light);
            vert(vc, m, n, new Vector3f(b).add(r0), 0.0F, len, light);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vector3f n, Vector3f p, float u, float v, int light) {
        vc.addVertex(m, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }

    @Override
    public AABB getRenderBoundingBox(T be) { return super.getRenderBoundingBox(be).expandTowards(0, -drop, 0).inflate(trail, 0, trail); }

    @Override public boolean shouldRenderOffScreen(T be) { return true; }
    @Override public int getViewDistance() { return 192; }
}
