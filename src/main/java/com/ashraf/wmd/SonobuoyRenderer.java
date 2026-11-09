package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class SonobuoyRenderer extends EntityRenderer<SonobuoyEntity> {

    public SonobuoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SonobuoyEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("sonobuoy");
        if (!mesh.has("part")) return;
        float drop = e.earDrop();
        ps.pushPose();
        float bob = (float) Math.sin((e.tickCount + pt) * 0.12) * 0.03F;
        ps.translate(0.0F, bob, 0.0F);
        mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("hydro")) {
            ps.pushPose();
            ps.translate(0.0F, -drop, 0.0F);
            mesh.draw("hydro", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (drop > 0.2F)
            CcRenderers.line(buffers.getBuffer(RenderType.lines()), ps.last(), 0.0F, 2.0F / 16.0F, 0.0F, 0.0F, 2.0F / 16.0F - drop, 0.0F, 0xFF20262C);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SonobuoyEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
