package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class SatDefenceData extends SavedData {

    private static final String NAME = "wmd_sat5a";
    public static final int LOG_MAX = 8;

    private final Map<Integer, CompoundTag> sats = new HashMap<>();
    private final Map<UUID, Integer> modes = new HashMap<>();
    private final Map<UUID, List<CompoundTag>> logs = new HashMap<>();

    public static SatDefenceData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SatDefenceData::new, SatDefenceData::load, null), NAME);
    }

    private CompoundTag sat(int id) { return sats.computeIfAbsent(id, k -> new CompoundTag()); }

    public long shotMs(int id) { CompoundTag t = sats.get(id); return t == null ? 0L : t.getLong("shot"); }

    public int hits(int id) { CompoundTag t = sats.get(id); return t == null ? 0 : t.getInt("hits"); }

    public int misses(int id) { CompoundTag t = sats.get(id); return t == null ? 0 : t.getInt("miss"); }

    public void shot(int id, long nowMs, boolean hit) {
        CompoundTag t = sat(id);
        t.putLong("shot", nowMs);
        if (hit) t.putInt("hits", t.getInt("hits") + 1);
        else t.putInt("miss", t.getInt("miss") + 1);
        setDirty();
    }

    public void recharge(int id) {
        CompoundTag t = sats.get(id);
        if (t != null && t.contains("shot")) { t.remove("shot"); setDirty(); }
    }

    public long rekeyMs(int id) { CompoundTag t = sats.get(id); return t == null ? 0L : t.getLong("rekey"); }

    public void rekeyed(int id, long nowMs) { sat(id).putLong("rekey", nowMs); setDirty(); }

    public void prune(MinecraftServer server) {
        OrbitData od = OrbitData.get(server);
        if (sats.keySet().removeIf(id -> od.byId(id) == null)) setDirty();
    }

    public int mode(UUID owner) { return owner == null ? SatDefence.MODE_ZONE : modes.getOrDefault(owner, SatDefence.MODE_ZONE); }

    public void setMode(UUID owner, int mode) {
        if (owner == null) return;
        modes.put(owner, mode == SatDefence.MODE_ANY ? SatDefence.MODE_ANY : SatDefence.MODE_ZONE);
        setDirty();
    }

    public List<CompoundTag> log(UUID owner) { return owner == null ? List.of() : List.copyOf(logs.getOrDefault(owner, List.of())); }

    public void addLog(UUID owner, CompoundTag e) {
        if (owner == null) return;
        List<CompoundTag> l = logs.computeIfAbsent(owner, k -> new ArrayList<>());
        l.add(e);
        while (l.size() > LOG_MAX) l.remove(0);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sl = new ListTag();
        for (Map.Entry<Integer, CompoundTag> e : sats.entrySet()) {
            CompoundTag c = e.getValue().copy();
            c.putInt("id", e.getKey());
            sl.add(c);
        }
        tag.put("sats", sl);
        ListTag ml = new ListTag();
        for (Map.Entry<UUID, Integer> e : modes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("o", e.getKey());
            c.putInt("m", e.getValue());
            ml.add(c);
        }
        tag.put("modes", ml);
        ListTag ll = new ListTag();
        for (Map.Entry<UUID, List<CompoundTag>> e : logs.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("o", e.getKey());
            ListTag l = new ListTag();
            l.addAll(e.getValue());
            c.put("l", l);
            ll.add(c);
        }
        tag.put("logs", ll);
        return tag;
    }

    public static SatDefenceData load(CompoundTag tag, HolderLookup.Provider registries) {
        SatDefenceData d = new SatDefenceData();
        for (Tag t : tag.getList("sats", Tag.TAG_COMPOUND)) {
            CompoundTag c = ((CompoundTag) t).copy();
            int id = c.getInt("id");
            c.remove("id");
            d.sats.put(id, c);
        }
        for (Tag t : tag.getList("modes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("o")) d.modes.put(c.getUUID("o"), c.getInt("m"));
        }
        for (Tag t : tag.getList("logs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("o")) continue;
            List<CompoundTag> l = new ArrayList<>();
            for (Tag e : c.getList("l", Tag.TAG_COMPOUND)) l.add((CompoundTag) e);
            d.logs.put(c.getUUID("o"), l);
        }
        return d;
    }
}
