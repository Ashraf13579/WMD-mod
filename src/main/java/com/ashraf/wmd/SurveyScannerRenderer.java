package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SurveyScannerRenderer implements BlockEntityRenderer<SurveyScannerBlockEntity> {

    private static final float PX = 1.0F / 16.0F;
    private final Look look = new Look();

    @Override
    public void render(SurveyScannerBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        look.render(be, partialTick, ps, buffers, light, overlay);
        float t = be.cycleTicks(partialTick);
        float[] ph = SurveyScannerBlockEntity.phases(t);
        if (ph[2] <= 0.0F) return;
        float active = Math.max(0.0F, t - Balance.SCANNER_ARM);
        double yaw = Math.toRadians(Balance.SCANNER_YAW_DEG_PER_TICK * active) * SurveyScannerBlockEntity.RY;
        float cx = 0.5F, cy = 0.5F + (-4.0F - 8.0F) * PX, cz = 0.5F + 3.4F * PX;
        float len = (float) Balance.SCANNER_FAN_LEN, spread = (float) Math.toRadians(Balance.SCANNER_FAN_DEG);

        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        int steps = 6;
        float flick = 0.75F + 0.25F * (float) Math.sin(t * 1.7F);
        for (int i = 0; i < steps; i++) {
            float a0 = -spread + 2.0F * spread * i / steps, a1 = -spread + 2.0F * spread * (i + 1) / steps;
            float x0 = cx + fx * (float) Math.sin(a0) * len, y0 = cy - (float) Math.cos(a0) * len, z0 = cz + fz * (float) Math.sin(a0) * len;
            float x1 = cx + fx * (float) Math.sin(a1) * len, y1 = cy - (float) Math.cos(a1) * len, z1 = cz + fz * (float) Math.sin(a1) * len;
            float al = 0.22F * flick;

            vc.addVertex(m, cx, cy, cz).setColor(0.25F, 0.55F, 1.0F, al);
            vc.addVertex(m, x0, y0, z0).setColor(0.10F, 0.30F, 1.0F, 0.0F);
            vc.addVertex(m, x1, y1, z1).setColor(0.10F, 0.30F, 1.0F, 0.0F);
            vc.addVertex(m, cx, cy, cz).setColor(0.25F, 0.55F, 1.0F, al);
            vc.addVertex(m, cx, cy, cz).setColor(0.25F, 0.55F, 1.0F, al);
            vc.addVertex(m, x1, y1, z1).setColor(0.10F, 0.30F, 1.0F, 0.0F);
            vc.addVertex(m, x0, y0, z0).setColor(0.10F, 0.30F, 1.0F, 0.0F);
            vc.addVertex(m, cx, cy, cz).setColor(0.25F, 0.55F, 1.0F, al);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SurveyScannerBlockEntity be) { return look.getRenderBoundingBox(be).inflate(Balance.SCANNER_FAN_LEN); }

    @Override public boolean shouldRenderOffScreen(SurveyScannerBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 96; }

    static final class Look extends StagedUnitRenderer<SurveyScannerBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing", "housing_door_w", "housing_door_e");
        private BlockState skin;

        Look() { super("survey_scanner", 1, 1); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(SurveyScannerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(SurveyScannerBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : ATTACH)
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named("survey_scanner"), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
