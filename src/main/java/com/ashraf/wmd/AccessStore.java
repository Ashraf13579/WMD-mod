package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

public final class AccessStore extends SavedData {

    static final String NAME = "wmd_access";
    public static final int NONE = 0, FRIEND = 1, USE = 2, CONFIGURE = 3;

    static final int MAX_GRANTS = 64, MAX_MACHINES = 4_096;

    static final class Grant {
        String name; int level; boolean all = true;
        final Set<String> machines = new HashSet<>();
        Grant(String name, int level) { this.name = name; this.level = level; }
        boolean covers(String key) { return all || machines.contains(key); }
    }

    record Machine(String block, String dim, long pos, long seen) {}

    final Map<UUID, Map<UUID, Grant>> grants = new HashMap<>();
    final Map<UUID, Set<String>> publics = new HashMap<>();
    final Map<UUID, Map<String, Machine>> machines = new HashMap<>();

    public AccessStore() {}

    private static AccessStore cached;
    private static MinecraftServer cachedFor;

    public static AccessStore get(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AccessStore::new, AccessStore::load, null), NAME);
        cachedFor = server;
        return cached;
    }

    static void clearCache() { cached = null; cachedFor = null; }

    static AccessStore of(Level level) {
        return level instanceof ServerLevel sl ? get(sl.getServer()) : null;
    }

    static String key(Level level, BlockPos pos) { return level.dimension().location() + "|" + pos.asLong(); }

    static String key(Owned o) {
        return o instanceof BlockEntity be && be.getLevel() != null ? key(be.getLevel(), be.getBlockPos()) : null;
    }

    static int level(Owned o, UUID who) {
        if (o == null || who == null || o.getOwner() == null || !(o instanceof BlockEntity be)) return NONE;
        AccessStore s = of(be.getLevel());
        if (s == null) return NONE;
        Map<UUID, Grant> m = s.grants.get(o.getOwner());
        Grant g = m == null ? null : m.get(who);
        return g != null && g.covers(key(be.getLevel(), be.getBlockPos())) ? g.level : NONE;
    }

    static boolean isPublic(Owned o) {
        if (o == null || o.getOwner() == null || !(o instanceof BlockEntity be)) return false;
        AccessStore s = of(be.getLevel());
        Set<String> p = s == null ? null : s.publics.get(o.getOwner());
        return p != null && p.contains(key(be.getLevel(), be.getBlockPos()));
    }

    static List<UUID> players(Owned o, int min) {
        List<UUID> out = new ArrayList<>();
        if (o == null || o.getOwner() == null || !(o instanceof BlockEntity be)) return out;
        AccessStore s = of(be.getLevel());
        Map<UUID, Grant> m = s == null ? null : s.grants.get(o.getOwner());
        if (m == null) return out;
        String k = key(be.getLevel(), be.getBlockPos());
        for (Map.Entry<UUID, Grant> e : m.entrySet()) if (e.getValue().level >= min && e.getValue().covers(k)) out.add(e.getKey());
        return out;
    }

    void index(Owned o) {
        if (o == null || o.getOwner() == null || !(o instanceof BlockEntity be) || be.getLevel() == null) return;
        if (o instanceof ProcessUnitBlockEntity pu && !pu.ownable()) return;
        Map<String, Machine> m = machines.computeIfAbsent(o.getOwner(), k -> new LinkedHashMap<>());
        String k = key(be.getLevel(), be.getBlockPos());
        String block = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        Machine old = m.get(k);
        if (old != null && old.block().equals(block)) return;
        if (old == null && m.size() >= MAX_MACHINES) return;
        if (old != null) forgetKey(o.getOwner(), k);
        m.put(k, new Machine(block, be.getLevel().dimension().location().toString(), be.getBlockPos().asLong(), be.getLevel().getGameTime()));
        setDirty();
    }

    private void forgetKey(UUID owner, String key) {
        Set<String> pub = publics.get(owner);
        if (pub != null) pub.remove(key);
        Map<UUID, Grant> gs = grants.get(owner);
        if (gs != null) for (Grant g : gs.values()) g.machines.remove(key);
    }

    void prune(ServerLevel level, int cx, int cz) {
        String dim = level.dimension().location().toString();
        boolean changed = false;
        for (Map.Entry<UUID, Map<String, Machine>> e : machines.entrySet()) {
            for (var it = e.getValue().entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Machine> me = it.next();
                Machine mc = me.getValue();
                if (!mc.dim().equals(dim)) continue;
                BlockPos p = BlockPos.of(mc.pos());
                if (p.getX() >> 4 != cx || p.getZ() >> 4 != cz) continue;
                if (!(level.getBlockEntity(p) instanceof Owned o) || !e.getKey().equals(o.getOwner())) {
                    it.remove();
                    forgetKey(e.getKey(), me.getKey());
                    changed = true;
                }
            }
        }
        if (changed) setDirty();
    }

    boolean check(MinecraftServer server, UUID owner, String key) {
        Map<String, Machine> m = machines.get(owner);
        Machine mc = m == null ? null : m.get(key);
        if (mc == null) return false;
        ServerLevel lv = levelOf(server, mc.dim());
        BlockPos p = BlockPos.of(mc.pos());
        if (lv == null || !lv.isLoaded(p)) return true;
        if (lv.getBlockEntity(p) instanceof Owned o && owner.equals(o.getOwner())) return true;
        m.remove(key);
        forgetKey(owner, key);
        setDirty();
        return false;
    }

    static ServerLevel levelOf(MinecraftServer server, String dim) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }

    Map<UUID, Grant> grantsOf(UUID owner) { return grants.computeIfAbsent(owner, k -> new LinkedHashMap<>()); }

    Set<String> publicsOf(UUID owner) { return publics.computeIfAbsent(owner, k -> new HashSet<>()); }

    Map<String, Machine> machinesOf(UUID owner) { return machines.computeIfAbsent(owner, k -> new LinkedHashMap<>()); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        Set<UUID> all = new HashSet<>(grants.keySet());
        all.addAll(publics.keySet());
        all.addAll(machines.keySet());
        for (UUID owner : all) {
            CompoundTag o = new CompoundTag();
            o.putUUID("owner", owner);
            ListTag gl = new ListTag();
            for (Map.Entry<UUID, Grant> e : grants.getOrDefault(owner, Map.of()).entrySet()) {
                CompoundTag g = new CompoundTag();
                g.putUUID("id", e.getKey());
                g.putString("name", e.getValue().name);
                g.putInt("level", e.getValue().level);
                g.putBoolean("all", e.getValue().all);
                ListTag ms = new ListTag();
                for (String k : e.getValue().machines) ms.add(net.minecraft.nbt.StringTag.valueOf(k));
                g.put("machines", ms);
                gl.add(g);
            }
            o.put("grants", gl);
            ListTag pl = new ListTag();
            for (String k : publics.getOrDefault(owner, Set.of())) pl.add(net.minecraft.nbt.StringTag.valueOf(k));
            o.put("public", pl);
            ListTag ml = new ListTag();
            for (Machine mc : machines.getOrDefault(owner, Map.of()).values()) {
                CompoundTag m = new CompoundTag();
                m.putString("block", mc.block());
                m.putString("dim", mc.dim());
                m.put("pos", LongTag.valueOf(mc.pos()));
                m.putLong("seen", mc.seen());
                ml.add(m);
            }
            o.put("machines", ml);
            owners.add(o);
        }
        tag.put("owners", owners);
        return tag;
    }

    public static AccessStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AccessStore s = new AccessStore();
        for (Tag t : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            if (!o.hasUUID("owner")) continue;
            UUID owner = o.getUUID("owner");
            for (Tag gt : o.getList("grants", Tag.TAG_COMPOUND)) {
                CompoundTag g = (CompoundTag) gt;
                if (!g.hasUUID("id")) continue;
                Grant gr = new Grant(g.getString("name"), Math.max(FRIEND, Math.min(CONFIGURE, g.getInt("level"))));
                gr.all = g.getBoolean("all");
                for (Tag mt : g.getList("machines", Tag.TAG_STRING)) gr.machines.add(mt.getAsString());
                s.grantsOf(owner).put(g.getUUID("id"), gr);
            }
            for (Tag pt : o.getList("public", Tag.TAG_STRING)) s.publicsOf(owner).add(pt.getAsString());
            for (Tag mt : o.getList("machines", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mt;
                String dim = m.getString("dim");
                long pos = m.contains("pos") ? ((LongTag) m.get("pos")).getAsLong() : 0L;
                s.machinesOf(owner).put(dim + "|" + pos, new Machine(m.getString("block"), dim, pos, m.getLong("seen")));
            }
        }
        return s;
    }
}
