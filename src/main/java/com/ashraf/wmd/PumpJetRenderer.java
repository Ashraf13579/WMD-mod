package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class PumpJetRenderer extends StagedUnitRenderer<PumpJetBlockEntity> {

    private static final Set<String> ATTACH = Set.of("housing");
    private BlockState skin;

    public PumpJetRenderer() { super("pump_jet", 3, 3); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

    @Override
    public void render(PumpJetBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copyShown();
        super.render(be, partialTick, poseStack, buffers, light, overlay);
        skin = null;
    }

    @Override
    protected void drawAttached(PumpJetBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin == null) return;
        MeshModel mesh = MeshModel.named("pump_jet");
        for (String b : ATTACH) CopycatSkin.draw(mesh, b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
    }
}
