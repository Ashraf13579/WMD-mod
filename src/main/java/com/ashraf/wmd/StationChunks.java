package com.ashraf.wmd;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class StationChunks {

    private StationChunks() {}

    static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station"), (level, helper) -> {
        for (var e : List.copyOf(helper.getBlockTickets().entrySet())) {
            for (long c : new LongArrayList(e.getValue().ticking())) helper.removeTicket(e.getKey(), c, true);
            for (long c : new LongArrayList(e.getValue().nonTicking())) helper.removeTicket(e.getKey(), c, false);
        }
    });

    private record Held(ServerLevel level, BlockPos owner, Set<Long> chunks) {}
    private static final Map<Integer, Held> HELD = Statics.server(new HashMap<>());

    static boolean busy(Station.Data d, int slot, List<Station.Module> mods) {
        if (d.reboost.containsKey(slot)) return true;
        for (Station.Module m : mods) {
            if (!m.built()) return true;
            if (Station.BAY.equals(m.type())) {
                int[] t = d.bay.get(m.id());
                int st = t == null ? AsteroidBay.NONE : t[AsteroidBay.P_STATE];
                if (st != AsteroidBay.NONE && st != AsteroidBay.READY && st != AsteroidBay.PARKED) return true;
            }
        }
        return RodOfDeath.busy(slot) || MirrorFocus.busy(slot) || StationLaser.busy(slot);
    }

    static void update(ServerLevel orbit, Station.Data d, int slot, List<Station.Module> mods, Station.Module core) {
        Held h = HELD.get(slot);
        if (h != null && h.level() != orbit) { HELD.remove(slot); h = null; }
        boolean busy = core != null && busy(d, slot, mods);
        if (!busy) {
            if (h != null) { release(orbit, h); HELD.remove(slot); Wmd.LOGGER.info("[station] slot {} idle: its chunks are let go", slot); }
            return;
        }
        if (h != null && orbit.getGameTime() % 200 != 0) return;
        Set<Long> want = new HashSet<>();
        for (Station.Module m : mods) Station.cells(m, (p, st) -> want.add(ChunkKey.of(p.getX() >> 4, p.getZ() >> 4)));
        want.add(ChunkKey.of(core.origin().getX() >> 4, core.origin().getZ() >> 4));
        BlockPos owner = core.origin();
        if (h != null && h.owner().equals(owner) && h.chunks().equals(want)) return;
        if (h != null) release(orbit, h);
        for (long c : want) CONTROLLER.forceChunk(orbit, owner, ChunkKey.x(c), ChunkKey.z(c), true, true);
        HELD.put(slot, new Held(orbit, owner, want));
        if (h == null) Wmd.LOGGER.info("[station] slot {} is busy: holding its {} chunk(s) ticking while nobody is aboard", slot, want.size());
    }

    private static void release(ServerLevel orbit, Held h) {
        for (long c : h.chunks()) CONTROLLER.forceChunk(orbit, h.owner(), ChunkKey.x(c), ChunkKey.z(c), false, true);
    }

    private static final class ChunkKey {
        static long of(int x, int z) { return net.minecraft.world.level.ChunkPos.asLong(x, z); }
        static int x(long k) { return net.minecraft.world.level.ChunkPos.getX(k); }
        static int z(long k) { return net.minecraft.world.level.ChunkPos.getZ(k); }
    }
}
