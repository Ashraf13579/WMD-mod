package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ParachuteRenderer extends EntityRenderer<ParachuteEntity> {

    public ParachuteRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(ParachuteEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel chute = MeshModel.named("eject_chute");
        float open = e.chuteOpen(pt);
        if (open > 0.0F && chute.has("chute")) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
            ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin((e.tickCount + pt) * 0.07) * 6.0F * open));
            float up = Math.min(1.0F, open * 2.0F), spread = 0.15F + 0.85F * Math.max(0.0F, open * 2.0F - 1.0F);
            ps.scale(spread, up, spread);
            chute.draw("chute", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ParachuteEntity e) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/eject_chute_mesh.png"); }
}
