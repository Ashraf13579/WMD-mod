package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class CctvNet {

    private CctvNet() {}

    public static final int CAM = 0, COVERT = 1, RELAY = 2;

    public record Node(int kind, UUID owner, int freq, float yaw, boolean up) {}

    public record Cam(BlockPos pos, float yaw, boolean covert) {}

    public static final class Data extends SavedData {
        private static final String NAME = "wmd_cctv";
        final Map<String, Map<Long, Node>> dims = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        Map<Long, Node> of(Level level) { return dims.computeIfAbsent(level.dimension().location().toString(), k -> new HashMap<>()); }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag all = new ListTag();
            for (Map.Entry<String, Map<Long, Node>> d : dims.entrySet()) for (Map.Entry<Long, Node> e : d.getValue().entrySet()) {
                Node n = e.getValue();
                if (n.owner() == null) continue;
                CompoundTag t = new CompoundTag();
                t.putString("d", d.getKey()); t.putLong("p", e.getKey()); t.putInt("k", n.kind()); t.putUUID("o", n.owner());
                t.putInt("f", n.freq()); t.putFloat("y", n.yaw()); t.putBoolean("u", n.up());
                all.add(t);
            }
            tag.put("nodes", all);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag raw : tag.getList("nodes", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                if (!t.hasUUID("o")) continue;
                d.dims.computeIfAbsent(t.getString("d"), k -> new HashMap<>()).put(t.getLong("p"), new Node(t.getInt("k"), t.getUUID("o"), t.getInt("f"), t.getFloat("y"), t.getBoolean("u")));
            }
            return d;
        }
    }

    static void register(ServerLevel sl, BlockPos pos, Node n) {
        if (n.owner() == null) return;
        Data d = Data.get(sl.getServer());
        Node old = d.of(sl).put(pos.asLong(), n);
        if (!n.equals(old)) d.setDirty();
    }

    static void forget(ServerLevel sl, BlockPos pos) {
        Data d = Data.get(sl.getServer());
        if (d.of(sl).remove(pos.asLong()) != null) d.setDirty();
    }

    public static List<Cam> reachable(ServerLevel sl, Vec3 from, UUID owner, int freq) {
        if (owner == null) return List.of();
        Map<Long, Node> all = Data.get(sl.getServer()).of(sl);
        List<Vec3> hubs = new ArrayList<>();
        hubs.add(from);
        boolean sat = reconOverhead(sl, owner);
        if (!sat) {
            List<Vec3> frontier = List.of(from);
            java.util.Set<Long> used = new java.util.HashSet<>();
            for (int hop = 0; hop < Balance.CCTV_HOPS && !frontier.isEmpty(); hop++) {
                List<Vec3> next = new ArrayList<>();
                for (Map.Entry<Long, Node> e : all.entrySet()) {
                    Node n = e.getValue();
                    if (n.kind() != RELAY || !n.up() || n.freq() != freq || !owner.equals(n.owner()) || used.contains(e.getKey())) continue;
                    Vec3 r = Vec3.atCenterOf(BlockPos.of(e.getKey()));
                    for (Vec3 f : frontier) if (f.distanceTo(r) <= Balance.CCTV_HOP) { used.add(e.getKey()); next.add(r); break; }
                }
                hubs.addAll(next);
                frontier = next;
            }
        }
        List<Cam> cams = new ArrayList<>();
        for (Map.Entry<Long, Node> e : all.entrySet()) {
            Node n = e.getValue();
            if (n.kind() == RELAY) continue;
            boolean hacked = !owner.equals(n.owner()) && owner.equals(Hacks.hacker(sl, BlockPos.of(e.getKey())));
            if (!hacked && (n.freq() != freq || !owner.equals(n.owner()))) continue;
            Vec3 c = Vec3.atCenterOf(BlockPos.of(e.getKey()));
            boolean in = sat;
            for (int i = 0; i < hubs.size() && !in; i++) in = hubs.get(i).distanceTo(c) <= Balance.CCTV_DIRECT;
            if (in) cams.add(new Cam(BlockPos.of(e.getKey()), n.yaw(), n.kind() == COVERT));
        }
        cams.sort(Comparator.comparingDouble(c -> Vec3.atCenterOf(c.pos()).distanceToSqr(from)));
        return cams.size() > Balance.CCTV_MAX ? List.copyOf(cams.subList(0, Balance.CCTV_MAX)) : cams;
    }

    private static boolean reconOverhead(ServerLevel sl, UUID owner) {
        if (!sl.dimension().equals(Level.OVERWORLD)) return false;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(sl.getServer()).sats())
            if ("recon".equals(s.type()) && owner.equals(s.owner()) && now < s.endMs() && OrbitData.controlled(s)) return true;
        return false;
    }

    private static final Set<BlockEntity> LIVE = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    static void join(BlockEntity be) { LIVE.add(be); }
    static void leave(BlockEntity be) { LIVE.remove(be); }

    static <T extends BlockEntity> List<T> near(Level level, Class<T> c, Vec3 p, double r) {
        List<T> l = new ArrayList<>();
        synchronized (LIVE) {
            for (BlockEntity be : LIVE)
                if (c.isInstance(be) && !be.isRemoved() && be.getLevel() == level && Vec3.atCenterOf(be.getBlockPos()).distanceTo(p) <= r) l.add(c.cast(be));
        }
        return l;
    }

    static <T extends BlockEntity> List<T> all(Level level, Class<T> c) { return near(level, c, Vec3.ZERO, Double.MAX_VALUE); }

    private record View(BlockPos cam, long at) {}
    private static final long VIEW_MS = 5_000L;
    private static final Map<UUID, View> VIEWING = new HashMap<>();

    static synchronized void viewing(UUID player, BlockPos cam) { if (cam == null) VIEWING.remove(player); else VIEWING.put(player, new View(cam, System.currentTimeMillis())); }
    static synchronized BlockPos viewing(UUID player) {
        View v = VIEWING.get(player);
        if (v == null) return null;
        if (System.currentTimeMillis() - v.at() > VIEW_MS) { VIEWING.remove(player); return null; }
        return v.cam();
    }

    public static synchronized void clearStatics() { VIEWING.clear(); LIVE.clear(); }

    static boolean jammed(ServerLevel sl, BlockPos pos, UUID owner) { return PortableJammerBlockEntity.jams(sl, pos, owner); }
}
