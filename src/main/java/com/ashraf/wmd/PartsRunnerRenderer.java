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

public class PartsRunnerRenderer extends EntityRenderer<PartsRunnerEntity> {

    private static final String NAME = "parts_runner";
    private static final float[] HIP_L = { -3.0F, 17.0F, 0.0F }, HIP_R = { 3.0F, 17.0F, 0.0F }, KNEE_L = { -3.0F, 9.5F, 3.0F }, KNEE_R = { 3.0F, 9.5F, 3.0F };

    public PartsRunnerRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.45F;
    }

    private static void leg(MeshModel mesh, String side, float[] hip, float[] knee, float swing, float bend, PoseStack ps, MultiBufferSource buf, int light) {
        if (!mesh.has("leg_" + side)) return;
        ps.pushPose();
        ps.translate(hip[0] / 16.0F, hip[1] / 16.0F, hip[2] / 16.0F);
        ps.mulPose(Axis.XP.rotationDegrees(swing));
        ps.translate(-hip[0] / 16.0F, -hip[1] / 16.0F, -hip[2] / 16.0F);
        mesh.draw("leg_" + side, ps.last(), buf, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("shin_" + side)) SatelliteMesh.drawBone(mesh, NAME, "shin_" + side, knee, bend, 0.0F, 0.0F, ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public void render(PartsRunnerEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float ph = e.walk(pt), st = e.stride();
        float sl = Mth.sin(ph) * 26.0F * st, sr = -sl;
        MeshModel mesh = MeshModel.named(NAME);
        ItemStack load = e.load();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (mesh.has("body")) {
            mesh.draw("body", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            leg(mesh, "l", HIP_L, KNEE_L, sl, -Math.max(0.0F, sl) * 0.8F, ps, buffers, light);
            leg(mesh, "r", HIP_R, KNEE_R, sr, -Math.max(0.0F, sr) * 0.8F, ps, buffers, light);
            if (!load.isEmpty() && mesh.has("tote")) mesh.draw("tote", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            if (mesh.has("glow")) mesh.draw("glow", ps.last(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            if (e.upgraded() && mesh.has("band")) mesh.draw("band", ps.last(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        if (!load.isEmpty()) {
            ps.pushPose();
            ps.translate(0.0F, 21.5F / 16.0F, -4.5F / 16.0F);
            ps.scale(0.5F, 0.5F, 0.5F);
            Minecraft.getInstance().getItemRenderer().renderStatic(load, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, e.level(), e.getId());
            ps.popPose();
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PartsRunnerEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
