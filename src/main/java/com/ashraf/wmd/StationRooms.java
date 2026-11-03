package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class StationRooms {

    private StationRooms() {}

    record Room(int module, Set<Long> cells) {}

    private static final Map<Integer, List<Room>> ROOMS = Statics.server(new HashMap<>());
    private static final Map<Integer, Set<Long>> OPEN_OK = Statics.server(new HashMap<>());
    private static final Map<Integer, Set<Long>> HATCH_OK = Statics.server(new HashMap<>());

    static void forget(int slot) { ROOMS.remove(slot); OPEN_OK.remove(slot); HATCH_OK.remove(slot); }

    static boolean openingSealed(int slot, int module, int k) { return OPEN_OK.getOrDefault(slot, Set.of()).contains(((long) module << 8) | k); }

    static boolean hatchSealed(int slot, BlockPos lower) { return HATCH_OK.getOrDefault(slot, Set.of()).contains(lower.asLong()); }

    static int roomModule(int slot, BlockPos pos) {
        long k = pos.asLong();
        for (Room r : ROOMS.getOrDefault(slot, List.of())) if (r.cells().contains(k)) return r.module();
        return -1;
    }

    static boolean closedHatch(BlockState s) { return s.is(Wmd.STATION_HATCH.get()) && !s.getValue(DoorBlock.OPEN); }

    static BlockPos lower(BlockPos p, BlockState s) { return s.is(Wmd.STATION_HATCH.get()) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p; }

    static void recompute(ServerLevel level, int slot, List<Station.Module> mods) {
        List<Room> rooms = new ArrayList<>();
        Set<Long> openOk = new HashSet<>(), hatchOk = new HashSet<>();
        for (Station.Module m : mods) {
            if (!Station.CORE.equals(m.type()) && !Station.HABITAT.equals(m.type())) continue;
            int id = m.id();
            for (Map.Entry<Integer, List<BlockPos>> o : Station.openings(m).entrySet()) {
                int k = o.getKey();
                if (StationLife.neighbour(mods, m, k) != null) continue;
                if (Station.CORE.equals(m.type()) && k == 4 && StationLife.capsuleOn(level, m)) continue;
                Direction out = outward(m, k);
                List<BlockPos> starts = new ArrayList<>();
                for (BlockPos l : o.getValue()) starts.add(Station.world(m, l.getX(), l.getY(), l.getZ()).relative(out));
                Set<Long> cells = fill(level, starts, mods);
                if (cells != null && !cells.isEmpty()) { rooms.add(new Room(id, cells)); openOk.add(((long) id << 8) | k); }
            }
            for (Station.HullCell c : Station.hull(m)) {
                BlockState st = level.getBlockState(c.pos());
                if (!st.is(Wmd.STATION_HATCH.get()) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                List<BlockPos> starts = new ArrayList<>();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos q = c.pos().relative(d);
                    if (!Station.interior(m, Station.local(m, q)) && !isHull(m, q)) { starts.add(q); starts.add(q.above()); }
                }
                Set<Long> cells = fill(level, starts, mods);
                if (cells != null && !cells.isEmpty()) { rooms.add(new Room(id, cells)); hatchOk.add(c.pos().asLong()); }
            }
        }
        ROOMS.put(slot, rooms); OPEN_OK.put(slot, openOk); HATCH_OK.put(slot, hatchOk);
    }

    private static boolean isHull(Station.Module m, BlockPos p) {
        for (Station.HullCell c : Station.hull(m)) if (c.pos().equals(p)) return true;
        return false;
    }

    static Direction outward(Station.Module m, int k) {
        if (Station.CORE.equals(m.type())) return k == 4 ? Direction.UP : Station.PORT_DIR[k];
        if (k >= 2) return Station.worldDir(m, Station.SIDE_Z[k - 2] > 0 ? Direction.SOUTH : Direction.NORTH);
        return Station.worldDir(m, k == 0 ? Direction.WEST : Direction.EAST);
    }

    private static Set<Long> fill(ServerLevel level, List<BlockPos> starts, List<Station.Module> mods) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        for (BlockPos s : starts) if (passable(level, s, mods) && seen.add(s.asLong())) q.add(s);
        while (!q.isEmpty()) {
            if (seen.size() > Balance.STATION_ROOM_MAX) return null;
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n.asLong()) || !passable(level, n, mods)) continue;
                seen.add(n.asLong());
                q.add(n);
            }
        }
        return seen;
    }

    private static boolean passable(ServerLevel level, BlockPos p, List<Station.Module> mods) {
        if (!level.isLoaded(p)) return true;
        BlockState s = level.getBlockState(p);
        if (!s.isAir() && (s.isCollisionShapeFullBlock(level, p) || closedHatch(s))) return false;
        for (Station.Module m : mods) if (Station.interior(m, Station.local(m, p))) return false;
        return true;
    }
}
