package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PalletEntityRenderer extends EntityRenderer<PalletEntity> {

    static final float PX = 1.0F / 16.0F;
    private static final float[][] CLUSTER = { { -1.15F, 0.0F, -6.0F }, { 0.0F, 0.35F, 0.0F }, { 1.15F, 0.0F, 6.0F } };

    public PalletEntityRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(PalletEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        boolean heavy = e.heavy();
        MeshModel mm = MeshModel.named(heavy ? "cargo_pallet_heavy" : "cargo_pallet");
        int ov = OverlayTexture.NO_OVERLAY;
        float open = e.chuteOpen(pt);
        boolean landed = e.phase() == PalletEntity.LANDED;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(e.rollPitch()));
        if (heavy) ps.translate(-0.5, 0.0, -0.5);
        if (mm.has("base")) {
            mm.draw("base", ps.last(), buffers, light, ov);
            if (mm.has("net")) mm.draw("net", ps.last(), buffers, light, ov);
            if (open <= 0.0F && !landed && mm.has("pack")) mm.draw("pack", ps.last(), buffers, light, ov);
        }
        ps.popPose();
        MeshModel ch = MeshModel.named("cargo_chute");
        if (ch.has("chute")) {
            float sway = (float) Math.sin((e.tickCount + pt) * 0.06) * 5.0F * open;
            if (open > 0.0F) {
                float up = Math.min(1.0F, open * 2.0F), spread = 0.15F + 0.85F * Math.max(0.0F, open * 2.0F - 1.0F);
                float[][] set = heavy ? CLUSTER : new float[][] { { 0.0F, 0.0F, 0.0F } };
                for (float[] c : set) {
                    ps.pushPose();
                    ps.translate(0.0, 1.0, 0.0);
                    ps.mulPose(Axis.ZP.rotationDegrees(sway + c[2]));
                    ps.translate(c[0] * spread, c[1], 0.0);
                    ps.scale(spread, up, spread);
                    ch.draw("chute", ps.last(), buffers, light, ov);
                    ps.popPose();
                }
            } else if (heavy && e.phase() == PalletEntity.AIR) {
                ps.pushPose();
                ps.translate(0.0, 1.2, -1.6);
                ps.mulPose(Axis.XP.rotationDegrees(-60.0F));
                ps.scale(0.35F, 0.35F, 0.35F);
                ch.draw("chute", ps.last(), buffers, light, ov);
                ps.popPose();
            }
            if (landed && ch.has("flat") && e.landedAt() >= 0 && e.tickCount - e.landedAt() < Balance.PALLET_FLAT_TICKS) {
                ps.pushPose();
                ps.translate(heavy ? 0.6 : 0.3, 0.0, 0.0);
                ch.draw("flat", ps.last(), buffers, light, ov);
                ps.popPose();
            }
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PalletEntity e) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/mesh/cargo_pallet_mesh.png"); }
}
