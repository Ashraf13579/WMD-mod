package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class GeneratorStructure {

    public static final int LENGTH = 5, DEPTH = 2, HEIGHT = 3;

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 2, Z0 = 0, Z1 = 1;

    public static final int FUEL_X = 2, FUEL_Y = 0, FUEL_Z = 0;

    public static final int POWER_X = -2, POWER_Y = 0, POWER_Z = 0;

    public static final int MODEL_Y = 1;

    private GeneratorStructure() {}

    public static boolean inside(BlockPos local) {
        return local.getX() >= X0 && local.getX() <= X1 && local.getY() >= Y0 && local.getY() <= Y1 && local.getZ() >= Z0 && local.getZ() <= Z1;
    }

    public static BlockPos powerPos(BlockPos controller, Direction facing) { return powerPos(controller, facing, Direction.UP); }
    public static BlockPos fuelPos(BlockPos controller, Direction facing) { return fuelPos(controller, facing, Direction.UP); }

    public static BlockPos powerPos(BlockPos controller, Direction facing, Direction mount) { return Machines.toWorld(controller, facing, mount, POWER_X, POWER_Y, POWER_Z); }
    public static BlockPos fuelPos(BlockPos controller, Direction facing, Direction mount) { return Machines.toWorld(controller, facing, mount, FUEL_X, FUEL_Y, FUEL_Z); }

    public static Vec3 modelToWorld(BlockPos controller, Direction facing, double x, double y, double z) {
        Direction right = facing.getCounterClockWise();
        Direction inward = facing.getOpposite();
        return new Vec3(
                controller.getX() + 0.5 - right.getStepX() * x + inward.getStepX() * z,
                controller.getY() + MODEL_Y + y,
                controller.getZ() + 0.5 - right.getStepZ() * x + inward.getStepZ() * z);
    }
}
