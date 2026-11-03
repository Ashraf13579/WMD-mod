package com.ashraf.wmd;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TerrainAtlas extends SavedData {

    private static final String NAME = "wmd_terrain_atlas";
    public static final int MAX_CELLS = 128, MIN_CELL = 4;
    private static final long PREDICT_NANOS = 2_000_000L;

    private final Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();

    private final Long2IntOpenHashMap predicted = new Long2IntOpenHashMap();
    private final LongLinkedOpenHashSet toPredict = new LongLinkedOpenHashSet();

    private final LongArrayFIFOQueue predictedOrder = new LongArrayFIFOQueue();
    private static final int PREDICTED_MAX = 50_000;

    private static final int CHART_RANGE = 2048;

    private record Sample(ServerLevel level, long chunk, int[] packed) {}
    private static final ConcurrentLinkedQueue<Sample> QUEUE = Statics.server(new ConcurrentLinkedQueue<>());
    private static final Map<UUID, Long> LAST_ASK = Statics.server(new ConcurrentHashMap<>());

    public static TerrainAtlas get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TerrainAtlas::new, TerrainAtlas::load, null), NAME);
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        int[] p = sample(chunk);
        if (p != null) QUEUE.add(new Sample(level, chunk.getPos().toLong(), p));
    }

    private static int[] sample(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int[] out = new int[16];
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            int lx = 2 + 4 * i, lz = 2 + 4 * j;
            int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            pos.set(cp.getMinBlockX() + lx, h, cp.getMinBlockZ() + lz);
            BlockState st = chunk.getBlockState(pos);
            if (st.isAir()) continue;
            MapColor mc = st.getMapColor(chunk, pos);
            if (mc == MapColor.NONE) continue;
            out[j * 4 + i] = (mc.col & 0xFFFFFF) << 8 | Math.max(1, Math.min(255, (h + 64) / 2));
            any = true;
        }
        return any ? out : null;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        for (int n = 0; n < 4096; n++) {
            Sample s = QUEUE.poll();
            if (s == null) break;
            TerrainAtlas a = get(s.level());
            a.cells.put(s.chunk(), s.packed());
            a.setDirty();
        }
        long until = System.nanoTime() + PREDICT_NANOS;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TerrainAtlas a = level.getDataStorage().get(new SavedData.Factory<>(TerrainAtlas::new, TerrainAtlas::load, null), NAME);
            if (a == null) continue;
            while (!a.toPredict.isEmpty() && System.nanoTime() < until) {
                long c = a.toPredict.removeFirstLong();
                a.predicted.put(c, predict(level, ChunkPos.getX(c), ChunkPos.getZ(c)));
                a.predictedOrder.enqueue(c);
                while (a.predictedOrder.size() > PREDICTED_MAX) a.predicted.remove(a.predictedOrder.dequeueLong());
            }
        }
    }

    private static int predict(ServerLevel level, int cx, int cz) {
        ServerChunkCache cs = level.getChunkSource();
        int x = cx * 16 + 8, z = cz * 16 + 8, h = level.getSeaLevel();
        var biome = cs.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(h), QuartPos.fromBlock(z), cs.randomState().sampler());
        int id = level.registryAccess().registryOrThrow(Registries.BIOME).getId(biome.value());
        return (Math.max(0, id) & 0xFFFF) << 8 | Math.max(1, Math.min(255, (h + 64) / 2));
    }

    static void answer(ServerPlayer player, int x0, int z0, int cell, int w, int h) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST_ASK.get(player.getUUID());
        if (last != null && now - last < 8) return;
        LAST_ASK.put(player.getUUID(), now);
        cell = Math.max(MIN_CELL, Math.min(4096, Integer.highestOneBit(Math.max(MIN_CELL, cell))));
        w = Math.max(1, Math.min(MAX_CELLS, w));
        h = Math.max(1, Math.min(MAX_CELLS, h));
        TerrainAtlas a = get(level);
        int[] raw = new int[w * h], out = new int[w * h];
        boolean pending = false;
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int bx = x0 + i * cell + cell / 2, bz = z0 + j * cell + cell / 2;
            if (Math.abs(bx - player.getX()) > CHART_RANGE || Math.abs(bz - player.getZ()) > CHART_RANGE) continue;
            int v = a.explored(bx, bz);
            if (v == 0) {
                long c = ChunkPos.asLong(bx >> 4, bz >> 4);
                int p = a.predicted.get(c);
                if (p != 0) v = 0x01000000 | p;
                else { if (a.toPredict.size() < 20_000) a.toPredict.add(c); pending = true; }
            }
            raw[j * w + i] = v;
        }
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int v = raw[j * w + i];
            if (v == 0) continue;
            int north = j > 0 ? raw[(j - 1) * w + i] : v;
            int hv = v & 0xFF, hn = north == 0 ? hv : north & 0xFF;
            int shade = hv > hn ? 2 : hv < hn ? 0 : 1;
            if ((v >>> 24) == 0x01) { out[j * w + i] = 0x01000000 | ((v >>> 8) & 0xFFFF) << 2 | shade; continue; }
            float k = shade == 2 ? 1.0F : shade == 0 ? 0.71F : 0.86F;
            int rgb = v >>> 8, r = (int) (((rgb >> 16) & 0xFF) * k), g = (int) (((rgb >> 8) & 0xFF) * k), b = (int) ((rgb & 0xFF) * k);
            out[j * w + i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        PacketDistributor.sendToPlayer(player, new ChartTerrainPayload(x0, z0, cell, w, h, pending, out));
    }

    private int explored(int bx, int bz) {
        int[] s = cells.get(ChunkPos.asLong(bx >> 4, bz >> 4));
        if (s == null) return 0;
        return s[((bz & 15) >> 2) * 4 + ((bx & 15) >> 2)];
    }

    public TerrainAtlas() { predicted.defaultReturnValue(0); }

    public static TerrainAtlas load(CompoundTag tag, HolderLookup.Provider registries) {
        TerrainAtlas a = new TerrainAtlas();
        long[] k = tag.getLongArray("k");
        int[] v = tag.getIntArray(tag.contains("s") ? "s" : "v");
        boolean full = tag.contains("s");
        for (int i = 0; i < k.length; i++) {
            int[] s = new int[16];
            if (full) { if ((i + 1) * 16 > v.length) break; System.arraycopy(v, i * 16, s, 0, 16); }
            else { if (i >= v.length) break; java.util.Arrays.fill(s, v[i]); }
            a.cells.put(k[i], s);
        }
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] k = new long[cells.size()];
        int[] v = new int[cells.size() * 16];
        int i = 0;
        for (var e : cells.long2ObjectEntrySet()) { k[i] = e.getLongKey(); System.arraycopy(e.getValue(), 0, v, i * 16, 16); i++; }
        tag.put("k", new LongArrayTag(k));
        tag.put("s", new IntArrayTag(v));
        return tag;
    }
}
