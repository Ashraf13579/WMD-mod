package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class EmpBlackout {

    private EmpBlackout() {}

    private static final Map<UUID, Long> OUT = Statics.server(new HashMap<>());

    public static void hit(ServerLevel level, UUID ship, long until) {
        if (level == null || ship == null) return;
        long now = level.getGameTime();
        if (OUT.size() > 256) OUT.values().removeIf(t -> t < now);
        OUT.merge(ship, until, Math::max);
    }

    public static boolean out(Level level, UUID ship) {
        if (level == null || ship == null || OUT.isEmpty()) return false;
        Long t = OUT.get(ship);
        if (t == null) return false;
        if (level.getGameTime() >= t) { OUT.remove(ship); return false; }
        return true;
    }

    public static int left(Level level, UUID ship) {
        Long t = level == null || ship == null ? null : OUT.get(ship);
        return t == null ? 0 : (int) Math.max(0L, t - level.getGameTime());
    }
}
