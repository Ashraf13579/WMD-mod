package com.ashraf.wmd;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PadCrew {

    private PadCrew() {}

    public static final double PX = 1.0 / 16.0;
    public static final double PT = 48.0, CZ = 128.0, CORE_TOP = 360.0;
    public static final double SX = -68.0, SZ = 180.0, R_CAGE = 9.5, CAR_Y0 = 2.0, FLOOR = 369.0;
    public static final double LIFT_TRAVEL = FLOOR - PT - CAR_Y0 - 1.5;
    public static final double D = Math.hypot(SX, CZ - SZ);

    public static final double ARM_YAW = Math.toDegrees(Math.atan2(-(CZ - SZ), -SX)), DOOR_PHI = Math.toDegrees(Math.atan2(CZ - SZ, -SX));

    public static final double HATCH_A = 45.0, HATCH_Y = 18.0;
    public static final double CAP_YAW = HATCH_A - Math.toDegrees(Math.atan2(SZ - CZ, SX));
    public static final double ARM_SWING = 70.0, XW1 = D - 24.0;

    public static final int ENTER = 225, BUSY = 310;

    static double ease(double x) { x = Mth.clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

    public static double liftY(double t) {
        if (t < 40 || t >= BUSY) return 0.0;
        if (t < 110) return LIFT_TRAVEL * ease((t - 40) / 70.0);
        if (t < 240) return LIFT_TRAVEL;
        return LIFT_TRAVEL * (1.0 - ease((t - 240) / 70.0));
    }

    public static double doors(double t) {
        if (t < 0) return 0.0;
        if (t < 40) return t < 10 ? t / 10.0 : t < 30 ? 1.0 : 1.0 - (t - 30) / 10.0;
        if (t >= 110 && t < 160) return t < 120 ? (t - 110) / 10.0 : t < 150 ? 1.0 : 1.0 - (t - 150) / 10.0;
        return 0.0;
    }

    public static double hatch(double t) {
        if (t < 180 || t >= 240) return 0.0;
        if (t < 195) return ease((t - 180) / 15.0);
        if (t < ENTER) return 1.0;
        return 1.0 - ease((t - ENTER) / 15.0);
    }

    static double[] yrot(double x, double z, double a) {
        double c = Math.cos(Math.toRadians(a)), s = Math.sin(Math.toRadians(a));
        return new double[] { x * c + z * s, -x * s + z * c };
    }

    public static Vec3 arm(double x, double y, double z) {
        double[] r = yrot(x, z, ARM_YAW);
        return new Vec3(SX + r[0], y, SZ + r[1]);
    }

    static double rCone(double y) { return 22.0 + (9.5 - 22.0) * (y - 3.0) / 31.0; }

    public static Vec3 capsule(double a, double y, double r) {
        double[] p = yrot(r * Math.cos(Math.toRadians(a)), r * Math.sin(Math.toRadians(a)), CAP_YAW);
        return new Vec3(p[0], CORE_TOP + y, CZ + p[1]);
    }

    public static Vec3 hatchSpot() { Vec3 h = capsule(HATCH_A, HATCH_Y - 6.0, rCone(HATCH_Y - 6.0) - 2.0); return new Vec3(h.x, FLOOR, h.z); }

    public static double[] puppet(double t) {
        if (t < 0 || t >= ENTER) return null;
        double ux = Math.cos(Math.toRadians(DOOR_PHI)), uz = Math.sin(Math.toRadians(DOOR_PHI));
        double x, y, z, dx, dz, walk = 1.0, crouch = 0.0;
        if (t < 30) {
            double f = ease(t / 30.0);
            x = SX + ux * 22.0 * (1.0 - f); z = SZ + uz * 22.0 * (1.0 - f); y = PT + (f > 0.55 ? CAR_Y0 + 1.5 : 0.0);
            dx = -ux; dz = -uz;
        } else if (t < 120) {
            x = SX; z = SZ; y = PT + CAR_Y0 + 1.5 + liftY(t); dx = ux; dz = uz; walk = 0.0;
        } else if (t < 180) {
            double f = (t - 120) / 60.0;
            Vec3 p = arm((XW1 - 6.0) * f, FLOOR, 0.0);
            x = p.x; y = FLOOR; z = p.z; dx = ux; dz = uz;
        } else if (t < 195) {
            Vec3 p = arm(XW1 - 6.0, FLOOR, 0.0);
            x = p.x; y = FLOOR; z = p.z; dx = ux; dz = uz; walk = 0.0;
        } else {
            double f = ease((t - 195) / 30.0);
            Vec3 a = arm(XW1 - 6.0, FLOOR, 0.0), h = hatchSpot();
            x = a.x + (h.x - a.x) * f; y = FLOOR; z = a.z + (h.z - a.z) * f; dx = h.x - a.x; dz = h.z - a.z; crouch = 1.0; walk = f < 0.95 ? 1.0 : 0.0;
        }
        return new double[] { x, y, z, Math.toDegrees(Math.atan2(-dx, dz)), walk, crouch };
    }

    public static Vec3 world(Vec3 o, double rot, double x, double y, double z) {
        double[] r = yrot(x, z, rot);
        return new Vec3(o.x + r[0] * PX, o.y + y * PX, o.z + r[1] * PX);
    }

    public static Vec3 origin(LaunchTerminalBlockEntity be) { return new Vec3(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY(), be.getBlockPos().getZ() + 0.5); }
    public static double rot(LaunchTerminalBlockEntity be) { return 180.0 - be.getFacing().toYRot(); }

    public static Vec3 foot(LaunchTerminalBlockEntity be) {
        double ux = Math.cos(Math.toRadians(DOOR_PHI)), uz = Math.sin(Math.toRadians(DOOR_PHI));
        return world(origin(be), rot(be), SX + ux * 22.0, PT, SZ + uz * 22.0);
    }

    public static Vec3 shaft(LaunchTerminalBlockEntity be) { return world(origin(be), rot(be), SX, PT, SZ); }
}
