package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class MountRender {

    private MountRender() {}

    public static void apply(PoseStack ps, BlockState state) {
        Direction m = Machines.mount(state);
        if (m == Direction.UP) return;
        ps.translate(0.5, 0.5, 0.5);
        switch (m) {
            case NORTH -> ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case SOUTH -> ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(180.0F));
            case EAST -> ps.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            case WEST -> ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
            default -> {}
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    public static net.minecraft.world.phys.AABB box(net.minecraft.core.BlockPos pos, int reach) {
        return new net.minecraft.world.phys.AABB(pos).inflate(reach);
    }
}
