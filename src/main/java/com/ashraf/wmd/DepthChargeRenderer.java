package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class DepthChargeRenderer extends EntityRenderer<DepthChargeEntity> {

    public DepthChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(DepthChargeEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("depth_charge");
        if (!mesh.has("part")) return;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        ps.mulPose(Axis.XP.rotationDegrees((e.tickCount + pt) * 9.0F));
        ps.translate(0.0F, -5.0F / 16.0F, 0.0F);
        mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DepthChargeEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
