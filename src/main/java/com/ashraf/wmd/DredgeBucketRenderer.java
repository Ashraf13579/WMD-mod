package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class DredgeBucketRenderer extends EntityRenderer<DredgeBucketEntity> {

    private static final float PX = 1.0F / 16.0F, OPEN_DEG = 30.0F;

    public DredgeBucketRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(DredgeBucketEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("dredge_winch");
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
        Vec3 top = e.top();
        if (top.lengthSqr() > 1.0E-6)
            CcRenderers.line(buffers.getBuffer(RenderType.lines()), ps.last(), 0.0F, 0.0F, 0.0F, (float) (top.x - ex), (float) (top.y - ey), (float) (top.z - ez), 0xFF2A2F36);
        if (!mesh.has("bucket")) return;
        int ov = OverlayTexture.NO_OVERLAY;
        float open = OPEN_DEG * Mth.clamp(e.jaw(), 0.0F, 1.0F);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - e.getYRot()));
        ps.translate(-8.0F * PX, -DredgeWinchBlockEntity.BUCKET_Y * PX, 5.0F * PX);
        mesh.draw("bucket", ps.last(), buffers, light, ov);
        jaw(mesh, "jaw_w", 7.8F, -open, ps, buffers, light, ov);
        jaw(mesh, "jaw_e", 8.2F, open, ps, buffers, light, ov);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static void jaw(MeshModel mesh, String bone, float px, float deg, PoseStack ps, MultiBufferSource buffers, int light, int ov) {
        if (!mesh.has(bone)) return;
        ps.pushPose();
        ps.translate(px * PX, 22.4F * PX, -5.0F * PX);
        ps.mulPose(Axis.ZP.rotationDegrees(deg));
        ps.translate(-px * PX, -22.4F * PX, 5.0F * PX);
        mesh.draw(bone, ps.last(), buffers, light, ov);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DredgeBucketEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
