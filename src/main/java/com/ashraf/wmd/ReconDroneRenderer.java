package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ReconDroneRenderer extends EntityRenderer<ReconDroneEntity> {

    private static final String NAME = "recon_drone";

    private static final float[] PROP_PIVOT = { 0.0F, 2.0F, 7.6F }, CAM_PIVOT = { 0.0F, 0.0F, -4.2F }, GLOW_PIVOT = { 0.0F, 0.0F, 0.0F };
    private static final float PROP_DEG_PER_TICK = 70.0F;

    public ReconDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ReconDroneEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float t = e.tickCount + pt;
        boolean falling = e.phase() == Drones.FALLING;
        MeshModel mesh = MeshModel.named(NAME);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (falling) ps.mulPose(Axis.ZP.rotationDegrees((t * 9.0F) % 360.0F));
        else ps.mulPose(Axis.ZP.rotationDegrees(4.0F * Mth.sin(t * 0.05F)));
        if (mesh.has("body")) {
            int ov = OverlayTexture.NO_OVERLAY;
            mesh.draw("body", ps.last(), buffers, light, ov);
            if (mesh.has("prop")) SatelliteMesh.drawBone(mesh, NAME, "prop", PROP_PIVOT, 0.0F, 0.0F, falling ? 0.0F : (t * PROP_DEG_PER_TICK) % 360.0F, ps, buffers, light, ov);
            if (mesh.has("cam")) SatelliteMesh.drawBone(mesh, NAME, "cam", CAM_PIVOT, 0.0F, 25.0F * Mth.sin(t * 0.03F), 0.0F, ps, buffers, light, ov);
            if (mesh.has("glow") && !falling && ((long) t) % 30L < 15L)
                SatelliteMesh.drawBone(mesh, NAME, "glow", GLOW_PIVOT, 0.0F, 0.0F, 0.0F, ps, buffers, LightTexture.FULL_BRIGHT, ov);
        } else {
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.scale(1.5F, 1.5F, 1.5F);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Wmd.RECON_DRONE.get()), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    ps, buffers, e.level(), e.getId());
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ReconDroneEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
