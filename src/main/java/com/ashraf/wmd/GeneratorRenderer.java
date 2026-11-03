package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class GeneratorRenderer extends GeoBlockRenderer<GeneratorEngineBlockEntity> {

    public GeneratorRenderer() {
        super(new DefaultedBlockGeoModel<GeneratorEngineBlockEntity>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "generator")) {
            @Override
            public void setCustomAnimations(GeneratorEngineBlockEntity be, long instanceId, software.bernie.geckolib.animation.AnimationState<GeneratorEngineBlockEntity> state) {
                super.setCustomAnimations(be, instanceId, state);
                BoneOverrides.apply(this, getModelResource(be));
            }
        });
    }

    @Override
    public AABB getRenderBoundingBox(GeneratorEngineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5, 4, 5);
    }

    @Override
    public void render(GeneratorEngineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        poseStack.pushPose();
        poseStack.translate(0.0, GeneratorStructure.MODEL_Y, 0.0);
        super.render(be, partialTick, poseStack, bufferSource, sampleLight(be), packedOverlay);
        poseStack.popPose();
    }

    private static int sampleLight(GeneratorEngineBlockEntity be) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        int a = LevelRenderer.getLightColor(level, pos.above(GeneratorStructure.HEIGHT));
        int b = LevelRenderer.getLightColor(level, pos.relative(be.getFacing()).above());
        return LightTexture.pack(
                Math.max(LightTexture.block(a), LightTexture.block(b)),
                Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }
}
