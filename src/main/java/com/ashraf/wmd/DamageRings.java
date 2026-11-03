package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DamageRings {

    private DamageRings() {}

    record Profile(double rubble, double burn, double strip, boolean fire) {}

    static final Profile NONE = new Profile(0, 0, 0, false);

    static Profile profile(WarheadType t) {
        return switch (t) {
            case HE, HEAVY_BLAST, BUNKER, PENETRATOR, BREACHER, DAM_BREAKER, UPHEAVAL, GRAVITY -> new Profile(1.4, 1.7, 2.2, false);
            case INCENDIARY, THERMOBARIC -> new Profile(1.3, 2.6, 3.0, true);
            case NUKE, THERMO, TSAR, PLASMA, ANTIMATTER -> new Profile(1.5, 2.5, 3.5, true);
            case RAILGUN, ORBITAL_ROD, ASTEROID, ROD -> new Profile(1.4, 1.8, 2.8, true);
            case CLUSTER, CARPET -> new Profile(1.3, 1.5, 1.8, false);
            case CONCUSSION -> new Profile(1.6, 0, 3.5, false);
            case DAISY_CUTTER -> new Profile(1.2, 0, 2.0, false);
            default -> NONE;
        };
    }

    private record Job(ServerLevel level, Crater crater, Profile p, List<Long> chunks, int[] cursor) {}

    private static final List<Job> JOBS = Statics.server(new ArrayList<>());

    static void queue(ServerLevel level, Crater crater, WarheadType t) {
        Profile p = profile(t);
        Collapse.queue(level, crater, p);
        Aftermath.start(level, crater, t);
        if (!Config.Server.damageRings()) return;
        double outer = Math.max(p.rubble, Math.max(p.burn, p.strip)) * crater.radius;
        if (outer <= crater.radius || crater.radius < 1.5) return;
        outer = Math.min(outer, Balance.RINGS_MAX_R);
        int r = (int) Math.ceil(outer), x0 = (crater.cx - r) >> 4, x1 = (crater.cx + r) >> 4, z0 = (crater.cz - r) >> 4, z1 = (crater.cz + r) >> 4;
        List<Long> chunks = new ArrayList<>();
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
        chunks.sort(java.util.Comparator.comparingDouble(c -> {
            double dx = (net.minecraft.world.level.ChunkPos.getX(c) << 4) + 8 - crater.cx, dz = (net.minecraft.world.level.ChunkPos.getZ(c) << 4) + 8 - crater.cz;
            return dx * dx + dz * dz;
        }));
        JOBS.add(new Job(level, crater, p, chunks, new int[1]));
        Wmd.LOGGER.info("[rings] {} at {} {}: crater r {}, rings to {} blocks, {} chunks", t.id, crater.cx, crater.cz, Math.round(crater.radius), Math.round(outer), chunks.size());
    }

    static boolean busy(Crater c) {
        for (Job j : JOBS) if (j.crater == c) return true;
        return false;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        long pt0 = ImpactProfiler.start();
        try { tick(event); } finally { ImpactProfiler.add(ImpactProfiler.RINGS, pt0); }
    }

    private static void tick(LevelTickEvent.Post event) {
        if (JOBS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long t0 = WorkBudget.start();
        try {
            for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
                Job j = it.next();
                if (j.level != sl || !j.crater.isDone()) continue;
                int done = 0;
                while (j.cursor[0] < j.chunks.size() && (done == 0 || WorkBudget.more(t0))) {
                    long c = j.chunks.get(j.cursor[0]++);
                    int cx = net.minecraft.world.level.ChunkPos.getX(c), cz = net.minecraft.world.level.ChunkPos.getZ(c);
                    if (!sl.hasChunk(cx, cz)) continue;
                    if (chunk(sl, j, cx, cz)) TargetSnapshot.changed(sl, cx, cz);
                    done++;
                }
                if (j.cursor[0] >= j.chunks.size()) {
                    it.remove();
                    Wmd.LOGGER.info("[rings] done at {} {}", j.crater.cx, j.crater.cz);
                }
                if (!WorkBudget.more(t0)) break;
            }
        } finally { WorkBudget.done(t0); }
    }

    private static boolean chunk(ServerLevel level, Job j, int cx, int cz) {
        Crater k = j.crater;
        double r = k.radius, rub = j.p.rubble * r, burn = j.p.burn * r, strip = j.p.strip * r, outer = Math.min(Math.max(rub, Math.max(burn, strip)), Balance.RINGS_MAX_R);
        RandomSource rnd = level.random;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = (cx << 4) + lx, z = (cz << 4) + lz;
            double h2 = (double) (x - k.cx) * (x - k.cx) + (double) (z - k.cz) * (z - k.cz);
            if (Math.sqrt(h2) > outer) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, low = Math.max(level.getMinBuildHeight(), top - Balance.RING_SPAN);
            for (int y = top; y >= low; y--) {
                m.set(x, y, z);
                BlockState s = level.getBlockState(m);
                if (s.isAir()) continue;
                boolean ground = natural(s);

                double d = Math.sqrt(h2 + (double) (y - k.cy) * (y - k.cy));
                int ring = d < r * 0.95 || d > outer ? -1 : d <= rub ? 0 : (burn > 0 && d <= burn) ? 1 : d <= strip ? 2 : -1;
                if (ring < 0) { if (ground) break; continue; }
                double a = ring == 0 ? r * 0.95 : ring == 1 ? Math.max(rub, r) : Math.max(Math.max(rub, burn), r), b = ring == 0 ? rub : ring == 1 ? burn : strip;
                double near = 1.0 - Math.max(0.0, Math.min(1.0, (d - a) / Math.max(1.0, b - a)));
                BlockState to = ring == 0 ? rubble(s, ground, near, rnd) : ring == 1 ? burnt(s, ground, rnd) : stripped(s, ground, near, rnd);
                if (to != null && to != s && BlockRules.blastChangeable(level, m, s)) {
                    level.setBlock(m, to, 2 | 16);
                    any = true;
                    if (ring == 1 && j.p.fire && to.isAir() == false && rnd.nextFloat() < 0.05F && level.getBlockState(m.above()).isAir() && BlockRules.blastChangeable(level, m.above(), Blocks.AIR.defaultBlockState()))
                        level.setBlock(m.above(), Blocks.FIRE.defaultBlockState(), 2 | 16);
                }
                if (ground) {
                    if (ring == 0 && rnd.nextFloat() < 0.18F * near) {
                        BlockPos up = m.above();
                        if (level.getBlockState(up).isAir() && BlockRules.blastChangeable(level, up, Blocks.AIR.defaultBlockState())) { level.setBlock(up, Blocks.GRAVEL.defaultBlockState(), 2 | 16); any = true; }
                    }
                    break;
                }
            }
        }
        return any;
    }

    static boolean natural(BlockState s) {
        return !s.getFluidState().isEmpty() || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || s.is(Tags.Blocks.ORES)
                || s.is(Blocks.BEDROCK) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE);
    }

    private static boolean plant(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE_BY_TREES)
                || s.is(Blocks.SNOW) || s.is(Blocks.VINE) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN);
    }

    private static boolean wood(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS);
    }

    private static boolean cloth(BlockState s) { return s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.HAY_BLOCK); }

    private static BlockState air() { return Blocks.AIR.defaultBlockState(); }

    private static BlockState rubble(BlockState s, boolean ground, double near, RandomSource rnd) {
        if (ground) {
            if (!s.is(BlockTags.DIRT) && !s.is(BlockTags.SAND)) return null;
            float f = rnd.nextFloat();
            return f < 0.35F ? Blocks.GRAVEL.defaultBlockState() : f < 0.55F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (plant(s) || cloth(s) || s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)) return air();
        if (rnd.nextFloat() < 0.35 + 0.5 * near) return air();
        if (s.is(Blocks.STONE_BRICKS)) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (s.is(Blocks.BRICKS) || s.is(Blocks.STONE)) return Blocks.COBBLESTONE.defaultBlockState();
        if (s.is(BlockTags.LOGS)) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        return null;
    }

    private static BlockState burnt(BlockState s, boolean ground, RandomSource rnd) {
        if (ground) return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) ? (rnd.nextBoolean() ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState() : null;
        if (plant(s) || cloth(s)) return air();
        if (s.is(BlockTags.LOGS)) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        if (wood(s)) return rnd.nextFloat() < 0.4F ? air() : s.is(BlockTags.PLANKS) ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : null;
        return null;
    }

    private static BlockState stripped(BlockState s, boolean ground, double near, RandomSource rnd) {
        if (ground) return s.is(Blocks.GRASS_BLOCK) && rnd.nextFloat() < 0.25 * near ? Blocks.DIRT.defaultBlockState() : null;
        if (s.is(BlockTags.LEAVES)) return rnd.nextFloat() < 0.3 + 0.7 * near ? air() : null;
        if (plant(s)) return air();
        return null;
    }

}
