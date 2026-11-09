package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class AsrocRenderer extends EntityRenderer<AsrocEntity> {

    private static final float CUT = -7.0F / 16.0F;

    public AsrocRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(AsrocEntity e, Frustum frustum, double x, double y, double z) {
        return e.phase() == AsrocEntity.CHUTE || super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(AsrocEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("asroc");
        if (!mesh.has("part")) return;
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3[] p = e.pose(pt);
        Vec3 at = p[0] == null ? here : p[0];
        int[] t = MissileParts.tint(2);
        int ph = e.phase();
        ps.pushPose();
        ps.translate(at.x - here.x, at.y - here.y, at.z - here.z);
        if (ph != AsrocEntity.CHUTE) {
            ps.mulPose(TorpedoRenderer.frame(p[1]));
            ps.translate(0.0F, -TorpedoRenderer.AXIS_Y, 0.0F);
            mesh.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
            ps.popPose();
            super.render(e, entityYaw, pt, ps, buffers, light);
            return;
        }

        double k = e.chuteAge(pt);
        MeshModel chute = MeshModel.named("drone_chute");
        if (chute.has("chute")) {
            ps.pushPose();
            ps.translate(0.0F, 1.6F, 0.0F);
            ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(k * 0.15) * 6.0F));
            float s = (float) Math.min(1.0, 0.25 + k / 10.0);
            ps.scale(s, s, s);
            chute.draw("chute", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.pushPose();
        ps.mulPose(TorpedoRenderer.frame(p[1]));
        ps.translate(0.0F, -TorpedoRenderer.AXIS_Y, 2.0F);
        mesh.drawTintedZ("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], -100.0F, CUT);
        ps.popPose();
        ps.popPose();

        Vec3 b = e.boosterPos(k);
        int top = NavalLayer.waterTop(e.level(), Mth.floor(b.x), Mth.floor(b.z));
        if (top == NavalLayer.NO_WATER || b.y > top + 0.5) {
            ps.pushPose();
            ps.translate(b.x - here.x, b.y - here.y, b.z - here.z);
            Vec3 h = e.pose(pt)[1];
            ps.mulPose(TorpedoRenderer.frame(new Vec3(h.x, 0.0, h.z).lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : new Vec3(h.x, 0.0, h.z).normalize()));
            ps.mulPose(Axis.XP.rotationDegrees((float) (k * 14.0)));
            ps.translate(0.0F, -TorpedoRenderer.AXIS_Y, -1.0F);
            mesh.drawTintedZ("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], CUT, 100.0F);
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AsrocEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
