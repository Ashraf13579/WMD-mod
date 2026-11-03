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

public class MissileRobotRenderer extends EntityRenderer<MissileRobotEntity> {

    private static final String NAME = "missile_robot";
    private static final float[] LIFT_F = { 0.0F, 26.0F, -28.0F }, LIFT_R = { 0.0F, 26.0F, 28.0F }, CLAMP_F = { 7.6F, 37.6F, -28.0F }, CLAMP_R = { 7.6F, 37.6F, 28.0F },
            LIDAR = { 0.0F, 26.0F, -57.0F }, GLOW = { 0.0F, 0.0F, 0.0F };

    private static final float LIFT_PX = 6.0F, V_TOP = 30.0F, STRAP_AXIS = 37.6F, STRAP_R = 7.0F;

    public MissileRobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.4F;
    }

    @Override
    public void render(MissileRobotEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float t = e.tickCount + pt;
        ItemStack load = e.load();
        boolean carrying = !load.isEmpty(), driving = e.state() != MissileRobotEntity.IDLE;
        MissileBuild build = carrying ? load.get(WmdComponents.MISSILE_BUILD.get()) : null;
        float lift = carrying ? LIFT_PX : 0.0F;

        float rad = build != null ? (float) (BayCalibre.R1 * BayCalibre.rad(build.calibre())) : 6.0F;
        float axis = V_TOP + lift + 1.2F * rad;
        MeshModel mesh = MeshModel.named(NAME);
        int ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (mesh.has("body")) {
            mesh.draw("body", ps.last(), buffers, light, ov);
            for (int k = 0; k < 2; k++) {
                String tag = k == 0 ? "f" : "r";
                float[] cp = k == 0 ? CLAMP_F : CLAMP_R;
                ps.pushPose();
                ps.translate(0.0F, lift / 16.0F, 0.0F);
                if (mesh.has("lift_" + tag)) mesh.draw("lift_" + tag, ps.last(), buffers, light, ov);
                ps.popPose();
                if (!mesh.has("clamp_" + tag)) continue;
                if (carrying) {
                    float s = (rad + 1.0F) / STRAP_R;
                    ps.pushPose();
                    ps.translate(0.0F, axis / 16.0F, cp[2] / 16.0F);
                    ps.scale(s, s, 1.0F);
                    ps.translate(0.0F, -STRAP_AXIS / 16.0F, -cp[2] / 16.0F);
                    mesh.draw("clamp_" + tag, ps.last(), buffers, light, ov);
                    ps.popPose();
                } else {
                    SatelliteMesh.drawBone(mesh, NAME, "clamp_" + tag, cp, 0.0F, 0.0F, 115.0F, ps, buffers, light, ov);
                }
            }
            if (mesh.has("lidar")) SatelliteMesh.drawBone(mesh, NAME, "lidar", LIDAR, 0.0F, driving ? (t * 12.0F) % 360.0F : 0.0F, 0.0F, ps, buffers, light, ov);
            if (mesh.has("glow")) SatelliteMesh.drawBone(mesh, NAME, "glow", GLOW, 0.0F, 0.0F, 0.0F, ps, buffers, LightTexture.FULL_BRIGHT, ov);
        }
        if (carrying) {
            ps.pushPose();
            if (build != null) {
                float len = (float) (160.0 * BayCalibre.len(build.calibre()));
                ps.translate(0.0F, axis / 16.0F, len / 2.0F / 16.0F);
                ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                MissileParts.render(build, ps, buffers, light, pt);
            } else {
                ps.translate(0.0F, (axis + 2.0F) / 16.0F, 0.0F);
                ps.mulPose(Axis.YP.rotationDegrees(90.0F));
                ps.scale(3.0F, 3.0F, 3.0F);
                Minecraft.getInstance().getItemRenderer().renderStatic(load, ItemDisplayContext.FIXED, light, ov, ps, buffers, e.level(), e.getId());
            }
            ps.popPose();
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileRobotEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
