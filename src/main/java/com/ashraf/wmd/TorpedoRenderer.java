package com.ashraf.wmd;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class TorpedoRenderer extends EntityRenderer<TorpedoEntity> {

    static final float AXIS_Y = 6.0F / 16.0F;

    public TorpedoRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(TorpedoEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        TorpedoKind k = e.kind();
        MeshModel mesh = MeshModel.named(k.mesh());
        if (!mesh.has("part")) return;
        Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (d.lengthSqr() < 1.0E-8) d = e.getDeltaMovement();
        if (d.lengthSqr() < 1.0E-8) d = new Vec3(0, 0, -1);
        ps.pushPose();
        ps.mulPose(frame(d.normalize()));
        ps.translate(0.0F, -k.axisPx() / 16.0F, 0.0F);
        draw(mesh, k, ps.last(), buffers, light);
        if (k == TorpedoKind.UUV && mesh.has("prop")) {
            ps.translate(0.0F, 4.4F / 16.0F, 16.6F / 16.0F);
            ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((e.tickCount + pt) * 40.0F));
            ps.translate(0.0F, -4.4F / 16.0F, -16.6F / 16.0F);
            mesh.draw("prop", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    static void draw(MeshModel mesh, TorpedoKind k, PoseStack.Pose pose, MultiBufferSource buffers, int light) {
        if (!k.tinted()) { mesh.draw("part", pose, buffers, light, OverlayTexture.NO_OVERLAY); return; }
        int[] t = MissileParts.tint(k.paint);
        mesh.drawTinted("part", pose, buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
    }

    static Quaternionf frame(Vec3 f) {
        Vector3f z = new Vector3f((float) -f.x, (float) -f.y, (float) -f.z);
        Vector3f up = Math.abs(f.y) > 0.98 ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f y = new Vector3f(up).sub(new Vector3f(z).mul(z.dot(up))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    @Override
    public ResourceLocation getTextureLocation(TorpedoEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
