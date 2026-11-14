package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class EjectionSeatRenderer extends EntityRenderer<EjectionSeatEntity> {

    private static final float PX = 1.0F / 16.0F;

    public EjectionSeatRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(EjectionSeatEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(e.tandem() ? "jet_cockpit_tandem" : "jet_cockpit");
        String bone = e.back() ? "seat2" : "seat";
        double d = e.tandem() && !e.back() ? 32.0 : 0.0;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot()) + 180.0F));
        if (mesh.has(bone)) {
            ps.pushPose();
            ps.translate(0.0F, -4.0F * PX, (float) (-(d + 4.0) * PX));
            mesh.draw(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        float open = e.chuteOpen(pt);
        MeshModel chute = MeshModel.named("eject_chute");
        if (open > 0.0F && chute.has("chute")) {
            ps.pushPose();
            ps.translate(0.0F, 24.0F * PX, 0.0F);
            float sway = (float) Math.sin((e.tickCount + pt) * 0.07) * 6.0F * open;
            ps.mulPose(Axis.ZP.rotationDegrees(sway));
            float up = Math.min(1.0F, open * 2.0F), spread = 0.15F + 0.85F * Math.max(0.0F, open * 2.0F - 1.0F);
            ps.scale(spread, up, spread);
            chute.draw("chute", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EjectionSeatEntity e) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/jet_cockpit_mesh.png"); }
}
