package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CcBuildingRenderer extends StagedUnitRenderer<CcBuildingBlockEntity> {

    private static final Set<String> BONES = Set.of(CcBuildingAnim.GLOW_BONE);

    public CcBuildingRenderer() { super(CcBuildingSpec.NAME, 10, 19); }

    @Override
    public int getViewDistance() { return 192; }

    @Override protected Set<String> attachBones() { return BONES; }
    @Override protected boolean drawsItself(String bone) { return BONES.contains(bone); }

    @Override
    protected void drawAttached(CcBuildingBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        Matrix4f m = poses.get(CcBuildingAnim.GLOW_BONE);
        MeshModel mesh = MeshModel.named(CcBuildingSpec.NAME);
        if (m == null || mesh == null) return;
        int rgb = CcBuildingAnim.glowRgb(be.defcon());
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get(CcBuildingAnim.GLOW_BONE));
        mesh.drawColoured(CcBuildingAnim.GLOW_BONE, ps.last(), buffers, light, overlay, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }
}
