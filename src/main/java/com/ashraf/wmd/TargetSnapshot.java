package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TargetSnapshot {

    private TargetSnapshot() {}

    private static final class Job {
        ServerLevel level;
        int id, tx, tz;
        ServerPlayer only;
        long until = Long.MAX_VALUE;
        final UUID ticket = UUID.randomUUID();
        final Set<Long> held = new HashSet<>();
        final ArrayDeque<Long> todo = new ArrayDeque<>(), again = new ArrayDeque<>(), far = new ArrayDeque<>();

        final ArrayDeque<Long> disk = new ArrayDeque<>();
        final java.util.concurrent.ConcurrentLinkedQueue<SnapshotPayload> ready = new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.concurrent.atomic.AtomicInteger inflight = new java.util.concurrent.atomic.AtomicInteger();
        final Set<Long> sent = new HashSet<>();
        boolean done() { return todo.isEmpty() && again.isEmpty(); }
    }

    private static final Map<Long, Job> JOBS = new HashMap<>();
    private static int nextId = 1;

    static int start(ServerLevel level, long key, int tx, int tz, ServerPlayer only) { return start(level, key, tx, tz, only, Balance.RG_SNAP_R, Balance.RG_SNAP_FAR_R); }

    static int start(ServerLevel level, long key, int tx, int tz, ServerPlayer only, int realR, int farR) {
        stop(key);
        Job j = new Job();
        j.level = level; j.id = nextId++; j.tx = tx; j.tz = tz; j.only = only;
        int r = (realR + 15) >> 4, ccx = tx >> 4, ccz = tz >> 4;
        List<long[]> order = new ArrayList<>();
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) order.add(new long[] { a * a + b * b, net.minecraft.world.level.ChunkPos.asLong(ccx + a, ccz + b) });
        order.sort((p, q) -> Long.compare(p[0], q[0]));
        for (long[] o : order) j.todo.add(o[1]);

        int rf = farR >> 4, rd = Balance.RG_SNAP_FAR_R >> 4;
        List<long[]> ring = new ArrayList<>();
        for (int a = -rf; a <= rf; a++) for (int b = -rf; b <= rf; b++)
            if (a * a + b * b <= rf * rf && (Math.abs(a) > r || Math.abs(b) > r)) ring.add(new long[] { a * a + b * b, net.minecraft.world.level.ChunkPos.asLong(ccx + a, ccz + b) });
        ring.sort((p, q) -> Long.compare(p[0], q[0]));
        for (long[] o : ring) (o[0] > (long) rd * rd ? j.disk : j.far).add(o[1]);
        JOBS.put(key, j);
        send(j, new SnapshotPayload(j.id, SnapshotPayload.START, tx, tz, realR, farR, new byte[0]));
        Wmd.LOGGER.info("[railgun] snapshot {} started round {} {} ({} chunks)", j.id, tx, tz, j.todo.size());
        return j.id;
    }

    static boolean has(long key) { return JOBS.containsKey(key); }

    static boolean ready(long key) { Job j = JOBS.get(key); return j != null && j.done(); }

    static void stopIn(long key, ServerLevel level, int ticks) { Job j = JOBS.get(key); if (j != null) j.until = level.getGameTime() + ticks; }

    static void stop(long key) {
        Job j = JOBS.remove(key);
        if (j == null) return;
        AsyncChunks.releaseAll(j.level, j.ticket, j.held);
        send(j, new SnapshotPayload(j.id, SnapshotPayload.END, 0, 0, 0, 0, new byte[0]));
    }

    static void changed(ServerLevel level, int cx, int cz) {
        Planet.changed(level, cx, cz);
        long k = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
        for (Job j : JOBS.values()) if (j.level == level && j.sent.contains(k) && !j.again.contains(k)) j.again.add(k);
    }

    private static void send(Job j, SnapshotPayload p) {
        if (j.only != null) { if (!j.only.hasDisconnected()) PacketDistributor.sendToPlayer(j.only, p); }
        else PacketDistributor.sendToPlayersInDimension(j.level, p);
    }

    @SubscribeEvent
    static void levelTick(LevelTickEvent.Post event) {
        long pt0 = ImpactProfiler.start();
        try { tickJobs(event); } finally { ImpactProfiler.add(ImpactProfiler.SNAPSHOT, pt0); }
    }

    private static void tickJobs(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || JOBS.isEmpty()) return;
        for (Iterator<Map.Entry<Long, Job>> it = JOBS.entrySet().iterator(); it.hasNext(); ) {
            Job j = it.next().getValue();
            if (j.level != level) continue;
            if (level.getGameTime() >= j.until || j.only != null && j.only.hasDisconnected()) {
                AsyncChunks.releaseAll(level, j.ticket, j.held);
                send(j, new SnapshotPayload(j.id, SnapshotPayload.END, 0, 0, 0, 0, new byte[0]));
                it.remove();
                continue;
            }
            int budget = Balance.RG_SNAP_CHUNKS_PER_TICK * Balance.WORK_CAP_MUL, sent = 0;
            long t0 = WorkBudget.start();
            try {

            while (budget > 0 && !j.again.isEmpty() && (sent < Balance.RG_SNAP_CHUNKS_PER_TICK || WorkBudget.more(t0))) {
                long k = j.again.peek();
                LevelChunk ch = AsyncChunks.now(level, net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k));
                j.again.poll();
                if (ch == null) continue;
                send(j, chunk(j, level, ch));
                budget--;
                sent++;
            }
            if (j.todo.isEmpty() && !j.far.isEmpty()) {
                int fb = Balance.RG_SNAP_FAR_PER_TICK * Balance.WORK_CAP_MUL, ft = 0, fs = 0;
                for (Iterator<Long> t = j.far.iterator(); t.hasNext() && fb > 0 && ft < 96 && (fs == 0 || WorkBudget.more(t0)); ft++) {
                    long k = t.next();
                    int cx = net.minecraft.world.level.ChunkPos.getX(k), cz = net.minecraft.world.level.ChunkPos.getZ(k);
                    LevelChunk ch = j.held.size() < Balance.RG_SNAP_FAR_PENDING || j.held.contains(k) ? AsyncChunks.get(level, j.ticket, cx, cz, j.held) : AsyncChunks.now(level, cx, cz);
                    if (ch == null) continue;
                    t.remove();
                    send(j, farChunk(j, level, ch));
                    fb--;
                    fs++;
                }
            }
            if (j.todo.isEmpty()) disk(j, level);
            int tries = 0;
            for (Iterator<Long> t = j.todo.iterator(); t.hasNext() && budget > 0 && tries < 64 && (sent == 0 || WorkBudget.more(t0)); tries++) {
                long k = t.next();
                int cx = net.minecraft.world.level.ChunkPos.getX(k), cz = net.minecraft.world.level.ChunkPos.getZ(k);
                LevelChunk ch = j.held.size() < Balance.RG_SNAP_PENDING || j.held.contains(k) ? AsyncChunks.get(level, j.ticket, cx, cz, j.held) : AsyncChunks.now(level, cx, cz);
                if (ch == null) continue;
                t.remove();
                j.sent.add(k);
                send(j, chunk(j, level, ch));
                budget--;
                sent++;
            }
            } finally { WorkBudget.done(t0); }
        }
    }

    @SubscribeEvent
    static void onStop(ServerStoppingEvent event) { JOBS.clear(); }

    private static void disk(Job j, ServerLevel level) {
        for (int n = 0; n < Balance.RG_SNAP_DISK_PER_TICK; n++) {
            SnapshotPayload p = j.ready.poll();
            if (p == null) break;
            send(j, p);
        }
        int minY = level.getMinBuildHeight(), height = level.getHeight(), id = j.id;
        while (!j.disk.isEmpty() && j.inflight.get() < Balance.RG_SNAP_DISK_INFLIGHT) {
            long k = j.disk.poll();
            int cx = net.minecraft.world.level.ChunkPos.getX(k), cz = net.minecraft.world.level.ChunkPos.getZ(k);
            LevelChunk ch = AsyncChunks.now(level, cx, cz);
            if (ch != null) { j.ready.add(farChunk(j, level, ch)); continue; }
            j.inflight.incrementAndGet();
            level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(cx, cz))
                    .thenApplyAsync(o -> o.map(tag -> diskFar(id, cx, cz, tag, minY, height)).orElse(null), net.minecraft.Util.backgroundExecutor())
                    .whenComplete((p, e) -> { if (p != null) j.ready.add(p); j.inflight.decrementAndGet(); });
        }
    }

    private static final com.mojang.serialization.Codec<net.minecraft.world.level.chunk.PalettedContainer<BlockState>> SECTION_STATES =
            net.minecraft.world.level.chunk.PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                    net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

    private static SnapshotPayload diskFar(int id, int cx, int cz, net.minecraft.nbt.CompoundTag tag, int minY, int height) {
        try {
            if (!tag.getString("Status").endsWith("full")) return null;
            long[] raw = tag.getCompound("Heightmaps").getLongArray("MOTION_BLOCKING_NO_LEAVES");
            if (raw.length == 0) return null;
            net.minecraft.util.SimpleBitStorage hm = new net.minecraft.util.SimpleBitStorage(net.minecraft.util.Mth.ceillog2(height + 1), 256, raw);
            net.minecraft.nbt.ListTag secs = tag.getList("sections", net.minecraft.nbt.Tag.TAG_COMPOUND);
            Map<Integer, net.minecraft.world.level.chunk.PalettedContainer<BlockState>> cache = new HashMap<>();
            int c = Balance.RG_SNAP_FAR_CELL, n = 16 / c;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(n * n * 6));
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
                int best = Integer.MIN_VALUE, bx = 0, bz = 0;
                for (int dz = 0; dz < c; dz++) for (int dx = 0; dx < c; dx++) {
                    int x = i * c + dx, z = k * c + dz, h = hm.get(x + z * 16) + minY - 1;
                    if (h > best) { best = h; bx = x; bz = z; }
                }
                BlockState st = diskState(secs, cache, bx, best, bz);
                if (st.isAir()) st = diskState(secs, cache, bx, best - 1, bz);
                boolean water = !st.getFluidState().isEmpty();
                int col = st.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
                if (col == 0) col = 0x7F7F7F;
                buf.writeShort(best + 1);
                buf.writeInt((col & 0xFFFFFF) | (water ? 1 << 24 : 0));
            }
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            return new SnapshotPayload(id, SnapshotPayload.FAR, cx, cz, 0, 0, data);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BlockState diskState(net.minecraft.nbt.ListTag secs, Map<Integer, net.minecraft.world.level.chunk.PalettedContainer<BlockState>> cache, int x, int y, int z) {
        net.minecraft.world.level.chunk.PalettedContainer<BlockState> pc = cache.computeIfAbsent(y >> 4, sy -> {
            for (int i = 0; i < secs.size(); i++) {
                net.minecraft.nbt.CompoundTag t = secs.getCompound(i);
                if (t.getByte("Y") == sy && t.contains("block_states", net.minecraft.nbt.Tag.TAG_COMPOUND))
                    return SECTION_STATES.parse(net.minecraft.nbt.NbtOps.INSTANCE, t.getCompound("block_states")).result().orElse(null);
            }
            return null;
        });
        return pc == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : pc.get(x & 15, y & 15, z & 15);
    }

    private static SnapshotPayload farChunk(Job j, ServerLevel level, LevelChunk ch) {
        int cx = ch.getPos().x, cz = ch.getPos().z, c = Balance.RG_SNAP_FAR_CELL, n = 16 / c;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(n * n * 6));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) {
            int best = Integer.MIN_VALUE, bx = 0, bz = 0;
            for (int dz = 0; dz < c; dz++) for (int dx = 0; dx < c; dx++) {
                int h = ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, i * c + dx, k * c + dz);
                if (h > best) { best = h; bx = i * c + dx; bz = k * c + dz; }
            }
            m.set((cx << 4) + bx, best, (cz << 4) + bz);
            BlockState st = ch.getBlockState(m);
            if (st.isAir()) { m.setY(best - 1); st = ch.getBlockState(m); }
            boolean water = !st.getFluidState().isEmpty();
            int col = st.getMapColor(level, m).col;
            if (col == 0) col = 0x7F7F7F;
            buf.writeShort(best + 1);
            buf.writeInt((col & 0xFFFFFF) | (water ? 1 << 24 : 0));
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        return new SnapshotPayload(j.id, SnapshotPayload.FAR, cx, cz, 0, 0, data);
    }

    private static SnapshotPayload chunk(Job j, ServerLevel level, LevelChunk ch) {
        int cx = ch.getPos().x, cz = ch.getPos().z;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(4096));
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        LevelChunk[] nb = { AsyncChunks.now(level, cx + 1, cz), AsyncChunks.now(level, cx - 1, cz), AsyncChunks.now(level, cx, cz + 1), AsyncChunks.now(level, cx, cz - 1) };
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) + 1;

            int ground = ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) + 1;
            int n = Math.max(0, Math.min(Math.min(Balance.RG_SNAP_MAX_DEPTH, top - ground + Balance.RG_SNAP_DEPTH), top - minY));

            int low = ground;
            for (int[] o : new int[][] { { 1, 0, 0 }, { -1, 0, 1 }, { 0, 1, 2 }, { 0, -1, 3 } }) {
                int nx = lx + o[0], nz = lz + o[1];
                LevelChunk c = nx >= 0 && nx < 16 && nz >= 0 && nz < 16 ? ch : nb[o[2]];
                if (c != null) low = Math.min(low, c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx & 15, nz & 15) + 1);
            }
            n = Math.max(n, Math.min(Balance.RG_SNAP_WALL_MAX, Math.min(top - low + 2, top - minY)));
            buf.writeVarInt(top - minY);
            buf.writeVarInt(n);
            int run = 0, last = -1;
            for (int y = top - 1; y >= top - n; y--) {
                BlockState s = ch.getBlockState(m.set((cx << 4) + lx, y, (cz << 4) + lz));
                int id = Block.getId(s);
                if (id == last) { run++; continue; }
                if (last >= 0) { buf.writeVarInt(last); buf.writeVarInt(run); }
                last = id; run = 1;
            }
            if (last >= 0) { buf.writeVarInt(last); buf.writeVarInt(run); }
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        var biome = level.getBiome(m.set((cx << 4) + 8, top, (cz << 4) + 8));
        int biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getId(biome.value());
        return new SnapshotPayload(j.id, SnapshotPayload.CHUNK, cx, cz, 0, Math.max(0, biomeId), data);
    }
}
