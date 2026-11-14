package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class ShipPower {

    private ShipPower() {}

    public static boolean draw(ServerLevel level, UUID ship, int fe) {
        if (level == null || ship == null || fe <= 0) return false;
        return AirGate.on() && PowerCoreBlockEntity.drawFrom(level, ship, fe);
    }

    public static int drawFuel(ServerLevel level, UUID ship, int mb) {
        if (level == null || ship == null || mb <= 0) return 0;
        return AirGate.on() ? PowerCoreBlockEntity.drainFuelFrom(level, ship, mb) : 0;
    }

    public static int[] fuelAboard(ServerLevel level, UUID ship) { return PowerCoreBlockEntity.fuelOf(level, ship); }
}
