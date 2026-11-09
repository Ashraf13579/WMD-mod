package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class Crater {

    private static final int CHECKS_PER_TICK = 40_000;
    private static final int MAX_DROPS = 120;

    final int cx, cy, cz;
    final double radius, depth;

    final double scorch, outer;

    final double hold;
    private final int ext, yMin, yMax;
    private int phase;
    private long cursor;
    private int drops;

    public Crater(int cx, int cy, int cz, double radius) {
        this(cx, cy, cz, radius, 0.55, 1.0, 0.0, radius * 1.5 + 16.0, 0, 0, 0);
    }

    public Crater(int cx, int cy, int cz, double radius, double depthFactor, double scorch, double outer, double hold) {
        this(cx, cy, cz, radius, depthFactor, scorch, outer, hold, 0, 0, 0);
    }

    private Crater(int cx, int cy, int cz, double radius, double depthFactor, double scorch, double outer, double hold, int phase, long cursor, int drops) {
        radius = Math.max(0.5, Math.min(radius, Config.Server.maxCraterRadius()));
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.depth = radius * depthFactor;
        this.scorch = scorch;
        this.outer = outer;
        this.hold = hold;
        this.ext = (int) Math.ceil(radius * Math.max(1.4, outer)) + 2;
        this.yMin = (int) Math.floor(cy - depth * 1.2) - 1;
        this.yMax = (int) Math.ceil(cy + radius * 0.95) + 1;
        this.phase = phase;
        this.cursor = cursor;
        this.drops = drops;
    }

    public boolean isDone() { return phase >= 2; }

    private int delay;

    public Crater delay(int ticks) { delay = Math.max(delay, ticks); return this; }

    private boolean fast;
    private java.util.List<Long> fastOrder;

    public Crater fast() { fast = true; return this; }

    private boolean glass;

    public Crater glass() { glass = true; return this; }

    static final int NO_SEA = Integer.MIN_VALUE;
    private int seaTop = NO_SEA;

    public Crater sea(int top) { seaTop = top; return this; }

    boolean wet() { return seaTop != NO_SEA; }

    private boolean seaKeeps(BlockState state) { return seaTop != NO_SEA && state.getBlock() == Blocks.WATER; }

    private boolean peak;
    static final double PEAK_RISE = 0.55, PEAK_R = 0.22;

    public Crater peak() { peak = true; return this; }

    private boolean buster;

    public Crater buster() { buster = true; return this; }

    private int ratingAt(net.minecraft.world.level.Level level, BlockPos p, BlockState state) {
        int r = Hardened.rating(level, p, state);
        return buster && r >= 3 && DeadHandBlockEntity.isBunker(level, p, state) ? 1 : r;
    }

    private Crater peakIf(boolean p) { peak = p; return this; }
    private Crater busterIf(boolean b) { buster = b; return this; }

    static double peakDepth(double depth, double rr, boolean peak) {
        if (!peak) return depth;
        double k = Math.max(0.0, 1.0 - Math.sqrt(rr) / PEAK_R);
        return depth * (1.0 - PEAK_RISE * Math.pow(k, 1.5));
    }

    private Crater fastIf(boolean f) { fast = f; return this; }

    private BlockState hole(int y) { return seaTop != NO_SEA && y <= seaTop ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(); }

    private static final int MASK_AHEAD = 48;
    private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)), r -> {
                Thread t = new Thread(r, "WMD crater shapes");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
    private final java.util.Map<Long, java.util.concurrent.CompletableFuture<long[][]>> masks = new java.util.HashMap<>();

    private long[][] mask(int ccx, int ccz, int minY, int maxY) {
        long[][] m = new long[256][];
        int x0 = ccx << 4, z0 = ccz << 4;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = x0 + lx, z = z0 + lz;
            double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5);
            double rr = (dx * dx + dz * dz) / (radius * radius);
            double nxz = noise(x, z) * 0.14, nmax = nxz + 0.04;
            if (rr >= 1.0 + nmax) continue;
            double s = Math.sqrt(Math.max(0.0, 1.0 + nmax - rr));
            int lo = Math.max(Math.max(yMin, (int) Math.floor(cy - depth * s) - 1), minY), hi = Math.min(Math.min(yMax, (int) Math.ceil(cy + radius * 0.9 * s) + 1), maxY - 1);
            if (hi < lo) continue;
            long[] col = new long[2 + ((hi - lo) >> 6) + 1];
            col[0] = lo; col[1] = hi;
            boolean any = false;
            for (int y = lo; y <= hi; y++) {
                double dy = y + 0.5 - cy;
                double n = nxz + noise(x + 1000, y * 3) * 0.04;
                double v = dy <= 0 ? rr + (dy * dy) / (peakDepth(depth, rr, peak) * peakDepth(depth, rr, peak)) : rr + (dy * dy) / (radius * radius * 0.81);
                if (v >= 1.0 + n) continue;
                col[2 + ((y - lo) >> 6)] |= 1L << ((y - lo) & 63);
                any = true;
            }
            if (any) m[lx * 16 + lz] = col;
        }
        return m;
    }

    private void askMasks(ServerLevel level) {
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
        for (long i = cursor; i < Math.min(fastOrder.size(), cursor + MASK_AHEAD); i++) {
            long key = fastOrder.get((int) i);
            if (masks.containsKey(key)) continue;
            int ccx = net.minecraft.world.level.ChunkPos.getX(key), ccz = net.minecraft.world.level.ChunkPos.getZ(key);
            masks.put(key, java.util.concurrent.CompletableFuture.supplyAsync(() -> mask(ccx, ccz, minY, maxY), POOL));
        }
    }

    private void ensureOrder() {
        if (fastOrder != null) return;
        int r = (ext >> 4) + 1, ccx = cx >> 4, ccz = cz >> 4;
        java.util.List<long[]> l = new java.util.ArrayList<>();
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) l.add(new long[] { a * a + b * b, net.minecraft.world.level.ChunkPos.asLong(ccx + a, ccz + b) });
        l.sort((p, q) -> Long.compare(p[0], q[0]));
        fastOrder = new java.util.ArrayList<>();
        for (long[] o : l) fastOrder.add(o[1]);
    }

    private void fastStep(ServerLevel level) {
        ensureOrder();
        boolean guard = Config.Server.protectSpawnRadius() > 0 || Config.Server.respectClaims();
        boolean loadedOnly = !Config.Server.chunkLoading();
        int budget = Balance.RG_FAST_CHUNKS_PER_TICK * Balance.WORK_CAP_MUL, done = 0;
        long t0 = WorkBudget.start();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        try {
        askMasks(level);
        while (cursor < fastOrder.size() && budget > 0 && (done == 0 || WorkBudget.more(t0))) {
            long key = fastOrder.get((int) cursor);
            int ccx = net.minecraft.world.level.ChunkPos.getX(key), ccz = net.minecraft.world.level.ChunkPos.getZ(key);
            net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(ccx, ccz);
            if (ch == null) {
                if (waitFor(level, p.set(ccx << 4, cy, ccz << 4), loadedOnly)) return;
                masks.remove(key);
                cursor++;
                continue;
            }
            java.util.concurrent.CompletableFuture<long[][]> f = masks.get(key);
            long[][] m;
            if (f != null && f.isDone()) m = f.join();
            else if (done == 0) m = mask(ccx, ccz, level.getMinBuildHeight(), level.getMaxBuildHeight());
            else break;
            masks.remove(key);
            cursor++;
            budget--;
            done++;
            carveChunk(level, ch, guard, p, m);
        }
        } finally { WorkBudget.done(t0); }
        if (cursor >= fastOrder.size()) { phase = scorch > 0.0 ? 1 : 2; cursor = 0; masks.clear(); }
    }

    private static final int FAST_FIRES = 220;
    private int fastFires;

    private void fastScorch(ServerLevel level) {
        ensureOrder();
        boolean guard = Config.Server.protectSpawnRadius() > 0 || Config.Server.respectClaims();
        RandomSource rnd = level.random;
        int budget = Balance.RG_FAST_CHUNKS_PER_TICK * Balance.WORK_CAP_MUL, done = 0;
        long t0 = WorkBudget.start();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        try {
        while (cursor < fastOrder.size() && budget > 0 && (done == 0 || WorkBudget.more(t0))) {
            long key = fastOrder.get((int) cursor++);
            net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key));
            if (ch == null) continue;
            budget--;
            done++;
            boolean any = false;
            int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5);
                double r = Math.sqrt(dx * dx + dz * dz) / radius, n = noise(x, z) * 0.12;
                if (r > 1.38 + n) continue;
                if (scorch < 1.0 && rnd.nextFloat() > scorch) continue;
                int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                if (top < level.getMinBuildHeight() || Math.abs(top - cy) > radius + 2.0) continue;
                p.set(x, top, z);
                if (guard && !Config.Server.canBreak(level, p)) continue;
                net.minecraft.world.level.chunk.LevelChunkSection sec = ch.getSection(ch.getSectionIndex(top));
                BlockState state = sec.getBlockState(lx, top & 15, lz);
                if (state.isAir() || state.hasBlockEntity() || needsHooks(state)) continue;
                if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    if (rnd.nextFloat() < 0.6F) { sec.setBlockState(lx, top & 15, lz, Blocks.AIR.defaultBlockState(), false); any = true; }
                    continue;
                }
                if (!state.getFluidState().isEmpty() || state.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, state) > 0) continue;
                float roll = rnd.nextFloat();
                boolean floor = r < 1.0 + n;
                BlockState scorched = null;
                if (floor && glass) {
                    if (roll < 0.006F) { level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS); continue; }
                    if (roll < 0.36F) scorched = Blocks.TINTED_GLASS.defaultBlockState();
                    else if (roll < 0.52F) scorched = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
                    else if (roll < 0.74F) scorched = Blocks.OBSIDIAN.defaultBlockState();
                    else if (roll < 0.90F) scorched = Blocks.BLACKSTONE.defaultBlockState();
                    else scorched = Blocks.BASALT.defaultBlockState();
                } else if (floor) {
                    if (roll < 0.004F) { level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS); continue; }
                    if (roll < 0.34F) scorched = Blocks.BLACKSTONE.defaultBlockState();
                    else if (roll < 0.52F) scorched = Blocks.BASALT.defaultBlockState();
                    else if (roll < 0.80F) scorched = Blocks.COARSE_DIRT.defaultBlockState();
                } else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                    if (roll < 0.45F) scorched = Blocks.COARSE_DIRT.defaultBlockState();
                    else if (roll < 0.60F) scorched = Blocks.BLACKSTONE.defaultBlockState();
                }
                if (scorched != null) { sec.setBlockState(lx, top & 15, lz, scorched, false); any = true; }
                if (fastFires < FAST_FIRES && rnd.nextFloat() < 0.01F) {
                    BlockPos up = p.above();
                    if (level.getBlockState(up).isAir()) { level.setBlock(up, BaseFireBlock.getState(level, up), Block.UPDATE_ALL); fastFires++; }
                }
            }
            if (!any) continue;
            ch.setUnsaved(true);
            Heightmap.primeHeightmaps(ch, java.util.EnumSet.allOf(Heightmap.Types.class));
            TargetSnapshot.changed(level, ch.getPos().x, ch.getPos().z);
            CraterLight.sendWhenLit(level, ch);
        }
        } finally { WorkBudget.done(t0); }
        if (cursor >= fastOrder.size()) phase = 2;
    }

    private void carveChunk(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk ch, boolean guard, BlockPos.MutableBlockPos p, long[][] m) {
        int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
        boolean any = false;
        java.util.List<BlockPos> floors = new java.util.ArrayList<>();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            long[] col = m[lx * 16 + lz];
            if (col == null) continue;
            int x = x0 + lx, z = z0 + lz, lo = (int) col[0], hi = (int) col[1];
            int lowest = Integer.MAX_VALUE;
            for (int y = hi; y >= lo; y--) {
                net.minecraft.world.level.chunk.LevelChunkSection sec = ch.getSection(ch.getSectionIndex(y));
                if (sec.hasOnlyAir()) { y = y & ~15; continue; }
                if ((col[2 + ((y - lo) >> 6)] & (1L << ((y - lo) & 63))) == 0) continue;
                BlockState state = sec.getBlockState(lx, y & 15, lz);
                if (state.isAir() || seaKeeps(state)) continue;
                p.set(x, y, z);
                if (guard && !Config.Server.canBreak(level, p)) continue;
                if (state.getDestroySpeed(level, p) < 0) continue;
                int rating = ratingAt(level, p, state);
                if (rating > 0) {
                    double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5), dy = y + 0.5 - cy, rr = (dx * dx + dz * dz) / (radius * radius);
                    double v = dy <= 0 ? rr + (dy * dy) / (peakDepth(depth, rr, peak) * peakDepth(depth, rr, peak)) : rr + (dy * dy) / (radius * radius * 0.81);
                    if (Hardened.survives(rating, Math.sqrt(v))) continue;
                }
                if (state.hasBlockEntity() || needsHooks(state)) {
                    if (state.hasBlockEntity()) CcAlarms.exploded(level, p.immutable());
                    level.setBlock(p, hole(y), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } else sec.setBlockState(lx, y & 15, lz, hole(y), false);
                any = true;
                lowest = Math.min(lowest, y);
            }
            if (lowest != Integer.MAX_VALUE) floors.add(new BlockPos(x, lowest, z));
        }
        if (!any) return;
        ch.setUnsaved(true);
        Heightmap.primeHeightmaps(ch, java.util.EnumSet.allOf(Heightmap.Types.class));
        ch.initializeLightSources();
        var light = level.getChunkSource().getLightEngine();
        for (int si = 0; si < ch.getSectionsCount(); si++)
            light.updateSectionStatus(net.minecraft.core.SectionPos.of(ch.getPos(), ch.getSectionYFromSectionIndex(si)), ch.getSection(si).hasOnlyAir());
        for (BlockPos f : floors) { light.checkBlock(f); light.checkBlock(f.above()); }
        TargetSnapshot.changed(level, ch.getPos().x, ch.getPos().z);
        CraterLight.sendWhenLit(level, ch);
    }

    static boolean needsHooks(BlockState s) {
        net.minecraft.world.level.block.Block b = s.getBlock();
        return b instanceof ReactorPartBlock || b instanceof RadarPartBlock || b instanceof PadFillerBlock || b instanceof PadPlateBlock;
    }

    static double noise(int x, int z) {
        int gx = Math.floorDiv(x, 5), gz = Math.floorDiv(z, 5);
        double fx = (x - gx * 5) / 5.0, fz = (z - gz * 5) / 5.0;
        double a = hash(gx, gz), b = hash(gx + 1, gz), c = hash(gx, gz + 1), d = hash(gx + 1, gz + 1);
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double hash(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h >>> 20) & 0xFFFF) / 32767.5 - 1.0;
    }

    private int waited;
    private static final int MAX_WAIT = 200;

    private static final int MAX_WAIT_FAST = 2_400;

    private boolean waitFor(ServerLevel level, BlockPos p, boolean loadedOnly) {
        if (loadedOnly || waited >= (fast ? MAX_WAIT_FAST : MAX_WAIT)) return false;
        if (!FlightManager.get(level).holds(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4))) return false;
        waited++;
        return true;
    }

    public void step(ServerLevel level) {
        if (delay > 0) { delay--; return; }
        if (!Config.Server.blockDamage()) { phase = 2; return; }
        if (fast && phase == 0) { fastStep(level); return; }
        if (fast && phase == 1) { fastScorch(level); return; }
        boolean loadedOnly = !Config.Server.chunkLoading();
        int spawnR = Config.Server.protectSpawnRadius();
        boolean guard = spawnR > 0 || Config.Server.respectClaims();
        RandomSource rnd = level.random;
        int w = ext * 2 + 1;
        int checks = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (phase == 0) {
            long total = (long) w * w * (yMax - yMin + 1);
            long t0 = WorkBudget.start();
            try {
            while (cursor < total && checks < CHECKS_PER_TICK * Balance.WORK_CAP_MUL && ((checks & 1023) != 1023 || WorkBudget.more(t0))) {
                long i = cursor++;
                checks++;
                int layer = (int) (i / ((long) w * w));
                int rest = (int) (i % ((long) w * w));
                int y = yMax - layer;
                int x = cx - ext + rest / w;
                int z = cz - ext + rest % w;
                if (level.isOutsideBuildHeight(y)) continue;
                double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5), dy = y + 0.5 - cy;
                double n = noise(x, z) * 0.14 + noise(x + 1000, y * 3) * 0.04;
                double rr = (dx * dx + dz * dz) / (radius * radius);
                double v = dy <= 0 ? rr + (dy * dy) / (peakDepth(depth, rr, peak) * peakDepth(depth, rr, peak)) : rr + (dy * dy) / (radius * radius * 0.81);
                boolean weakOnly = false;
                if (v >= 1.0 + n) {

                    if (outer <= 0.0 || dy < -2.0 || dy > radius * 0.8 || rr >= outer * outer * (1.0 + n)) continue;
                    weakOnly = true;
                }
                p.set(x, y, z);
                if (!level.hasChunkAt(p)) {
                    if (waitFor(level, p, loadedOnly)) { cursor--; break; }
                    continue;
                }
                if (guard && !Config.Server.canBreak(level, p)) continue;
                BlockState state = level.getBlockState(p);
                if (state.isAir() || seaKeeps(state)) continue;
                if (state.getDestroySpeed(level, p) < 0) continue;
                int rating = ratingAt(level, p, state);
                if (rating > 0 && Hardened.survives(rating, Math.sqrt(v))) continue;
                if (weakOnly && (state.getDestroySpeed(level, p) > 3.0F || !state.getFluidState().isEmpty())) continue;
                if (drops < MAX_DROPS && state.getFluidState().isEmpty() && rnd.nextFloat() < 0.03F) {
                    Block.dropResources(state, level, p);
                    drops++;
                }
                if (state.hasBlockEntity()) CcAlarms.exploded(level, p.immutable());
                level.setBlock(p, hole(y), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            } finally { WorkBudget.done(t0); }
            if (cursor >= total) { phase = scorch > 0.0 ? 1 : 2; cursor = 0; }
        } else if (phase == 1) {
            long total = (long) w * w;
            long t0 = WorkBudget.start();
            try {
            while (cursor < total && checks < CHECKS_PER_TICK / 8 * Balance.WORK_CAP_MUL && ((checks & 127) != 127 || WorkBudget.more(t0))) {
                long i = cursor++;
                checks++;
                int x = cx - ext + (int) (i / w);
                int z = cz - ext + (int) (i % w);
                double dx = x + 0.5 - (cx + 0.5), dz = z + 0.5 - (cz + 0.5);
                double r = Math.sqrt(dx * dx + dz * dz) / radius;
                double n = noise(x, z) * 0.12;
                if (r > 1.38 + n) continue;
                if (scorch < 1.0 && rnd.nextFloat() > scorch) continue;
                if (!level.hasChunkAt(p.set(x, cy, z))) {
                    if (waitFor(level, p, loadedOnly)) { cursor--; break; }
                    continue;
                }
                if (guard && !Config.Server.canBreak(level, p.set(x, cy, z))) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (top < level.getMinBuildHeight() || Math.abs(top - cy) > radius + 2.0) continue;
                p.set(x, top, z);
                BlockState state = level.getBlockState(p);
                if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    if (rnd.nextFloat() < 0.6F) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    continue;
                }
                if (!state.getFluidState().isEmpty() || state.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, state) > 0) continue;
                float roll = rnd.nextFloat();
                boolean floor = r < 1.0 + n;
                BlockState scorched = null;
                if (floor) {
                    if (roll < 0.30F) scorched = Blocks.BLACKSTONE.defaultBlockState();
                    else if (roll < 0.45F) scorched = Blocks.BASALT.defaultBlockState();
                    else if (roll < 0.52F) scorched = Blocks.MAGMA_BLOCK.defaultBlockState();
                    else if (roll < 0.80F) scorched = Blocks.COARSE_DIRT.defaultBlockState();
                } else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                    if (roll < 0.40F) scorched = Blocks.COARSE_DIRT.defaultBlockState();
                    else if (roll < 0.55F) scorched = Blocks.BLACKSTONE.defaultBlockState();
                    else if (roll < 0.60F) scorched = Blocks.MAGMA_BLOCK.defaultBlockState();
                }
                if (scorched != null) level.setBlock(p, scorched, Block.UPDATE_CLIENTS);
                float fireChance = (float) ((floor ? 0.06F : 0.14F) * scorch);
                BlockPos above = p.above();
                if (rnd.nextFloat() < fireChance && level.getBlockState(above).isAir()) {
                    level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
                }
            }
            } finally { WorkBudget.done(t0); }
            if (cursor >= total) phase = 2;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("x", cx); t.putInt("y", cy); t.putInt("z", cz);
        t.putDouble("r", radius); t.putInt("phase", phase); t.putLong("cursor", cursor); t.putInt("drops", drops);
        t.putDouble("df", depth / radius); t.putDouble("sc", scorch); t.putDouble("out", outer); t.putDouble("hold", hold);
        if (delay > 0) t.putInt("dl", delay);
        if (fast) t.putBoolean("fast", true);
        if (peak) t.putBoolean("peak", true);
        if (buster) t.putBoolean("buster", true);
        if (glass) t.putBoolean("glass", true);
        if (seaTop != NO_SEA) t.putInt("sea", seaTop);
        return t;
    }

    public static Crater load(CompoundTag t) {
        double r = t.getDouble("r");
        Crater c = new Crater(t.getInt("x"), t.getInt("y"), t.getInt("z"), r, t.contains("df") ? t.getDouble("df") : 0.55, t.contains("sc") ? t.getDouble("sc") : 1.0,
                t.contains("out") ? t.getDouble("out") : 0.0, t.contains("hold") ? t.getDouble("hold") : r * 1.5 + 16.0, t.getInt("phase"), t.getLong("cursor"), t.getInt("drops"))
                .delay(t.getInt("dl")).fastIf(t.getBoolean("fast")).peakIf(t.getBoolean("peak")).busterIf(t.getBoolean("buster"));
        if (t.getBoolean("glass")) c.glass();
        if (t.contains("sea")) c.sea(t.getInt("sea"));
        return c;
    }
}
