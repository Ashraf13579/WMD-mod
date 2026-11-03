package com.ashraf.wmd;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PartItemRenderer extends BlockEntityWithoutLevelRenderer {

    public PartItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static float[] extent(MissilePartItem part, MissileBuild b) {
        int c = b.calibre();
        float cl = (float) BayCalibre.len(c), cr = (float) BayCalibre.rad(c);
        return switch (part.slot()) {
            case 0 -> new float[] { c >= 3 ? -4.0F : 0.0F, 84.0F * cl, c == 1 ? 17.0F : c == 2 ? 23.6F : c == 3 ? 31.2F : 41.0F };
            case 1 -> new float[] { -3.0F * cl, 36.0F * cl, 9.6F * cr };
            case 2 -> new float[] { 0.0F, 14.0F * cl, 11.6F * cr };
            default -> new float[] { 0.0F, 27.0F * cl, 9.5F * cr };
        };
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof MissilePartItem part)) return;
        MissileBuild look = part.look();
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float[] e = extent(part, look);
        float size = Math.max(e[1] - e[0], 2.0F * e[2]);
        float k = 0.95F * 16.0F / size;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(k, k, k);
        poseStack.translate(0.0F, -(e[0] + e[1]) * 0.5F / 16.0F, 0.0F);
        if (ctx == ItemDisplayContext.GUI) {

            Lighting.setupForFlatItems();
            MultiBufferSource.BufferSource src = buffers instanceof MultiBufferSource.BufferSource bs ? bs : mc.renderBuffers().bufferSource();
            MissileParts.renderPart(look, part.slot(), false, poseStack, src, packedLight, pt);
            src.endBatch();
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
        } else {
            MissileParts.renderPart(look, part.slot(), false, poseStack, buffers, packedLight, pt);
        }
        poseStack.popPose();
    }
}
