package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class LaserTripodRenderer implements BlockEntityRenderer<LaserTripodBlockEntity> {

    private static final String NAME = "laser_tripod";
    private static final float[] PAN = { 0.0F, 20.4F, 1.0F }, TILT = { 0.0F, 22.3F, 1.0F };

    private static final float EX = -1.2F, EY = 25.6F, EZ = -7.9F, CORE = 0.045F, GLOW = 0.16F, DOT = 0.22F, HALO = 0.6F;

    @Override
    public void render(LaserTripodBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        float face = switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; };

        Vec3 d = Machines.mountUnturn(Machines.mount(be.getBlockState()),
                ShipFrame.dirInFrame(be.getLevel(), be.getBlockPos(), LaserTripodBlockEntity.dir(be.yaw(), be.pitch()), pt));
        float ly = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), lp = (float) -Math.toDegrees(Math.asin(net.minecraft.util.Mth.clamp(d.y, -1.0, 1.0)));
        float pan = 180.0F - ly - face, tilt = net.minecraft.util.Mth.clamp(-lp, -80.0F, 80.0F);
        boolean scoped = be.getBlockPos().equals(ClientTripod.active());
        ps.pushPose();

        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(face));
        if (!scoped && mesh.has("base")) mesh.draw("base", ps.last(), buffers, light, overlay);

        ps.pushPose();
        ps.translate(PAN[0] / 16.0F, PAN[1] / 16.0F, PAN[2] / 16.0F);
        ps.mulPose(Axis.YP.rotationDegrees(pan));
        ps.translate(-PAN[0] / 16.0F, -PAN[1] / 16.0F, -PAN[2] / 16.0F);
        if (!scoped && mesh.has("pan")) mesh.draw("pan", ps.last(), buffers, light, overlay);
        ps.translate(TILT[0] / 16.0F, TILT[1] / 16.0F, TILT[2] / 16.0F);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.translate(-TILT[0] / 16.0F, -TILT[1] / 16.0F, -TILT[2] / 16.0F);
        if (!scoped && mesh.has("tilt")) mesh.draw("tilt", ps.last(), buffers, light, overlay);
        if (!scoped && mesh.has("emit")) mesh.draw("emit", ps.last(), buffers, be.lasing() ? LightTexture.FULL_BRIGHT : light, overlay);
        if (be.lasing() && be.beamLen() > 0.5) beam(buffers.getBuffer(RenderType.lightning()), ps.last().pose(), (float) be.beamLen());
        ps.popPose();
        ps.popPose();
    }

    private static void beam(VertexConsumer vc, Matrix4f m, float len) {
        float x = EX / 16.0F, y = EY / 16.0F, z0 = EZ / 16.0F, z1 = z0 - len;
        strip(vc, m, x, y, z0, z1, GLOW, 255, 30, 20, 70);
        strip(vc, m, x, y, z0, z1, CORE, 255, 90, 70, 235);
        float zd = z1 + 0.05F;
        square(vc, m, x, y, zd, HALO, 'z', 255, 40, 30, 90);
        square(vc, m, x, y, zd, HALO, 'x', 255, 40, 30, 90);
        square(vc, m, x, y, zd, HALO, 'y', 255, 40, 30, 90);
        square(vc, m, x, y, zd, DOT, 'z', 255, 200, 180, 255);
        square(vc, m, x, y, zd, DOT, 'x', 255, 200, 180, 255);
        square(vc, m, x, y, zd, DOT, 'y', 255, 200, 180, 255);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, float x, float y, float z0, float z1, float h, int r, int g, int b, int a) {
        quad(vc, m, x - h, y, z0, x + h, y, z0, x + h, y, z1, x - h, y, z1, r, g, b, a);
        quad(vc, m, x, y - h, z0, x, y + h, z0, x, y + h, z1, x, y - h, z1, r, g, b, a);
    }

    private static void square(VertexConsumer vc, Matrix4f m, float x, float y, float z, float h, char axis, int r, int g, int b, int a) {
        switch (axis) {
            case 'x' -> quad(vc, m, x, y - h, z - h, x, y + h, z - h, x, y + h, z + h, x, y - h, z + h, r, g, b, a);
            case 'y' -> quad(vc, m, x - h, y, z - h, x + h, y, z - h, x + h, y, z + h, x - h, y, z + h, r, g, b, a);
            default -> quad(vc, m, x - h, y - h, z, x + h, y - h, z, x + h, y + h, z, x - h, y + h, z, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             int r, int g, int b, int a) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }

    @Override public AABB getRenderBoundingBox(LaserTripodBlockEntity be) { return AABB.INFINITE; }

    @Override public boolean shouldRenderOffScreen(LaserTripodBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 256; }
}
