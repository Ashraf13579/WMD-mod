package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class BlastLog {

    private BlastLog() {}

    private static long total, unknown;
    private static final Map<UUID, long[]> BY_SHOOTER = Statics.server(new HashMap<>());

    static boolean listening() { return true; }

    static void record(ServerLevel level, long pos, BlockState before, BlockState after, UUID shooter, net.minecraft.nbt.CompoundTag nbt) {
        RollbackLog.record(level, pos, before, after, shooter, nbt);
        WarScore.block(level, pos, shooter);
        total++;
        if (shooter == null) { unknown++; return; }
        BY_SHOOTER.computeIfAbsent(shooter, k -> new long[1])[0]++;
    }

    static long total() { return total; }
    static long unknown() { return unknown; }
    static Map<UUID, long[]> byShooter() { return BY_SHOOTER; }

    static void reset() { total = 0L; unknown = 0L; BY_SHOOTER.clear(); }
}
