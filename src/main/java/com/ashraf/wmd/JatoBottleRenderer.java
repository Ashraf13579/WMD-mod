package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class JatoBottleRenderer extends EntityRenderer<JatoBottleEntity> {

    private static final float PX = 1.0F / 16.0F;

    public JatoBottleRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(JatoBottleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("jato_pod");
        if (!mesh.has("bottle")) return;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot()) + 180.0F));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.mulPose(Axis.XP.rotationDegrees(e.tumble(pt)));
        ps.translate(0.0F, -7.0F * PX, -16.0F * PX);
        mesh.draw("bottle", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("nozzle")) mesh.draw("nozzle", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(JatoBottleEntity e) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/jato_pod_mesh.png"); }
}
