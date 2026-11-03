package com.ashraf.wmd;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class HandMissileRenderer extends EntityRenderer<HandMissileEntity> {

    private static final float[] HALF = { 11.2F / 16.0F, 8.8F / 16.0F };

    public HandMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(HandMissileEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        int k = e.kind() == HandLock.MANPADS ? 0 : 1;
        MeshModel mesh = MeshModel.named(k == 0 ? "manpads_missile" : "javelin_missile");
        if (mesh.has("part")) {
            Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
            if (d.lengthSqr() < 1.0E-8) d = new Vec3(0, 1, 0);
            d = d.normalize();
            ps.pushPose();
            ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) d.x, (float) d.y, (float) d.z));
            ps.translate(0.0F, -HALF[k], 0.0F);
            mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HandMissileEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
