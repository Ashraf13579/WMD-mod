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

public class InterceptorSiloRenderer extends GeoBlockRenderer<InterceptorSiloBlockEntity> {

    private static final float LID_SIGN = 1.0F, LID_OPEN_DEG = 100.0F, LIFT_PX = 300.0F;

    private static final float BODY_SCALE = 0.85F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class SiloModel extends GeoModel<InterceptorSiloBlockEntity> {
        @Override public ResourceLocation getModelResource(InterceptorSiloBlockEntity be) { return rl("geo/block/silo_t3.geo.json"); }
        @Override public ResourceLocation getTextureResource(InterceptorSiloBlockEntity be) { return rl("textures/mesh/silo_t3_mesh.png"); }
        @Override public ResourceLocation getAnimationResource(InterceptorSiloBlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(InterceptorSiloBlockEntity be, long instanceId, AnimationState<InterceptorSiloBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float lid = (float) LabAnim.ease(be.shownLid), lift = (float) LabAnim.ease(be.shownLift);
            getBone("base").ifPresent(b -> b.setPosZ(-8.0F));
            getBone("lid").ifPresent(b -> b.setRotX(LID_SIGN * (float) Math.toRadians(LID_OPEN_DEG * lid)));
            getBone("lidarmor").ifPresent(b -> b.setHidden(!be.hardened()));
            getBone("ic0").ifPresent(b -> b.setHidden(!be.canisterLoaded()));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();
    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().startsWith("ic")) {
            if (!bone.isHidden())
                sockets.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
            return;
        }
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden())
                meshPoses.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    public InterceptorSiloRenderer() {
        super(new SiloModel());
    }

    @Override
    public AABB getRenderBoundingBox(InterceptorSiloBlockEntity be) { return new AABB(be.getBlockPos()).inflate(4, 0, 4).expandTowards(0, 6, 0).expandTowards(0, -22, 0); }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public void render(InterceptorSiloBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {

            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(15, Math.max(LightTexture.block(a), LightTexture.block(packedLight))), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named("silo_t3");
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        MissileBuild build = MissileBuild.interceptor(InterceptorSiloBlockEntity.TIER, false);
        for (Socket k : sockets.values()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(k.pose());
            ps.last().normal().set(k.normal());
            ps.translate(k.px() / 16.0F, k.py() / 16.0F, k.pz() / 16.0F);
            ps.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE);
            MissileParts.render(build, true, ps, bufferSource, light, partialTick);
        }
        sockets.clear();
    }
}
