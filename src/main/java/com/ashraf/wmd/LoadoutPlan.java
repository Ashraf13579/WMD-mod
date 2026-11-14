package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class LoadoutPlan {

    private LoadoutPlan() {}

    static final int G_SINGLE = 0, G_PAIR = 1, G_RIPPLE = 2, G_HOLD = 3, MODES = 4, GROUPS = 4;

    static LoadoutHolder holderOf(ServerLevel sl, UUID ship) {
        if (ship == null) return null;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) if (sl.getBlockEntity(p) instanceof LoadoutHolder h) return h;
        return null;
    }

    static CompoundTag read(ServerLevel sl, UUID ship, AmmoDepotBlockEntity depot) {
        LoadoutHolder h = holderOf(sl, ship);
        if (h != null) return h.loadoutTag().copy();
        return depot == null ? new CompoundTag() : depot.fallbackLoadout(ship).copy();
    }

    static void write(ServerLevel sl, UUID ship, AmmoDepotBlockEntity depot, CompoundTag t) {
        LoadoutHolder h = holderOf(sl, ship);
        if (h != null) h.setLoadoutTag(t);
        else if (depot != null) depot.setFallbackLoadout(ship, t);
    }

    static boolean hasPlan(CompoundTag t) { return t.contains("plan"); }

    static Map<Long, ItemStack> plan(CompoundTag t, HolderLookup.Provider r) { return readPlan(t.getList("plan", Tag.TAG_COMPOUND), r); }

    private static Map<Long, ItemStack> readPlan(ListTag l, HolderLookup.Provider r) {
        Map<Long, ItemStack> m = new LinkedHashMap<>();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            ItemStack s = ItemStack.parseOptional(r, e.getCompound("i"));
            if (!s.isEmpty()) m.put(e.getLong("p"), s);
        }
        return m;
    }

    private static ListTag writePlan(Map<Long, ItemStack> m, HolderLookup.Provider r) {
        ListTag l = new ListTag();
        for (Map.Entry<Long, ItemStack> e : m.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.put("i", e.getValue().copyWithCount(1).save(r));
            l.add(c);
        }
        return l;
    }

    static void setPlan(CompoundTag t, long pos, ItemStack item, HolderLookup.Provider r) {
        Map<Long, ItemStack> m = plan(t, r);
        if (item == null || item.isEmpty()) m.remove(pos); else m.put(pos, item.copyWithCount(1));
        t.put("plan", writePlan(m, r));
    }

    static void clearPlan(CompoundTag t) { t.remove("plan"); }

    static List<String> presetNames(CompoundTag t) {
        List<String> n = new ArrayList<>();
        ListTag l = t.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) n.add(l.getCompound(i).getString("n"));
        return n;
    }

    static int activePreset(CompoundTag t) { return t.contains("active") ? t.getInt("active") : -1; }

    static void savePreset(CompoundTag t, String name) {
        String nm = name == null || name.isBlank() ? "Preset" : name.strip();
        if (nm.length() > 24) nm = nm.substring(0, 24);
        ListTag l = t.getList("presets", Tag.TAG_COMPOUND);
        int at = -1;
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getString("n").equals(nm)) at = i;
        if (at < 0 && l.size() >= Balance.LO_PRESETS) return;
        CompoundTag p = new CompoundTag();
        p.putString("n", nm);
        p.put("plan", t.getList("plan", Tag.TAG_COMPOUND).copy());
        if (at >= 0) l.set(at, p); else { l.add(p); at = l.size() - 1; }
        t.put("presets", l);
        t.putInt("active", at);
    }

    static void loadPreset(CompoundTag t, int i) {
        ListTag l = t.getList("presets", Tag.TAG_COMPOUND);
        if (i < 0 || i >= l.size()) return;
        t.put("plan", l.getCompound(i).getList("plan", Tag.TAG_COMPOUND).copy());
        t.putInt("active", i);
    }

    static void deletePreset(CompoundTag t, int i) {
        ListTag l = t.getList("presets", Tag.TAG_COMPOUND);
        if (i < 0 || i >= l.size()) return;
        l.remove(i);
        t.put("presets", l);
        int a = activePreset(t);
        if (a == i) t.remove("active"); else if (a > i) t.putInt("active", a - 1);
    }

    static long[] groupStations(CompoundTag t, int g) {
        ListTag l = t.getList("groups", Tag.TAG_COMPOUND);
        return g < l.size() ? l.getCompound(g).getLongArray("st") : new long[0];
    }

    static int groupMode(CompoundTag t, int g) {
        ListTag l = t.getList("groups", Tag.TAG_COMPOUND);
        return g < l.size() ? l.getCompound(g).getInt("m") : G_SINGLE;
    }

    private static ListTag groups(CompoundTag t) {
        ListTag l = t.getList("groups", Tag.TAG_COMPOUND);
        while (l.size() < GROUPS) { CompoundTag c = new CompoundTag(); c.putLongArray("st", new long[0]); c.putInt("m", G_SINGLE); l.add(c); }
        return l;
    }

    static void toggleGroup(CompoundTag t, int g, long pos) {
        if (g < 0 || g >= GROUPS) return;
        ListTag l = groups(t);
        boolean had = false;
        for (int i = 0; i < GROUPS; i++) {
            CompoundTag c = l.getCompound(i);
            List<Long> st = new ArrayList<>();
            for (long x : c.getLongArray("st")) { if (x == pos) { if (i == g) had = true; continue; } st.add(x); }
            if (i == g && !had && st.size() < Balance.LO_GROUP_MAX) st.add(pos);
            c.put("st", new LongArrayTag(st.stream().mapToLong(Long::longValue).toArray()));
        }
        t.put("groups", l);
    }

    static void cycleMode(CompoundTag t, int g) {
        if (g < 0 || g >= GROUPS) return;
        ListTag l = groups(t);
        CompoundTag c = l.getCompound(g);
        c.putInt("m", (c.getInt("m") + 1) % MODES);
        t.put("groups", l);
    }

    static int groupOf(CompoundTag t, long pos) {
        for (int g = 0; g < GROUPS; g++) for (long x : groupStations(t, g)) if (x == pos) return g;
        return -1;
    }

    static Map<UUID, CompoundTag> newFallback() { return new HashMap<>(); }
}
