package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

public class DriveCouplingRenderer implements BlockEntityRenderer<DriveCouplingBlockEntity> {

    @Override
    public void render(DriveCouplingBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("drive_coupling");
        if (!mesh.has("base")) return;
        Direction f = be.facing();
        float yRot = switch (f) { case SOUTH -> 180.0F; case EAST -> -90.0F; case WEST -> 90.0F; default -> 0.0F; };
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        if (be.running() && mesh.has("lamp")) mesh.draw("lamp", ps.last(), buffers, light, overlay);
        float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), f.getAxis());
        float sign = f == Direction.NORTH || f == Direction.WEST ? 1.0F : -1.0F;
        ps.translate(0.0, 0.5, 0.0);
        ps.mulPose(Axis.ZP.rotation(angle * sign));
        ps.translate(0.0, -0.5, 0.0);
        if (mesh.has("shaft")) mesh.draw("shaft", ps.last(), buffers, light, overlay);
        ps.popPose();
    }
}
