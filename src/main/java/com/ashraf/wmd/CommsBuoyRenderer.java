package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class CommsBuoyRenderer extends EntityRenderer<CommsBuoyEntity> {

    public CommsBuoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(CommsBuoyEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("comms_buoy");
        if (!mesh.has("buoy")) return;
        ps.pushPose();
        ps.translate(-0.5F, -26.0F / 16.0F, -0.5F);
        mesh.draw("buoy", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        if (mesh.has("antenna")) {
            ps.pushPose();
            ps.translate(8.0F / 16.0F, 29.4F / 16.0F, 1.0F);
            if (e.floating()) ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80.0F));
            ps.translate(-8.0F / 16.0F, -29.4F / 16.0F, -1.0F);
            mesh.draw("antenna", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (mesh.has("blink") && e.floating() && (e.tickCount / 10) % 2 == 0) mesh.draw("blink", ps.last(), buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        Vec3 a = e.anchor().subtract(e.getPosition(pt));
        if (a.lengthSqr() > 0.25)
            CcRenderers.line(buffers.getBuffer(RenderType.lines()), ps.last(), 0.0F, -0.35F, 0.0F, (float) a.x, (float) a.y, (float) a.z, 0xFF20262C);
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CommsBuoyEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
