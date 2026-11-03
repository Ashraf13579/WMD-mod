package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class AlarmNet {

    private AlarmNet() {}

    public static final int HOLD_TICKS = 200;

    private static final Map<String, Long> ACTIVE = new HashMap<>();

    static void clearStatics() { ACTIVE.clear(); }

    private static String key(ServerLevel level, UUID owner, int frequency) {
        return level.dimension().location() + "|" + owner + "|" + frequency;
    }

    public static void raise(ServerLevel level, UUID owner, int frequency) {
        long now = level.getGameTime();
        for (Iterator<Long> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            if (it.next() < now) it.remove();
        }
        String k = key(level, owner, frequency);
        boolean fresh = !ACTIVE.containsKey(k);
        ACTIVE.put(k, now + HOLD_TICKS);
        if (fresh) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p != null) {
                p.displayClientMessage(Component.translatable("message.wmd.radar.alarm", String.format("%04d", frequency)), true);
            }
            Wmd.LOGGER.info("Radar alarm raised: frequency {} owner {}", frequency, owner);
        }
    }

    public static boolean isActive(ServerLevel level, UUID owner, int frequency) {
        Long until = ACTIVE.get(key(level, owner, frequency));
        if (until == null) until = ACTIVE.get(key(level, owner, ALL));
        return until != null && until >= level.getGameTime();
    }

    static final int ALL = -1;

    public static void raiseAll(ServerLevel level, UUID owner) {
        ACTIVE.put(key(level, owner, ALL), level.getGameTime() + HOLD_TICKS);
    }
}
