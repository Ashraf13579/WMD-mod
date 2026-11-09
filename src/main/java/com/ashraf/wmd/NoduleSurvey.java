package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class NoduleSurvey {

    private NoduleSurvey() {}

    private record Gen(ResourceKey<Level> dim, int x, int y, int z, int r, int count) {}

    private static final Queue<Gen> GENERATED = Statics.server(new ConcurrentLinkedQueue<>());
    private static final List<Job> JOBS = Statics.server(new ArrayList<>());

    static final class Patch {
        int x, y, z, r, left;

        Patch(int x, int y, int z, int r, int left) { this.x = x; this.y = y; this.z = z; this.r = r; this.left = left; }
    }

    public record Mark(int x, int y, int z, int r, int left, long time, UUID ship) {}

    static void generated(WorldGenLevel level, int x, int y, int z, int r, int count) {
        if (count <= 0) return;
        try { GENERATED.add(new Gen(level.getLevel().dimension(), x, y, z, r, count)); }
        catch (RuntimeException e) {  }
    }

    static String net(UUID owner, int freq) { return (owner == null ? "-" : owner.toString()) + "|" + freq; }

    static void start(ServerLevel sl, UUID owner, int freq, Vec3 ear, UUID ship, UUID player) {
        for (Job j : JOBS)
            if (j.dim == sl.dimension() && j.net.equals(net(owner, freq)) && Math.hypot(j.ex - ear.x, j.ez - ear.z) < 16.0) return;
        JOBS.add(new Job(sl, net(owner, freq), ear, ship, player));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Gen g; (g = GENERATED.poll()) != null; ) {
            ServerLevel sl = server.getLevel(g.dim());
            if (sl != null) Data.get(sl).add(g.x(), g.y(), g.z(), g.r(), g.count());
        }
        if (JOBS.isEmpty()) return;
        long t0 = WorkBudget.start();
        boolean first = true;
        while (!JOBS.isEmpty() && (first || WorkBudget.more(t0))) {
            Job j = JOBS.get(0);
            ServerLevel sl = server.getLevel(j.dim);
            if (sl == null) { JOBS.remove(0); continue; }
            if (j.step(sl)) { JOBS.remove(0); j.finish(sl); }
            first = false;
        }
        WorkBudget.done(t0);
    }

    static void mined(ServerLevel sl, BlockPos p) {
        Data d = Data.get(sl);
        Patch f = d.nearest(p.getX(), p.getY(), p.getZ(), Balance.SURVEY_JOIN);
        if (f != null && f.left > 0) { f.left--; d.setDirty(); }
    }

    public static List<Mark> marks(ServerLevel sl, UUID owner, int freq) {
        Data d = Data.get(sl);
        List<Mark> l = d.marks.get(net(owner, freq));
        if (l == null) return List.of();
        long now = sl.getGameTime();
        if (l.removeIf(m -> now - m.time() > Balance.SURVEY_KEEP_TICKS)) d.setDirty();
        return new ArrayList<>(l);
    }

    static Mark nearestOf(ServerLevel sl, UUID owner, double x, double z) {
        if (owner == null) return null;
        Data d = Data.get(sl);
        String pre = owner + "|";
        long now = sl.getGameTime();
        Mark best = null;
        double bd = Double.MAX_VALUE;
        for (Map.Entry<String, List<Mark>> e : d.marks.entrySet()) {
            if (!e.getKey().startsWith(pre)) continue;
            for (Mark m : e.getValue()) {
                if (now - m.time() > Balance.SURVEY_KEEP_TICKS || m.left() <= 0) continue;
                double dd = (m.x() + 0.5 - x) * (m.x() + 0.5 - x) + (m.z() + 0.5 - z) * (m.z() + 0.5 - z);
                if (dd < bd) { bd = dd; best = m; }
            }
        }
        return best;
    }

    static int depth(ServerLevel sl, int y) { return Math.max(0, sl.getSeaLevel() - 1 - y); }

    private static final class Job {
        final ResourceKey<Level> dim;
        final String net;
        final double ex, ez;
        final UUID ship, player;
        final LongArrayList chunks = new LongArrayList();
        final LongOpenHashSet scanned = new LongOpenHashSet();
        final List<int[]> found = new ArrayList<>();
        int idx;

        Job(ServerLevel sl, String net, Vec3 ear, UUID ship, UUID player) {
            this.dim = sl.dimension();
            this.net = net;
            this.ex = ear.x;
            this.ez = ear.z;
            this.ship = ship;
            this.player = player;
            int r = Balance.SURVEY_RANGE + Balance.SURVEY_JOIN;
            int c0x = (int) Math.floor(ex - r) >> 4, c1x = (int) Math.floor(ex + r) >> 4, c0z = (int) Math.floor(ez - r) >> 4, c1z = (int) Math.floor(ez + r) >> 4;
            for (int cx = c0x; cx <= c1x; cx++)
                for (int cz = c0z; cz <= c1z; cz++) {
                    double nx = Math.max(cx * 16, Math.min(ex, cx * 16 + 16)), nz = Math.max(cz * 16, Math.min(ez, cz * 16 + 16));
                    if (Math.hypot(nx - ex, nz - ez) <= r) chunks.add(ChunkPos.asLong(cx, cz));
                }
        }

        boolean step(ServerLevel sl) {
            if (idx >= chunks.size()) return true;
            long key = chunks.getLong(idx++);
            LevelChunk c = sl.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (c == null) return idx >= chunks.size();
            scanned.add(key);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int bx = ChunkPos.getX(key) << 4, bz = ChunkPos.getZ(key) << 4;
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++) {
                    int y = c.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
                    p.set(bx + lx, y, bz + lz);
                    if (!c.getBlockState(p).is(Wmd.NODULE_FIELD.get())) continue;
                    p.setY(y + 1);
                    if (c.getFluidState(p).is(FluidTags.WATER)) found.add(new int[] { bx + lx, y, bz + lz });
                }
            return idx >= chunks.size();
        }

        void finish(ServerLevel sl) {
            Data d = Data.get(sl);
            int range = Balance.SURVEY_RANGE, join = Balance.SURVEY_JOIN;
            List<Patch> near = d.near(ex, ez, range + join * 2);
            Map<Patch, List<int[]>> got = new IdentityHashMap<>();
            for (int[] b : found) {
                Patch best = null;
                double bd = (double) join * join + 0.01;
                for (Patch f : near) {
                    double dd = (double) (f.x - b[0]) * (f.x - b[0]) + (double) (f.z - b[2]) * (f.z - b[2]);
                    if (dd <= bd && Math.abs(f.y - b[1]) <= 8) { bd = dd; best = f; }
                }
                if (best == null) { best = new Patch(b[0], b[1], b[2], 1, 0); near.add(best); d.put(best); }
                got.computeIfAbsent(best, k -> new ArrayList<>()).add(b);
            }
            for (Patch f : new ArrayList<>(near)) {
                boolean full = fullyScanned(f.x, f.z, join);
                List<int[]> bs = got.get(f);
                if (bs == null || bs.isEmpty()) {
                    if (full) d.remove(f);
                    continue;
                }
                if (full || f.left == 0) {
                    long sx = 0, sy = 0, sz = 0;
                    for (int[] b : bs) { sx += b[0]; sy += b[1]; sz += b[2]; }
                    int nx = (int) Math.round(sx / (double) bs.size()), ny = (int) Math.round(sy / (double) bs.size()), nz = (int) Math.round(sz / (double) bs.size());
                    double rr = 1.0;
                    for (int[] b : bs) rr = Math.max(rr, Math.hypot(b[0] - nx, b[2] - nz) + 0.5);
                    d.move(f, nx, ny, nz);
                    f.r = (int) Math.ceil(rr);
                    f.left = full ? bs.size() : Math.max(f.left, bs.size());
                } else f.left = Math.max(f.left, bs.size());
            }

            long now = sl.getGameTime();
            List<Mark> marks = d.marks.computeIfAbsent(net, k -> new ArrayList<>());
            marks.removeIf(m -> now - m.time() > Balance.SURVEY_KEEP_TICKS || Math.hypot(m.x() + 0.5 - ex, m.z() + 0.5 - ez) <= range);
            int n = 0;
            List<Patch> inRange = new ArrayList<>();
            for (Patch f : d.near(ex, ez, range)) if (f.left > 0 && Math.hypot(f.x + 0.5 - ex, f.z + 0.5 - ez) <= range) inRange.add(f);
            inRange.sort((a, b) -> Double.compare(Math.hypot(a.x - ex, a.z - ez), Math.hypot(b.x - ex, b.z - ez)));
            for (Patch f : inRange) {
                if (n >= Balance.SURVEY_MAX_MARKS) break;
                marks.add(new Mark(f.x, f.y, f.z, f.r, f.left, now, ship));
                n++;
            }
            while (marks.size() > Balance.SURVEY_MAX_MARKS) marks.remove(0);
            d.setDirty();
            Wmd.LOGGER.info("[survey] net {} at {} {}: {} loaded chunk(s) of {}, {} nodule block(s) seen, {} field(s) marked", net, (int) ex, (int) ez,
                    scanned.size(), chunks.size(), found.size(), n);
            ServerPlayer pl = player == null ? null : sl.getServer().getPlayerList().getPlayer(player);
            if (pl != null) pl.displayClientMessage(Component.translatable("gui.wmd.sonar.survey_done", n, range), true);
        }

        private boolean fullyScanned(int x, int z, int pad) {
            for (int cx = (x - pad) >> 4; cx <= (x + pad) >> 4; cx++)
                for (int cz = (z - pad) >> 4; cz <= (z + pad) >> 4; cz++)
                    if (!scanned.contains(ChunkPos.asLong(cx, cz))) return false;
            return true;
        }
    }

    static final class Data extends SavedData {

        private static final String NAME = "wmd_nodule_survey";

        private final Long2ObjectOpenHashMap<List<Patch>> byChunk = new Long2ObjectOpenHashMap<>();

        final Map<String, List<Mark>> marks = new LinkedHashMap<>();

        static Data get(ServerLevel sl) {
            return sl.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static long key(int x, int z) { return ChunkPos.asLong(x >> 4, z >> 4); }

        void put(Patch f) { byChunk.computeIfAbsent(key(f.x, f.z), k -> new ArrayList<>()).add(f); setDirty(); }

        void remove(Patch f) {
            List<Patch> l = byChunk.get(key(f.x, f.z));
            if (l != null) { l.remove(f); if (l.isEmpty()) byChunk.remove(key(f.x, f.z)); }
            setDirty();
        }

        void move(Patch f, int x, int y, int z) {
            if (key(x, z) != key(f.x, f.z)) { remove(f); f.x = x; f.z = z; f.y = y; put(f); }
            else { f.x = x; f.y = y; f.z = z; }
        }

        void add(int x, int y, int z, int r, int count) {
            Patch old = nearest(x, y, z, Balance.SURVEY_JOIN);
            if (old != null) remove(old);
            put(new Patch(x, y, z, r, count));
        }

        List<Patch> near(double x, double z, double r) {
            List<Patch> out = new ArrayList<>();
            int c0x = (int) Math.floor(x - r) >> 4, c1x = (int) Math.floor(x + r) >> 4, c0z = (int) Math.floor(z - r) >> 4, c1z = (int) Math.floor(z + r) >> 4;
            for (int cx = c0x; cx <= c1x; cx++)
                for (int cz = c0z; cz <= c1z; cz++) {
                    List<Patch> l = byChunk.get(ChunkPos.asLong(cx, cz));
                    if (l == null) continue;
                    for (Patch f : l) if (Math.hypot(f.x + 0.5 - x, f.z + 0.5 - z) <= r) out.add(f);
                }
            return out;
        }

        Patch nearest(int x, int y, int z, int r) {
            Patch best = null;
            double bd = Double.MAX_VALUE;
            for (Patch f : near(x + 0.5, z + 0.5, r)) {
                double dd = Math.hypot(f.x - x, f.z - z);
                if (Math.abs(f.y - y) <= 8 && dd < bd) { bd = dd; best = f; }
            }
            return best;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            int[] a = tag.getIntArray("fields");
            for (int i = 0; i + 4 < a.length; i += 5) d.put(new Patch(a[i], a[i + 1], a[i + 2], a[i + 3], a[i + 4]));
            ListTag nets = tag.getList("marks", Tag.TAG_COMPOUND);
            for (Tag t : nets) {
                if (!(t instanceof CompoundTag c)) continue;
                List<Mark> l = new ArrayList<>();
                for (Tag u : c.getList("m", Tag.TAG_COMPOUND)) {
                    if (!(u instanceof CompoundTag m)) continue;
                    int[] v = m.getIntArray("v");
                    if (v.length < 5) continue;
                    l.add(new Mark(v[0], v[1], v[2], v[3], v[4], m.getLong("t"), m.hasUUID("s") ? m.getUUID("s") : null));
                }
                if (!l.isEmpty()) d.marks.put(c.getString("net"), l);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            List<Integer> a = new ArrayList<>();
            for (List<Patch> l : byChunk.values()) for (Patch f : l) { a.add(f.x); a.add(f.y); a.add(f.z); a.add(f.r); a.add(f.left); }
            int[] arr = new int[a.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = a.get(i);
            tag.put("fields", new IntArrayTag(arr));
            ListTag nets = new ListTag();
            for (Map.Entry<String, List<Mark>> e : marks.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                CompoundTag c = new CompoundTag();
                c.putString("net", e.getKey());
                ListTag ml = new ListTag();
                for (Mark m : e.getValue()) {
                    CompoundTag t = new CompoundTag();
                    t.putIntArray("v", new int[] { m.x(), m.y(), m.z(), m.r(), m.left() });
                    t.putLong("t", m.time());
                    if (m.ship() != null) t.putUUID("s", m.ship());
                    ml.add(t);
                }
                c.put("m", ml);
                nets.add(c);
            }
            tag.put("marks", nets);
            return tag;
        }
    }

    static final class Hint {
        int dist = -1, bearing, left, depth;

        boolean refresh(ServerLevel sl, UUID owner, BlockPos at) {
            if ((sl.getGameTime() + (at.asLong() & 31L)) % Balance.SURVEY_HINT_TICKS != 0) return false;
            Vec3 me = SableBridge.worldPos(sl, at);
            Mark m = nearestOf(sl, owner, me.x, me.z);
            int nd = -1, nb = 0, nl = 0, nh = 0;
            if (m != null) {
                double dx = m.x() + 0.5 - me.x, dz = m.z() + 0.5 - me.z;
                nd = (int) Math.round(Math.hypot(dx, dz));
                nb = (int) Math.round((Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0);
                nl = m.left();
                nh = depth(sl, m.y());
            }
            boolean ch = nd != dist || nb != bearing || nl != left || nh != depth;
            dist = nd; bearing = nb; left = nl; depth = nh;
            return ch;
        }

        void write(CompoundTag tag) { tag.putIntArray("svHint", new int[] { dist, bearing, left, depth }); }

        void read(CompoundTag tag) {
            int[] v = tag.getIntArray("svHint");
            if (v.length >= 4) { dist = v[0]; bearing = v[1]; left = v[2]; depth = v[3]; }
        }

        private static final String[] DIRS = { "n", "ne", "e", "se", "s", "sw", "w", "nw" };

        Component line() {
            if (dist < 0) return Component.translatable("gui.wmd.survey.hint_none");
            Component dir = Component.translatable("gui.wmd.sonar.dir." + DIRS[(int) Math.floor((bearing + 22.5) / 45.0) % 8]);
            return Component.translatable("gui.wmd.survey.hint", String.format("%,d", dist), dir, left, depth);
        }

        boolean found() { return dist >= 0; }
    }

    static int knownFields(ServerLevel sl) {
        int n = 0;
        for (List<Patch> l : Data.get(sl).byChunk.values()) n += l.size();
        return n;
    }
}
