package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class Snorkels {

    private Snorkels() {}

    private static final int AIR = 0, WANT = 1, EXHAUST = 2;

    private static final Map<String, long[]> SHIPS = Statics.server(new HashMap<>());

    private static String key(ServerLevel level, UUID ship) { return level.dimension().location() + "|" + ship; }

    private static void mark(ServerLevel level, UUID ship, int i, int ttl) {
        if (level == null || ship == null) return;
        SHIPS.computeIfAbsent(key(level, ship), k -> new long[3])[i] = level.getGameTime() + ttl;
    }

    private static boolean live(ServerLevel level, UUID ship, int i) {
        if (level == null || ship == null) return false;
        long[] u = SHIPS.get(key(level, ship));
        return u != null && u[i] >= level.getGameTime();
    }

    public static void breathe(ServerLevel level, UUID ship) { mark(level, ship, AIR, Balance.SN_AIR_TTL); }

    public static boolean air(ServerLevel level, UUID ship) { return live(level, ship, AIR); }

    public static void want(ServerLevel level, UUID ship) { mark(level, ship, WANT, 20); }

    public static boolean wanted(ServerLevel level, UUID ship) { return live(level, ship, WANT); }

    public static void exhaust(ServerLevel level, UUID ship) { mark(level, ship, EXHAUST, 20); }

    public static boolean exhausting(ServerLevel level, UUID ship) { return live(level, ship, EXHAUST); }
}
