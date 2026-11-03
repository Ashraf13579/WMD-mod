package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class RobotPath {

    private RobotPath() {}

    static double ground(Level level, int x, int z, int y0) { return ground(level, x, z, y0, 3); }

    static double ground(Level level, int x, int z, int y0, int head) {
        for (int y = y0 + 2; y >= y0 - 3; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.isLoaded(p)) return Double.NaN;
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return Double.NaN;
            VoxelShape sh = s.getCollisionShape(level, p);
            if (sh.isEmpty()) continue;
            double top = y + sh.max(Direction.Axis.Y);
            for (int k = 1; k <= head; k++) {
                BlockPos q = p.above(k);
                BlockState qs = level.getBlockState(q);
                if (!qs.getFluidState().isEmpty() || !qs.getCollisionShape(level, q).isEmpty()) return Double.NaN;
            }
            return top;
        }
        return Double.NaN;
    }

    static final int W_WALL = 1, W_WATER = 2, W_DROP = 3, W_STEEP = 4, W_NARROW = 5;

    static int whyNot(Level level, int x, int z, int y0, int head) {
        for (int y = y0 + 2; y >= y0 - 3; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.isLoaded(p)) return W_DROP;
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return W_WATER;
            if (s.getCollisionShape(level, p).isEmpty()) continue;
            for (int k = 1; k <= head; k++) {
                BlockPos q = p.above(k);
                if (!level.getBlockState(q).getFluidState().isEmpty()) return W_WATER;
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return W_WALL;
            }
            return 0;
        }
        return W_DROP;
    }

    private static final class Cache {
        final Level level;
        final int half, head;
        final Map<Long, Double> g = new HashMap<>();

        int why, whyX, whyY, whyZ;

        Set<Long> dock = Set.of();
        double dockY;
        int sx, sz;
        Cache(Level level, int half, int head) { this.level = level; this.half = half; this.head = head; }

        double ground(int x, int z, int y0) {
            if (dock.contains(BlockPos.asLong(x, 0, z)) && Math.abs(dockY - y0) <= 3) return dockY;
            return g.computeIfAbsent(BlockPos.asLong(x, y0, z), k -> RobotPath.ground(level, x, z, y0, head));
        }

        double fits(int x, int z, int y0) {
            double c = ground(x, z, y0);
            if (Double.isNaN(c)) { why = whyNot(level, x, z, y0, head); whyX = x; whyY = y0; whyZ = z; return c; }
            if (Math.max(Math.abs(x - sx), Math.abs(z - sz)) <= ESCAPE) return c;
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                if (dx == 0 && dz == 0) continue;
                double n = ground(x + dx, z + dz, (int) Math.floor(c));
                if (Double.isNaN(n) || Math.abs(n - c) > Math.min(Math.abs(dx) + Math.abs(dz), 2) + 0.01) {
                    why = Double.isNaN(n) ? whyNot(level, x + dx, z + dz, (int) Math.floor(c), head) : W_NARROW;
                    if (why == 0) why = W_NARROW;
                    whyX = x + dx; whyY = (int) Math.floor(c); whyZ = z + dz;
                    return Double.NaN;
                }
            }
            return c;
        }
    }

    private record Node(int x, int z, double y, double g, double f, Node parent) {}

    static final int ESCAPE = 3;

    static Set<Long> columns(List<BlockPos> cells) {
        Set<Long> s = new java.util.HashSet<>();
        for (BlockPos c : cells) s.add(BlockPos.asLong(c.getX(), 0, c.getZ()));
        return s;
    }

    static List<Vec3> find(Level level, Vec3 from, BlockPos goal) { return find(level, from, goal, 1, 3); }

    static List<Vec3> find(Level level, Vec3 from, BlockPos goal, int half, int head) { return find(level, from, goal, half, head, Set.of(), 0); }

    static List<Vec3> find(Level level, Vec3 from, BlockPos goal, int half, int head, Set<Long> dock, double dockY) {
        Cache c = new Cache(level, half, head);
        int sx = (int) Math.floor(from.x), sz = (int) Math.floor(from.z);
        c.dock = dock; c.dockY = dockY; c.sx = sx; c.sz = sz;
        double sy = c.fits(sx, sz, (int) Math.floor(from.y + 0.2));
        if (Double.isNaN(sy)) sy = from.y;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Double> best = new HashMap<>();
        open.add(new Node(sx, sz, sy, 0.0, h(sx, sz, goal), null));
        best.put(BlockPos.asLong(sx, 0, sz), 0.0);
        int seen = 0;
        while (!open.isEmpty() && seen++ < Balance.ROBOT_PATH_NODES) {
            Node n = open.poll();
            if (Math.hypot(n.x() - goal.getX(), n.z() - goal.getZ()) <= 1.5) return smooth(n);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = n.x() + dx, nz = n.z() + dz;
                double ny = c.fits(nx, nz, (int) Math.floor(n.y() + 0.2));
                if (Double.isNaN(ny) || Math.abs(ny - n.y()) > 1.01) continue;
                if (dx != 0 && dz != 0 && (Double.isNaN(c.fits(n.x() + dx, n.z(), (int) Math.floor(n.y() + 0.2))) || Double.isNaN(c.fits(n.x(), n.z() + dz, (int) Math.floor(n.y() + 0.2))))) continue;
                double g = n.g() + (dx != 0 && dz != 0 ? 1.414 : 1.0) + Math.abs(ny - n.y()) * 0.5;
                long k = BlockPos.asLong(nx, 0, nz);
                Double old = best.get(k);
                if (old != null && old <= g) continue;
                best.put(k, g);
                open.add(new Node(nx, nz, ny, g, g + h(nx, nz, goal), n));
            }
        }
        return null;
    }

    record Result(List<Vec3> route, BlockPos closest, double shortBy, int explored, Map<Long, int[]> walls) {
        Result(List<Vec3> route, BlockPos closest, double shortBy, int explored) { this(route, closest, shortBy, explored, Map.of()); }
    }

    static Result findNear(Level level, Vec3 from, List<BlockPos> cells, int half, int head) { return findNear(level, from, cells, half, head, Set.of(), 0); }

    static Result findNear(Level level, Vec3 from, List<BlockPos> cells, int half, int head, Set<Long> dock, double dockY) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : cells) { x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX()); z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ()); }
        final int bx0 = x0, bx1 = x1, bz0 = z0, bz1 = z1, near = half + 1, far = Balance.ROBOT_STAND_RADIUS + half - 1;
        java.util.function.BiFunction<Integer, Integer, Integer> gap = (x, z) -> Math.max(x < bx0 ? bx0 - x : x > bx1 ? x - bx1 : 0, z < bz0 ? bz0 - z : z > bz1 ? z - bz1 : 0);
        Cache c = new Cache(level, half, head);
        int sx = (int) Math.floor(from.x), sz = (int) Math.floor(from.z);
        c.dock = dock; c.dockY = dockY; c.sx = sx; c.sz = sz;
        double sy = c.fits(sx, sz, (int) Math.floor(from.y + 0.2));
        if (Double.isNaN(sy)) sy = from.y;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Double> best = new HashMap<>();
        open.add(new Node(sx, sz, sy, 0.0, Math.max(0, gap.apply(sx, sz) - far), null));
        best.put(BlockPos.asLong(sx, 0, sz), 0.0);
        Node closest = open.peek();
        int closestGap = gap.apply(sx, sz), seen = 0;
        Map<Long, int[]> walls = new HashMap<>();
        while (!open.isEmpty() && seen < Balance.ROBOT_PATH_NODES) {
            Node n = open.poll();
            seen++;
            int gp = gap.apply(n.x(), n.z());
            if (gp >= near && gp <= far) return new Result(smooth(n), new BlockPos(n.x(), (int) Math.floor(n.y()), n.z()), 0.0, seen);
            if (gp < closestGap) { closestGap = gp; closest = n; }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = n.x() + dx, nz = n.z() + dz;
                if (gap.apply(nx, nz) < near && gap.apply(nx, nz) < gp) continue;
                double ny = c.fits(nx, nz, (int) Math.floor(n.y() + 0.2));
                if (Double.isNaN(ny)) { if (walls.size() < 6000) walls.put(BlockPos.asLong(c.whyX, 0, c.whyZ), new int[] { c.whyX, c.whyY, c.whyZ, c.why }); continue; }
                if (Math.abs(ny - n.y()) > 1.01) { if (walls.size() < 6000) walls.put(BlockPos.asLong(nx, 0, nz), new int[] { nx, (int) Math.floor(ny), nz, W_STEEP }); continue; }
                if (dx != 0 && dz != 0 && (Double.isNaN(c.fits(n.x() + dx, n.z(), (int) Math.floor(n.y() + 0.2))) || Double.isNaN(c.fits(n.x(), n.z() + dz, (int) Math.floor(n.y() + 0.2))))) continue;
                double g = n.g() + (dx != 0 && dz != 0 ? 1.414 : 1.0) + Math.abs(ny - n.y()) * 0.5;
                long k = BlockPos.asLong(nx, 0, nz);
                Double old = best.get(k);
                if (old != null && old <= g) continue;
                best.put(k, g);
                open.add(new Node(nx, nz, ny, g, g + Math.max(0, gap.apply(nx, nz) - far), n));
            }
        }
        for (Long k : best.keySet()) walls.remove(k);
        return new Result(null, new BlockPos(closest.x(), (int) Math.floor(closest.y()), closest.z()), Math.max(0, closestGap - far), seen, walls);
    }

    private static double h(int x, int z, BlockPos goal) {
        int dx = Math.abs(x - goal.getX()), dz = Math.abs(z - goal.getZ());
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz);
    }

    private static List<Vec3> smooth(Node end) {
        List<Vec3> pts = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent()) pts.add(0, new Vec3(n.x() + 0.5, n.y(), n.z() + 0.5));
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0 && i < pts.size() - 1) {
                Vec3 a = pts.get(i - 1), b = pts.get(i), d = pts.get(i + 1);
                if (Math.abs(b.y - a.y) < 0.01 && Math.abs(d.y - b.y) < 0.01 && Math.abs((b.x - a.x) * (d.z - b.z) - (b.z - a.z) * (d.x - b.x)) < 1e-6) continue;
            }
            out.add(pts.get(i));
        }
        return out;
    }

    static BlockPos standNear(Level level, BlockPos target, Vec3 robot) { return standNear(level, target, robot, 1, 3); }

    static BlockPos standNear(Level level, List<BlockPos> cells, Vec3 robot, int half, int head) {
        if (cells.isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE;
        for (BlockPos c : cells) { x0 = Math.min(x0, c.getX()); x1 = Math.max(x1, c.getX()); z0 = Math.min(z0, c.getZ()); z1 = Math.max(z1, c.getZ()); y0 = Math.min(y0, c.getY()); }
        Cache c = new Cache(level, half, head);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int r = Balance.ROBOT_STAND_RADIUS + half - 1;
        for (int x = x0 - r; x <= x1 + r; x++) for (int z = z0 - r; z <= z1 + r; z++) {
            int ox = x < x0 ? x0 - x : x > x1 ? x - x1 : 0, oz = z < z0 ? z0 - z : z > z1 ? z - z1 : 0;
            if (Math.max(ox, oz) < 1 + half) continue;
            double dist = Math.hypot(ox, oz);
            if (dist > r) continue;
            double y = Double.NaN;
            for (int dy : new int[] { 0, 2, -2 }) { y = c.fits(x, z, y0 + dy); if (!Double.isNaN(y)) break; }
            if (Double.isNaN(y)) continue;
            double d = dist * 4.0 + Math.hypot(x + 0.5 - robot.x, z + 0.5 - robot.z) * 0.01;
            if (d < bestD) { bestD = d; best = new BlockPos(x, (int) Math.floor(y), z); }
        }
        return best;
    }

    static BlockPos standNear(Level level, BlockPos target, Vec3 robot, int half, int head) {
        Cache c = new Cache(level, half, head);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int r = Balance.ROBOT_STAND_RADIUS + half - 1;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dx) < 1 + half && Math.abs(dz) < 1 + half) continue;
            int x = target.getX() + dx, z = target.getZ() + dz;
            double y = c.fits(x, z, target.getY());
            if (Double.isNaN(y)) continue;
            double d = Math.hypot(dx, dz) * 4.0 + Math.hypot(x + 0.5 - robot.x, z + 0.5 - robot.z) * 0.01;
            if (d < bestD) { bestD = d; best = new BlockPos(x, (int) Math.floor(y), z); }
        }
        return best;
    }
}
