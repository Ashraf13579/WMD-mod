package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class BlastWrites {

    private BlastWrites() {}

    private static int depth;

    static boolean inBlast() { return depth > 0; }

    private static net.minecraft.nbt.CompoundTag data(Level level, BlockPos pos, BlockState before) {
        if (!before.hasBlockEntity() || !(level instanceof ServerLevel) || !BlastLog.listening()) return null;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        try {
            CompoundTag full = be.saveWithFullMetadata(level.registryAccess()), keep = new CompoundTag();
            for (String k : KEEP) if (full.contains(k)) keep.put(k, full.get(k));
            return keep.isEmpty() ? null : keep;
        } catch (RuntimeException e) { return null; }
    }

    static final String[] KEEP = { "owner", "ownerName", "public", "trusted", "controller", "freq", "frequency" };

    private static java.util.UUID machineOwner(Level level, BlockPos pos, BlockState before) {
        if (!before.hasBlockEntity() || !WarGate.anyActive) return null;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Owned o && !(be instanceof MachinePartBlockEntity) ? o.getOwner() : null;
    }

    static boolean set(Level level, BlockPos pos, BlockState to, int flags) {
        BlockState before = level.getBlockState(pos);
        net.minecraft.nbt.CompoundTag nbt = data(level, pos, before);
        java.util.UUID machineOwner = machineOwner(level, pos, before);
        boolean ok;
        depth++;
        try { ok = level.setBlock(pos, to, flags); } finally { depth--; }
        if (ok && machineOwner != null && level instanceof ServerLevel sl) WarScore.machine(sl, before, ShipStrike.shooter(), machineOwner);
        if (ok) note(level, pos, before, to, nbt);
        return ok;
    }

    static boolean set(Level level, BlockPos pos, BlockState to) { return set(level, pos, to, 3); }

    static boolean destroy(Level level, BlockPos pos, boolean drop) {
        BlockState before = level.getBlockState(pos);
        net.minecraft.nbt.CompoundTag nbt = data(level, pos, before);
        java.util.UUID machineOwner = machineOwner(level, pos, before);
        boolean ok;
        depth++;
        try { ok = level.destroyBlock(pos, drop); } finally { depth--; }
        if (ok && machineOwner != null && level instanceof ServerLevel sl) WarScore.machine(sl, before, ShipStrike.shooter(), machineOwner);
        if (ok) note(level, pos, before, level.getBlockState(pos), nbt);
        return ok;
    }

    static boolean remove(Level level, BlockPos pos, boolean moving) {
        BlockState before = level.getBlockState(pos);
        net.minecraft.nbt.CompoundTag nbt = data(level, pos, before);
        java.util.UUID machineOwner = machineOwner(level, pos, before);
        boolean ok;
        depth++;
        try { ok = level.removeBlock(pos, moving); } finally { depth--; }
        if (ok && machineOwner != null && level instanceof ServerLevel sl) WarScore.machine(sl, before, ShipStrike.shooter(), machineOwner);
        if (ok) note(level, pos, before, level.getBlockState(pos), nbt);
        return ok;
    }

    static void section(Level level, int x, int y, int z, BlockState before, BlockState after) {
        if (level instanceof ServerLevel sl && BlastLog.listening()) BlastLog.record(sl, BlockPos.asLong(x, y, z), before, after, ShipStrike.shooter(), null);
    }

    static void fell(Level level, BlockPos pos, BlockState before) { section(level, pos.getX(), pos.getY(), pos.getZ(), before, Blocks.AIR.defaultBlockState()); }

    private static void note(Level level, BlockPos pos, BlockState before, BlockState after, net.minecraft.nbt.CompoundTag nbt) {
        if (before == after || !(level instanceof ServerLevel sl) || !BlastLog.listening()) return;
        BlastLog.record(sl, pos.asLong(), before, after, ShipStrike.shooter(), nbt);
    }
}
