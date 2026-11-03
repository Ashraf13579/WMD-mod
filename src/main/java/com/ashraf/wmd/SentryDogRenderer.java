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

public class SentryDogRenderer extends EntityRenderer<SentryDogEntity> {

    private static final String NAME = "sentry_dog";
    private static final float HIP_Y = 11.0F, KNEE_Y = 5.8F, LX = 4.4F;
    private static final String[] LEGS = { "fl", "fr", "rl", "rr" };
    private static final float[][] LEG_POS = { { -LX, -6.4F }, { LX, -6.4F }, { -LX, 6.4F }, { LX, 6.4F } };
    private static final float[] PHASE = { 0.0F, (float) Math.PI, (float) Math.PI, 0.0F };

    public SentryDogRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    private static void turn(PoseStack ps, String bone, float px, float py, float pz, float rx, float ry, float rz) {
        rx = BoneOverrides.deg(NAME, bone, "rotX", rx);
        ry = BoneOverrides.deg(NAME, bone, "rotY", ry);
        rz = BoneOverrides.deg(NAME, bone, "rotZ", rz);
        ps.translate(px / 16.0F, py / 16.0F, pz / 16.0F);
        if (rz != 0.0F) ps.mulPose(Axis.ZP.rotationDegrees(rz));
        if (ry != 0.0F) ps.mulPose(Axis.YP.rotationDegrees(ry));
        if (rx != 0.0F) ps.mulPose(Axis.XP.rotationDegrees(rx));
        ps.translate(-px / 16.0F, -py / 16.0F, -pz / 16.0F);
    }

    @Override
    public void render(SentryDogEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("body")) { super.render(e, entityYaw, pt, ps, buffers, light); return; }
        int ov = OverlayTexture.NO_OVERLAY;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - bodyYaw), -40.0F, 40.0F);
        boolean asleep = e.asleep();
        float pos = e.walkAnimation.position(pt), speed = Math.min(1.0F, e.walkAnimation.speed(pt));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (asleep) ps.translate(0.0F, -2.0F / 16.0F, 0.0F);
        mesh.draw("body", ps.last(), buffers, light, ov);

        ps.pushPose();
        turn(ps, "head", 0.0F, 13.6F, -8.2F, 0.0F, -headYaw, 0.0F);
        mesh.draw("head", ps.last(), buffers, light, ov);
        if (!asleep) mesh.draw(e.locked() ? "eyered" : "eye", ps.last(), buffers, LightTexture.FULL_BRIGHT, ov);
        ps.popPose();

        for (int k = 0; k < 4; k++) {
            float swing = asleep ? 0.0F : Mth.sin(pos * 0.9F + PHASE[k]) * 28.0F * speed;
            float lift = asleep ? 0.0F : Math.max(0.0F, Mth.sin(pos * 0.9F + PHASE[k] + 1.2F)) * 30.0F * speed;
            float splay = asleep ? 8.0F * (LEG_POS[k][0] < 0 ? 1.0F : -1.0F) : 0.0F;
            ps.pushPose();
            turn(ps, "hip_" + LEGS[k], LEG_POS[k][0], HIP_Y, LEG_POS[k][1], swing, 0.0F, splay);
            mesh.draw("hip_" + LEGS[k], ps.last(), buffers, light, ov);
            turn(ps, "knee_" + LEGS[k], LEG_POS[k][0], KNEE_Y, LEG_POS[k][1], -lift, 0.0F, 0.0F);
            mesh.draw("knee_" + LEGS[k], ps.last(), buffers, light, ov);
            ps.popPose();
        }

        float spin = e.spin + e.spinSpeed * pt;
        ps.pushPose();
        turn(ps, "turret", 0.0F, SentryDogEntity.TURRET_Y, SentryDogEntity.GUN_Z, 0.0F, e.shownYaw, 0.0F);
        mesh.draw("turret", ps.last(), buffers, light, ov);
        turn(ps, "gun", 0.0F, SentryDogEntity.GUN_Y, SentryDogEntity.GUN_Z, e.shownPitch, 0.0F, 0.0F);
        mesh.draw("gun", ps.last(), buffers, light, ov);
        if (e.firing() && (e.tickCount & 1) == 0) mesh.draw("flash", ps.last(), buffers, LightTexture.FULL_BRIGHT, ov);
        turn(ps, "barrels", 0.0F, SentryDogEntity.GUN_Y, -2.0F, 0.0F, 0.0F, spin);
        mesh.draw("barrels", ps.last(), buffers, light, ov);
        ps.popPose();
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SentryDogEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
