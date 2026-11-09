package com.ashraf.wmd;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class SubHits {

    private SubHits() {}

    static boolean mayHole(ServerLevel level, SableBridge.ShipInfo ship) {
        return Config.Server.navalSubHoles() || !NavalLayer.submerged(level, ship);
    }

    public static double implodePower(double depth) {
        return Math.max(Balance.SUB_IMPLODE_MIN_POWER, Balance.SUB_IMPLODE_POWER - Balance.SUB_IMPLODE_PER_DEPTH * Math.max(0.0, depth));
    }

    static void hit(ServerLevel level, SableBridge.ShipInfo ship, Vec3 at, float power, double dist) {
        if (ship == null || !Config.Server.navalSubImplosion() || dist > Balance.SUB_IMPLODE_REACH || !DeepSeasBridge.isSub(ship.id())) return;
        if (!NavalLayer.submerged(level, ship)) return;
        DeepSeasBridge.SubState st = DeepSeasBridge.state(ship.id());
        double depth = Math.max(NavalLayer.shipDepth(level, ship), st == null ? 0 : st.depth());
        depth = Math.max(0.0, depth - DeepHull.implodeBonus(level, ship.id()));
        if (power >= implodePower(depth)) DeepSeasBridge.implode(level, ship.id(), at);
    }
}
