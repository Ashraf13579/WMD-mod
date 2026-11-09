package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

final class SeabedPath {

    private SeabedPath() {}

    static final int STEP = 2;
    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    static int floor(ServerLevel sl, int x, int z, int yHint) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = yHint + 3; y >= yHint - 4; y--) {
            p.set(x, y, z);
            if (!sl.isLoaded(p)) return Integer.MIN_VALUE;
            BlockState s = sl.getBlockState(p);
            if (s.isAir() || (s.getFluidState().is(FluidTags.WATER) && s.getCollisionShape(sl, p).isEmpty())) continue;
            p.set(x, y + 1, z);
            if (!wet(sl, p)) return Integer.MIN_VALUE;
            p.set(x, y + 2, z);
            if (!wet(sl, p)) return Integer.MIN_VALUE;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean wet(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        return s.getFluidState().is(FluidTags.WATER) && s.getCollisionShape(sl, p).isEmpty();
    }

    private record Node(int x, int z, int y, double g, double f) {}

    static List<BlockPos> find(ServerLevel sl, BlockPos from, int tx, int tz, BlockPos foot, int reach) {
        long goal = key(tx, tz);
        Map<Long, Double> best = new HashMap<>();
        Map<Long, long[]> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        open.add(new Node(from.getX(), from.getZ(), from.getY(), 0.0, h(from.getX(), from.getZ(), tx, tz)));
        best.put(key(from.getX(), from.getZ()), 0.0);
        parent.put(key(from.getX(), from.getZ()), new long[] { Long.MIN_VALUE, from.getY() });
        int seen = 0;
        double r2 = (reach + 1.5) * (reach + 1.5);
        while (!open.isEmpty() && seen++ < Balance.CRAWLER_PATH_NODES) {
            Node n = open.poll();
            long k = key(n.x, n.z);
            if (n.g > best.getOrDefault(k, Double.MAX_VALUE) + 1.0E-9) continue;
            if (k == goal) return unwind(parent, k);
            for (int[] d : DIRS) {
                int nx = n.x + d[0], nz = n.z + d[1];
                double dfx = nx - foot.getX(), dfz = nz - foot.getZ();
                if (dfx * dfx + dfz * dfz > r2) continue;
                int ny = floor(sl, nx, nz, n.y);
                if (ny == Integer.MIN_VALUE || Math.abs(ny - n.y) > STEP) continue;
                if (d[0] != 0 && d[1] != 0
                        && (floor(sl, n.x + d[0], n.z, n.y) == Integer.MIN_VALUE || floor(sl, n.x, n.z + d[1], n.y) == Integer.MIN_VALUE)) continue;
                double g = n.g + (d[0] != 0 && d[1] != 0 ? 1.4142 : 1.0) + 0.5 * Math.abs(ny - n.y);
                long nk = key(nx, nz);
                if (g >= best.getOrDefault(nk, Double.MAX_VALUE)) continue;
                best.put(nk, g);
                parent.put(nk, new long[] { k, ny });
                open.add(new Node(nx, nz, ny, g, g + h(nx, nz, tx, tz)));
            }
        }
        return null;
    }

    private static List<BlockPos> unwind(Map<Long, long[]> parent, long k) {
        List<BlockPos> out = new ArrayList<>();
        while (true) {
            long[] p = parent.get(k);
            if (p == null || p[0] == Long.MIN_VALUE) break;
            out.add(0, new BlockPos((int) (k >> 32), (int) p[1], (int) k));
            k = p[0];
        }
        return out;
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    private static double h(int x, int z, int tx, int tz) {
        int dx = Math.abs(x - tx), dz = Math.abs(z - tz);
        return Math.max(dx, dz) + 0.4142 * Math.min(dx, dz);
    }
}
