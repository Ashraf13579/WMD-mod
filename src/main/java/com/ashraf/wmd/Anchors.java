package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class Anchors {

    private Anchors() {}

    public static final int NONE = 0, WORKING = 1, HOLDING = 2, DRAGGING = 3;

    private record Beat(int state, long seen) {}

    private static final Map<Level, Map<UUID, Map<BlockPos, Beat>>> BEATS = Statics.server(new WeakHashMap<>());

    static void report(ServerLevel sl, UUID ship, BlockPos windlass, int windlassState) {
        if (sl == null || ship == null || windlass == null) return;
        Map<UUID, Map<BlockPos, Beat>> m = BEATS.computeIfAbsent(sl, l -> new HashMap<>());
        if (m.size() > 512) m.clear();
        m.computeIfAbsent(ship, s -> new HashMap<>()).put(windlass.immutable(), new Beat(windlassState, sl.getGameTime()));
    }

    static void withdraw(ServerLevel sl, UUID ship, BlockPos windlass) {
        if (sl == null || ship == null || windlass == null) return;
        Map<UUID, Map<BlockPos, Beat>> m = BEATS.get(sl);
        Map<BlockPos, Beat> b = m == null ? null : m.get(ship);
        if (b == null) return;
        b.remove(windlass);
        if (b.isEmpty()) m.remove(ship);
    }

    public static int state(ServerLevel sl, UUID ship) {
        Map<UUID, Map<BlockPos, Beat>> m = sl == null || ship == null ? null : BEATS.get(sl);
        Map<BlockPos, Beat> b = m == null ? null : m.get(ship);
        if (b == null) return NONE;
        long now = sl.getGameTime();
        int best = NONE;
        boolean drag = false;
        for (Beat x : b.values()) {
            if (now - x.seen() > Balance.ANCHOR_TTL) continue;
            if (x.state() == AnchorWindlassBlockEntity.S_DRAGGING) drag = true;
            else if (x.state() == AnchorWindlassBlockEntity.S_HOLDING) best = HOLDING;
            else if (x.state() != AnchorWindlassBlockEntity.S_STOWED && best == NONE) best = WORKING;
        }
        return drag ? DRAGGING : best;
    }

    public static boolean holds(ServerLevel sl, UUID ship) { return state(sl, ship) == HOLDING; }

    static int hudFlags(ServerLevel sl, UUID ship) {
        int s = state(sl, ship);
        return s == HOLDING ? HelmHudPayload.F_ANCHOR : s == DRAGGING ? HelmHudPayload.F_DRAG : 0;
    }

    public static String order(ServerLevel sl, BlockPos windlass, boolean drop, ServerPlayer by) {
        if (sl == null || windlass == null || !AirGate.on()) return "gated";
        if (!sl.isLoaded(windlass) || !(sl.getBlockEntity(windlass) instanceof AnchorWindlassBlockEntity be) || be.isRemoved()) return "gone";
        if (by != null && (!Access.mayUse(be, by) || !AnchorPayloads.craftAllows(sl, be, by))) return "denied";
        return be.command(drop, by);
    }
}
