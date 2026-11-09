package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class SeaMineRenderer implements BlockEntityRenderer<SeaMineBlockEntity> {

    private static final ResourceLocation CABLE = ResourceLocation.withDefaultNamespace("textures/block/gray_concrete.png");
    private static final float CAP_HINGE_X = 6.4F, CAP_HINGE_Y = 31.2F, CAP_OPEN_DEG = -110.0F;

    @Override
    public void render(SeaMineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        int kind = be.kind();
        Direction f = Machines.facing(be.getBlockState());
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        if (kind == SeaMineBlockEntity.K_BOTTOM) {
            MeshModel m = MeshModel.named("mine_bottom");
            if (m.has("base")) m.draw("base", ps.last(), buffers, light, overlay);
            if (m.has("cap")) {
                double since = be.getLevel().getGameTime() + pt - be.lastFire();
                float open = !be.loaded() ? 1.0F : since >= 0 && since < 30 ? (float) Math.sin(Math.PI * Math.min(1.0, since / 15.0) / 2.0) : 0.0F;
                ps.pushPose();
                ps.translate(CAP_HINGE_X / 16.0F, CAP_HINGE_Y / 16.0F, 0.0F);
                ps.mulPose(Axis.ZP.rotationDegrees(CAP_OPEN_DEG * open));
                ps.translate(-CAP_HINGE_X / 16.0F, -CAP_HINGE_Y / 16.0F, 0.0F);
                m.draw("cap", ps.last(), buffers, light, overlay);
                ps.popPose();
            }
            ps.popPose();
            return;
        }
        MeshModel anchor = MeshModel.named("mine_anchor");
        if (anchor.has("base")) anchor.draw("base", ps.last(), buffers, light, overlay);
        double eye = be.eyeY();
        if (Double.isNaN(eye)) { ps.popPose(); return; }
        float top = SeaMineBlockEntity.FAIRLEAD_PX / 16.0F, up = (float) (eye - be.getBlockPos().getY());
        if (be.laidAt() >= 0) {
            double since = be.getLevel().getGameTime() + pt - be.laidAt() - Balance.MINE_LAY_PAYOUT_DELAY;
            up = Math.min(up, top + (float) (Math.max(0.0, since) * Balance.MINE_LAY_PAYOUT));
            eye = be.getBlockPos().getY() + up;
        }
        BlockPos casePos = BlockPos.containing(be.getBlockPos().getX() + 0.5, eye + 0.6, be.getBlockPos().getZ() + 0.5);
        int caseLight = LevelRenderer.getLightColor(be.getLevel(), casePos);
        cable(ps, buffers, top, up, light, caseLight);
        ps.translate(0.0F, up, 0.0F);
        if (kind == SeaMineBlockEntity.K_CAPTOR) {
            MeshModel m = MeshModel.named("mine_captor");
            ps.translate(0.0F, 0.0F, -16.0F / 16.0F);
            if (m.has("base")) m.draw("base", ps.last(), buffers, caseLight, overlay);
            if (m.has("cap") && be.loaded()) m.draw("cap", ps.last(), buffers, caseLight, overlay);
        } else {
            MeshModel m = MeshModel.named("mine_influence");
            ps.translate(-8.0F / 16.0F, 0.0F, -8.0F / 16.0F);
            if (m.has("base")) m.draw("base", ps.last(), buffers, caseLight, overlay);
        }
        ps.popPose();
    }

    private static void cable(PoseStack ps, MultiBufferSource buffers, float y0, float y1, int l0, int l1) {
        if (y1 <= y0 + 0.01F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(CABLE));
        PoseStack.Pose p = ps.last();
        float r = 0.035F, v = 0.5F;
        float[][] faces = { { -r, -r, r, -r, 0, 0, -1 }, { r, -r, r, r, 1, 0, 0 }, { r, r, -r, r, 0, 0, 1 }, { -r, r, -r, -r, -1, 0, 0 } };
        for (float[] q : faces) {
            float ax = q[0], az = q[1], bx = q[2], bz = q[3], nx = q[4], nz = q[6];
            put(vc, p, ax, y0, az, 0.0F, 0.0F, l0, nx, nz);
            put(vc, p, ax, y1, az, 0.0F, v, l1, nx, nz);
            put(vc, p, bx, y1, bz, 0.12F, v, l1, nx, nz);
            put(vc, p, bx, y0, bz, 0.12F, 0.0F, l0, nx, nz);
        }
    }

    private static void put(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, float nx, float nz) {
        vc.addVertex(p, x, y, z).setColor(0.55F, 0.58F, 0.62F, 1.0F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0.0F, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(SeaMineBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 96; }

    @Override
    public AABB getRenderBoundingBox(SeaMineBlockEntity be) {
        BlockPos p = be.getBlockPos();
        double eye = Double.isNaN(be.eyeY()) ? p.getY() + 1 : be.eyeY();
        return new AABB(p).inflate(2.0, 0.0, 2.0).expandTowards(0.0, Math.max(2.0, eye - p.getY() + 2.5), 0.0);
    }
}
