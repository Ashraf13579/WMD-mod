package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ShipModes {

    private ShipModes() {}

    private static final Set<UUID> HOVER = Statics.server(new HashSet<>());
    private static final Map<UUID, Long> CHUTE = Statics.server(new HashMap<>());

    static boolean hover(UUID ship) { return ship != null && HOVER.contains(ship); }

    static boolean toggleHover(UUID ship) {
        if (ship == null) return false;
        if (HOVER.remove(ship)) return false;
        HOVER.add(ship);
        return true;
    }

    static void requestChute(UUID ship, long now) { if (ship != null) CHUTE.put(ship, now); }

    private static final Set<UUID> HOOK_DOWN = Statics.server(new HashSet<>());
    private static final Map<UUID, Long> HOOK_RELEASE = Statics.server(new HashMap<>());
    private static final Set<UUID> REFUEL = Statics.server(new HashSet<>());

    static boolean hookDown(UUID ship) { return ship != null && HOOK_DOWN.contains(ship); }

    static boolean toggleHook(UUID ship) {
        if (ship == null) return false;
        if (HOOK_DOWN.remove(ship)) return false;
        HOOK_DOWN.add(ship);
        return true;
    }

    static void requestRelease(UUID ship, long now) { if (ship != null) HOOK_RELEASE.put(ship, now); }

    static boolean releaseAsked(UUID ship, long now) {
        Long t = ship == null ? null : HOOK_RELEASE.get(ship);
        return t != null && now - t <= 20;
    }

    static boolean refuel(UUID ship) { return ship != null && REFUEL.contains(ship); }

    static boolean toggleRefuel(UUID ship) {
        if (ship == null) return false;
        if (REFUEL.remove(ship)) return false;
        REFUEL.add(ship);
        return true;
    }

    static boolean takeChuteRequest(UUID ship, long now) {
        Long t = ship == null ? null : CHUTE.get(ship);
        if (t == null) return false;
        CHUTE.remove(ship);
        return now - t <= Balance.CHUTE_REQUEST_TICKS;
    }
}
