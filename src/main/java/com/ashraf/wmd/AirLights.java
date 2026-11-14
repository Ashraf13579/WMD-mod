package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class AirLights {

    private AirLights() {}

    static float[] tips(ServerLevel sl, UUID ship, BlockPos pos, Direction facing) {
        int[] b = SableBridge.plotBox(sl, ship);
        if (b == null) return null;

        double[] l0 = local(b[0] - (pos.getX() + 0.5), b[2] - (pos.getZ() + 0.5), facing), l1 = local(b[3] + 1 - (pos.getX() + 0.5), b[5] + 1 - (pos.getZ() + 0.5), facing);
        double x0 = Math.min(l0[0], l1[0]), x1 = Math.max(l0[0], l1[0]), z0 = Math.min(l0[1], l1[1]), z1 = Math.max(l0[1], l1[1]);
        float y = 0.4F, top = b[4] + 1 - pos.getY();
        float zm = (float) ((z0 + z1) * 0.5), xm = (float) ((x0 + x1) * 0.5);
        return new float[] { (float) x0 - 0.05F, y, zm, (float) x1 + 0.05F, y, zm, xm, top + 0.1F, (float) z1 + 0.05F };
    }

    static double[] local(double dx, double dz, Direction f) {
        return switch (f) {
            case EAST -> new double[] { dz, -dx };
            case SOUTH -> new double[] { -dx, -dz };
            case WEST -> new double[] { -dz, dx };
            default -> new double[] { dx, dz };
        };
    }

    static double sideAccel(Vec3 prev, Vec3 now, double dt) {
        if (prev == null || now == null || dt <= 0.0) return 0.0;
        Vec3 a = now.subtract(prev).scale(1.0 / dt);
        double sp = now.length();
        if (sp < 1.0E-3) return 0.0;
        Vec3 u = now.scale(1.0 / sp);
        return a.subtract(u.scale(a.dot(u))).length();
    }

    static void vapour(ServerLevel sl, double[]... at) {
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(at[0][0], at[0][1], at[0][2]) > 256.0 * 256.0) continue;
            for (double[] w : at) sl.sendParticles(p, WmdParticles.VAPOUR.get(), true, w[0], w[1], w[2], 2, 0.15, 0.15, 0.15, 0.0);
        }
    }
}
