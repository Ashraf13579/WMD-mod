package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class Masts {

    private Masts() {}

    public static final int PERISCOPE = 1, BUOY = 2;

    private static final Map<String, long[]> UP = Statics.server(new HashMap<>());

    private static String key(ServerLevel level, UUID ship) { return level.dimension().location() + "|" + ship; }

    public static void beat(ServerLevel level, UUID ship, int kind) {
        if (level == null || ship == null) return;
        long[] u = UP.computeIfAbsent(key(level, ship), k -> new long[2]);
        u[kind == BUOY ? 1 : 0] = level.getGameTime() + Balance.MAST_TTL;
    }

    public static void drop(ServerLevel level, UUID ship, int kind) {
        if (level == null || ship == null) return;
        long[] u = UP.get(key(level, ship));
        if (u != null) u[kind == BUOY ? 1 : 0] = 0L;
    }

    private static boolean live(ServerLevel level, UUID ship, int i) {
        if (level == null || ship == null) return false;
        long[] u = UP.get(key(level, ship));
        return u != null && u[i] >= level.getGameTime();
    }

    public static boolean up(ServerLevel level, UUID ship) { return live(level, ship, 0) || live(level, ship, 1); }

    public static boolean buoyUp(ServerLevel level, UUID ship) { return live(level, ship, 1); }

    public static boolean periscopeUp(ServerLevel level, UUID ship) { return live(level, ship, 0); }

    public static boolean mastOnly(ServerLevel level, SableBridge.ShipInfo ship) {
        return ship != null && !Config.Server.navalRadarSeesSubmerged() && NavalLayer.submerged(level, ship) && up(level, ship.id());
    }
}
