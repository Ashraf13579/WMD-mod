package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import net.minecraft.server.level.ServerLevel;

public final class ShipPower {

    private ShipPower() {}

    private static final Map<UUID, long[]> CREW = Statics.server(new HashMap<>());

    public static boolean crewed(ServerLevel level, UUID ship) {
        if (level == null || ship == null) return false;
        long now = level.getGameTime();
        long[] c = CREW.get(ship);
        if (c == null || now - c[0] >= 20 || now < c[0]) {
            boolean cockpit = false, av = false;
            for (BlockPos q : SableBridge.blockEntitiesOn(level, ship)) {
                if (!level.isLoaded(q) || !(level.getBlockEntity(q) instanceof JetCockpitBlockEntity jc)) continue;
                cockpit = true;
                if (jc.avionicsOn()) { av = true; break; }
            }
            c = new long[] { now, !cockpit || av ? 1L : 0L };
            if (CREW.size() > 512) CREW.clear();
            CREW.put(ship, c);
        }
        return c[1] != 0L;
    }

    public static boolean draw(ServerLevel level, UUID ship, int fe) {
        if (level == null || ship == null || fe <= 0) return false;
        if (EmpBlackout.out(level, ship)) return false;
        return AirGate.on() && PowerCoreBlockEntity.drawFrom(level, ship, fe);
    }

    public static boolean draw(net.minecraft.world.level.block.entity.BlockEntity who, ServerLevel level, UUID ship, int fe) {
        boolean paid = draw(level, ship, fe);
        if (paid) PowerMeter.noteIn(who, fe);
        return paid;
    }

    public static int drawFuel(ServerLevel level, UUID ship, int mb) {
        if (level == null || ship == null || mb <= 0) return 0;
        if (EmpBlackout.out(level, ship)) return 0;
        return AirGate.on() ? PowerCoreBlockEntity.drainFuelFrom(level, ship, mb) : 0;
    }

    public static int[] coreCharge(ServerLevel level, UUID ship) { return PowerCoreBlockEntity.chargeOf(level, ship); }

    public static int[] fuelAboard(ServerLevel level, UUID ship) { return PowerCoreBlockEntity.fuelOf(level, ship); }
}
