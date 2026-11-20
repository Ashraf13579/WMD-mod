package com.ashraf.wmd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongPredicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class RollbackLog {

    private RollbackLog() {}

    static final int MAGIC = 0x574D4452, VERSION = 1, FLUSH_TICKS = 100;

    record Entry(long pos, BlockState before, BlockState after, long time, UUID shooter, CompoundTag nbt) {}

    record Op(long pos, BlockState before, BlockState after, CompoundTag nbt, long first, long last) {}

    private static final Map<String, List<Entry>> PENDING = Statics.server(new HashMap<>());
    private static ExecutorService files;

    static synchronized ExecutorService files() {
        if (files == null || files.isShutdown()) files = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "WMD rollback files"); t.setDaemon(true); return t; });
        return files;
    }

    static void record(ServerLevel level, long pos, BlockState before, BlockState after, UUID shooter, CompoundTag nbt) {
        if (!Config.Server.rollbackLog()) return;
        if (!Config.Server.rollbackLogAll() && !needed(level, pos)) return;
        PENDING.computeIfAbsent(level.dimension().location().toString(), k -> new ArrayList<>()).add(new Entry(pos, before, after, WarManager.now(), shooter, nbt));
    }

    private static final Map<Long, Boolean> NEED = Statics.server(new HashMap<>());
    private static long needTick = Long.MIN_VALUE;
    private static String needDim = "";

    static boolean willLog(ServerLevel level, BlockPos pos) {
        return Config.Server.rollbackLog() && (Config.Server.rollbackLogAll() || needed(level, pos.asLong()));
    }

    static boolean needed(ServerLevel level, long pos) {
        long t = level.getGameTime();
        String dim = level.dimension().location().toString();
        if (t != needTick || !dim.equals(needDim)) { NEED.clear(); needTick = t; needDim = dim; }
        long ck = net.minecraft.world.level.ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
        Boolean b = NEED.get(ck);
        if (b == null) {
            b = RollbackJob.wildChunk(level, ck) ? Config.Server.wildernessHeal() : WarGate.healCovers(level, BlockPos.of(pos));
            NEED.put(ck, b);
        }
        return b;
    }

    static Path root(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve("wmd_rollback"); }

    static Path dir(MinecraftServer server, String dim) { return root(server).resolve(dim.replace(':', '_').replace('/', '_')); }

    static Path region(MinecraftServer server, String dim, int rx, int rz) { return dir(server, dim).resolve("r." + rx + "." + rz + ".bin"); }

    static int rx(long pos) { return BlockPos.getX(pos) >> 9; }
    static int rz(long pos) { return BlockPos.getZ(pos) >> 9; }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) { if (event.getServer().getTickCount() % FLUSH_TICKS == 0) flush(event.getServer()); }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        flush(event.getServer());
        CHECKED.clear();
        ExecutorService f = files;
        if (f != null) {
            f.shutdown();
            try { f.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        RollbackIndex idx = RollbackIndex.get(server);
        for (Map.Entry<String, List<Entry>> e : PENDING.entrySet()) {
            String dim = e.getKey();
            Map<Long, List<Entry>> byRegion = new HashMap<>();
            for (Entry x : e.getValue()) byRegion.computeIfAbsent(((long) rx(x.pos()) << 32) ^ (rz(x.pos()) & 0xFFFFFFFFL), k -> new ArrayList<>()).add(x);
            for (Map.Entry<Long, List<Entry>> r : byRegion.entrySet()) {
                int rx = (int) (r.getKey() >> 32), rz = (int) (long) r.getKey();
                List<Entry> list = r.getValue();
                long t0 = Long.MAX_VALUE, t1 = Long.MIN_VALUE;
                for (Entry x : list) { t0 = Math.min(t0, x.time()); t1 = Math.max(t1, x.time()); }
                idx.add(dim, rx, rz, t0, t1, list.size());
                Path file = region(server, dim, rx, rz);
                files().execute(() -> write(file, list));
            }
        }
        PENDING.clear();
    }

    private static final java.util.Set<Path> CHECKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void repairTail(Path file) {
        try {
            if (!Files.exists(file)) return;
            byte[] all = Files.readAllBytes(file);
            int p = 0;
            while (p < all.length) { int e = memberEnd(all, p); if (e < 0) break; p = e; }
            if (p < all.length) {
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) { ch.truncate(p); }
                Wmd.LOGGER.warn("[rollback] {}: cut {} bytes of a half-written segment off the end", file.getFileName(), all.length - p);
            }
        } catch (IOException | RuntimeException ex) {
            Wmd.LOGGER.warn("[rollback] could not check {}: {}", file, ex.toString());
        }
    }

    private static void write(Path file, List<Entry> list) {
        long size0 = -1L;
        try {
            Files.createDirectories(file.getParent());
            if (CHECKED.add(file)) repairTail(file);
            size0 = Files.exists(file) ? Files.size(file) : 0L;
            Map<BlockState, Integer> states = new IdentityHashMap<>();
            List<BlockState> pal = new ArrayList<>();
            Map<UUID, Integer> who = new HashMap<>();
            List<UUID> whoList = new ArrayList<>();
            List<CompoundTag> nbts = new ArrayList<>();
            int[][] rows = new int[list.size()][4];
            for (int i = 0; i < list.size(); i++) {
                Entry x = list.get(i);
                rows[i][0] = states.computeIfAbsent(x.before(), s -> { pal.add(s); return pal.size() - 1; });
                rows[i][1] = states.computeIfAbsent(x.after(), s -> { pal.add(s); return pal.size() - 1; });
                rows[i][2] = x.shooter() == null ? -1 : who.computeIfAbsent(x.shooter(), u -> { whoList.add(u); return whoList.size() - 1; });
                if (x.nbt() != null) { nbts.add(x.nbt()); rows[i][3] = nbts.size() - 1; } else rows[i][3] = -1;
            }
            try (OutputStream fo = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(fo)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(pal.size());
                for (BlockState s : pal) NbtIo.write(NbtUtils.writeBlockState(s), out);
                out.writeInt(whoList.size());
                for (UUID u : whoList) { out.writeLong(u.getMostSignificantBits()); out.writeLong(u.getLeastSignificantBits()); }
                out.writeInt(nbts.size());
                for (CompoundTag t : nbts) NbtIo.write(t, out);
                out.writeInt(list.size());
                for (int i = 0; i < list.size(); i++) {
                    Entry x = list.get(i);
                    out.writeLong(x.pos()); out.writeInt(rows[i][0]); out.writeInt(rows[i][1]); out.writeLong(x.time()); out.writeInt(rows[i][2]); out.writeInt(rows[i][3]);
                }
            }
        } catch (IOException | RuntimeException ex) {
            Wmd.LOGGER.error("[rollback] could not write {}", file, ex);
            if (size0 >= 0) {
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) { ch.truncate(size0); }
                catch (IOException | RuntimeException ignored) {}
            }
        }
    }

    static Map<Long, Op> read(List<Path> regionFiles, long t0, long t1, LongPredicate chunkOk, java.util.function.Predicate<UUID> shooterOk) {
        long keep = WarManager.now() - Config.Server.rollbackKeepDays() * 86_400_000L;
        long from = Math.max(t0, keep);
        Map<Long, Op> out = new HashMap<>();
        var blocks = BuiltInRegistries.BLOCK.asLookup();
        for (Path file : regionFiles) {
            if (!Files.exists(file)) continue;
            try (InputStream fi = Files.newInputStream(file);
                 DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(fi)))) {
                while (true) {
                    int magic;
                    try { magic = in.readInt(); } catch (EOFException eof) { break; }
                    if (magic != MAGIC || in.readInt() != VERSION) { Wmd.LOGGER.warn("[rollback] {}: unknown segment, the rest of the file is skipped", file); break; }
                    int np = in.readInt();
                    BlockState[] pal = new BlockState[np];
                    for (int i = 0; i < np; i++) pal[i] = NbtUtils.readBlockState(blocks, NbtIo.read(in, NbtAccounter.unlimitedHeap()));
                    int nw = in.readInt();
                    UUID[] who = new UUID[nw];
                    for (int i = 0; i < nw; i++) who[i] = new UUID(in.readLong(), in.readLong());
                    int nn = in.readInt();
                    CompoundTag[] nbts = new CompoundTag[nn];
                    for (int i = 0; i < nn; i++) nbts[i] = NbtIo.read(in, NbtAccounter.unlimitedHeap());
                    int ne = in.readInt();
                    for (int i = 0; i < ne; i++) {
                        long pos = in.readLong();
                        int b = in.readInt(), a = in.readInt();
                        long time = in.readLong();
                        int w = in.readInt(), n = in.readInt();
                        if (time < from || time > t1) continue;
                        if (!chunkOk.test(net.minecraft.world.level.ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4))) continue;
                        if (shooterOk != null && !shooterOk.test(w < 0 ? null : who[w])) continue;
                        CompoundTag nbt = n < 0 ? null : nbts[n];
                        Op old = out.get(pos);
                        if (old == null) out.put(pos, new Op(pos, pal[b], pal[a], nbt, time, time));
                        else if (time < old.first()) out.put(pos, new Op(pos, pal[b], old.after(), nbt, time, old.last()));
                        else if (time >= old.last()) out.put(pos, new Op(pos, old.before(), pal[a], old.nbt(), old.first(), time));
                    }
                }
            } catch (IOException | RuntimeException ex) {
                Wmd.LOGGER.error("[rollback] could not read {}", file, ex);
            }
        }
        return out;
    }

    static final double TRIM_SHARE = 0.5;
    static final long TRIM_MIN_BYTES = 1L << 20;

    static void trim(Path file, long cutoff) {
        try {
            if (!Files.exists(file)) return;
            byte[] all = Files.readAllBytes(file);
            int cut = 0;
            boolean live = false;
            while (cut < all.length) {
                int end = memberEnd(all, cut);
                if (end < 0) { live = true; break; }
                if (maxTime(all, cut, end) >= cutoff) { live = true; break; }
                cut = end;
            }
            if (!live) { Files.deleteIfExists(file); Wmd.LOGGER.info("[rollback] {}: every entry is older than keepDays, removed", file.getFileName()); return; }
            if (cut < TRIM_MIN_BYTES || cut < all.length * TRIM_SHARE) return;
            Path tmp = file.resolveSibling(file.getFileName() + ".trim");
            Files.write(tmp, java.util.Arrays.copyOfRange(all, cut, all.length));
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Wmd.LOGGER.info("[rollback] {}: trimmed {} KB of expired entries, {} KB kept", file.getFileName(), cut >> 10, (all.length - cut) >> 10);
        } catch (IOException | RuntimeException ex) {
            Wmd.LOGGER.warn("[rollback] could not trim {}: {}", file, ex.toString());
        }
    }

    private static int memberEnd(byte[] b, int p) {
        if (p + 18 > b.length || b[p] != (byte) 0x1f || b[p + 1] != (byte) 0x8b || b[p + 2] != 8) return -1;
        int flg = b[p + 3] & 0xFF, q = p + 10;
        if ((flg & 4) != 0) q += 2 + ((b[q] & 0xFF) | (b[q + 1] & 0xFF) << 8);
        if ((flg & 8) != 0) { while (q < b.length && b[q] != 0) q++; q++; }
        if ((flg & 16) != 0) { while (q < b.length && b[q] != 0) q++; q++; }
        if ((flg & 2) != 0) q += 2;
        if (q >= b.length) return -1;
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        try {
            inf.setInput(b, q, b.length - q);
            byte[] buf = new byte[65_536];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) return -1;
            }
            int end = b.length - inf.getRemaining() + 8;
            return end <= b.length ? end : -1;
        } catch (java.util.zip.DataFormatException e) {
            return -1;
        } finally {
            inf.end();
        }
    }

    private static long maxTime(byte[] b, int from, int to) throws IOException {
        long max = Long.MIN_VALUE;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new java.io.ByteArrayInputStream(b, from, to - from))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return Long.MAX_VALUE;
            int np = in.readInt();
            for (int i = 0; i < np; i++) NbtIo.read(in, NbtAccounter.unlimitedHeap());
            int nw = in.readInt();
            in.skipNBytes(16L * nw);
            int nn = in.readInt();
            for (int i = 0; i < nn; i++) NbtIo.read(in, NbtAccounter.unlimitedHeap());
            int ne = in.readInt();
            for (int i = 0; i < ne; i++) {
                in.skipNBytes(16);
                max = Math.max(max, in.readLong());
                in.skipNBytes(8);
            }
        }
        return max;
    }

    static List<Path> allRegions(MinecraftServer server, String dim) {
        List<Path> l = new ArrayList<>();
        Path d = dir(server, dim);
        if (!Files.isDirectory(d)) return l;
        try (var s = Files.list(d)) { s.filter(p -> p.getFileName().toString().endsWith(".bin")).forEach(l::add); } catch (IOException e) { Wmd.LOGGER.warn("[rollback] {}", e.toString()); }
        return l;
    }

    static List<Path> regionsFor(MinecraftServer server, String dim, int cx0, int cz0, int cx1, int cz1) {
        List<Path> l = new ArrayList<>();
        for (int rx = cx0 >> 5; rx <= cx1 >> 5; rx++) for (int rz = cz0 >> 5; rz <= cz1 >> 5; rz++) l.add(region(server, dim, rx, rz));
        return l;
    }
}
