package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockRules {

    private BlockRules() {}

    public static boolean blastMay(Level level, BlockPos p) { return Config.Server.canBreak(level, p); }

    public static boolean blastChangeable(Level level, BlockPos p, BlockState s) {
        return !s.hasBlockEntity() && s.getDestroySpeed(level, p) >= 0.0F && Hardened.rating(level, p, s) == 0 && blastMay(level, p);
    }

    public static boolean blastRemovable(Level level, BlockPos p, BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && s.getDestroySpeed(level, p) >= 0.0F && !s.hasBlockEntity() && Hardened.rating(level, p, s) == 0
                && blastMay(level, p);
    }
}
