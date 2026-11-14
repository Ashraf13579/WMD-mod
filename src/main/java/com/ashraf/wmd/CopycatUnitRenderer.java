package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class CopycatUnitRenderer<T extends ProcessUnitBlockEntity & CopycatState.Host> extends StagedUnitRenderer<T> {

    private static final Set<String> ATTACH = Set.of("housing");
    private final float[][] boxes;
    private final String name;
    private BlockState skin;

    public CopycatUnitRenderer(String name, int up, int reach, float[][] boxes) {
        super(name, up, reach);
        this.boxes = boxes;
        this.name = name;
    }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && "housing".equals(bone); }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(T be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        if (skin == null || !poses.containsKey("housing")) return;
        if (boxes == null) CopycatSkin.draw(MeshModel.named(name), "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        else AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get("housing"), normals.get("housing"), skin, be, boxes, light, overlay);
    }
}
