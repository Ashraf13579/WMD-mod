package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class EscapePodRenderer extends EntityRenderer<EscapePodEntity> {

    public EscapePodRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(EscapePodEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("escape_pod");
        if (!mesh.has("pod")) return;
        ps.pushPose();
        ps.translate(-0.5F, -2.8F / 16.0F, -0.5F);
        mesh.draw("pod", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("hatch")) mesh.draw("hatch", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("beacon") && (!e.surfaced() || (e.tickCount / 8) % 3 == 0)) mesh.draw("beacon", ps.last(), buffers, e.surfaced() ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EscapePodEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
