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

public class LandingGearRenderer implements BlockEntityRenderer<LandingGearBlockEntity> {

    private final Look small = new Look("landing_gear", 1, "door_l", "door_r"), medium = new Look("landing_gear_m", 1, "door_l", "door_r"),
            large = new Look("landing_gear_large", 2, "door_n", "door_s"),
            bogie = new Look("landing_gear_bogie", 2, "door_l", "door_r");

    private Look of(LandingGearBlockEntity be) {
        return switch (be.size()) {
            case LandingGearBlockEntity.LARGE -> large;
            case LandingGearBlockEntity.BOGIE -> bogie;
            case LandingGearBlockEntity.MEDIUM -> medium;
            default -> small;
        };
    }

    @Override
    public void render(LandingGearBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(LandingGearBlockEntity be) { return of(be).getRenderBoundingBox(be).inflate(1.0); }
    @Override public boolean shouldRenderOffScreen(LandingGearBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Look extends StagedUnitRenderer<LandingGearBlockEntity> {
        private final Set<String> attach;
        private final String name;
        private BlockState skin;

        Look(String name, int reach, String doorA, String doorB) {
            super(name, 1, reach);
            this.name = name;
            this.attach = Set.of("housing", doorA, doorB);
        }

        @Override protected Set<String> attachBones() { return attach; }
        @Override protected boolean drawsItself(String bone) { return skin != null && attach.contains(bone); }

        @Override
        public void render(LandingGearBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(LandingGearBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : attach)
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named(name), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
