package com.ashraf.wmd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Planet {

    private Planet() {}

    static final int TILE = 64;

    static final class Snap {
        final ResourceKey<Level> dim;
        final int gx, gz, key;
        final byte[] nearId = new byte[Balance.PLANET_NEAR_PX * Balance.PLANET_NEAR_PX];
        final short[] nearH = new short[Balance.PLANET_NEAR_PX * Balance.PLANET_NEAR_PX];
        final byte[] farRgb = new byte[Balance.PLANET_FAR_PX * Balance.PLANET_FAR_PX * 3];
        final int[] nearVer = new int[(Balance.PLANET_NEAR_PX / TILE) * (Balance.PLANET_NEAR_PX / TILE)];
        final int[] farVer = new int[(Balance.PLANET_FAR_PX / TILE) * (Balance.PLANET_FAR_PX / TILE)];
        final ArrayDeque<Long> todo = new ArrayDeque<>();
        final java.util.Set<Long> todoSet = new java.util.HashSet<>();
        volatile boolean saving;
        long viewedAt;
        final AtomicInteger inFlight = new AtomicInteger();
        int farRow = 0;
        boolean dirty;
        long savedAt;

        Snap(ResourceKey<Level> dim, int gx, int gz) { this.dim = dim; this.gx = gx; this.gz = gz; this.key = (dim.location().hashCode() * 31 + gx) * 31 + gz; }

        int nearHalf() { return Balance.PLANET_NEAR_PX * Balance.PLANET_NEAR_BLOCKS / 2; }
        void enqueue(long k, boolean first) { if (todoSet.add(k)) { if (first) todo.addFirst(k); else todo.add(k); } }
        Long poll() { Long k = todo.poll(); if (k != null) todoSet.remove(k); return k; }
        int x0() { return gx - nearHalf(); }
        int z0() { return gz - nearHalf(); }
    }

    private static final Map<String, Snap> SNAPS = new HashMap<>();

    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private record Sent(int key, int[] near, int[] far) {}

    static void clearStatics() { SNAPS.clear(); SENT.clear(); }

    private static final int EVICT_TICKS = 12000, EVICT_CHECK = 200;

    private static String id(ResourceKey<Level> dim, int gx, int gz) { return dim.location().toString().replace(':', '_') + "_" + gx + "_" + gz; }

    static Snap get(ServerLevel ground, int gx, int gz) {
        gx = Math.floorDiv(gx, 64) * 64; gz = Math.floorDiv(gz, 64) * 64;
        String id = id(ground.dimension(), gx, gz);
        Snap s = SNAPS.get(id);
        if (s != null) { s.viewedAt = ground.getServer().getTickCount(); return s; }
        s = new Snap(ground.dimension(), gx, gz);
        s.viewedAt = ground.getServer().getTickCount();
        SNAPS.put(id, s);
        boolean saved = load(ground, s, id);
        {
            int span = Balance.PLANET_NEAR_PX * Balance.PLANET_NEAR_BLOCKS;
            int c0x = s.x0() >> 4, c0z = s.z0() >> 4, n = span >> 4;

            java.util.List<Long> l = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (!saved || unknown(s, c0x + i, c0z + j)) l.add(ChunkPos.asLong(c0x + i, c0z + j));
            final int mx = c0x + n / 2, mz = c0z + n / 2;
            l.sort(java.util.Comparator.comparingInt(k -> { int dx = ChunkPos.getX(k) - mx, dz = ChunkPos.getZ(k) - mz; return dx * dx + dz * dz; }));
            for (long k : l) s.enqueue(k, false);
        }
        return s;
    }

    private static boolean unknown(Snap s, int cx, int cz) {
        int step = Balance.PLANET_NEAR_BLOCKS;
        for (int lx = 0; lx < 16; lx += step) for (int lz = 0; lz < 16; lz += step) {
            int px = ((cx << 4) + lx - s.x0()) / step, pz = ((cz << 4) + lz - s.z0()) / step;
            if (px < 0 || pz < 0 || px >= Balance.PLANET_NEAR_PX || pz >= Balance.PLANET_NEAR_PX) continue;
            if (s.nearId[pz * Balance.PLANET_NEAR_PX + px] == 0) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (SNAPS.isEmpty() && SENT.isEmpty() && (server.getTickCount() % 20 != 0)) return;
        for (Snap s : SNAPS.values()) {
            ServerLevel lv = server.getLevel(s.dim);
            if (lv == null) continue;
            int budget = Balance.PLANET_READS_PER_TICK;
            while (budget-- > 0 && !s.todo.isEmpty() && s.inFlight.get() < Balance.PLANET_READS_PER_TICK * 4) readChunk(lv, s, s.poll());
            if (s.farRow < Balance.PLANET_FAR_PX) far(lv, s, 8);
            if (s.dirty && server.getTickCount() - s.savedAt > 600 && s.todo.isEmpty() && s.inFlight.get() == 0) { save(lv, s); s.savedAt = server.getTickCount(); }
        }
        if (server.getTickCount() % 4 == 0) sync(server);
        if (server.getTickCount() % EVICT_CHECK == 0) {
            for (java.util.Iterator<Snap> it = SNAPS.values().iterator(); it.hasNext(); ) {
                Snap s = it.next();
                if (server.getTickCount() - s.viewedAt < EVICT_TICKS || s.inFlight.get() > 0 || s.saving) continue;
                ServerLevel lv = server.getLevel(s.dim);
                if (lv != null && s.dirty) save(lv, s);
                it.remove();
            }
        }
    }

    private static void readChunk(ServerLevel lv, Snap s, long k) {
        int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
        LevelChunk c = lv.getChunkSource().getChunkNow(cx, cz);
        if (c != null) { fromLoaded(lv, s, c); return; }
        s.inFlight.incrementAndGet();
        CompletableFuture<Optional<CompoundTag>> f = lv.getChunkSource().chunkMap.read(new ChunkPos(cx, cz));
        f.thenAcceptAsync(o -> {
            try { o.ifPresent(tag -> fromNbt(lv, s, cx, cz, tag)); }
            catch (RuntimeException e) { Wmd.LOGGER.debug("[planet] chunk {} {} not read: {}", cx, cz, e.toString()); }
            finally { s.inFlight.decrementAndGet(); }
        }, Util.backgroundExecutor());
    }

    private static void put(Snap s, int bx, int bz, int colour, int h) {
        int px = (bx - s.x0()) / Balance.PLANET_NEAR_BLOCKS, pz = (bz - s.z0()) / Balance.PLANET_NEAR_BLOCKS;
        if (px < 0 || pz < 0 || px >= Balance.PLANET_NEAR_PX || pz >= Balance.PLANET_NEAR_PX) return;
        int i = pz * Balance.PLANET_NEAR_PX + px;
        s.nearId[i] = (byte) colour;
        s.nearH[i] = (short) h;
        int t = (pz / TILE) * (Balance.PLANET_NEAR_PX / TILE) + px / TILE;
        synchronized (s) { s.nearVer[t]++; s.dirty = true; }
    }

    private static void fromLoaded(ServerLevel lv, Snap s, LevelChunk c) {
        int step = Balance.PLANET_NEAR_BLOCKS;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx += step) for (int lz = 0; lz < 16; lz += step) {
            int bx = c.getPos().getMinBlockX() + lx, bz = c.getPos().getMinBlockZ() + lz;
            int y = c.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
            m.set(bx, y, bz);
            BlockState st = c.getBlockState(m);
            put(s, bx, bz, st.getMapColor(lv, m).id, y);
        }
    }

    private static void fromNbt(ServerLevel lv, Snap s, int cx, int cz, CompoundTag tag) {
        if (!tag.getString("Status").endsWith("full")) return;
        CompoundTag hm = tag.getCompound("Heightmaps");
        long[] h = hm.getLongArray("MOTION_BLOCKING");
        if (h.length == 0) return;
        int minY = lv.getMinBuildHeight(), bits = Mth.ceillog2(lv.getHeight() + 1), per = 64 / bits;
        ListTag sections = tag.getList("sections", Tag.TAG_COMPOUND);
        Map<Integer, CompoundTag> byY = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) { CompoundTag sec = sections.getCompound(i); byY.put((int) sec.getByte("Y"), sec); }
        Map<Integer, BlockState[]> palettes = new HashMap<>();
        int step = Balance.PLANET_NEAR_BLOCKS;
        for (int lx = 0; lx < 16; lx += step) for (int lz = 0; lz < 16; lz += step) {
            int idx = lz * 16 + lx;
            if (idx / per >= h.length) continue;
            int v = (int) ((h[idx / per] >> ((idx % per) * bits)) & ((1L << bits) - 1));
            int y = minY + v - 1;
            CompoundTag sec = byY.get(Math.floorDiv(y, 16));
            int colour = 0;
            if (sec != null && sec.contains("block_states")) {
                CompoundTag bs = sec.getCompound("block_states");
                BlockState[] pal = palettes.computeIfAbsent(Math.floorDiv(y, 16), k -> {
                    ListTag p = bs.getList("palette", Tag.TAG_COMPOUND);
                    BlockState[] out = new BlockState[p.size()];
                    for (int i = 0; i < p.size(); i++) out[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), p.getCompound(i));
                    return out;
                });
                BlockState st;
                if (pal.length == 1) st = pal[0];
                else {
                    long[] d = bs.getLongArray("data");
                    int b = Math.max(4, Mth.ceillog2(pal.length)), pv = 64 / b;
                    int i = ((y & 15) << 8) | (lz << 4) | lx;
                    int pi = i / pv < d.length ? (int) ((d[i / pv] >> ((i % pv) * b)) & ((1L << b) - 1)) : 0;
                    st = pal[Math.min(pi, pal.length - 1)];
                }
                colour = st.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).id;
            }
            put(s, (cx << 4) + lx, (cz << 4) + lz, colour, y);
        }
    }

    private static void far(ServerLevel lv, Snap s, int rows) {
        var gen = lv.getChunkSource().getGenerator();
        var sampler = lv.getChunkSource().randomState().sampler();
        int half = Balance.PLANET_FAR_PX * Balance.PLANET_FAR_BLOCKS / 2, qy = 63 >> 2;
        for (int r = 0; r < rows && s.farRow < Balance.PLANET_FAR_PX; r++, s.farRow++) {
            int z = s.gz - half + s.farRow * Balance.PLANET_FAR_BLOCKS;
            for (int c = 0; c < Balance.PLANET_FAR_PX; c++) {
                int x = s.gx - half + c * Balance.PLANET_FAR_BLOCKS;
                Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(x >> 2, qy, z >> 2, sampler);
                int rgb = biomeColour(b);
                int i = (s.farRow * Balance.PLANET_FAR_PX + c) * 3;
                s.farRgb[i] = (byte) (rgb >> 16); s.farRgb[i + 1] = (byte) (rgb >> 8); s.farRgb[i + 2] = (byte) rgb;
            }
            s.farVer[(s.farRow / TILE) * (Balance.PLANET_FAR_PX / TILE)]++;
            for (int t = 1; t < Balance.PLANET_FAR_PX / TILE; t++) s.farVer[(s.farRow / TILE) * (Balance.PLANET_FAR_PX / TILE) + t]++;
        }
        s.dirty = true;
    }

    static int biomeColour(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_DEEP_OCEAN)) return 0x1B3A6B;
        if (b.is(BiomeTags.IS_OCEAN)) return 0x2A5490;
        if (b.is(BiomeTags.IS_RIVER)) return 0x3567A8;
        String n = b.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (n.contains("frozen") || n.contains("ice") || n.contains("snowy") || n.contains("peaks") || n.contains("glacier")) return 0xDDE6EE;
        if (b.is(BiomeTags.IS_BADLANDS)) return 0xB0603A;
        if (n.contains("desert") || b.is(BiomeTags.IS_BEACH)) return 0xD9C98E;
        if (n.contains("mushroom")) return 0x8A7486;
        if (n.contains("swamp") || n.contains("mangrove")) return 0x4E5F35;
        if (b.is(BiomeTags.IS_JUNGLE)) return 0x3E8A2C;
        if (b.is(BiomeTags.IS_SAVANNA)) return 0x9A9A4E;
        if (b.is(BiomeTags.IS_TAIGA)) return 0x3F6A4E;
        if (b.is(BiomeTags.IS_MOUNTAIN) || n.contains("stony") || n.contains("windswept")) return 0x7E8478;
        if (b.is(BiomeTags.IS_FOREST)) return 0x3D6E2E;
        return 0x6C9B48;
    }

    static void changed(ServerLevel level, int cx, int cz) {
        for (Snap s : SNAPS.values()) {
            if (s.dim != level.dimension()) continue;
            int bx = cx << 4, bz = cz << 4, span = Balance.PLANET_NEAR_PX * Balance.PLANET_NEAR_BLOCKS;
            if (bx < s.x0() - 16 || bz < s.z0() - 16 || bx >= s.x0() + span || bz >= s.z0() + span) continue;
            long k = ChunkPos.asLong(cx, cz);
            s.enqueue(k, true);
        }
    }

    static void flash(ServerLevel level, double x, double z, int tier) {
        if (tier < 3) return;
        for (ServerPlayer p : orbitPlayers(level.getServer())) {
            Snap s = snapFor(p);
            if (s == null || s.dim != level.dimension()) continue;
            double dx = x - s.gx, dz = z - s.gz, half = Balance.PLANET_FAR_PX * Balance.PLANET_FAR_BLOCKS / 2.0;
            if (Math.abs(dx) > half || Math.abs(dz) > half) continue;
            PacketDistributor.sendToPlayer(p, new PlanetPayloads.Flash((float) dx, (float) dz, tier));
        }
    }

    private static java.util.List<ServerPlayer> orbitPlayers(net.minecraft.server.MinecraftServer server) {
        ServerLevel o = server.getLevel(Orbit.ORBIT);
        return o == null ? java.util.List.of() : o.players();
    }

    private static Snap snapFor(ServerPlayer p) {
        int n = Math.max(0, (int) Math.round(p.getX() / Balance.ORBIT_SLOT_SPACING));
        Orbit.Ground g = Orbit.ground(p.server, n);
        ServerLevel lv = p.server.getLevel(g.dim());
        return lv == null ? null : get(lv, g.x(), g.z());
    }

    private static void sync(net.minecraft.server.MinecraftServer server) {
        java.util.Set<UUID> here = new java.util.HashSet<>();
        for (ServerPlayer p : orbitPlayers(server)) {
            Snap s = snapFor(p);
            if (s == null) continue;
            here.add(p.getUUID());
            Sent sent = SENT.get(p.getUUID());
            if (sent == null || sent.key() != s.key) {
                sent = new Sent(s.key, new int[s.nearVer.length], new int[s.farVer.length]);
                SENT.put(p.getUUID(), sent);
                PacketDistributor.sendToPlayer(p, new PlanetPayloads.Info(s.key, s.gx, s.gz, Balance.PLANET_NEAR_PX, Balance.PLANET_NEAR_BLOCKS, Balance.PLANET_FAR_PX, Balance.PLANET_FAR_BLOCKS));
            }
            int budget = Balance.PLANET_TILES_PER_SEND;
            int nt = Balance.PLANET_NEAR_PX / TILE, ft = Balance.PLANET_FAR_PX / TILE;
            for (int t = 0; t < s.farVer.length && budget > 0; t++)
                if (s.farVer[t] != sent.far()[t] && (t / ft + 1) * TILE <= s.farRow) { sent.far()[t] = s.farVer[t]; PacketDistributor.sendToPlayer(p, tile(s, false, t % ft, t / ft)); budget--; }

            int mid = nt / 2;
            for (int ring = 0; ring <= mid && budget > 0; ring++)
                for (int ty = mid - ring; ty <= mid + ring && budget > 0; ty++)
                    for (int tx = mid - ring; tx <= mid + ring && budget > 0; tx++) {
                        if (tx < 0 || ty < 0 || tx >= nt || ty >= nt || (Math.abs(tx - mid) != ring && Math.abs(ty - mid) != ring)) continue;
                        int t = ty * nt + tx;
                        int nv;
                        synchronized (s) { nv = s.nearVer[t]; }
                        if (nv == sent.near()[t]) continue;
                        sent.near()[t] = nv;
                        PacketDistributor.sendToPlayer(p, tile(s, true, tx, ty));
                        budget--;
                    }
        }
        SENT.keySet().retainAll(here);
    }

    private static PlanetPayloads.Tile tile(Snap s, boolean near, int tx, int ty) {
        byte[] rgba = new byte[TILE * TILE * 4];
        int w = near ? Balance.PLANET_NEAR_PX : Balance.PLANET_FAR_PX;
        for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) {
            int px = tx * TILE + x, py = ty * TILE + y, o = (y * TILE + x) * 4;
            if (near) {
                int i = py * w + px, id = s.nearId[i] & 0xFF;
                if (id == 0) continue;
                int hN = py > 0 ? s.nearH[i - w] : s.nearH[i];
                int d = s.nearH[i] - hN;
                MapColor.Brightness br = id == MapColor.WATER.id ? MapColor.Brightness.NORMAL : d > 0 ? MapColor.Brightness.HIGH : d < 0 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                int c = MapColor.byId(id).calculateRGBColor(br);
                rgba[o] = (byte) c; rgba[o + 1] = (byte) (c >> 8); rgba[o + 2] = (byte) (c >> 16); rgba[o + 3] = (byte) 255;
            } else {
                int i = (py * w + px) * 3;
                rgba[o] = s.farRgb[i]; rgba[o + 1] = s.farRgb[i + 1]; rgba[o + 2] = s.farRgb[i + 2]; rgba[o + 3] = (byte) 255;
            }
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(bo, new Deflater(Deflater.BEST_SPEED))) { d.write(rgba); } catch (IOException ignored) {}
        return new PlanetPayloads.Tile(s.key, near, tx, ty, bo.toByteArray());
    }

    private static Path file(ServerLevel lv, String id) { return lv.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("wmd_planet").resolve(id + ".bin"); }

    private static void save(ServerLevel lv, Snap s) {
        if (s.saving) return;
        Path f = file(lv, id(s.dim, s.gx, s.gz));
        final byte[] nearId; final short[] nearH; final byte[] farRgb; final int farRow;
        synchronized (s) { nearId = s.nearId.clone(); nearH = s.nearH.clone(); farRgb = s.farRgb.clone(); farRow = s.farRow; s.dirty = false; }
        s.saving = true;
        Util.ioPool().execute(() -> {
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try {
                Files.createDirectories(f.getParent());
                try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 65536)), 65536))) {
                    o.writeInt(2); o.writeInt(Balance.PLANET_NEAR_PX); o.writeInt(Balance.PLANET_FAR_PX); o.writeInt(farRow);
                    o.write(nearId);
                    ByteBuffer hb = ByteBuffer.allocate(nearH.length * 2);
                    hb.asShortBuffer().put(nearH);
                    o.write(hb.array());
                    o.write(farRgb);
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { s.dirty = true; Wmd.LOGGER.warn("[planet] could not save {}: {}", f, e.toString()); }
            finally { s.saving = false; }
        });
    }

    private static boolean load(ServerLevel lv, Snap s, String id) {
        Path f = file(lv, id);
        if (!Files.exists(f)) return false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new BufferedInputStream(Files.newInputStream(f), 65536)), 65536))) {
            if (in.readInt() != 2 || in.readInt() != Balance.PLANET_NEAR_PX || in.readInt() != Balance.PLANET_FAR_PX) return false;
            s.farRow = in.readInt();
            in.readFully(s.nearId);
            byte[] hb = new byte[s.nearH.length * 2];
            in.readFully(hb);
            ByteBuffer.wrap(hb).asShortBuffer().get(s.nearH);
            in.readFully(s.farRgb);
            java.util.Arrays.fill(s.nearVer, 1); java.util.Arrays.fill(s.farVer, 1);
            return true;
        } catch (IOException e) { Wmd.LOGGER.warn("[planet] could not load {}: {}", f, e.toString()); return false; }
    }

    static void refresh(ServerPlayer p) {
        Snap s = snapFor(p);
        if (s == null) return;
        SNAPS.values().remove(s);
        try { Files.deleteIfExists(file(p.server.getLevel(s.dim), id(s.dim, s.gx, s.gz))); } catch (IOException ignored) {}
        SENT.remove(p.getUUID());
    }
}
