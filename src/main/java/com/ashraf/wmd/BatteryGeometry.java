package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class BatteryGeometry {

    private BatteryGeometry() {}

    public static final double TX = 0.0, TZ = 64.0, PY = 92.0, PZ = 64.0, E0 = 72.0, BOT = 100.0, TOP = 262.0;
    public static final double[] COLS = { -21.0, 21.0 }, ROWS = { -21.0, 21.0 };
    public static final double LUG_Y = 30.0, LUG_Z = 34.0, B0_Y = 44.0, B0_Z = 108.0, LR = 66.0;

    public static final double SCALE = 0.45, MISSILE_PX = 241.5 * SCALE;

    public static final double F = 0.65;

    public static final double REST_Y = BOT + 2.0 + MISSILE_PX / 2.0;
    public static final double MIN_EL = 25.0, MAX_EL = 88.0, STOW_EL = 25.0, READY_EL = 72.0;

    public static double[] packPoint(double x, double y, double z, double elevation, double azimuth) {
        double d = Math.toRadians(90.0 - elevation);
        double ry = y - PY, rz = z - PZ;
        double y1 = PY + ry * Math.cos(d) - rz * Math.sin(d);
        double z1 = PZ + ry * Math.sin(d) + rz * Math.cos(d);
        double a = Math.toRadians(azimuth);
        double dx = x - TX, dz = z1 - TZ;
        return new double[] { TX + dx * Math.cos(a) + dz * Math.sin(a), y1, TZ - dx * Math.sin(a) + dz * Math.cos(a) };
    }

    public static double[] tubeCentre(int tube, double elevation, double azimuth) {
        return packPoint(COLS[tube % 2], REST_Y, PZ + ROWS[tube / 2], elevation, azimuth);
    }

    public static Vec3 toWorld(BlockPos controller, Direction facing, double[] p) {
        double lx = p[0] / 16.0 * F, lz = p[2] / 16.0 * F, dx, dz;
        switch (facing) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return new Vec3(controller.getX() + 0.5 + dx, controller.getY() + p[1] / 16.0 * F, controller.getZ() + 0.5 + dz);
    }

    public static double azimuthFor(Direction facing, double dx, double dz) {
        double lx, lz;
        switch (facing) {
            case EAST -> { lx = dz; lz = -dx; }
            case SOUTH -> { lx = -dx; lz = -dz; }
            case WEST -> { lx = -dz; lz = dx; }
            default -> { lx = dx; lz = dz; }
        }
        return Math.toDegrees(Math.atan2(lx, lz));
    }

    public static double[] ram(double elevation) {
        double d = Math.toRadians(90.0 - elevation);
        double ay = PY + LUG_Y * Math.cos(d) - LUG_Z * Math.sin(d);
        double az = PZ + LUG_Y * Math.sin(d) + LUG_Z * Math.cos(d);
        double vy = ay - B0_Y, vz = az - B0_Z;
        return new double[] { Math.toDegrees(Math.atan2(vz, vy)), Math.hypot(vy, vz) };
    }

    public static double wrap(double deg) { return deg - 360.0 * Math.floor((deg + 180.0) / 360.0); }
}
