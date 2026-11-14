package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class SnorkelMastRenderer extends StagedUnitRenderer<SnorkelMastBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing", "housing_hatch");
    private BlockState skin;

    public SnorkelMastRenderer() { super("snorkel_mast", 6); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

    @Override
    public void render(SnorkelMastBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copyShown();
        super.render(be, partialTick, poseStack, buffers, light, overlay);
        skin = null;
    }

    @Override
    protected void drawAttached(SnorkelMastBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin == null) return;
        MeshModel mesh = MeshModel.named("snorkel_mast");
        for (String b : ATTACH) if (poses.containsKey(b)) CopycatSkin.draw(mesh, b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
    }
}
