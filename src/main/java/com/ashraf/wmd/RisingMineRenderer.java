package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class RisingMineRenderer extends EntityRenderer<RisingMineEntity> {

    public RisingMineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(RisingMineEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(TorpedoKind.T1.mesh());
        if (!mesh.has("part")) return;
        Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (d.lengthSqr() < 1.0E-8) d = e.getDeltaMovement();
        if (d.lengthSqr() < 1.0E-8) d = new Vec3(0, 1, 0);
        ps.pushPose();
        ps.mulPose(TorpedoRenderer.frame(d.normalize()));
        ps.translate(0.0F, -TorpedoKind.T1.axisPx() / 16.0F, 0.0F);
        TorpedoRenderer.draw(mesh, TorpedoKind.T1, ps.last(), buffers, light);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RisingMineEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
