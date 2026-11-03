package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Collapse {

    private Collapse() {}

    private record Job(ServerLevel level, Crater crater, int cx, int cy, int cz, int minY, long at, double outer, List<Long> chunks, int[] cursor, int[] fallen, int[] bodies, Set<Long> weighed,
                       long[] ns, ArrayDeque<Long> crumble, double topple, double[] dust) {}

    private static final List<Job> JOBS = Statics.server(new ArrayList<>());

    private static final int QUIET = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    static void queue(ServerLevel level, Crater crater, DamageRings.Profile p) {
        if (!Config.Server.collapse() || crater.radius < 1.5) return;
        double outer = Math.min(Math.max(1.5, Math.max(p.rubble(), Math.max(p.burn(), p.strip()))) * crater.radius, Balance.COLLAPSE_MAX_R);
        add(level, crater, crater.cx, crater.cy, crater.cz, crater.cy - (int) Math.ceil(crater.depth) - Balance.COLLAPSE_BELOW, 0L, outer);
    }

    static void queueAt(ServerLevel level, BlockPos at, double outer) {
        if (!Config.Server.collapse()) return;
        add(level, null, at.getX(), at.getY(), at.getZ(), at.getY() - Balance.C4_DEPTH - Balance.COLLAPSE_BELOW, level.getGameTime() + Balance.C4_COLLAPSE_DELAY, outer);
    }

    private static void add(ServerLevel level, Crater crater, int x, int y, int z, int minY, long at, double outer) {
        int r = (int) Math.ceil(outer) + 1;
        List<Long> chunks = new ArrayList<>();
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++)
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) chunks.add(ChunkPos.asLong(cx, cz));
        chunks.sort(java.util.Comparator.comparingDouble(c -> {
            double dx = (ChunkPos.getX(c) << 4) + 8 - x, dz = (ChunkPos.getZ(c) << 4) + 8 - z;
            return dx * dx + dz * dz;
        }));
        JOBS.add(new Job(level, crater, x, y, z, minY, at, outer, chunks, new int[1], new int[1], new int[1], new HashSet<>(), new long[2], new ArrayDeque<>(),
                level.random.nextDouble() * Math.PI * 2.0, new double[] { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, 0, 0, 0 }));
        LAST.add(new Reach(level.getGameTime(), x, z, outer));
        if (LAST.size() > 8) LAST.remove(0);
        ImpactProfiler.watchAny(crater == null ? "c4 collapse" : "collapse r" + (int) crater.radius);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        long pt0 = ImpactProfiler.start();
        try { tick(event); } finally { ImpactProfiler.add(ImpactProfiler.COLLAPSE, pt0); }
    }

    private static void tick(LevelTickEvent.Post event) {
        if (JOBS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long t0 = WorkBudget.start();
        try {
            for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
                Job j = it.next();
                if (j.level != sl || sl.getGameTime() < j.at || j.crater != null && (!j.crater.isDone() || DamageRings.busy(j.crater))) continue;
                int done = 0;
                while (j.cursor[0] < j.chunks.size() && (done == 0 || WorkBudget.more(t0))) {
                    long c = j.chunks.get(j.cursor[0]++);
                    int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
                    if (!sl.hasChunk(cx, cz)) continue;
                    long c0 = System.nanoTime();
                    if (chunk(sl, j, cx, cz)) TargetSnapshot.changed(sl, cx, cz);
                    long dt = System.nanoTime() - c0;
                    j.ns[0] += dt;
                    j.ns[1] = Math.max(j.ns[1], dt);
                    done++;
                }
                if (!j.crumble.isEmpty()) crumble(sl, j, t0);
                if (j.cursor[0] >= j.chunks.size() && j.crumble.isEmpty()) {
                    dust(sl, j);
                    it.remove();
                    Wmd.LOGGER.info("[collapse] done at {} {}: {} blocks fell, {} as Sable pieces; work {} ms, slowest chunk {} ms", j.cx, j.cz, j.fallen[0], j.bodies[0],
                            j.ns[0] / 1_000_000L, j.ns[1] / 1_000_000L);
                }
                if (!WorkBudget.more(t0)) break;
            }
        } finally { WorkBudget.done(t0); }
    }

    private static void crumble(ServerLevel level, Job j, long t0) {
        Set<Long> changed = new HashSet<>();
        int n = 0;
        int least = Math.max(32, LoadGuard.scale(Balance.COLLAPSE_CRUMBLE_MIN, 0.25));
        while (!j.crumble.isEmpty() && (n < least || WorkBudget.more(t0))) {
            BlockPos p = BlockPos.of(j.crumble.poll());
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !BlockRules.blastRemovable(level, p, s)) continue;
            if ((n++ & 15) == 0) level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(s));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
            j.fallen[0]++;
            changed.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
            double[] d = j.dust;
            d[0] = Math.min(d[0], p.getX()); d[1] = Math.max(d[1], p.getX()); d[2] = Math.min(d[2], p.getZ()); d[3] = Math.max(d[3], p.getZ()); d[4] += p.getY(); d[5]++;
        }
        for (long c : changed) TargetSnapshot.changed(level, ChunkPos.getX(c), ChunkPos.getZ(c));
        if (level.getGameTime() - (long) j.dust[6] >= 20) dust(level, j);
    }

    private static void dust(ServerLevel level, Job j) {
        double[] d = j.dust;
        if (d[5] >= Balance.COLLAPSE_DUST_MIN)
            Aftermath.collapseDust(level, (d[0] + d[1]) * 0.5 + 0.5, d[4] / d[5], (d[2] + d[3]) * 0.5 + 0.5, (float) (Math.max(d[1] - d[0], d[3] - d[2]) * 0.5 + 2.0));
        d[0] = Double.MAX_VALUE; d[1] = -Double.MAX_VALUE; d[2] = Double.MAX_VALUE; d[3] = -Double.MAX_VALUE; d[4] = 0; d[5] = 0;
        d[6] = level.getGameTime();
    }

    private static boolean chunk(ServerLevel level, Job j, int cx, int cz) {
        List<BlockPos> cand = new ArrayList<>();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = (cx << 4) + lx, z = (cz << 4) + lz;
            double d = Math.sqrt((double) (x - j.cx) * (x - j.cx) + (double) (z - j.cz) * (z - j.cz));
            if (d > j.outer) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, low = Math.max(Math.max(level.getMinBuildHeight(), top - Balance.COLLAPSE_SPAN), j.minY);
            for (int y = top; y >= low; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir()) continue;
                if (ground(level, p, s)) break;
                cand.add(p);
            }
        }
        if (cand.isEmpty()) return false;
        cand.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        Set<Long> falling = new HashSet<>();
        Map<Long, Boolean> vert = new HashMap<>();
        List<BlockPos> hanging = new ArrayList<>();
        for (BlockPos p : cand) {
            BlockState s = level.getBlockState(p);
            if (!solid(level, p, s)) continue;
            if (vertical(level, p, falling, vert)) continue;
            if (spanSupport(level, p, s, falling, vert) == null) { falling.add(p.asLong()); continue; }
            hanging.add(p);
        }
        for (BlockPos p : hanging)
            if (!falling.contains(p.asLong()) && !j.weighed.contains(p.asLong())) weigh(level, p, falling, vert, j.weighed, j.minY);

        Set<Long> grouped = new HashSet<>();
        boolean any = false;
        List<Long> starts = new ArrayList<>(falling);
        starts.sort(java.util.Comparator.comparingInt(l -> BlockPos.getY(l)));
        for (long start : starts) {
            if (grouped.contains(start)) continue;
            List<BlockPos> piece = piece(level, start, falling, vert, grouped, j.minY);
            List<BlockPos> ok = new ArrayList<>(piece.size());
            for (BlockPos p : piece) if (BlockRules.blastRemovable(level, p, level.getBlockState(p))) ok.add(p);
            if (ok.isEmpty()) continue;
            any = true;

            int from = 0;
            while (ok.size() - from >= Balance.COLLAPSE_BODY_MIN && j.bodies[0] < LoadGuard.scale(Balance.COLLAPSE_MAX_BODIES, 0.0) && SableBridge.debrisActive()) {
                int to = Math.min(ok.size(), from + Balance.COLLAPSE_BODY_MAX);
                List<BlockPos> slice = breakLine(level, new ArrayList<>(ok.subList(from, to)));
                from = to;
                if (slice.size() < Balance.COLLAPSE_BODY_MIN) continue;
                BlockPos anchor = slice.get(0);
                BlockState look = level.getBlockState(anchor);
                long until = level.getGameTime() + Balance.COLLAPSE_BODY_LIFE;
                java.util.UUID id = SableBridge.debrisSpawn(level, anchor, slice, until);
                if (id == null) { j.crumble.addAll(slice.stream().map(BlockPos::asLong).toList()); continue; }

                double sx = 0, sz = 0;
                int y0 = slice.get(0).getY(), y1 = slice.get(slice.size() - 1).getY();
                for (BlockPos q : slice) { sx += q.getX(); sz += q.getZ(); }

                double ox = sx / slice.size() + 0.5 - j.cx, oz = sz / slice.size() + 0.5 - j.cz, ol = Math.sqrt(ox * ox + oz * oz);
                double out = Math.min(1.0, ol / Balance.COLLAPSE_TOPPLE_R), dx = (ol > 1.0E-3 ? ox / ol : 0.0) * out + Math.cos(j.topple) * (1.0 - out),
                        dz = (ol > 1.0E-3 ? oz / ol : 0.0) * out + Math.sin(j.topple) * (1.0 - out), dl = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
                double force = Math.min(Balance.COLLAPSE_PUSH_MAX, Balance.COLLAPSE_PUSH + Math.max(0, y0 - j.cy) / Balance.COLLAPSE_PUSH_PER_BLOCK)
                        * Balance.blastPower(j.crater != null ? j.crater.radius : 0.0);
                Vec3 push = new Vec3(dx / dl * force, 0.0, dz / dl * force), off = new Vec3(0.0, (y1 - y0) * Balance.COLLAPSE_PUSH_HIGH + 0.5, 0.0);
                Debris.adopt(level, id, until, look, slice.size(), push, off);
                j.bodies[0]++; j.fallen[0] += slice.size();
            }
            for (BlockPos p : ok.subList(from, ok.size())) {
                BlockState s = level.getBlockState(p);
                if (s.isAir()) continue;
                if (j.fallen[0] < LoadGuard.scale(Balance.COLLAPSE_MAX_FALLING, 0.25) && s.isCollisionShapeFullBlock(level, p)) {
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, p, s);
                    fb.dropItem = false;
                    j.fallen[0]++;
                } else {
                    j.crumble.add(p.asLong());
                }
            }
        }

        for (BlockPos p : cand) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || solid(level, p, s) || holds(level, p, s) || falling.contains(p.asLong())) continue;
            if (attached(level, p, falling, vert)) continue;
            j.crumble.add(p.asLong());
            any = true;
        }
        return any;
    }

    private static boolean attached(ServerLevel level, BlockPos p, Set<Long> falling, Map<Long, Boolean> vert) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Map<Long, Integer> dist = new HashMap<>();
        open.add(p);
        dist.put(p.asLong(), 0);
        while (!open.isEmpty()) {
            BlockPos c = open.poll();
            int d = dist.get(c.asLong());
            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                long nl = n.asLong();
                if (dist.containsKey(nl) || falling.contains(nl)) continue;
                if (!level.hasChunkAt(n)) return true;
                BlockState ns = level.getBlockState(n);
                if (ns.isAir()) continue;
                if (ground(level, n, ns) || holds(level, n, ns)) return true;
                if (solid(level, n, ns)) {
                    if (vertical(level, n, falling, vert) || spanSupport(level, n, ns, falling, vert) != null) return true;
                    continue;
                }
                if (!ns.getFluidState().isEmpty() || d + 1 >= Balance.COLLAPSE_DECOR_REACH) continue;
                dist.put(nl, d + 1);
                open.add(n);
            }
        }
        return false;
    }

    private static List<BlockPos> breakLine(ServerLevel level, List<BlockPos> ok) {
        Set<Long> in = new HashSet<>(ok.size() * 2);
        for (BlockPos p : ok) in.add(p.asLong());
        List<BlockPos> free = new ArrayList<>(ok.size()), cut = new ArrayList<>();
        for (BlockPos p : ok) {
            boolean touches = false;
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (in.contains(n.asLong()) || !level.hasChunkAt(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (!ns.getCollisionShape(level, n).isEmpty()) { touches = true; break; }
            }
            (touches ? cut : free).add(p);
        }
        int puffs = 0;
        for (BlockPos p : cut) {
            if (!BlockRules.blastRemovable(level, p, level.getBlockState(p))) continue;
            if (puffs++ < 48) level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(level.getBlockState(p)));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
        }
        return free;
    }

    private static List<BlockPos> piece(ServerLevel level, long start, Set<Long> falling, Map<Long, Boolean> vert, Set<Long> grouped, int minY) {
        List<BlockPos> out = new ArrayList<>();
        ArrayDeque<Long> open = new ArrayDeque<>();
        open.add(start);
        grouped.add(start);
        while (!open.isEmpty() && out.size() < Balance.COLLAPSE_PIECE_MAX) {
            BlockPos c = BlockPos.of(open.poll());
            out.add(c);
            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                long nl = n.asLong();
                if (grouped.contains(nl) || !level.hasChunkAt(n) || n.getY() < minY) continue;
                if (!falling.contains(nl)) {
                    BlockState ns = level.getBlockState(n);
                    if (!solid(level, n, ns)) {
                        if (ns.isAir() || !ns.getFluidState().isEmpty() || ns.hasBlockEntity() || ground(level, n, ns) || attached(level, n, falling, vert)) continue;
                    } else if (ground(level, n, ns) || ns.hasBlockEntity() || vertical(level, n, falling, vert) || spanSupport(level, n, ns, falling, vert) != null) continue;
                    falling.add(nl);
                }
                grouped.add(nl);
                open.add(nl);
            }
        }
        out.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        return out;
    }

    private static boolean solid(ServerLevel level, BlockPos p, BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level, p);
    }

    static boolean ground(ServerLevel level, BlockPos p, BlockState s) {
        if (!DamageRings.natural(s)) return false;
        BlockPos.MutableBlockPos q = p.mutable();
        for (int i = 0; i < GROUND_DEPTH; i++) {
            q.move(Direction.DOWN);
            if (q.getY() < level.getMinBuildHeight() || !level.hasChunkAt(q)) return true;
            if (!DamageRings.natural(level.getBlockState(q))) return false;
        }
        return true;
    }

    private static final int GROUND_DEPTH = 3;

    private static boolean holds(ServerLevel level, BlockPos p, BlockState s) {
        return s.hasBlockEntity() && !s.getCollisionShape(level, p).isEmpty();
    }

    private static boolean vertical(ServerLevel level, BlockPos p, Set<Long> falling, Map<Long, Boolean> memo) {
        Boolean m = memo.get(p.asLong());
        if (m != null) return m;
        boolean ok = true;
        BlockPos.MutableBlockPos q = p.mutable();
        for (int i = 0; i < Balance.COLLAPSE_SPAN + 8; i++) {
            q.move(Direction.DOWN);
            if (!level.hasChunkAt(q)) { ok = true; break; }
            if (falling.contains(q.asLong())) { ok = false; break; }
            BlockState s = level.getBlockState(q);
            if (ground(level, q, s) || holds(level, q, s) || Hardened.rating(level, q, s) > 0 || s.getDestroySpeed(level, q) < 0.0F) { ok = s.getFluidState().isEmpty(); break; }
            if (!solid(level, q, s)) { ok = false; break; }
            Boolean below = memo.get(q.asLong());
            if (below != null) { ok = below; break; }
        }
        memo.put(p.asLong(), ok);
        return ok;
    }

    private static BlockPos spanSupport(ServerLevel level, BlockPos p, BlockState s, Set<Long> falling, Map<Long, Boolean> vert) {
        int span = span(level, p, s);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Map<Long, Integer> dist = new HashMap<>();
        open.add(p);
        dist.put(p.asLong(), 0);
        while (!open.isEmpty()) {
            BlockPos c = open.poll();
            int d = dist.get(c.asLong());
            if (d >= span) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = c.relative(dir);
                if (dist.containsKey(n.asLong()) || falling.contains(n.asLong())) continue;
                if (!level.hasChunkAt(n)) return n;
                BlockState ns = level.getBlockState(n);
                if (!solid(level, n, ns) && !holds(level, n, ns)) continue;
                if (holds(level, n, ns) || vertical(level, n, falling, vert)) return n;
                dist.put(n.asLong(), d + 1);
                open.add(n);
            }
        }
        return null;
    }

    static int span(ServerLevel level, BlockPos p, BlockState s) {
        if (Hardened.rating(level, p, s) > 0) return 8;
        if (s.is(Tags.Blocks.STORAGE_BLOCKS)) return 10;
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOOL)) return 4;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (id.contains("concrete")) return 8;
        return 6;
    }

    record Weight(int mass, long capacity, int columns, boolean capped) {}

    private static Weight weigh(ServerLevel level, BlockPos start, Set<Long> falling, Map<Long, Boolean> vert, Set<Long> weighed, int minY) {
        List<Long> mass = new ArrayList<>();
        Map<Long, Integer> cols = new HashMap<>();
        ArrayDeque<Long> open = new ArrayDeque<>();
        open.add(start.asLong());
        weighed.add(start.asLong());
        while (!open.isEmpty() && mass.size() < Balance.COLLAPSE_HANG_MAX) {
            BlockPos c = BlockPos.of(open.poll());
            mass.add(c.asLong());
            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                long nl = n.asLong();
                if (weighed.contains(nl) || falling.contains(nl) || n.getY() < minY) continue;
                if (!level.hasChunkAt(n)) { cols.merge(column(n), Integer.MAX_VALUE / 4, Math::max); continue; }
                BlockState ns = level.getBlockState(n);
                boolean held = ground(level, n, ns) || holds(level, n, ns);
                if (!held && !solid(level, n, ns)) continue;
                if (held || vertical(level, n, falling, vert)) {
                    if (dir.getAxis() != Direction.Axis.Y) cols.merge(column(n), span(level, n, ns) * Balance.COLLAPSE_HANG_LOAD, Math::max);
                    continue;
                }
                weighed.add(nl);
                open.add(nl);
            }
        }
        long capacity = 0;
        for (int v : cols.values()) capacity += v;
        if (mass.size() > capacity) falling.addAll(mass);
        return new Weight(mass.size(), capacity, cols.size(), mass.size() >= Balance.COLLAPSE_HANG_MAX);
    }

    private record Reach(long time, int x, int z, double outer) {}

    private static final List<Reach> LAST = Statics.server(new ArrayList<>());

    static String why(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        String what = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " at " + p.getX() + " " + p.getY() + " " + p.getZ();
        StringBuilder reach = new StringBuilder();
        for (int i = LAST.size() - 1; i >= 0 && i >= LAST.size() - 3; i--) {
            Reach r = LAST.get(i);
            double d = Math.sqrt((double) (p.getX() - r.x) * (p.getX() - r.x) + (double) (p.getZ() - r.z) * (p.getZ() - r.z));
            reach.append(String.format(" | collapse at %d %d looked %.0f blocks out, this is %.0f away%s", r.x, r.z, r.outer, d, d > r.outer ? " (NOT CHECKED)" : ""));
        }
        if (LAST.isEmpty()) reach.append(" | no collapse yet this session");
        SableBridge.ShipInfo ship = SableBridge.shipAt(level, p);
        if (ship != null) return String.format("%s: a SABLE BODY ('%s', %d blocks) at %.0f %.0f %.0f, moving %.1f blocks/s (a collapse / debris piece: 0 = stuck)",
                what, ship.name(), ship.blockCount(), ship.worldCenter().x, ship.worldCenter().y, ship.worldCenter().z, ship.linearVelocity().length());
        if (s.isAir()) return what + ": air" + reach;
        if (ground(level, p, s)) return what + ": GROUND (natural, with natural under it)" + reach;
        if (holds(level, p, s)) return what + ": a block entity, never moved; it holds what touches it" + reach;
        if (!solid(level, p, s)) return what + ": not a full block, it goes with what it hangs on" + reach;
        Set<Long> falling = new HashSet<>();
        Map<Long, Boolean> vert = new HashMap<>();
        if (vertical(level, p, falling, vert)) {
            BlockPos.MutableBlockPos q = p.mutable();
            BlockState b;
            do { q.move(Direction.DOWN); b = level.getBlockState(q); } while (q.getY() > level.getMinBuildHeight() && solid(level, q, b) && !ground(level, q, b) && !holds(level, q, b));
            String foot = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
            return String.format("%s: STANDS, VERTICAL: solid blocks straight down to %s at y %d", what, foot, q.getY()) + reach;
        }
        BlockPos sup = spanSupport(level, p, s, falling, vert);
        if (sup == null) return String.format("%s: WOULD FALL, no supported block within its span %d", what, span(level, p, s)) + reach;
        Weight w = weigh(level, p, falling, vert, new HashSet<>(), level.getMinBuildHeight());
        return String.format("%s: HANGS on %d %d %d (span %d); its hanging mass %d%s blocks on %d columns holding %d: %s", what, sup.getX(), sup.getY(), sup.getZ(),
                span(level, p, s), w.mass(), w.capped() ? "+" : "", w.columns(), w.capacity(), w.mass() > w.capacity() ? "WOULD FALL (too heavy)" : "STANDS") + reach;
    }

    private static long column(BlockPos p) { return ((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL); }
}
