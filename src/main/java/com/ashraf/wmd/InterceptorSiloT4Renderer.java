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

public class InterceptorSiloT4Renderer extends GeoBlockRenderer<InterceptorSiloT4BlockEntity> {

    private static final float LID_SIGN = 1.0F, DRUM_SIGN = 1.0F, LID_TRAVEL_PX = 58.0F, EJECT_PX = 350.0F;

    private static final float BODY_SCALE = 0.62F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class SiloModel extends GeoModel<InterceptorSiloT4BlockEntity> {
        @Override public ResourceLocation getModelResource(InterceptorSiloT4BlockEntity be) { return rl("geo/block/silo_t4.geo.json"); }
        @Override public ResourceLocation getTextureResource(InterceptorSiloT4BlockEntity be) { return rl("textures/mesh/silo_t4_mesh.png"); }
        @Override public ResourceLocation getAnimationResource(InterceptorSiloT4BlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(InterceptorSiloT4BlockEntity be, long instanceId, AnimationState<InterceptorSiloT4BlockEntity> state) {
            if (be.getLevel() == null) return;
            final float lid = (float) LabAnim.ease(be.shownLid);
            getBone("base").ifPresent(b -> b.setPosZ(-8.0F));
            getBone("lidA").ifPresent(b -> b.setPosX(LID_SIGN * LID_TRAVEL_PX * lid));
            getBone("lidAarmor").ifPresent(b -> b.setHidden(!be.hardened()));
            getBone("lidBarmor").ifPresent(b -> b.setHidden(!be.hardened()));
            getBone("lidB").ifPresent(b -> b.setPosX(-LID_SIGN * LID_TRAVEL_PX * lid));
            getBone("drum").ifPresent(b -> b.setRotY(DRUM_SIGN * (float) Math.toRadians(be.shownDrum)));
            final int active = ((int) Math.round(be.shownDrum / 90.0) % 4 + 4) % 4;
            final float eject = (float) LabAnim.ease(be.shownLift);
            for (int i = 0; i < 4; i++) {
                final int ii = i;
                getBone("ic" + i).ifPresent(b -> { b.setHidden(!be.canisterLoaded(ii)); b.setPosY(0.0F); });
            }
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

    public InterceptorSiloT4Renderer() {
        super(new SiloModel());
    }

    @Override
    public AABB getRenderBoundingBox(InterceptorSiloT4BlockEntity be) { return new AABB(be.getBlockPos()).inflate(6, 0, 6).expandTowards(0, 8, 0).expandTowards(0, -26, 0); }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public void render(InterceptorSiloT4BlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {

            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(15, Math.max(LightTexture.block(a), LightTexture.block(packedLight))), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named("silo_t4");
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        MissileBuild build = MissileBuild.interceptor(3, false);
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
