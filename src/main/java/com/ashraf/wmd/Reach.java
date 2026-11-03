package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class Reach {

    private Reach() {}

    public static boolean within(Player p, BlockPos pos, double r) {
        return SableBridge.distSqWithShips(p.level(), p.position(), Vec3.atCenterOf(pos)) <= r * r;
    }

    public static boolean usable(Player p, BlockPos pos, double r) {
        return p.level().isLoaded(pos) && within(p, pos, r);
    }
}
