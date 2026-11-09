package com.ashraf.wmd;

import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

public class TorpedoRackRenderer extends StagedUnitRenderer<TorpedoRackBlockEntity> {

    private static final float[][] SLOT = { { 0.0F, 8.0F, 24.0F }, { 16.0F, 8.0F, 24.0F }, { 0.0F, 24.0F, 24.0F }, { 16.0F, 24.0F, 24.0F } };
    private static final java.util.Set<String> BONES = java.util.Set.of("slot0", "slot1", "slot2", "slot3");

    public TorpedoRackRenderer() { super("torpedo_rack", 2, 4); }

    @Override protected java.util.Set<String> attachBones() { return BONES; }

    @Override
    protected void drawAttached(TorpedoRackBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        int[] k = be.kinds();
        for (int i = 0; i < 4; i++) {
            if (k[i] < 0) continue;
            Matrix4f m = poses.get("slot" + i);
            if (m == null) continue;
            TorpedoKind kind = TorpedoKind.of(k[i]);
            MeshModel mesh = MeshModel.named(kind.mesh());
            if (!mesh.has("part")) continue;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            ps.last().normal().set(normals.get("slot" + i));
            ps.translate(SLOT[i][0] / 16.0F, (SLOT[i][1] - kind.axisPx()) / 16.0F, SLOT[i][2] / 16.0F);
            TorpedoRenderer.draw(mesh, kind, ps.last(), buffers, light);
        }
    }
}
