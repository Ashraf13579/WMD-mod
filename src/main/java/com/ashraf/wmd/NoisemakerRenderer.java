package com.ashraf.wmd;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class NoisemakerRenderer extends EntityRenderer<NoisemakerEntity> {

    static final float HALF = 9.6F / 16.0F;

    public NoisemakerRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(NoisemakerEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("noisemaker");
        if (!mesh.has("part")) return;
        ps.pushPose();
        Vec3 d = e.launchDir();
        float t = (e.tickCount + pt) / NoisemakerEntity.TIP_TICKS;
        if (d.lengthSqr() > 1e-4 && t < 1.0F) {
            float s = t * t * (3.0F - 2.0F * t);
            Vec3 up = d.scale(-1.0).normalize().scale(1.0 - s).add(0.0, s, 0.0);
            if (up.lengthSqr() < 1e-6) up = new Vec3(0, 1, 0);
            up = up.normalize();
            ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) up.x, (float) up.y, (float) up.z));
        } else {
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees((e.getId() * 47) % 360));
        }
        ps.translate(0.0F, -HALF, 0.0F);
        mesh.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(NoisemakerEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
