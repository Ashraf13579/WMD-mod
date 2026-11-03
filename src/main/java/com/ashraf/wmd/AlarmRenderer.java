package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class AlarmRenderer extends GeoBlockRenderer<AlarmBlockEntity> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class AlarmModel extends GeoModel<AlarmBlockEntity> {
        @Override
        public ResourceLocation getModelResource(AlarmBlockEntity be) { return rl("geo/block/alarm.geo.json"); }

        @Override
        public ResourceLocation getTextureResource(AlarmBlockEntity be) { return rl("textures/block/alarm.png"); }

        @Override
        public ResourceLocation getAnimationResource(AlarmBlockEntity be) { return rl("animations/block/alarm.animation.json"); }

        @Override
        public void setCustomAnimations(AlarmBlockEntity be, long instanceId, AnimationState<AlarmBlockEntity> state) {
            float a = be.rotorAngle(state.getPartialTick()) * Mth.DEG_TO_RAD;
            getBone("rotor_l").ifPresent(b -> b.setRotX(a));
            getBone("rotor_r").ifPresent(b -> b.setRotX(-a));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    public static final float SCALE = 1.5F;

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) {
                meshPoses.put(bone.getName(), new Matrix4f(poseStack.last().pose()));
                meshNormals.put(bone.getName(), new Matrix3f(poseStack.last().normal()));
            }
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(AlarmBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5, 0.0, -0.5);
        meshPoses.clear();
        meshNormals.clear();
        mesh = MeshModel.named("alarm");
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(AlarmBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0, 0.0, 1.0).expandTowards(0, 1, 0);
    }

    public AlarmRenderer() {
        super(new AlarmModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
