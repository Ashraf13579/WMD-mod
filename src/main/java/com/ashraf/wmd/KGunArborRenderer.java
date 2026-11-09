package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class KGunArborRenderer extends EntityRenderer<KGunArborEntity> {

    static final float[] ARB_C = { -0.3F, 15.55F, 4.0F };
    private static final float SPIN = 24.0F, ROLL = 7.0F;

    public KGunArborRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(KGunArborEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("depth_charge_thrower");
        if (!mesh.has("arbor")) return;
        float t = e.tumbleTicks(pt), ease = Math.min(1.0F, t / 4.0F);
        float roll = (e.getId() & 1) == 0 ? ROLL : -ROLL;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        ps.mulPose(Axis.ZP.rotationDegrees(e.getXRot()));
        ps.mulPose(Axis.ZP.rotationDegrees(SPIN * t * ease));
        ps.mulPose(Axis.YP.rotationDegrees(roll * t * ease));
        ps.translate(-ARB_C[0] / 16.0F, -ARB_C[1] / 16.0F, -ARB_C[2] / 16.0F);
        mesh.draw("arbor", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KGunArborEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
