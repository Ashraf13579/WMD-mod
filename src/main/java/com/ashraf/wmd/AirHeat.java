package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AirHeat {

    private AirHeat() {}

    private static final Map<ResourceKey<Level>, Map<UUID, double[]>> HEAT = Statics.server(new HashMap<>());

    public static void report(ServerLevel level, UUID ship, double f, long now, Vec3 exhaust) {
        if (ship == null || !(f > 0.0)) return;
        Map<UUID, double[]> m = HEAT.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        double[] e = m.get(ship);
        double until = now + Balance.PROP_BEAT * 3L;
        if (e == null || e[0] < now || e[1] <= f) {
            Vec3 x = exhaust == null || exhaust.lengthSqr() < 1.0E-8 ? Vec3.ZERO : exhaust.normalize();
            m.put(ship, new double[] { until, f, x.x, x.y, x.z });
        }
        if (m.size() > 512) m.values().removeIf(x -> x[0] < now);
    }

    public static double factor(ServerLevel level, UUID ship, long now) {
        double[] e = fresh(level, ship, now);
        return e == null ? Balance.HEAT_SIG_COLD : e[1];
    }

    private static double[] fresh(ServerLevel level, UUID ship, long now) {
        Map<UUID, double[]> m = ship == null ? null : HEAT.get(level.dimension());
        double[] e = m == null ? null : m.get(ship);
        return e == null || e[0] < now ? null : e;
    }

    public static double rearHalfAngle(ServerLevel level, UUID ship, long now) {
        return Balance.HEAT_REAR_HALF_ANGLE + (factor(level, ship, now) >= Balance.HEAT_SIG_HOT ? Balance.HEAT_REAR_HOT_BONUS : 0.0);
    }

    public static boolean rearAspect(ServerLevel level, UUID ship, Vec3 pos, Vec3 vel, Vec3 seeker, long now) {
        double[] e = fresh(level, ship, now);
        Vec3 tail = e != null && (e[2] != 0.0 || e[3] != 0.0 || e[4] != 0.0) ? new Vec3(e[2], e[3], e[4])
                : vel != null && vel.lengthSqr() > Balance.HEAT_REAR_MIN_SPEED * Balance.HEAT_REAR_MIN_SPEED ? vel.normalize().scale(-1.0) : null;
        if (tail == null) return true;
        Vec3 d = seeker.subtract(pos);
        if (d.lengthSqr() < 1.0E-6) return true;
        double cos = d.normalize().dot(tail);
        return cos >= Math.cos(Math.toRadians(rearHalfAngle(level, ship, now)));
    }
}
