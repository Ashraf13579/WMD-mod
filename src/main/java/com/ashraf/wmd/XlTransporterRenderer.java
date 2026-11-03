package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public class XlTransporterRenderer extends EntityRenderer<XlTransporterEntity> {

    private static final String NAME = "xl_transporter";
    private static final float HY = 28.0F, HZ = 124.0F, MOUNT_Y = 25.0F, MOUNT_Z = 0.0F, PIN_Y = -3.0F, PIN_Z = -50.0F, RAISE = 75.0F;
    private static final float[] ERECTOR = { 0.0F, HY, HZ }, RAM = { 0.0F, MOUNT_Y, MOUNT_Z }, ROD = { 0.0F, HY + PIN_Y, HZ + PIN_Z }, GLOW = { 0.0F, 0.0F, 0.0F };

    public XlTransporterRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 2.6F;
    }

    @Override
    public void render(XlTransporterEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float elev = RAISE * e.erect(pt);
        double a = Math.toRadians(elev);

        float py = (float) (HY + PIN_Y * Math.cos(a) - PIN_Z * Math.sin(a)), pz = (float) (HZ + PIN_Y * Math.sin(a) + PIN_Z * Math.cos(a));
        float ram = (float) -Math.toDegrees(Math.atan2(py - MOUNT_Y, pz - MOUNT_Z));
        MeshModel mesh = MeshModel.named(NAME);
        int ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (mesh.has("body")) {
            mesh.draw("body", ps.last(), buffers, light, ov);
            if (mesh.has("erector")) SatelliteMesh.drawBone(mesh, NAME, "erector", ERECTOR, elev, 0.0F, 0.0F, ps, buffers, light, ov);
            if (mesh.has("ram")) SatelliteMesh.drawBone(mesh, NAME, "ram", RAM, ram, 0.0F, 0.0F, ps, buffers, light, ov);
            if (mesh.has("rod")) {
                ps.pushPose();
                ps.translate(0.0F, (py - ROD[1]) / 16.0F, (pz - ROD[2]) / 16.0F);
                SatelliteMesh.drawBone(mesh, NAME, "rod", ROD, ram, 0.0F, 0.0F, ps, buffers, light, ov);
                ps.popPose();
            }
            if (mesh.has("glow")) SatelliteMesh.drawBone(mesh, NAME, "glow", GLOW, 0.0F, 0.0F, 0.0F, ps, buffers, LightTexture.FULL_BRIGHT, ov);
        }
        ItemStack load = e.load();
        MissileBuild build = Logistics.buildOf(load);
        if (build != null) {
            float rad = (float) (BayCalibre.R1 * BayCalibre.rad(build.calibre()));
            float axis = HY + 1.2F * rad;
            ps.pushPose();
            ps.translate(0.0F, HY / 16.0F, HZ / 16.0F);
            ps.mulPose(Axis.XP.rotationDegrees(elev));
            ps.translate(0.0F, (axis - HY) / 16.0F, (122.0F - HZ) / 16.0F);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            MissileParts.render(build, ps, buffers, light, pt);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(XlTransporterEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
