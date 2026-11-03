package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

public class LaserReceiverRenderer implements BlockEntityRenderer<LaserReceiverBlockEntity> {

    private static final String NAME = "laser_receiver";

    @Override
    public void render(LaserReceiverBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("base")) return;
        ps.pushPose();

        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        if (mesh.has("glow")) mesh.draw("glow", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        boolean alert = be.getBlockState().hasProperty(LaserReceiverBlock.ALERT) && be.getBlockState().getValue(LaserReceiverBlock.ALERT);
        boolean blink = be.getLevel() != null && (be.getLevel().getGameTime() / 6) % 2 == 0;
        if (alert && blink && mesh.has("alert")) mesh.draw("alert", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        ps.popPose();
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(LaserReceiverBlockEntity be) { return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(2.0); }
}
