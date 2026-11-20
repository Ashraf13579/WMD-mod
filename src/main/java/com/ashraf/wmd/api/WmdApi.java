package com.ashraf.wmd.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongPredicate;

import net.minecraft.server.level.ServerLevel;

public final class WmdApi {

    private WmdApi() {}

    public static final int API_VERSION = 1;

    public static int apiVersion() { return API_VERSION; }

    public static UUID blastShooter() { return com.ashraf.wmd.WmdApiImpl.shooter(); }

    public record FlightInfo(long id, int tier, double fromX, double fromZ, int targetX, int targetZ, long impactGameTime, UUID owner, String launcher,
                             boolean cruise, String warhead) {}

    public static List<FlightInfo> flights(ServerLevel level) { return level == null ? new ArrayList<>() : com.ashraf.wmd.WmdApiImpl.flights(level); }

    public static boolean restore(ServerLevel level, long fromMs, long toMs, LongPredicate chunkOk, String label) {
        return com.ashraf.wmd.WmdApiImpl.restore(level, fromMs, toMs, chunkOk, label);
    }
}
