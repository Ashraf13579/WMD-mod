package com.ashraf.wmd;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

public class BombRenderer extends EntityRenderer<BombEntity> {

    private static final float WING_X = 0.0F, WING_Y = 22.0F / 16.0F, WING_Z = (4.8F + 0.9F) / 16.0F, WING_OPEN = 70.0F, WING_TICKS = 8.0F, BOMBLET_SCALE = 0.2F;

    public BombRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    static final String[] WHOLE = { "part", "band", "fuze", "tail" };

    static void drawWhole(MeshModel mesh, PoseStack.Pose pose, MultiBufferSource buffers, int light) {
        for (String b : WHOLE) mesh.draw(b, pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public boolean shouldRender(BombEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return e.type() == BombType.TSAR || super.shouldRender(e, f, x, y, z); }

    @Override
    public void render(BombEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        BombType t = e.type();
        MeshModel mesh = MeshModel.named(e.isDud() ? BombType.CLUSTER.mesh : t.mesh);
        boolean stand = false;
        if (t == BombType.TSAR && !mesh.has("part")) { mesh = MeshModel.named(BombType.TALLBOY.mesh); stand = true; }
        if (!mesh.has("part")) { super.render(e, entityYaw, pt, ps, buffers, light); return; }
        ps.pushPose();
        if (t == BombType.TSAR) {
            float age = e.age() + pt, open = Math.max(0.0F, Math.min(1.0F, (age - Balance.TSAR_CHUTE_DELAY) / 30.0F));
            if (open > 0.0F) {
                MeshModel ch = MeshModel.named("tsar_chutes");
                if (ch.has("part")) {
                    ps.pushPose();
                    ps.translate(0.0F, t.length / 32.0F + 0.5F, 0.0F);
                    ps.scale(open, 0.3F + 0.7F * open, open);
                    drawWhole(ch, ps.last(), buffers, light);
                    ps.popPose();
                }
                ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(age * 0.05F) * 4.0F * open));
            }
            if (stand) ps.scale(1.23F, 1.23F, 1.23F);
        }
        if (e.isDud()) {
            ps.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
            ps.translate(0.0F, 0.06F, 0.0F);
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.scale(BOMBLET_SCALE, BOMBLET_SCALE, BOMBLET_SCALE);
            ps.translate(0.0F, -t.length / 32.0F, 0.0F);
            drawWhole(mesh, ps.last(), buffers, light);
            ps.popPose();
            return;
        }
        Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (d.lengthSqr() < 1.0E-8) d = new Vec3(0, -1, 0);
        ps.mulPose(frame(d.normalize()));
        if (t == BombType.NAPALM) ps.mulPose(Axis.XP.rotationDegrees((e.age() + pt) * 23.0F));
        if (t == BombType.BOUNCING) {
            ps.mulPose(Axis.XP.rotationDegrees(-(e.age() + pt) * 30.0F));
        }
        ps.translate(0.0F, -t.length / 32.0F, 0.0F);
        drawWhole(mesh, ps.last(), buffers, light);
        if (t == BombType.GLIDE) {
            float open = Math.max(0.0F, Math.min(1.0F, (e.age() + pt - Balance.GLIDE_WINGS) / WING_TICKS));
            wing(mesh, "wing_l", -WING_OPEN * open, ps, buffers, light);
            wing(mesh, "wing_r", WING_OPEN * open, ps, buffers, light);
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static void wing(MeshModel mesh, String bone, float deg, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!mesh.has(bone)) return;
        ps.pushPose();
        ps.translate(WING_X, WING_Y, WING_Z);
        ps.mulPose(Axis.ZP.rotationDegrees(deg));
        ps.translate(-WING_X, -WING_Y, -WING_Z);
        mesh.draw(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    static Quaternionf frame(Vec3 f) {
        Vector3f y = new Vector3f((float) f.x, (float) f.y, (float) f.z);
        Vector3f up = Math.abs(f.y) > 0.98 ? new Vector3f(0, 0, f.y > 0 ? -1 : 1) : new Vector3f(0, 1, 0);
        Vector3f z = new Vector3f(up).sub(new Vector3f(y).mul(y.dot(up))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    @Override
    public ResourceLocation getTextureLocation(BombEntity e) { return InventoryMenu.BLOCK_ATLAS; }
}
