package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

public class P6StyledRenderer<T extends P6DriveBlockEntity> implements BlockEntityRenderer<T> {

    private final CopycatUnitRenderer<T> boxed, bare;

    public P6StyledRenderer(CopycatUnitRenderer<T> boxed, CopycatUnitRenderer<T> bare) {
        this.boxed = boxed;
        this.bare = bare;
    }

    public static <T extends P6DriveBlockEntity> P6StyledRenderer<T> of(String mesh, int up, int reach) {
        return new P6StyledRenderer<>(new CopycatUnitRenderer<>(mesh, up, reach, null), new CopycatUnitRenderer<>(mesh + "_bare", up, reach, null));
    }

    private CopycatUnitRenderer<T> of(T be) { return be.bare() ? bare : boxed; }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(T be) { return of(be).getRenderBoundingBox(be); }
    @Override public boolean shouldRenderOffScreen(T be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Prop extends CopycatUnitRenderer<TurbopropBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing", "pblade", "pblade2", "prop2");
        private final String mesh;
        private final Map<String, float[]> hub = new HashMap<>();
        private boolean contra;
        private int blades = 4;

        Prop(String mesh) {
            super(mesh, 2, 3, null);
            this.mesh = mesh;
        }

        @Override protected Set<String> attachBones() { return ATTACH; }

        @Override
        protected boolean drawsItself(String bone) {
            return bone.equals("pblade") || bone.equals("pblade2") || bone.equals("prop2") || super.drawsItself(bone);
        }

        @Override
        public void render(TurbopropBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            contra = be.contra();
            blades = be.blades();
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        }

        @Override
        protected void drawAttached(TurbopropBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            super.drawAttached(be, poses, normals, buffers, light, overlay);
            MeshModel m = MeshModel.named(mesh);
            copies(m, "pblade", "prop", poses, normals, buffers, light);
            if (!contra) return;
            draw(m, "prop2", poses.get("prop2"), normals.get("prop2"), buffers, light);
            copies(m, "pblade2", "prop2", poses, normals, buffers, light);
        }

        private void copies(MeshModel m, String bone, String disc, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light) {
            Matrix4f p = poses.get(bone);
            Matrix3f n = normals.get(bone);
            if (p == null || n == null || !m.has(bone)) return;
            float[] c = hub.computeIfAbsent(disc, d -> measure(m, d));
            if (c == null) return;
            for (int k = 0; k < blades; k++) {
                float a = (float) (Math.PI * 2.0 * k / blades);
                Matrix4f cp = new Matrix4f(p).translate(c[0], c[1], 0.0F).rotateZ(a).translate(-c[0], -c[1], 0.0F);
                draw(m, bone, cp, new Matrix3f(n).rotateZ(a), buffers, light);
            }
        }

        private static void draw(MeshModel m, String bone, Matrix4f p, Matrix3f n, MultiBufferSource buffers, int light) {
            if (p == null || n == null || !m.has(bone)) return;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(p);
            ps.last().normal().set(n);
            m.draw(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        }

        private static float[] measure(MeshModel m, String disc) {
            double[] s = new double[3];
            m.forEachQuad(disc, (v, i) -> { for (int j = 0; j < 4; j++) { s[0] += v[i + j * 8]; s[1] += v[i + j * 8 + 1]; s[2]++; } });
            return s[2] == 0 ? null : new float[] { (float) (s[0] / s[2]), (float) (s[1] / s[2]) };
        }
    }
}
