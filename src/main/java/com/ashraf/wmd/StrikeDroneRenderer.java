package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class StrikeDroneRenderer extends EntityRenderer<StrikeDroneEntity> {

    public StrikeDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.25F;
    }

    @Override
    public void render(StrikeDroneEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        if (ClientDroneFly.flying(e.droneId())) return;
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float t = e.tickCount + pt;
        boolean falling = e.phase() == Drones.FALLING;
        double moving = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        ps.pushPose();

        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (falling) ps.mulPose(Axis.ZP.rotationDegrees((t * 11.0F) % 360.0F));
        else if (e.big()) {
            double climb = e.getY() - e.yo;

            if (e.phase() != BigDrones.CHUTE) ps.mulPose(Axis.XP.rotationDegrees((float) Mth.clamp(Math.toDegrees(Math.atan2(climb, Math.max(0.05, moving))), -20.0, 20.0)));
            if (e.phase() == BigDrones.LOITER) ps.mulPose(Axis.ZP.rotationDegrees(-18.0F));
        } else ps.mulPose(Axis.XP.rotationDegrees((float) Math.min(18.0, moving * 30.0)));
        boolean spin = !falling && e.phase() != BigDrones.CHUTE;
        DroneModuleRenderer.drawDrone(ps, e.type(), DroneAnim.Item.DRONE, true, false, spin, t, buffers, light);
        if (e.phase() == BigDrones.CHUTE) {
            MeshModel chute = MeshModel.named("drone_chute");
            if (chute.has("chute")) {
                float k = e.type() == DroneType.T4 ? 1.6F : 1.0F;
                ps.translate(0.0F, (e.type() == DroneType.T4 ? 18.0F : 9.0F) / 16.0F, 0.0F);
                ps.scale(k, k, k);
                chute.draw("chute", ps.last(), buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StrikeDroneEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
