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

public class JammerPodRenderer implements BlockEntityRenderer<JammerPodBlockEntity> {

    private final Look pod = new Look("jammer_pod"), flush = new Look("jammer_flush");

    private Look of(JammerPodBlockEntity be) { return be.flush() ? flush : pod; }

    @Override
    public void render(JammerPodBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(JammerPodBlockEntity be) { return of(be).getRenderBoundingBox(be); }
    @Override public boolean shouldRenderOffScreen(JammerPodBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Look extends StagedUnitRenderer<JammerPodBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing");
        private final String name;
        private BlockState skin;

        Look(String name) {
            super(name, 1, 2);
            this.name = name;
        }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(JammerPodBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(JammerPodBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null || !poses.containsKey("housing")) return;
            CopycatSkin.draw(MeshModel.named(name), "housing", poses.get("housing"), normals.get("housing"), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
