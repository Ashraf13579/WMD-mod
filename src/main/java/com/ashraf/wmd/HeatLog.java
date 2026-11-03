package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class HeatLog {

    private HeatLog() {}

    public record Hot(BlockPos pos, ResourceKey<Level> dim, UUID owner, long firedAt) {}

    private static final Map<Long, Hot> LOG = new HashMap<>();

    static synchronized void clearStatics() { LOG.clear(); }

    public static synchronized void fired(Level level, BlockPos pos, UUID owner) {
        if (level == null || level.isClientSide || pos == null) return;
        LOG.put(pos.asLong() ^ (long) level.dimension().location().hashCode() << 40, new Hot(pos.immutable(), level.dimension(), owner, level.getGameTime()));
    }

    public static synchronized Hot nearest(ServerLevel level, double x, double z, double r, UUID notOwner) {
        long now = level.getGameTime();
        Hot best = null;
        double bd = r * r;
        for (Hot h : LOG.values()) {
            if (!h.dim().equals(level.dimension()) || now - h.firedAt() > Balance.HEAT_WINDOW || h.owner() != null && h.owner().equals(notOwner)) continue;
            net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, h.pos());
            double dx = w.x - x, dz = w.z - z, d = dx * dx + dz * dz;
            if (d > bd || hidden(level, h.pos())) continue;
            bd = d;
            best = h;
        }
        return best;
    }

    static boolean hidden(ServerLevel level, BlockPos p) {
        return Camo.thermalHides(level, p) || SmokeScreens.inside(level, SableBridge.worldPos(level, p));
    }

    public static synchronized java.util.List<Hot> near(ServerLevel level, double x, double z, double r) {
        long now = level.getGameTime();
        java.util.List<Hot> l = new java.util.ArrayList<>();
        for (Hot h : LOG.values())
            if (h.dim().equals(level.dimension()) && now - h.firedAt() <= Balance.HEAT_WINDOW && Math.hypot(h.pos().getX() + 0.5 - x, h.pos().getZ() + 0.5 - z) <= r) l.add(h);
        return l;
    }

    static synchronized void expire(ServerLevel level) {
        long now = level.getGameTime();
        LOG.values().removeIf(h -> h.dim().equals(level.dimension()) && now - h.firedAt() > Balance.HEAT_WINDOW);
    }
}
