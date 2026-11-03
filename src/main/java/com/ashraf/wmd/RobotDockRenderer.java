package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class RobotDockRenderer implements BlockEntityRenderer<RobotDockBlockEntity> {

    private static final String NAME = "robot_dock";
    private static final float[] ARM = { -8.0F, 27.0F, 124.0F }, GLOW = { -8.0F, 16.0F, 64.0F };

    @Override
    public void render(RobotDockBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("base")) return;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        if (mesh.has("arm")) SatelliteMesh.drawBone(mesh, NAME, "arm", ARM, -35.0F * (1.0F - be.armDown()), 0.0F, 0.0F, ps, buffers, light, overlay);
        if (mesh.has("glow")) SatelliteMesh.drawBone(mesh, NAME, "glow", GLOW, 0.0F, 0.0F, 0.0F, ps, buffers, LightTexture.FULL_BRIGHT, overlay);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RobotDockBlockEntity be) { return new AABB(be.getBlockPos()).inflate(10.0, 3.0, 10.0); }

    @Override
    public int getViewDistance() { return 128; }
}
