package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StorageTankRenderer extends GeoBlockRenderer<StorageTankBlockEntity> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class TankModel extends GeoModel<StorageTankBlockEntity> {
        @Override public ResourceLocation getModelResource(StorageTankBlockEntity be) { return rl("geo/block/storage_tank.geo.json"); }
        @Override public ResourceLocation getTextureResource(StorageTankBlockEntity be) { return rl("textures/block/storage_tank_geo.png"); }
        @Override public ResourceLocation getAnimationResource(StorageTankBlockEntity be) { return rl("animations/block/storage_tank.animation.json"); }

        @Override
        public void setCustomAnimations(StorageTankBlockEntity be, long instanceId, AnimationState<StorageTankBlockEntity> state) {
            float level = be.shownLevel();
            int shown = be.fluidIndex();
            for (int i = 0; i < WmdFluids.ALL.size(); i++) {
                final boolean on = i == shown && level > 0.002F;
                final int idx = i;
                getBone("fluid_" + idx).ifPresent(b -> {
                    b.setHidden(!on);
                    b.setScaleY(Math.max(level, 0.001F));
                });
            }

            double t = ((be.getLevel() != null ? be.getLevel().getGameTime() : 0) + state.getPartialTick()) / 20.0;
            float amp = be.isFilling() ? 0.9F : 0.3F;
            final boolean surfaceOn = shown >= 0 && level > 0.002F;
            for (int i = 0; i < 7; i++) {
                final float wave = amp * (float) (Math.sin(t * 6.9 + i * 1.1) + 0.4 * Math.sin(t * 11.3 + i * 2.3));
                getBone("ripple" + i).ifPresent(b -> {
                    b.setHidden(!surfaceOn);
                    b.setPosY(level * 90.2F + wave);
                });
            }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

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
    public void render(StorageTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        mesh = MeshModel.named("storage_tank");
        meshPoses.clear();
        meshNormals.clear();
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        PoseStack.Pose glassPose = null;
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            if (e.getKey().equals("glasspane")) {
                PoseStack gps = new PoseStack();
                gps.last().pose().set(e.getValue());
                gps.last().normal().set(meshNormals.get(e.getKey()));
                glassPose = gps.last();
                continue;
            }
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            if (e.getKey().equals("accent")) {
                int c = accentColour(be);
                mesh.drawColoured("accent", ps.last(), bufferSource, packedLight, packedOverlay, (c >> 16) & 255, (c >> 8) & 255, c & 255);
                continue;
            }
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }
        if (glassPose != null) mesh.drawTinted("glasspane", glassPose, bufferSource, packedLight, packedOverlay, 255, 255, 255, 96, false);
        meshPoses.clear();
        meshNormals.clear();
    }

    public StorageTankRenderer() {
        super(new TankModel());
    }

    @Override
    public AABB getRenderBoundingBox(StorageTankBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6, 0, 6).expandTowards(0, 8.2, 0);
    }

    @Override
    public int getViewDistance() { return 128; }

    private static int accentColour(StorageTankBlockEntity be) {
        int idx = be.fluidIndex();
        if (idx < 0 || idx >= WmdFluids.ALL.size()) return 0xFFFFFF;
        int argb = WmdFluids.ALL.get(idx).color();
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (Math.min(255, (int) (r * 1.3 + 25)) << 16) | (Math.min(255, (int) (g * 1.3 + 25)) << 8) | Math.min(255, (int) (b * 1.3 + 25));
    }
}
