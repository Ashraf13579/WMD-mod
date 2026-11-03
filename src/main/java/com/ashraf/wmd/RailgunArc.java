package com.ashraf.wmd;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RailgunArc {

    private RailgunArc() {}

    public static double apex(Vec3 m, Vec3 t) {
        double d = Math.hypot(t.x - m.x, t.z - m.z);
        return Mth.clamp(d * Balance.RG_ARC_APEX_PER_BLOCK, Balance.RG_ARC_APEX_MIN, Balance.RG_ARC_APEX_MAX) + Math.max(0.0, Math.max(m.y, t.y) - Math.min(m.y, t.y)) * 0.5;
    }

    public static Vec3 at(Vec3 m, Vec3 t, double f) {
        f = Mth.clamp(f, 0.0, 1.0);
        double u = 1.0 - (1.0 - f) * (1.0 - f);
        double h = apex(m, t);
        return new Vec3(m.x + (t.x - m.x) * u, m.y + (t.y - m.y) * f + 4.0 * h * f * (1.0 - f), m.z + (t.z - m.z) * u);
    }

    public static Vec3 dir(Vec3 m, Vec3 t, double f) {
        Vec3 a = at(m, t, Math.max(0.0, f - 0.002)), b = at(m, t, Math.min(1.0, f + 0.002));
        Vec3 d = b.subtract(a);
        return d.lengthSqr() < 1.0E-9 ? new Vec3(0, -1, 0) : d.normalize();
    }
}
