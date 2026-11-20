package com.ashraf.wmd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.core.registries.BuiltInRegistries;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WorldMap {

    private WorldMap() {}

    public static final int TILE = 64, MAX_LEVEL = 5, BASE = 4, MAX_RADIUS = 100_000;
    static final int VALID = 1 << 24;
    private static final int WINDOW = 8, RESIDENT_MAX = 192, TILE_CACHE_MAX = 128, MAX_PROMOTE = 16, MAX_PER_REQUEST = 8, MAX_PENDING_PER_PLAYER = 48, MIN_GAP_TICKS = 3;
    private static final int MAGIC = 0x574D4150, VERSION = 1, WATER_ID = MapColor.WATER.id;
    private static final long RESCAN_MS = 5 * 60_000L, STALE_MS = 10 * 60_000L, INCOMPLETE_REUSE_MS = 2_000L;
    public static final int F_COMPLETE = 1, F_OUTSIDE = 2, F_DISABLED = 4;
    private static final Pattern MCA = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    static final class Region {
        final int rx, rz;
        final int[] s = new int[1024 * 16];
        final long[] have = new long[16];
        long built;
        Region(int rx, int rz) { this.rx = rx; this.rz = rz; }
        int at(int bx, int bz) { return s[(((bz >> 4) & 31) * 32 + ((bx >> 4) & 31)) * 16 + ((bz & 15) >> 2) * 4 + ((bx & 15) >> 2)]; }
    }

    static final class Dim {
        final String id; final ServerLevel level; final Path regionDir, mapDir; final int minY, height;
        final Map<Long, Region> resident = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Region> e) { return size() > RESIDENT_MAX; }
        });

        final LongSet exists = LongSets.synchronize(new LongOpenHashSet()), done = LongSets.synchronize(new LongOpenHashSet());
        final ArrayDeque<Long> priority = new ArrayDeque<>(), sweep = new ArrayDeque<>();
        final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap builtAt = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        final Map<String, Cached> tiles = new LinkedHashMap<>(256, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> e) { return size() > TILE_CACHE_MAX; }
        };
        int cx, cz, radius; long scannedAt;
        volatile boolean scanned, rebuild;
        volatile int doneCount, totalCount;
        Dim(ServerLevel level) {
            this.level = level; this.id = level.dimension().location().toString();
            Path root = level.getServer().getWorldPath(LevelResource.ROOT);
            this.regionDir = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(), root).resolve("region");
            this.mapDir = root.resolve("data").resolve("wmd_map").resolve(id.replace(':', '_'));
            this.minY = level.getMinBuildHeight(); this.height = level.getHeight();
        }
        int pct() { int t = totalCount; return t <= 0 ? 100 : Math.min(100, doneCount * 100 / t); }
        boolean inside(int bx, int bz) { return Math.abs(bx - cx) <= radius && Math.abs(bz - cz) <= radius; }
        boolean regionInside(int rx, int rz) { return rx * 512L + 511 >= cx - radius && rx * 512L <= cx + radius && rz * 512L + 511 >= cz - radius && rz * 512L <= cz + radius; }
    }

    private record Cached(byte[] ids, boolean complete, long at) {}
    private record Job(UUID player, Dim dim, int lvl, int tx, int tz) {}

    private record HJob(UUID player, Dim dim, int lvl, int tx, int tz) {}

    private static final Map<String, Dim> DIMS = Statics.server(new ConcurrentHashMap<>());
    private static final ConcurrentLinkedQueue<Job> JOBS = Statics.server(new ConcurrentLinkedQueue<>());
    private static final ConcurrentLinkedQueue<HJob> HJOBS = Statics.server(new ConcurrentLinkedQueue<>());
    private static final Map<UUID, Integer> LAST_ASK_H = Statics.server(new ConcurrentHashMap<>());

    private record Load(Dim dim, int rx, int rz) {}
    private static final ConcurrentLinkedQueue<Load> LOADS = Statics.server(new ConcurrentLinkedQueue<>());
    private static final Map<UUID, Integer> LAST_ASK = Statics.server(new ConcurrentHashMap<>());
    private static final Map<UUID, Integer> PENDING = Statics.server(new ConcurrentHashMap<>());
    private static volatile Thread thread;
    private static volatile boolean running;

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel l : server.getAllLevels()) if (eligible(l)) DIMS.put(l.dimension().location().toString(), new Dim(l));
        running = true;
        Thread t = new Thread(() -> run(server), "wmd-world-map");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        thread = t;
        t.start();
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) { t.interrupt(); try { t.join(3000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
    }

    private static boolean eligible(ServerLevel l) { return !l.dimensionType().hasCeiling(); }

    static void request(ServerPlayer p, int lvl, int[] tiles) {
        ServerLevel level = p.serverLevel();
        String id = level.dimension().location().toString();
        int n = Math.min(tiles.length / 2, MAX_PER_REQUEST);
        if (n <= 0 || lvl < 0 || lvl > MAX_LEVEL) return;
        int tick = p.server.getTickCount();
        Integer last = LAST_ASK.get(p.getUUID());
        if (last != null && tick >= last && tick - last < MIN_GAP_TICKS) return;
        LAST_ASK.put(p.getUUID(), tick);
        Dim d = DIMS.get(id);
        if (!Config.Server.worldMap() || d == null) {
            PacketDistributor.sendToPlayer(p, new WorldMapPayloads.Tile(id, lvl, tiles[0], tiles[1], !Config.Server.worldMap() ? F_DISABLED : F_OUTSIDE, 0, new byte[0]));
            return;
        }
        int pending = PENDING.getOrDefault(p.getUUID(), 0);
        if (pending > MAX_PENDING_PER_PLAYER) return;
        PENDING.merge(p.getUUID(), n, Integer::sum);
        for (int i = 0; i < n; i++) JOBS.add(new Job(p.getUUID(), d, lvl, tiles[2 * i], tiles[2 * i + 1]));
    }

    static void requestHeights(ServerPlayer p, int lvl, int[] tiles) {
        ServerLevel level = p.serverLevel();
        String id = level.dimension().location().toString();
        int n = Math.min(tiles.length / 2, MAX_PER_REQUEST);
        if (n <= 0 || lvl < 0 || lvl > MAX_LEVEL) return;
        int tick = p.server.getTickCount();
        Integer last = LAST_ASK_H.get(p.getUUID());
        if (last != null && tick >= last && tick - last < MIN_GAP_TICKS) return;
        LAST_ASK_H.put(p.getUUID(), tick);
        Dim d = DIMS.get(id);
        if (!Config.Server.worldMap() || d == null) {
            PacketDistributor.sendToPlayer(p, new HoloPayloads.TerrainTile(id, lvl, tiles[0], tiles[1], !Config.Server.worldMap() ? F_DISABLED : F_OUTSIDE, new byte[0], new byte[0]));
            return;
        }
        int pending = PENDING.getOrDefault(p.getUUID(), 0);
        if (pending > MAX_PENDING_PER_PLAYER) return;
        PENDING.merge(p.getUUID(), n, Integer::sum);
        for (int i = 0; i < n; i++) HJOBS.add(new HJob(p.getUUID(), d, lvl, tiles[2 * i], tiles[2 * i + 1]));
    }

    private static long nextAt;

    private static void run(MinecraftServer server) {
        nextAt = System.nanoTime();
        try {
            while (running) {
                if (drain(server)) continue;
                if (!Config.Server.worldMap()) { Thread.sleep(1000); continue; }
                boolean worked = false;
                for (Dim d : DIMS.values()) {
                    if (!running) break;
                    if (!d.scanned || d.rebuild || System.currentTimeMillis() - d.scannedAt > RESCAN_MS) { scan(d); worked = true; }
                    Long key = next(d);
                    if (key == null) continue;
                    buildRegion(server, d, key);
                    worked = true;
                    break;
                }
                if (!worked) Thread.sleep(400);
            }
        } catch (InterruptedException e) {

        } catch (Throwable t) {
            Wmd.LOGGER.error("WMD world map: the builder thread stopped: ", t);
        }
    }

    private static boolean drain(MinecraftServer server) throws InterruptedException {
        boolean did = false;
        for (int n = 0; n < 4; n++) {
            Load l = LOADS.poll();
            if (l == null) break;
            long k = ChunkPos.asLong(l.rx(), l.rz());
            if (l.dim().resident.containsKey(k) || !l.dim().done.contains(k)) continue;
            Region r = read(l.dim(), l.rx(), l.rz());
            if (r != null) l.dim().resident.put(k, r);
            did = true;
        }
        for (int n = 0; n < 4; n++) {
            Job j = JOBS.poll();
            if (j == null) break;
            did = true;
            PENDING.computeIfPresent(j.player(), (k, v) -> v <= 1 ? null : v - 1);
            try { answer(server, j); } catch (RuntimeException e) { Wmd.LOGGER.warn("WMD world map: a tile failed: {}", e.toString()); }
        }
        for (int n = 0; n < 2; n++) {
            HJob j = HJOBS.poll();
            if (j == null) break;
            did = true;
            PENDING.computeIfPresent(j.player(), (k, v) -> v <= 1 ? null : v - 1);
            try { answerHeights(server, j); } catch (RuntimeException e) { Wmd.LOGGER.warn("WMD world map: a holo tile failed: {}", e.toString()); }
        }
        return did;
    }

    private static void answerHeights(MinecraftServer server, HJob j) {
        Dim d = j.dim();
        if (!d.scanned) scan(d);
        Composed c = tile(d, j.lvl(), j.tx(), j.tz());
        int flags = (c.complete ? F_COMPLETE : 0) | (c.outside ? F_OUTSIDE : 0);
        byte[] h = new byte[TILE * TILE];
        if (!c.outside) {
            int B = BASE << j.lvl(), span = TILE * B;
            compose(d, j.lvl(), (long) j.tx() * span, (long) j.tz() * span, h);
        }
        HoloPayloads.TerrainTile out = new HoloPayloads.TerrainTile(d.id, j.lvl(), j.tx(), j.tz(), flags, c.outside ? new byte[0] : c.ids, c.outside ? new byte[0] : h);
        UUID id = j.player();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, out);
        });
    }

    private static void answer(MinecraftServer server, Job j) {
        Dim d = j.dim();
        if (!d.scanned) scan(d);
        Composed c = tile(d, j.lvl(), j.tx(), j.tz());
        int flags = (c.complete ? F_COMPLETE : 0) | (c.outside ? F_OUTSIDE : 0);
        WorldMapPayloads.Tile out = new WorldMapPayloads.Tile(d.id, j.lvl(), j.tx(), j.tz(), flags, d.pct(), c.outside ? new byte[0] : c.ids);
        UUID id = j.player();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, out);
        });
    }

    private static void scan(Dim d) {
        boolean full = d.rebuild, first = full || !d.scanned;
        if (full) {
            d.rebuild = false;
            d.resident.clear();
            d.tiles.clear();
            d.done.clear();
            d.exists.clear();
            d.builtAt.clear();
            try (var st = Files.exists(d.mapDir) ? Files.list(d.mapDir) : java.util.stream.Stream.<Path>empty()) {
                for (Path p : (Iterable<Path>) st::iterator) if (p.getFileName().toString().endsWith(".wmap")) Files.deleteIfExists(p);
            } catch (IOException e) { Wmd.LOGGER.warn("WMD world map: could not clear the cache: {}", e.toString()); }
        }
        WorldBorder b = d.level.getWorldBorder();
        int bord = (int) Math.min(MAX_RADIUS, b.getSize() / 2);
        int cfg = Config.Server.worldMapRadius();
        d.cx = Mth.floor(b.getCenterX()); d.cz = Mth.floor(b.getCenterZ());
        d.radius = cfg > 0 ? Math.min(cfg, bord) : bord;
        d.scannedAt = System.currentTimeMillis();
        List<long[]> todo = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        BlockPos spawn = d.level.getSharedSpawnPos();
        if (Files.isDirectory(d.regionDir)) {
            try (var st = Files.list(d.regionDir)) {
                for (Path p : (Iterable<Path>) st::iterator) {
                    Matcher m = MCA.matcher(p.getFileName().toString());
                    if (!m.matches()) continue;
                    int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
                    if (!d.regionInside(rx, rz)) continue;
                    long key = ChunkPos.asLong(rx, rz);
                    seen.add(key);
                    long mtime;
                    try { mtime = Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { continue; }
                    boolean known = d.exists.contains(key);
                    d.exists.add(key);
                    if (d.done.contains(key)) {
                        if (mtime <= d.builtAt.get(key) + STALE_MS) continue;
                        d.done.remove(key);
                    } else if (first || !known) {
                        long built = peekBuilt(file(d, rx, rz));
                        if (built >= mtime) { d.done.add(key); d.builtAt.put(key, built); continue; }
                    }
                    todo.add(new long[] { key, dist(rx, rz, spawn) });
                }
            } catch (IOException e) { Wmd.LOGGER.warn("WMD world map: could not list {}: {}", d.regionDir, e.toString()); }
        }
        for (long k : d.exists.toLongArray()) if (!seen.contains(k)) { d.exists.remove(k); d.done.remove(k); }
        todo.sort((a, c) -> Long.compare(a[1], c[1]));
        d.sweep.clear();
        for (long[] t : todo) d.sweep.add(t[0]);
        d.totalCount = d.exists.size();
        d.doneCount = d.done.size();
        d.scanned = true;
    }

    private static long dist(int rx, int rz, BlockPos spawn) {
        long dx = rx * 512L + 256 - spawn.getX(), dz = rz * 512L + 256 - spawn.getZ();
        return dx * dx + dz * dz;
    }

    private static Long next(Dim d) {
        while (!d.priority.isEmpty()) { Long k = d.priority.poll(); if (!d.done.contains(k) && d.exists.contains(k)) { d.sweep.remove(k); return k; } }
        if (!Config.Server.worldMapPrebuild()) return null;
        while (!d.sweep.isEmpty()) { Long k = d.sweep.poll(); if (!d.done.contains(k) && d.exists.contains(k)) return k; }
        return null;
    }

    private record Pend(int c, CompletableFuture<Optional<CompoundTag>> f) {}

    private static void buildRegion(MinecraftServer server, Dim d, long key) throws InterruptedException {
        int rx = ChunkPos.getX(key), rz = ChunkPos.getZ(key);
        int[] hdr = header(d.regionDir.resolve("r." + rx + "." + rz + ".mca"));
        Region r = new Region(rx, rz);
        if (hdr != null) {
            ArrayDeque<Pend> window = new ArrayDeque<>();
            int n = 0;
            for (int c = 0; c < 1024; c++) {
                if (hdr[c] == 0) continue;
                if (!running) return;
                CompletableFuture<Optional<CompoundTag>> f;
                try { f = d.level.getChunkSource().chunkMap.read(new ChunkPos(rx * 32 + (c & 31), rz * 32 + (c >> 5))); }
                catch (RuntimeException e) { continue; }
                window.add(new Pend(c, f));
                if (window.size() >= WINDOW) finish(d, r, window.poll());
                pace(server);
                if ((++n & 15) == 0) drain(server);
            }
            while (!window.isEmpty()) finish(d, r, window.poll());
        }
        r.built = System.currentTimeMillis();
        try { save(d, r); } catch (IOException e) { Wmd.LOGGER.warn("WMD world map: could not save region {} {}: {}", rx, rz, e.toString()); }
        d.resident.put(key, r);
        d.builtAt.put(key, r.built);
        d.done.add(key);
        d.doneCount = d.done.size();
        d.tiles.clear();
    }

    private static void finish(Dim d, Region r, Pend p) throws InterruptedException {
        Optional<CompoundTag> o;
        try { o = p.f().get(15, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException | TimeoutException | java.util.concurrent.CancellationException e) { return; }
        if (o == null || o.isEmpty()) return;
        int[] s;
        try { s = sample(o.get(), d.minY, d.height); } catch (RuntimeException e) { return; }
        if (s == null) return;
        System.arraycopy(s, 0, r.s, p.c() * 16, 16);
        r.have[p.c() >> 6] |= 1L << (p.c() & 63);
    }

    private static void pace(MinecraftServer server) throws InterruptedException {
        while (running && server.getAverageTickTimeNanos() > 45_000_000L) Thread.sleep(250);
        int cps = Math.max(10, Config.Server.worldMapChunksPerSecond());
        nextAt += 1_000_000_000L / cps;
        long now = System.nanoTime();
        if (nextAt > now) Thread.sleep((nextAt - now) / 1_000_000L, (int) ((nextAt - now) % 1_000_000L));
        else if (now - nextAt > 250_000_000L) nextAt = now;
    }

    static final class Sec {
        final ListTag palette; final BlockState[] states; SimpleBitStorage store; boolean bad;
        Sec(CompoundTag t) {
            palette = t.getList("palette", Tag.TAG_COMPOUND);
            states = new BlockState[palette.size()];
            if (palette.isEmpty()) { bad = true; return; }
            if (palette.size() > 1) {
                long[] data = t.getLongArray("data");
                int bits = Math.max(4, Mth.ceillog2(palette.size())), per = 64 / bits;
                if (data.length != (4096 + per - 1) / per) bad = true;
                else store = new SimpleBitStorage(bits, 4096, data);
            }
        }
        BlockState at(int lx, int ly, int lz) {
            if (bad) return null;
            int idx = store == null ? 0 : store.get(ly << 8 | lz << 4 | lx);
            if (idx >= states.length) return null;
            BlockState s = states[idx];
            if (s == null) s = states[idx] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(idx));
            return s;
        }
    }

    static int[] sample(CompoundTag tag, int minY, int height) {
        if (tag.getInt("DataVersion") < 2860 || !tag.getString("Status").endsWith("full")) return null;
        CompoundTag hm = tag.getCompound("Heightmaps");
        if (!hm.contains("WORLD_SURFACE", Tag.TAG_LONG_ARRAY)) return null;
        int bits = Mth.ceillog2(height + 1);
        SimpleBitStorage surf = new SimpleBitStorage(bits, 256, hm.getLongArray("WORLD_SURFACE"));
        SimpleBitStorage floor = hm.contains("OCEAN_FLOOR", Tag.TAG_LONG_ARRAY) ? new SimpleBitStorage(bits, 256, hm.getLongArray("OCEAN_FLOOR")) : null;
        int minSec = minY >> 4, nSec = height >> 4;
        Sec[] secs = new Sec[nSec];
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int y = c.getByte("Y") - minSec;
            if (y >= 0 && y < nSec && c.contains("block_states", Tag.TAG_COMPOUND)) secs[y] = new Sec(c.getCompound("block_states"));
        }
        int[] out = new int[16];
        boolean any = false;
        for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) {
            int lx = 2 + 4 * i, lz = 2 + 4 * j, idx = lz * 16 + lx, hs = surf.get(idx);
            if (hs <= 0) continue;
            int y = minY + hs - 1;
            BlockState st = null;
            for (int k = 0; k < 12 && y >= minY; k++, y--) {
                Sec sec = secs[(y >> 4) - minSec];
                BlockState s = sec == null ? null : sec.at(lx, y & 15, lz);
                if (s == null) break;
                if (s.isAir()) continue;
                if (s.getFluidState().is(FluidTags.WATER) || s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) != MapColor.NONE) { st = s; break; }
            }
            if (st == null) continue;
            boolean water = st.getFluidState().is(FluidTags.WATER);
            int depth = 0, id;
            if (water) {
                id = WATER_ID;
                depth = floor == null ? 8 : Mth.clamp(hs - floor.get(idx), 1, 255);
            } else id = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).id;
            out[j * 4 + i] = VALID | depth << 16 | id << 8 | Mth.clamp((y + 64) >> 1, 0, 255);
            any = true;
        }
        return any ? out : null;
    }

    private static Path file(Dim d, int rx, int rz) { return d.mapDir.resolve("r." + rx + "." + rz + ".wmap"); }

    private static int[] header(Path f) {
        if (!Files.isRegularFile(f)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f), 4096))) {
            int[] t = new int[1024];
            for (int i = 0; i < 1024; i++) t[i] = in.readInt();
            return t;
        } catch (IOException e) { return null; }
    }

    private static long peekBuilt(Path f) {
        if (!Files.isRegularFile(f)) return -1;
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != MAGIC || in.readShort() != VERSION) return -1;
            return in.readLong();
        } catch (IOException e) { return -1; }
    }

    private static void save(Dim d, Region r) throws IOException {
        Files.createDirectories(d.mapDir);
        Path f = file(d, r.rx, r.rz), tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(Files.newOutputStream(tmp)), 1 << 16))) {
            o.writeInt(MAGIC); o.writeShort(VERSION); o.writeLong(r.built);
            for (long l : r.have) o.writeLong(l);
            for (int v : r.s) o.writeInt(v);
        }
        try { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException e) { Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static Region read(Dim d, int rx, int rz) {
        Path f = file(d, rx, rz);
        if (!Files.isRegularFile(f)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(f)), 1 << 16))) {
            if (in.readInt() != MAGIC || in.readShort() != VERSION) return null;
            Region r = new Region(rx, rz);
            r.built = in.readLong();
            for (int i = 0; i < r.have.length; i++) r.have[i] = in.readLong();
            for (int i = 0; i < r.s.length; i++) r.s[i] = in.readInt();
            return r;
        } catch (IOException e) { return null; }
    }

    private record Composed(byte[] ids, boolean complete, boolean outside) {}

    private static final class Ctx {
        final Dim d; long lastKey = Long.MIN_VALUE; Region last;
        Ctx(Dim d) { this.d = d; }
        int sample(int bx, int bz) {
            int rx = bx >> 9, rz = bz >> 9;
            long k = ChunkPos.asLong(rx, rz);
            if (k != lastKey) {
                lastKey = k;
                Region r = d.resident.get(k);
                if (r == null && d.done.contains(k) && d.exists.contains(k)) {
                    r = read(d, rx, rz);
                    if (r != null) d.resident.put(k, r);
                    else { d.done.remove(k); d.priority.add(k); d.doneCount = d.done.size(); }
                }
                last = r;
            }
            return last == null ? 0 : last.at(bx, bz);
        }
    }

    private static Composed tile(Dim d, int lvl, int tx, int tz) {
        String ck = lvl + "|" + tx + "|" + tz;
        long now = System.currentTimeMillis();
        Cached c = d.tiles.get(ck);
        if (c != null && (c.complete() || now - c.at() < INCOMPLETE_REUSE_MS)) return new Composed(c.ids(), c.complete(), false);
        int B = BASE << lvl, span = TILE * B;
        long x0 = (long) tx * span, z0 = (long) tz * span;
        if (Math.abs(x0) > 40_000_000L || Math.abs(z0) > 40_000_000L) return new Composed(new byte[TILE * TILE], true, true);

        int rx0 = Math.floorDiv((int) x0, 512), rx1 = Math.floorDiv((int) x0 + span - 1, 512), rz0 = Math.floorDiv((int) z0, 512), rz1 = Math.floorDiv((int) z0 + span - 1, 512);
        boolean complete = true, anyInside = false;
        List<long[]> lack = new ArrayList<>();
        for (int rz = rz0; rz <= rz1; rz++) for (int rx = rx0; rx <= rx1; rx++) {
            if (!d.regionInside(rx, rz)) continue;
            anyInside = true;
            long k = ChunkPos.asLong(rx, rz);
            if (!d.exists.contains(k) || d.done.contains(k)) continue;
            complete = false;
            long dx = rx * 512L + 256 - (x0 + span / 2), dz = rz * 512L + 256 - (z0 + span / 2);
            lack.add(new long[] { k, dx * dx + dz * dz });
        }
        if (!anyInside) return new Composed(new byte[TILE * TILE], true, true);
        if (!lack.isEmpty()) {
            lack.sort((a, b) -> Long.compare(a[1], b[1]));
            for (int i = 0; i < Math.min(MAX_PROMOTE, lack.size()); i++) if (!d.priority.contains(lack.get(i)[0])) d.priority.add(lack.get(i)[0]);
        }
        byte[] ids = compose(d, lvl, x0, z0);
        d.tiles.put(ck, new Cached(ids, complete, now));
        return new Composed(ids, complete, false);
    }

    private static final int LOW = 0, NORMAL = 1, HIGH = 2, LOWEST = 3;

    private static byte[] compose(Dim d, int lvl, long x0l, long z0l) { return compose(d, lvl, x0l, z0l, null); }

    private static byte[] compose(Dim d, int lvl, long x0l, long z0l, byte[] hOut) {
        int B = BASE << lvl, n = B / 4, stride = Math.max(1, n / 4), x0 = (int) x0l, z0 = (int) z0l;
        Ctx ctx = new Ctx(d);
        int[] cnt = new int[64];
        int[] colour = new int[(TILE + 1) * TILE], avgH = new int[(TILE + 1) * TILE], depth = new int[(TILE + 1) * TILE];
        for (int j = -1; j < TILE; j++) for (int i = 0; i < TILE; i++) {
            int o = (j + 1) * TILE + i;
            colour[o] = -1;
            int bx0 = x0 + i * B, bz0 = z0 + j * B;
            if (!d.inside(bx0 + B / 2, bz0 + B / 2)) continue;
            java.util.Arrays.fill(cnt, 0);
            int tot = 0, sumH = 0, water = 0, sumD = 0;
            for (int a = 0; a < n; a += stride) for (int b = 0; b < n; b += stride) {
                int sv = ctx.sample(bx0 + a * 4 + 2, bz0 + b * 4 + 2);
                if ((sv & VALID) == 0) continue;
                int id = (sv >> 8) & 0xFF;
                cnt[id & 63]++;
                tot++; sumH += sv & 0xFF;
                if (id == WATER_ID) { water++; sumD += (sv >> 16) & 0xFF; }
            }
            if (tot == 0) continue;
            int best = 0;
            for (int k = 1; k < 64; k++) if (cnt[k] > cnt[best]) best = k;
            colour[o] = best; avgH[o] = sumH / tot; depth[o] = water == 0 ? 0 : sumD / water;
        }
        byte[] out = new byte[TILE * TILE];
        for (int j = 0; j < TILE; j++) for (int i = 0; i < TILE; i++) {
            int o = (j + 1) * TILE + i, id = colour[o];
            if (id < 0 || id == 0) continue;
            if (hOut != null) hOut[j * TILE + i] = (byte) Math.max(1, Math.min(255, avgH[o]));
            int shade;
            if (id == WATER_ID) { int dp = depth[o]; shade = dp < 3 ? HIGH : dp < 8 ? NORMAL : dp < 20 ? LOW : LOWEST; }
            else {
                int north = colour[o - TILE] < 0 ? avgH[o] : avgH[o - TILE];
                shade = avgH[o] > north ? HIGH : avgH[o] < north ? LOW : NORMAL;
            }
            out[j * TILE + i] = (byte) (id * 4 + shade);
        }
        return out;
    }

    private static int peek(ServerLevel level, int bx, int bz) {
        if (!Config.Server.worldMap()) return 0;
        Dim d = DIMS.get(level.dimension().location().toString());
        if (d == null || !d.scanned) return 0;
        int rx = bx >> 9, rz = bz >> 9;
        long k = ChunkPos.asLong(rx, rz);
        Region r = d.resident.get(k);
        if (r == null) { if (d.done.contains(k) && LOADS.size() < 256) LOADS.add(new Load(d, rx, rz)); return 0; }
        return r.at(bx, bz);
    }

    static void warm(ServerLevel level, int x0, int z0, int x1, int z1) {
        if (!Config.Server.worldMap()) return;
        Dim d = DIMS.get(level.dimension().location().toString());
        if (d == null || !d.scanned || x1 < x0 || z1 < z0) return;
        int sync = 0;
        for (int rz = z0 >> 9; rz <= Math.min(z1 >> 9, (z0 >> 9) + 15); rz++) for (int rx = x0 >> 9; rx <= Math.min(x1 >> 9, (x0 >> 9) + 15); rx++) {
            long k = ChunkPos.asLong(rx, rz);
            if (d.resident.containsKey(k) || !d.done.contains(k)) continue;
            if (sync < 8) { Region r = read(d, rx, rz); sync++; if (r != null) d.resident.put(k, r); }
            else if (LOADS.size() < 256) LOADS.add(new Load(d, rx, rz));
        }
    }

    static int depthAt(ServerLevel level, int bx, int bz) {
        int v = peek(level, bx, bz);
        if ((v & VALID) == 0) return -1;
        return ((v >> 8) & 0xFF) == WATER_ID ? Math.max(1, (v >> 16) & 0xFF) : 0;
    }

    static int exploredAt(ServerLevel level, int bx, int bz) {
        int v = peek(level, bx, bz);
        if ((v & VALID) == 0) return 0;
        return (MapColor.byId((v >> 8) & 0xFF).col & 0xFFFFFF) << 8 | (v & 0xFF);
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("worldmap")
                        .executes(c -> status(c.getSource()))
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("rebuild").executes(c -> rebuild(c.getSource())))));
    }

    private static int status(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder("World map: ").append(Config.Server.worldMap() ? "on" : "OFF (config worldMap.enabled)");
        sb.append(", ").append(Config.Server.worldMapChunksPerSecond()).append(" chunks/s, prebuild ").append(Config.Server.worldMapPrebuild());
        for (Dim d : DIMS.values())
            sb.append("\n").append(d.id).append(": ").append(d.scanned ? d.doneCount + " / " + d.totalCount + " regions (" + d.pct() + " %), radius " + d.radius + " round " + d.cx + ", " + d.cz : "not scanned yet");
        String s = sb.toString();
        src.sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    private static int rebuild(CommandSourceStack src) {
        for (Dim d : DIMS.values()) d.rebuild = true;
        src.sendSuccess(() -> Component.literal("World map: every region is read again in the background (the map fills in as it goes)."), true);
        return 1;
    }
}
