package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PayloadUnitItemRenderer extends BlockEntityWithoutLevelRenderer {

    public PayloadUnitItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        float h = MissileParts.PAYLOAD_H / 16.0F;
        boolean gui = ctx == ItemDisplayContext.GUI;
        float s = (gui ? 0.95F : 1.3F) / h;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(s, s, s);
        poseStack.translate(0.0, -h * 0.5, 0.0);
        String type = PayloadUnitItem.typeOf(stack);
        if (gui) {
            Lighting.setupForFlatItems();
            MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource b ? b : mc.renderBuffers().bufferSource();
            MissileParts.renderPayload(type, 0.0F, 0.0F, 1.0F, poseStack, src, packedLight);
            src.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        } else {
            MissileParts.renderPayload(type, 0.0F, 0.0F, 1.0F, poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }
}
