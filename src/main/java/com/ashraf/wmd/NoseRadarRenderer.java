package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class NoseRadarRenderer implements BlockEntityRenderer<NoseRadarBlockEntity> {

    static final float[][] HOUSING = {
            { -8.0F, 0.0F, -6.6F, 8.0F, 16.0F, 8.0F },
            { -8.0F, 0.0F, -8.0F, 8.0F, 1.5F, -6.6F }, { -8.0F, 14.5F, -8.0F, 8.0F, 16.0F, -6.6F },
            { -8.0F, 1.5F, -8.0F, -6.5F, 14.5F, -6.6F }, { 6.5F, 1.5F, -8.0F, 8.0F, 14.5F, -6.6F } };

    static final float[][] CAP = { { -8.0F, 0.0F, -24.0F, 8.0F, 16.0F, -8.0F } };

    private final Front ogive = new Front("nose_radar_ogive", false), flush = new Front("nose_radar_flush", true);

    private Front of(NoseRadarBlockEntity be) { return be.flush() ? flush : ogive; }

    @Override
    public void render(NoseRadarBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(NoseRadarBlockEntity be) { return of(be).getRenderBoundingBox(be); }
    @Override public boolean shouldRenderOffScreen(NoseRadarBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Front extends StagedUnitRenderer<NoseRadarBlockEntity> {
        private final Set<String> attach;
        private BlockState skin;

        Front(String name, boolean cap) {
            super(name, 1, 2);
            attach = cap ? Set.of("housing", "housing_cap") : Set.of("housing");
        }

        @Override protected Set<String> attachBones() { return attach; }
        @Override protected boolean drawsItself(String bone) { return skin != null && attach.contains(bone); }

        @Override
        public void render(NoseRadarBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(NoseRadarBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            if (poses.containsKey("housing")) AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get("housing"), normals.get("housing"), skin, be, HOUSING, light, overlay);
            if (poses.containsKey("housing_cap")) AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get("housing_cap"), normals.get("housing_cap"), skin, be, CAP, light, overlay);
        }
    }
}
