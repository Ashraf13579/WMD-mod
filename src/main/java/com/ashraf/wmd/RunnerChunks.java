package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;

final class RunnerChunks {

    private RunnerChunks() {}

    private static final double LOOK = 20.0;

    private static final double MIN_AHEAD = 64.0, SIDE = 16.0;
    private static final int MAX_CORRIDOR = 24;

    static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "runner"), (level, helper) -> {
        int n = 0;
        for (var e : List.copyOf(helper.getEntityTickets().entrySet())) {
            UUID owner = e.getKey();
            for (long c : new LongArrayList(e.getValue().ticking())) { helper.removeTicket(owner, c, true); n++; }
            for (long c : new LongArrayList(e.getValue().nonTicking())) { helper.removeTicket(owner, c, false); n++; }
        }
        if (n > 0) Wmd.LOGGER.info("WMD: dropped {} stale torpedo / rocket chunk ticket(s) in {}", n, level.dimension().location());
    });

    static long[] follow(ServerLevel sl, UUID id, Vec3 pos, Vec3 vel, long[] held) {
        if (!Config.Server.chunkLoading()) return release(sl, id, held);
        long a = ChunkPos.asLong(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
        it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet load = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
        double hx = vel == null ? 0.0 : vel.x, hz = vel == null ? 0.0 : vel.z, hl = Math.hypot(hx, hz);
        if (hl > 1e-4) {
            double dx = hx / hl, dz = hz / hl, ahead = Math.max(MIN_AHEAD, hl * LOOK);
            for (double t = 0.0; t <= ahead && load.size() < MAX_CORRIDOR; t += 8.0)
                for (double side : new double[] { 0.0, -SIDE, SIDE }) {
                    double x = pos.x + dx * t - dz * side, z = pos.z + dz * t + dx * side;
                    long c = ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
                    if (c != a && load.size() < MAX_CORRIDOR) load.add(c);
                }
        }
        long[] want = new long[1 + load.size()];
        want[0] = a;
        int i = 1;
        for (long c : load) want[i++] = c;
        if (held != null && java.util.Arrays.equals(held, want)) return held;

        if (held != null) {
            if (held[0] != a) set(sl, id, held[0], false, true);
            for (int k = 1; k < held.length; k++) if (!load.contains(held[k])) set(sl, id, held[k], false, false);
        }
        if (held == null || held[0] != a) set(sl, id, a, true, true);
        for (long c : load) if (held == null || !contains(held, 1, c)) set(sl, id, c, true, false);
        return want;
    }

    static long[] release(ServerLevel sl, UUID id, long[] held) {
        if (held != null) for (int k = 0; k < held.length; k++) set(sl, id, held[k], false, k == 0);
        return null;
    }

    private static boolean contains(long[] a, int from, long c) {
        for (int k = from; k < a.length; k++) if (a[k] == c) return true;
        return false;
    }

    private static void set(ServerLevel sl, UUID id, long c, boolean add, boolean ticking) {
        CONTROLLER.forceChunk(sl, id, ChunkPos.getX(c), ChunkPos.getZ(c), add, ticking);
    }
}
