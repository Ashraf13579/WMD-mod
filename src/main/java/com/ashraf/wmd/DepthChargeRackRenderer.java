package com.ashraf.wmd;

import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

public class DepthChargeRackRenderer extends StagedUnitRenderer<DepthChargeRackBlockEntity> {

    private static final float[][] SLOT = { { 0.0F, 12.7F, 0.0F }, { 0.0F, 16.3F, 13.0F }, { 0.0F, 19.9F, 26.0F } };
    private static final java.util.Set<String> BONES = java.util.Set.of("slot0", "slot1", "slot2");

    public DepthChargeRackRenderer() { super("depth_charge_rack", 2, 3); }

    @Override protected java.util.Set<String> attachBones() { return BONES; }

    @Override
    protected void drawAttached(DepthChargeRackBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("depth_charge");
        if (!mesh.has("part")) return;
        int n = be.shownCount();
        for (int i = 0; i < 3 && i < n; i++) {
            Matrix4f m = poses.get("slot" + i);
            if (m == null) continue;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            ps.last().normal().set(normals.get("slot" + i));
            ps.translate(SLOT[i][0] / 16.0F, (SLOT[i][1] - 5.0F) / 16.0F, SLOT[i][2] / 16.0F);
            mesh.draw("part", ps.last(), buffers, light, overlay);
        }
    }
}
