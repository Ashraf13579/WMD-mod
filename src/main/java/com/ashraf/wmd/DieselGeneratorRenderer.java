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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DieselGeneratorRenderer extends GeoBlockRenderer<GeneratorEngineBlockEntity> {

    private static final String NAME = "diesel_generator";
    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }
    private static final ResourceLocation GEO = rl("geo/block/" + NAME + ".geo.json");

    static class Model extends GeoModel<GeneratorEngineBlockEntity> {
        @Override public ResourceLocation getModelResource(GeneratorEngineBlockEntity be) { return GEO; }
        @Override public ResourceLocation getTextureResource(GeneratorEngineBlockEntity be) { return rl("textures/block/" + NAME + ".png"); }
        @Override public ResourceLocation getAnimationResource(GeneratorEngineBlockEntity be) { return rl("animations/block/" + NAME + ".animation.json"); }

        @Override
        public void setCustomAnimations(GeneratorEngineBlockEntity be, long instanceId, AnimationState<GeneratorEngineBlockEntity> state) {
            double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + state.getPartialTick();
            boolean run = be.isRunning();
            getBone("fan").ifPresent(b -> b.setRotX(run ? (float) (tt * 0.9) : 0.0F));
            getBone("motor").ifPresent(b -> {
                b.setPosY(run ? 0.15F * (float) Math.sin(tt * 3.3) : 0.0F);
                b.setPosZ(run ? 0.1F * (float) Math.cos(tt * 2.7) : 0.0F);
            });
            getBone("lamp").ifPresent(b -> b.setHidden(!run || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private final GeneratorRenderer fallback = new GeneratorRenderer();
    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    public DieselGeneratorRenderer() { super(new Model()); }

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
    public void render(GeneratorEngineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;
        if (!GeckoLibCache.getBakedModels().containsKey(GEO)) { fallback.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay); return; }
        int light = sampleLight(be);
        mesh = MeshModel.named(NAME);
        meshPoses.clear();
        meshNormals.clear();
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    private static int sampleLight(GeneratorEngineBlockEntity be) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        int a = LevelRenderer.getLightColor(level, pos.above(3));
        int b = LevelRenderer.getLightColor(level, pos.relative(be.getFacing()));
        return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    @Override
    public AABB getRenderBoundingBox(GeneratorEngineBlockEntity be) { return new AABB(be.getBlockPos()).inflate(4, 0, 4).expandTowards(0, 4, 0); }
}
