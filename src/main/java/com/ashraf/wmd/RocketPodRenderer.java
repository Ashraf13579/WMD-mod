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

public class RocketPodRenderer implements BlockEntityRenderer<RocketPodBlockEntity> {

    private final Look pod = new Look("rocket_pod"), flush = new Look("rocket_flush");

    private Look of(RocketPodBlockEntity be) { return be.flush() ? flush : pod; }

    @Override
    public void render(RocketPodBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(RocketPodBlockEntity be) { return of(be).getRenderBoundingBox(be).inflate(1.0); }
    @Override public boolean shouldRenderOffScreen(RocketPodBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Look extends StagedUnitRenderer<RocketPodBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing", "housing_tray");
        private final String name;
        private BlockState skin;

        Look(String name) {
            super(name, 1, 2);
            this.name = name;
        }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(RocketPodBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(RocketPodBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : ATTACH)
                if (poses.containsKey(b) && normals.containsKey(b))
                    CopycatSkin.draw(MeshModel.named(name), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
