package com.ashraf.wmd;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class UnderwaterMotion {

    private UnderwaterMotion() {}

    public static final double GRAVITY = 0.04, AIR_DRAG = 0.98;

    public static Vec3 coast(Level level, Vec3 pos, Vec3 vel) {
        if (NavalLayer.underwater(level, pos) || (level != null && level.getFluidState(net.minecraft.core.BlockPos.containing(pos)).is(net.minecraft.tags.FluidTags.WATER))) {
            Vec3 v = vel.scale(Balance.WATER_DRAG);
            double vy = v.y + (-Balance.SINK_SPEED - v.y) * (1.0 - Balance.WATER_DRAG);
            return new Vec3(v.x, vy, v.z);
        }
        return new Vec3(vel.x * AIR_DRAG, (vel.y - GRAVITY) * AIR_DRAG, vel.z * AIR_DRAG);
    }

    public static double holdDepth(Level level, Vec3 pos, double targetDepth, double maxVy) {
        double depth = NavalLayer.depthBelowSurface(level, pos);
        double err = depth - targetDepth;
        return Math.max(-maxVy, Math.min(maxVy, err * 0.1));
    }

    public static Vec3 run(Level level, Vec3 pos, Vec3 heading, double speed, double targetDepth, double maxVy) {
        Vec3 h = new Vec3(heading.x, 0.0, heading.z);
        if (h.lengthSqr() < 1e-8) h = new Vec3(0, 0, -1);
        h = h.normalize().scale(speed);
        return new Vec3(h.x, holdDepth(level, pos, targetDepth, maxVy), h.z);
    }
}
