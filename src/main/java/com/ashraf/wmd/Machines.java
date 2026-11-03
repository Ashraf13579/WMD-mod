package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class Machines {

    private Machines() {}

    public static BlockPos toWorld(BlockPos origin, Direction facing, int lx, int ly, int lz) {
        int dx, dz;
        switch (facing) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return origin.offset(dx, ly, dz);
    }

    public static BlockPos toLocal(BlockPos origin, Direction facing, BlockPos world) {
        int dx = world.getX() - origin.getX(), dz = world.getZ() - origin.getZ();
        return switch (facing) {
            case EAST -> new BlockPos(dz, world.getY() - origin.getY(), -dx);
            case SOUTH -> new BlockPos(-dx, world.getY() - origin.getY(), -dz);
            case WEST -> new BlockPos(-dz, world.getY() - origin.getY(), dx);
            default -> new BlockPos(dx, world.getY() - origin.getY(), dz);
        };
    }

    public static boolean atPart(BlockPos origin, Direction facing, BlockPos part, Direction side, int x, int y, int z, Direction localSide) {
        BlockPos l = toLocal(origin, facing, part);
        return l.getX() == x && l.getY() == y && l.getZ() == z && side == dirToWorld(facing, localSide);
    }

    public static Direction dirToWorld(Direction facing, Direction local) {
        if (local.getAxis() == Direction.Axis.Y) return local;
        BlockPos v = toWorld(BlockPos.ZERO, facing, local.getStepX(), 0, local.getStepZ());
        return Direction.fromDelta(v.getX(), 0, v.getZ());
    }

    public static Direction mount(net.minecraft.world.level.block.state.BlockState s) {
        return s != null && s.hasProperty(MachineBlock.MOUNT) ? s.getValue(MachineBlock.MOUNT) : Direction.UP;
    }

    public static Direction facing(net.minecraft.world.level.block.state.BlockState s) {
        return s != null && s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    public static double[] mountTurn(Direction m, double x, double y, double z) {
        return switch (m) {
            case DOWN -> new double[] { x, -y, -z };
            case NORTH -> new double[] { x, z, -y };
            case SOUTH -> new double[] { x, -z, y };
            case EAST -> new double[] { y, -x, z };
            case WEST -> new double[] { -y, x, z };
            default -> new double[] { x, y, z };
        };
    }

    public static double[] mountUnturn(Direction m, double x, double y, double z) {
        return switch (m) {
            case DOWN -> new double[] { x, -y, -z };
            case NORTH -> new double[] { x, -z, y };
            case SOUTH -> new double[] { x, z, -y };
            case EAST -> new double[] { -y, x, z };
            case WEST -> new double[] { y, -x, z };
            default -> new double[] { x, y, z };
        };
    }

    public static net.minecraft.world.phys.Vec3 mountTurn(Direction m, net.minecraft.world.phys.Vec3 v) {
        double[] r = mountTurn(m, v.x, v.y, v.z);
        return new net.minecraft.world.phys.Vec3(r[0], r[1], r[2]);
    }

    public static net.minecraft.world.phys.Vec3 mountUnturn(Direction m, net.minecraft.world.phys.Vec3 v) {
        double[] r = mountUnturn(m, v.x, v.y, v.z);
        return new net.minecraft.world.phys.Vec3(r[0], r[1], r[2]);
    }

    public static BlockPos toWorld(BlockPos origin, Direction facing, Direction mount, int lx, int ly, int lz) {
        if (mount == Direction.UP) return toWorld(origin, facing, lx, ly, lz);
        BlockPos f = toWorld(BlockPos.ZERO, facing, lx, ly, lz);
        double[] r = mountTurn(mount, f.getX(), f.getY(), f.getZ());
        return origin.offset((int) Math.round(r[0]), (int) Math.round(r[1]), (int) Math.round(r[2]));
    }

    public static BlockPos toLocal(BlockPos origin, Direction facing, Direction mount, BlockPos world) {
        if (mount == Direction.UP) return toLocal(origin, facing, world);
        double[] u = mountUnturn(mount, world.getX() - origin.getX(), world.getY() - origin.getY(), world.getZ() - origin.getZ());
        return toLocal(BlockPos.ZERO, facing, new BlockPos((int) Math.round(u[0]), (int) Math.round(u[1]), (int) Math.round(u[2])));
    }

    public static Direction dirToWorld(Direction facing, Direction mount, Direction local) {
        BlockPos v = toWorld(BlockPos.ZERO, facing, mount, local.getStepX(), local.getStepY(), local.getStepZ());
        Direction d = Direction.fromDelta(v.getX(), v.getY(), v.getZ());
        return d == null ? local : d;
    }

    public static boolean atPart(BlockPos origin, Direction facing, Direction mount, BlockPos part, Direction side, int x, int y, int z, Direction localSide) {
        BlockPos l = toLocal(origin, facing, mount, part);
        return l.getX() == x && l.getY() == y && l.getZ() == z && side == dirToWorld(facing, mount, localSide);
    }

    public static net.minecraft.world.phys.Vec3 mountPoint(BlockPos origin, Direction mount, double fx, double fy, double fz) {
        double[] r = mountTurn(mount, fx, fy - 0.5, fz);
        return new net.minecraft.world.phys.Vec3(origin.getX() + 0.5 + r[0], origin.getY() + 0.5 + r[1], origin.getZ() + 0.5 + r[2]);
    }

    public static net.minecraft.world.phys.shapes.VoxelShape mountShape(net.minecraft.world.phys.shapes.VoxelShape s, Direction mount) {
        if (mount == Direction.UP || s.isEmpty()) return s;
        net.minecraft.world.phys.shapes.VoxelShape[] turned = MOUNTED.computeIfAbsent(s, k -> new net.minecraft.world.phys.shapes.VoxelShape[6]);
        net.minecraft.world.phys.shapes.VoxelShape t = turned[mount.ordinal()];
        if (t == null) turned[mount.ordinal()] = t = turnShape(s, mount);
        return t;
    }

    private static final java.util.Map<net.minecraft.world.phys.shapes.VoxelShape, net.minecraft.world.phys.shapes.VoxelShape[]> MOUNTED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static net.minecraft.world.phys.shapes.VoxelShape turnShape(net.minecraft.world.phys.shapes.VoxelShape s, Direction mount) {
        net.minecraft.world.phys.shapes.VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        for (net.minecraft.world.phys.AABB b : s.toAabbs()) {
            double[] a = mountTurn(mount, b.minX - 0.5, b.minY - 0.5, b.minZ - 0.5), c = mountTurn(mount, b.maxX - 0.5, b.maxY - 0.5, b.maxZ - 0.5);
            out = net.minecraft.world.phys.shapes.Shapes.or(out, net.minecraft.world.phys.shapes.Shapes.box(Math.min(a[0], c[0]) + 0.5, Math.min(a[1], c[1]) + 0.5,
                    Math.min(a[2], c[2]) + 0.5, Math.max(a[0], c[0]) + 0.5, Math.max(a[1], c[1]) + 0.5, Math.max(a[2], c[2]) + 0.5));
        }
        return out.optimize();
    }
}
