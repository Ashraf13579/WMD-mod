package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class ScrewPropellerRenderer extends StagedUnitRenderer<ScrewPropellerBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing");
    private BlockState skin;

    public ScrewPropellerRenderer() { super("screw_propeller", 2, 4); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && "housing".equals(bone); }

    @Override
    public void render(ScrewPropellerBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copyShown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(ScrewPropellerBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin == null || !poses.containsKey("housing")) return;
        CopycatSkin.draw(MeshModel.named("screw_propeller"), "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
    }
}
