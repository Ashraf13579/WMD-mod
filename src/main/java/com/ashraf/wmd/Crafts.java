package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class Crafts {

    private Crafts() {}

    static final int OWNER_CACHE_TICKS = 40;

    private record Own(UUID owner, long at) {}

    private static final Map<UUID, Own> OWNERS = Statics.server(new HashMap<>());

    static UUID owner(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return null;
        long now = sl.getGameTime();
        Own c = OWNERS.get(ship);
        if (c != null && now >= c.at() && now - c.at() < OWNER_CACHE_TICKS) return c.owner();
        Map<UUID, Integer> count = new HashMap<>();
        UUID best = null;
        int most = 0;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) {
            if (!(sl.getBlockEntity(p) instanceof Owned o) || o.getOwner() == null) continue;
            int n = count.merge(o.getOwner(), 1, Integer::sum);
            if (n > most) { most = n; best = o.getOwner(); }
        }
        if (best == null) { OWNERS.remove(ship); return null; }
        if (OWNERS.size() > 512) OWNERS.clear();
        OWNERS.put(ship, new Own(best, now));
        return best;
    }

    static boolean ownedBy(ServerLevel sl, UUID ship, UUID player) {
        return player != null && player.equals(owner(sl, ship));
    }

    static double speed(ServerLevel sl, UUID craft, UUID base) {
        SableBridge.ShipInfo c = craft == null ? null : SableBridge.shipInfo(sl, craft);
        if (c == null || c.linearVelocity() == null) return Double.MAX_VALUE;
        Vec3 v = ShipDefence.velocity(sl, c);
        SableBridge.ShipInfo m = base == null || base.equals(craft) ? null : SableBridge.shipInfo(sl, base);
        if (m != null && m.linearVelocity() != null) v = v.subtract(ShipDefence.velocity(sl, m));
        return v.length() * 20.0;
    }

    static boolean parked(ServerLevel sl, UUID craft, UUID base, double limit) {
        if (Anchors.holds(sl, craft) && speed(sl, craft, base) < Math.max(limit, Balance.ANCHOR_PARKED)) return true;
        return speed(sl, craft, base) < limit;
    }
}
