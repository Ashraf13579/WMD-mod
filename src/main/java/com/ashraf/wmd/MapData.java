package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class MapData extends SavedData {

    private static final String NAME = "wmd_map";

    public record Hit(String dim, String type, int tier, boolean refined, double x, double y, double z, double r, long realTime, UUID owner, String ownerName) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("d", dim); t.putString("ty", type); t.putInt("ti", tier); t.putBoolean("rf", refined);
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z); t.putDouble("r", r); t.putLong("rt", realTime);
            if (owner != null) { t.putUUID("ow", owner); t.putString("on", ownerName == null ? "" : ownerName); }
            return t;
        }

        static Hit load(CompoundTag t) {
            return new Hit(t.getString("d"), t.getString("ty"), t.getInt("ti"), t.getBoolean("rf"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getDouble("r"),
                    t.getLong("rt"), t.hasUUID("ow") ? t.getUUID("ow") : null, t.contains("on") ? t.getString("on") : null);
        }
    }

    public record Machine(String dim, int x, int y, int z, int kind, String type, UUID owner, String ownerName, boolean camo, boolean decoy) {
        String key() { return dim + "|" + x + "|" + y + "|" + z; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("d", dim); t.putInt("x", x); t.putInt("y", y); t.putInt("z", z); t.putInt("k", kind); t.putString("ty", type);
            t.putUUID("ow", owner); t.putString("on", ownerName == null ? "" : ownerName); t.putBoolean("c", camo); t.putBoolean("de", decoy);
            return t;
        }

        static Machine load(CompoundTag t) {
            return new Machine(t.getString("d"), t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("k"), t.getString("ty"), t.getUUID("ow"), t.getString("on"),
                    t.getBoolean("c"), t.getBoolean("de"));
        }
    }

    private final List<Hit> history = new ArrayList<>();
    private final Map<String, Machine> machines = new LinkedHashMap<>();

    public static MapData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MapData::new, MapData::load, null), NAME);
    }

    List<Hit> history() { return history; }

    Map<String, Machine> machines() { return machines; }

    void addHit(Hit h, int max) {
        history.add(h);
        while (history.size() > max) history.remove(0);
        setDirty();
    }

    void clearHistory() { history.clear(); setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag h = new ListTag();
        for (Hit x : history) h.add(x.save());
        tag.put("hist", h);
        ListTag m = new ListTag();
        for (Machine x : machines.values()) m.add(x.save());
        tag.put("mach", m);
        return tag;
    }

    public static MapData load(CompoundTag tag, HolderLookup.Provider registries) {
        MapData d = new MapData();
        for (Tag t : tag.getList("hist", Tag.TAG_COMPOUND)) d.history.add(Hit.load((CompoundTag) t));
        for (Tag t : tag.getList("mach", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("ow")) continue;
            Machine mm = Machine.load(c);
            d.machines.put(mm.key(), mm);
        }
        return d;
    }
}
