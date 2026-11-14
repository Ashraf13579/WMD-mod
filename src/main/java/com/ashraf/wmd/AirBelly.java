package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

final class AirBelly {

    private AirBelly() {}

    private static final Map<UUID, long[]> CACHE = Statics.server(new HashMap<>());

    static boolean sliding(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return false;
        long now = sl.getGameTime();
        long[] c = CACHE.get(ship);
        if (c != null && now - c[0] < Balance.BELLY_CHECK && now >= c[0]) return c[1] == 1;
        boolean s = check(sl, ship);
        if (CACHE.size() > 512) CACHE.clear();
        CACHE.put(ship, new long[] { now, s ? 1 : 0 });
        return s;
    }

    private static boolean check(ServerLevel sl, UUID ship) {
        if (LandingGearBlockEntity.stateAboard(sl, ship) >= 10) return false;
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null || info.worldBounds() == null) return false;
        AABB b = info.worldBounds();
        double[][] at = { { b.minX + 0.5, b.minZ + 0.5 }, { b.maxX - 0.5, b.minZ + 0.5 }, { b.minX + 0.5, b.maxZ - 0.5 }, { b.maxX - 0.5, b.maxZ - 0.5 },
                { (b.minX + b.maxX) * 0.5, (b.minZ + b.maxZ) * 0.5 } };
        boolean touching = false;
        for (double[] p : at) {
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            if (!sl.hasChunkAt(new net.minecraft.core.BlockPos(x, 0, z))) continue;
            int ground = sl.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (b.minY - ground < Balance.BELLY_GAP) { touching = true; break; }
        }
        return touching && SableBridge.countLiftBlocks(sl, ship) > 0;
    }
}
