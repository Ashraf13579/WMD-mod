package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SalvageTurretRenderer extends GeoBlockRenderer<SalvageTurretBlockEntity> {

    private static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F, SPIN_SIGN = 1.0F;
    private static final int[] RUST = { 206, 168, 138 }, DEAD = { 140, 124, 112 };

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class TurretModel extends GeoModel<SalvageTurretBlockEntity> {
        @Override public ResourceLocation getModelResource(SalvageTurretBlockEntity be) { return rl("geo/block/ciws.geo.json"); }
        @Override public ResourceLocation getTextureResource(SalvageTurretBlockEntity be) { return rl("textures/mesh/ciws_mesh.png"); }
        @Override public ResourceLocation getAnimationResource(SalvageTurretBlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(SalvageTurretBlockEntity be, long instanceId, AnimationState<SalvageTurretBlockEntity> state) {
            if (be.getLevel() == null) return;
            long now = be.getLevel().getGameTime();
            getBone("turn").ifPresent(b -> b.setRotY(AZ_SIGN * (float) Math.toRadians(be.shownAz)));
            getBone("gun").ifPresent(b -> b.setRotX(EL_SIGN * (float) Math.toRadians(be.shownEl)));
            getBone("barrels").ifPresent(b -> { b.setRotZ(SPIN_SIGN * (float) Math.toRadians(be.spin)); b.setPosZ(be.recoil); });
            getBone("pod").ifPresent(b -> b.setRotY((float) Math.sin(be.podAngle) * 0.9F));
            getBone("ammofill").ifPresent(b -> b.setHidden(false));
            for (int k = 0; k < 9; k++) getBone("belt" + k).ifPresent(b -> b.setHidden(true));
            boolean alert = be.powered() && (be.state() != SalvageTurretBlockEntity.IDLE || be.firing(now));
            boolean blink = ((now / 4) & 1) == 0;
            getBone("lamp_g").ifPresent(b -> b.setHidden(true));
            getBone("lamp_a").ifPresent(b -> b.setHidden(!be.powered() || alert));
            getBone("lamp_r").ifPresent(b -> b.setHidden(!alert || !blink));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Pose(Matrix4f pose, Matrix3f normal) {}
    private final Map<String, Pose> meshPoses = new HashMap<>();
    private MeshModel mesh;

    public SalvageTurretRenderer() { super(new TurretModel()); }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) meshPoses.put(bone.getName(), new Pose(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal())));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public AABB getRenderBoundingBox(SalvageTurretBlockEntity be) { return new AABB(be.getBlockPos()).inflate(2, 1, 2).expandTowards(0, 3, 0); }

    @Override
    public int getViewDistance() { return 128; }

    @Override
    public void render(SalvageTurretBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        meshPoses.clear();
        mesh = MeshModel.named("ciws");

        poseStack.pushPose();
        float s = SalvageTurretBlockEntity.SCALE;
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.scale(s, s, s);
        poseStack.translate(-0.5, 0.0, -1.5);
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
        int[] tint = be.powered() ? RUST : DEAD;
        for (Map.Entry<String, Pose> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.drawColoured(e.getKey(), ps.last(), bufferSource, light, packedOverlay, tint[0], tint[1], tint[2]);
        }
        meshPoses.clear();
    }
}
