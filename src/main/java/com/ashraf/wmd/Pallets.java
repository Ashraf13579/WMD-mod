package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class Pallets {

    private Pallets() {}

    record Fly(int id, Vec3 pos, boolean chute, boolean heavy) {}

    private static final Map<ResourceKey<Level>, Map<Integer, Fly>> AIR = Statics.server(new HashMap<>());

    static void update(ServerLevel sl, PalletEntity e, boolean chute) {
        Map<Integer, Fly> m = AIR.computeIfAbsent(sl.dimension(), k -> new LinkedHashMap<>());
        if (m.size() > 256 && !m.containsKey(e.getId())) return;
        m.put(e.getId(), new Fly(e.getId(), e.position(), chute, e.heavy()));
        if (sl.getGameTime() % Balance.PALLET_FAR_TICKS == e.getId() % Balance.PALLET_FAR_TICKS) broadcast(sl, e, chute);
    }

    static void remove(ServerLevel sl, PalletEntity e) {
        Map<Integer, Fly> m = AIR.get(sl.dimension());
        if (m != null) { m.remove(e.getId()); if (m.isEmpty()) AIR.remove(sl.dimension()); }
    }

    static List<Fly> inAir(ServerLevel sl) {
        Map<Integer, Fly> m = AIR.get(sl.dimension());
        return m == null ? List.of() : new ArrayList<>(m.values());
    }

    private static void broadcast(ServerLevel sl, PalletEntity e, boolean chute) {
        double r2 = Balance.PALLET_FAR_RANGE * Balance.PALLET_FAR_RANGE, n2 = Balance.PALLET_FAR_NEAR * Balance.PALLET_FAR_NEAR;
        PalletFarPayload p = new PalletFarPayload(e.getId(), e.getX(), e.getY(), e.getZ(), chute, e.heavy());
        for (ServerPlayer sp : sl.players()) {
            double d = sp.distanceToSqr(e);
            if (d > n2 && d <= r2) PacketDistributor.sendToPlayer(sp, p);
        }
    }
}
