package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MissileItemRenderer extends BlockEntityWithoutLevelRenderer {

    public MissileItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MissileBuild build = stack.getItem() instanceof InterceptorItem it
                ? MissileBuild.interceptor(it.tier(), stack.getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0) : MissileBuild.of(stack);
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);

        float h = build.isStage() ? MissileParts.kindHeight(build) : Missiles.height(build.calibre());

        boolean gui = ctx == ItemDisplayContext.GUI;
        float sy = (gui ? 0.80F : 1.6F) / h;
        float sxz = gui ? sy * 2.2F : sy;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(sxz, sy, sxz);
        poseStack.translate(0.0, -h * 0.5, 0.0);
        if (gui) {

            Lighting.setupForFlatItems();
            MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource b ? b : mc.renderBuffers().bufferSource();
            MissileParts.render(build, poseStack, src, packedLight, pt);
            src.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        } else {
            MissileParts.render(build, poseStack, buffers, packedLight, pt);
        }
        poseStack.popPose();
    }
}
