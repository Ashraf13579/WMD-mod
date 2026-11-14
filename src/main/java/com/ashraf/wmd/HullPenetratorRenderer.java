package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

final class HullPenetratorRenderer implements BlockEntityRenderer<BusbarBlockEntity> {

    private static final float[] CUBE = { -8.0F, 0.0F, -8.0F, 8.0F, 16.0F, 8.0F };

    @Override
    public void render(BusbarBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState skin = be.copycat().shown();
        if (skin == null || !(be.getBlockState().getBlock() instanceof HullPenetratorBlock) || be.getLevel() == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        PoseStack.Pose last = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(skin);
        for (Direction d : Direction.values()) {
            int l = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(d));
            int lit = Math.max(l & 0xFFFF, light & 0xFFFF) | Math.max(l >>> 16, light >>> 16) << 16;
            AirEngineRenderer.CopycatSkin.face(vc, last.pose(), last.normal(), model, skin, be, d, CUBE, lit, overlay);
        }
        poseStack.popPose();
    }
}
