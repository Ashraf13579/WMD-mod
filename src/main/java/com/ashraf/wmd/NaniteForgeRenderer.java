package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class NaniteForgeRenderer extends StagedUnitRenderer<NaniteForgeBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing", "housing_door_l", "housing_door_r", "housing_hatch_f", "housing_hatch_b");
    private BlockState skin;

    public NaniteForgeRenderer() { super("nanite_forge", 3, 2); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

    @Override
    public void render(NaniteForgeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(NaniteForgeBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin == null) return;
        for (String b : ATTACH)
            if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named("nanite_forge"), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
    }
}
