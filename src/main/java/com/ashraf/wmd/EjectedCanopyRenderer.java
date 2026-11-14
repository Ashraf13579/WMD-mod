package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class EjectedCanopyRenderer extends EntityRenderer<EjectedCanopyEntity> {

    private static final float PX = 1.0F / 16.0F;

    public EjectedCanopyRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(EjectedCanopyEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(e.tandem() ? "jet_cockpit_tandem" : "jet_cockpit");
        if (!mesh.has("canopy")) return;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot()) + 180.0F));
        ps.mulPose(Axis.XP.rotationDegrees(e.tumble(pt)));
        ps.translate(0.0F, -22.0F * PX, (float) (-(e.tandem() ? 29.0 : 13.0) * PX));
        mesh.draw("canopy", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        int[] t = JetCockpitRenderer.GLASS_TINT[Math.max(0, Math.min(2, e.tint()))];
        mesh.drawTinted("glass", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], t[3], false);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EjectedCanopyEntity e) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/jet_cockpit_mesh.png"); }
}
