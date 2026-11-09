package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CommsLink {

    private CommsLink() {}

    private static final Map<String, Object[]> CACHE = Statics.server(new HashMap<>());

    public static boolean onAir(ServerLevel level, UUID ship) {
        if (ship == null || level == null || !Config.Server.navalDeepLinkLoss()) return true;
        String k = level.dimension().location() + "|" + ship;
        long now = level.getGameTime();
        Object[] c = CACHE.get(k);
        if (c != null && (Long) c[1] == now) return (Boolean) c[0];
        boolean air = compute(level, ship);
        if (CACHE.size() > 512) CACHE.clear();
        CACHE.put(k, new Object[] { air, now });
        return air;
    }

    private static boolean compute(ServerLevel level, UUID ship) {
        SableBridge.ShipInfo s = SableBridge.shipInfo(level, ship);
        if (s == null || !NavalLayer.submerged(level, s)) return true;
        if (NavalLayer.shipDepth(level, s) <= Balance.COMMS_DEPTH) return true;
        return Masts.buoyUp(level, ship);
    }

    public static UUID shipOf(ServerLevel level, BlockPos pos) { return SableBridge.shipIdAt(level, pos); }

    public static UUID shipAtWorld(ServerLevel level, Vec3 p) {
        if (p == null || !SableBridge.active()) return null;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, new AABB(p, p).inflate(0.25)))
            if (s.worldBounds().contains(p)) return s.id();
        return null;
    }

    public static boolean linked(ServerLevel level, UUID a, UUID b) {
        if (Objects.equals(a, b)) return true;
        return onAir(level, a) && onAir(level, b);
    }

    public static boolean linked(ServerLevel level, BlockPos viewer, BlockPos other) {
        return linked(level, shipOf(level, viewer), shipOf(level, other));
    }

    public static boolean hears(ServerLevel level, BlockPos viewer, Vec3 ear) {
        if (ear == null || !Config.Server.navalDeepLinkLoss()) return true;
        return linked(level, shipOf(level, viewer), shipAtWorld(level, ear));
    }

    public static <T extends BlockEntity> List<T> keep(ServerLevel level, BlockPos viewer, List<T> members) {
        if (!Config.Server.navalDeepLinkLoss() || members.isEmpty()) return members;
        UUID me = shipOf(level, viewer);
        List<T> out = new ArrayList<>(members.size());
        for (T t : members) if (linked(level, me, shipOf(level, t.getBlockPos()))) out.add(t);
        return out;
    }

    public static boolean cutOff(ServerLevel level, BlockPos pos) { return !onAir(level, shipOf(level, pos)); }
}
