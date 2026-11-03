package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

public class MineRenderer implements BlockEntityRenderer<MineBlockEntity> {

    @Override
    public void render(MineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        boolean claymore = be.getBlockState().getBlock() instanceof MineBlock m && m.claymore;
        MeshModel mesh = MeshModel.named(claymore ? "claymore" : "pressure_mine");
        if (!mesh.has("base") || be.getLevel() == null) return;
        Direction f = Machines.facing(be.getBlockState());
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        long age = be.age(be.getLevel());
        boolean lit = age >= 0 && (age < Balance.MINE_ARM_TICKS ? age % 6 < 3 : age % 30 < 2);
        if (lit && mesh.has("armed")) mesh.draw("armed", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        if (claymore) cone(be, ps, buffers);
        ps.popPose();
    }

    private static void cone(MineBlockEntity be, PoseStack ps, MultiBufferSource buffers) {
        var p = Minecraft.getInstance().player;
        if (p == null || !p.isShiftKeyDown() || be.getOwner() == null || !be.getOwner().equals(p.getUUID())) return;
        if (p.distanceToSqr(be.getBlockPos().getCenter()) > 256.0) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        float len = Balance.CLAY_TRIP, t = (float) Math.tan(Math.toRadians(Balance.CLAY_TRIP_DEG)), y0 = 6.5F / 16.0F;
        float[][] ends = { { 0, 0 }, { t, 0 }, { -t, 0 }, { 0, t }, { 0, -t } };
        for (float[] e : ends) {
            float x = e[0] * len, y = y0 + e[1] * len, z = -len;
            float nx = x, ny = y - y0, nz = z, n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            vc.addVertex(pose, 0.0F, y0, -0.1F).setColor(1.0F, 0.15F, 0.1F, 0.9F).setNormal(pose, nx / n, ny / n, nz / n);
            vc.addVertex(pose, x, y, z).setColor(1.0F, 0.15F, 0.1F, 0.15F).setNormal(pose, nx / n, ny / n, nz / n);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(MineBlockEntity be) { return true; }
}
