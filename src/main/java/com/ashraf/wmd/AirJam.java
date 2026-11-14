package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class AirJam {

    private AirJam() {}

    private static final Map<ResourceKey<Level>, Map<UUID, Long>> JAM = Statics.server(new HashMap<>());

    public static void jamming(ServerLevel level, UUID ship, long now) {
        if (!AirGate.on() || level == null || ship == null) return;
        Map<UUID, Long> m = JAM.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        if (m.size() > Balance.RWR_MAX_AIRCRAFT && !m.containsKey(ship)) m.entrySet().removeIf(e -> e.getValue() < now);
        m.put(ship, now + Balance.AIRJAM_HOLD);
    }

    static void jamFor(ServerLevel level, UUID ship, long ticks) {
        if (!AirGate.on() || level == null || ship == null) return;
        Map<UUID, Long> m = JAM.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        if (ticks <= 0) m.remove(ship);
        else m.put(ship, level.getGameTime() + ticks);
    }

    public static boolean on(ServerLevel level, UUID ship) {
        if (!AirGate.on() || level == null || ship == null) return false;
        Map<UUID, Long> m = JAM.get(level.dimension());
        Long until = m == null ? null : m.get(ship);
        return until != null && until >= level.getGameTime();
    }

    public static double lockRange(ServerLevel level, UUID target, double base) {
        return on(level, target) ? base * Balance.AIRJAM_LOCK_FACTOR : base;
    }
}
