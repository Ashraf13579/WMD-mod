package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ShipFrame {

    private ShipFrame() {}

    public static boolean onShip(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isClientSide || !SableBridge.active()) return false;
        try { return SableClient.pose(level, pos, Float.NaN) != null; }
        catch (Throwable t) { SableBridge.fail(t); return false; }
    }

    private static Vec3 toShip(Level level, BlockPos pos, Vec3 dir, float pt) {
        if (level == null || pos == null || !level.isClientSide || !SableBridge.active()) return null;
        try { return SableClient.dirToShip(level, pos, dir, pt); }
        catch (Throwable t) { SableBridge.fail(t); return null; }
    }

    public static double[] modelAim(Level level, BlockPos pos, Direction facing, double az, double el, float pt) {
        if (level == null || !level.isClientSide || !SableBridge.active()) return null;
        double a = Math.toRadians(az), e = Math.toRadians(el), lx = Math.sin(a), lz = Math.cos(a), dx, dz;
        switch (facing) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        double ce = Math.cos(e);
        Vec3 s = toShip(level, pos, new Vec3(dx * ce, Math.sin(e), dz * ce), pt);
        if (s == null) return null;
        double h = Math.hypot(s.x, s.z);
        double az2 = h < 1.0E-6 ? az : LauncherGeometry.azimuthFor(facing, s.x, s.z);
        return new double[] { az2, Math.toDegrees(Math.atan2(s.y, h)) };
    }

    public static double[] modelAim(Level level, BlockPos pos, Direction facing, Direction mount, double az, double el, float pt) {
        if (mount == Direction.UP) {
            double[] r = modelAim(level, pos, facing, az, el, pt);
            return r == null ? new double[] { az, el } : r;
        }
        double a = Math.toRadians(az), e = Math.toRadians(el), lx = Math.sin(a), lz = Math.cos(a), dx, dz;
        switch (facing) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        double ce = Math.cos(e);
        Vec3 s = Machines.mountUnturn(mount, dirInFrame(level, pos, new Vec3(dx * ce, Math.sin(e), dz * ce), pt));
        double h = Math.hypot(s.x, s.z);
        double az2 = h < 1.0E-6 ? az : LauncherGeometry.azimuthFor(facing, s.x, s.z);
        return new double[] { az2, Math.toDegrees(Math.atan2(s.y, h)) };
    }

    public static double[] compassAim(Level level, BlockPos pos, double bearing, double el, float pt) {
        if (level == null || !level.isClientSide || !SableBridge.active()) return null;
        double b = Math.toRadians(bearing), e = Math.toRadians(el), ce = Math.cos(e);
        Vec3 s = toShip(level, pos, new Vec3(Math.sin(b) * ce, Math.sin(e), -Math.cos(b) * ce), pt);
        if (s == null) return null;
        double h = Math.hypot(s.x, s.z);
        double b2 = h < 1.0E-6 ? bearing : Math.toDegrees(Math.atan2(s.x, -s.z));
        if (b2 < 0.0) b2 += 360.0;
        return new double[] { b2, Math.toDegrees(Math.atan2(s.y, h)) };
    }

    public static Vec3 dirInFrame(Level level, BlockPos pos, Vec3 dir, float pt) {
        Vec3 s = toShip(level, pos, dir, pt);
        return s == null ? dir : s;
    }

    public static Vec3 drawnWorld(Level level, BlockPos pos, Vec3 p, float pt) {
        if (level == null || pos == null || p == null || !level.isClientSide || !SableBridge.active()) return p;
        try {
            Vec3 w = SableClient.toWorld(level, pos, p, pt);
            return w == null ? p : w;
        } catch (Throwable t) { SableBridge.fail(t); return p; }
    }
}
