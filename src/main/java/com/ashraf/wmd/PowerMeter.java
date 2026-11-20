package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PowerMeter {

    private PowerMeter() {}

    private static final int WINDOW = 20;

    private static final Map<ResourceKey<Level>, Map<Long, long[]>> METER = Statics.server(new HashMap<>());

    public static void noteIn(Level level, BlockPos pos, long fe) { note(level, pos, fe, 1); }
    public static void noteOut(Level level, BlockPos pos, long fe) { note(level, pos, fe, 2); }

    public static void noteIn(BlockEntity be, long fe) {
        if (be != null && be.getLevel() != null) note(be.getLevel(), be.getBlockPos(), fe, 1);
    }

    private static void note(Level level, BlockPos pos, long fe, int slot) {
        if (level == null || level.isClientSide() || pos == null || fe <= 0) return;
        long now = level.getGameTime();
        Map<Long, long[]> m = METER.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long[] e = m.get(pos.asLong());
        if (e == null) {
            if (m.size() > 4_096) prune(m, now);
            e = new long[] { now - now % WINDOW, 0, 0, 0, 0 };
            m.put(pos.asLong(), e);
        }
        roll(e, now);
        e[slot] += fe;
    }

    private static void roll(long[] e, long now) {
        long start = now - now % WINDOW;
        if (start == e[0]) return;
        boolean next = start - e[0] == WINDOW;
        e[3] = next ? e[1] : 0;
        e[4] = next ? e[2] : 0;
        e[1] = 0; e[2] = 0; e[0] = start;
    }

    private static void prune(Map<Long, long[]> m, long now) {
        for (Iterator<long[]> it = m.values().iterator(); it.hasNext();) if (now - it.next()[0] > WINDOW * 10L) it.remove();
    }

    public static long[] perSecond(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        Map<Long, long[]> m = METER.get(level.dimension());
        long[] e = m == null ? null : m.get(pos.asLong());
        if (e == null) return null;
        roll(e, level.getGameTime());
        return new long[] { e[3], e[4] };
    }

    public static BlockPos owner(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachinePartBlockEntity p && p.getController() != null) return p.getController();
        return pos;
    }
}
