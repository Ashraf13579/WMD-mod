package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class WarData extends SavedData {

    private static final String NAME = "wmd_wars";

    static final int HISTORY = 64;

    static final int DECLARED = 0, ACTIVE = 1, ENDED = 2;

    static final int R_NONE = 0, R_PEACE = 1, R_SURRENDER = 2, R_TIMEOUT = 3, R_ADMIN = 4, R_GONE = 5, R_SCORE = 6;

    static final class War {
        int id;
        UUID attacker, defender;
        final Set<UUID> sideA = new LinkedHashSet<>(), sideB = new LinkedHashSet<>();

        final Set<UUID> callA = new LinkedHashSet<>(), callB = new LinkedHashSet<>();
        long declaredAt, startsAt, endsAt, endedAt;
        int state, result;

        UUID winner;

        int peaceBy;
        long peaceUntil;

        int takeLeft;
        long takeUntil;

        long healAt;
        boolean healed;

        boolean healDone;
        int healTries;
        final Set<Long> taken = new java.util.HashSet<>();

        String takenDim = "";

        final int[] points = new int[3];
        final int[][] cats = new int[3][WarScore.CATS];

        final Set<UUID> craftsScored = new java.util.HashSet<>();

        boolean live() { return state != ENDED; }

        int sideOf(UUID nation) { return nation == null ? 0 : sideA.contains(nation) ? 1 : sideB.contains(nation) ? 2 : 0; }

        UUID principal(int side) { return side == 1 ? attacker : side == 2 ? defender : null; }

        Set<UUID> side(int s) { return s == 1 ? sideA : sideB; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putUUID("a", attacker);
            t.putUUID("d", defender);
            t.put("sa", ids(sideA)); t.put("sb", ids(sideB)); t.put("ca", ids(callA)); t.put("cb", ids(callB));
            t.putLong("dec", declaredAt); t.putLong("st", startsAt); t.putLong("end", endsAt); t.putLong("ended", endedAt);
            t.putInt("state", state); t.putInt("res", result);
            if (winner != null) t.putUUID("win", winner);
            t.putInt("pb", peaceBy); t.putLong("pu", peaceUntil);
            t.putInt("tl", takeLeft); t.putLong("tu", takeUntil);
            t.putLong("heal", healAt); t.putBoolean("healed", healed); t.putBoolean("healDone", healDone); t.putInt("healTries", healTries);
            t.putLongArray("taken", taken.stream().mapToLong(Long::longValue).toArray());
            t.putString("takenDim", takenDim);
            t.putIntArray("pts", points);
            t.putIntArray("pca", cats[1]); t.putIntArray("pcb", cats[2]);
            t.put("crafts", ids(craftsScored));
            return t;
        }

        static War load(CompoundTag t) {
            War w = new War();
            w.id = t.getInt("id");
            w.attacker = t.getUUID("a");
            w.defender = t.getUUID("d");
            read(t.getList("sa", Tag.TAG_INT_ARRAY), w.sideA); read(t.getList("sb", Tag.TAG_INT_ARRAY), w.sideB);
            read(t.getList("ca", Tag.TAG_INT_ARRAY), w.callA); read(t.getList("cb", Tag.TAG_INT_ARRAY), w.callB);
            w.declaredAt = t.getLong("dec"); w.startsAt = t.getLong("st"); w.endsAt = t.getLong("end"); w.endedAt = t.getLong("ended");
            w.state = t.getInt("state"); w.result = t.getInt("res");
            w.winner = t.hasUUID("win") ? t.getUUID("win") : null;
            w.peaceBy = t.getInt("pb"); w.peaceUntil = t.getLong("pu");
            w.takeLeft = t.getInt("tl"); w.takeUntil = t.getLong("tu");
            w.healAt = t.getLong("heal"); w.healed = t.getBoolean("healed");
            w.healDone = t.contains("healDone") ? t.getBoolean("healDone") : w.healed;
            w.healTries = t.getInt("healTries");
            for (long c : t.getLongArray("taken")) w.taken.add(c);
            w.takenDim = t.contains("takenDim") ? t.getString("takenDim") : "minecraft:overworld";
            int[] p = t.getIntArray("pts");
            if (p.length == 3) System.arraycopy(p, 0, w.points, 0, 3);
            int[] a = t.getIntArray("pca"), b = t.getIntArray("pcb");
            System.arraycopy(a, 0, w.cats[1], 0, Math.min(a.length, WarScore.CATS));
            System.arraycopy(b, 0, w.cats[2], 0, Math.min(b.length, WarScore.CATS));
            read(t.getList("crafts", Tag.TAG_INT_ARRAY), w.craftsScored);
            return w;
        }
    }

    final List<War> wars = new ArrayList<>();

    final Map<String, Long> cooldowns = new HashMap<>();
    int nextId = 1;

    final Map<UUID, int[]> totals = new HashMap<>();

    final Map<UUID, long[]> treaty = new HashMap<>();
    final Map<UUID, Long> breakers = new HashMap<>();

    int[] totals(UUID nation) { return totals.computeIfAbsent(nation, k -> new int[4]); }

    public static WarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WarData::new, WarData::load, null), NAME);
    }

    static String pair(UUID a, UUID b) { return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a; }

    War byId(int id) {
        for (War w : wars) if (w.id == id) return w;
        return null;
    }

    private static ListTag ids(Set<UUID> s) {
        ListTag l = new ListTag();
        for (UUID u : s) l.add(net.minecraft.nbt.NbtUtils.createUUID(u));
        return l;
    }

    private static void read(ListTag l, Set<UUID> into) {
        for (Tag t : l) into.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (War w : wars) l.add(w.save());
        tag.put("wars", l);
        CompoundTag c = new CompoundTag();
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) c.putLong(e.getKey(), e.getValue());
        tag.put("cool", c);
        tag.putInt("next", nextId);
        CompoundTag tot = new CompoundTag();
        for (Map.Entry<UUID, int[]> e : totals.entrySet()) tot.putIntArray(e.getKey().toString(), e.getValue());
        tag.put("totals", tot);
        CompoundTag tr = new CompoundTag(), br = new CompoundTag();
        for (Map.Entry<UUID, long[]> e : treaty.entrySet()) tr.putLongArray(e.getKey().toString(), e.getValue());
        for (Map.Entry<UUID, Long> e : breakers.entrySet()) br.putLong(e.getKey().toString(), e.getValue());
        tag.put("treaty", tr);
        tag.put("breakers", br);
        return tag;
    }

    public static WarData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarData d = new WarData();
        for (Tag t : tag.getList("wars", Tag.TAG_COMPOUND)) d.wars.add(War.load((CompoundTag) t));
        CompoundTag c = tag.getCompound("cool");
        for (String k : c.getAllKeys()) d.cooldowns.put(k, c.getLong(k));
        d.nextId = Math.max(1, tag.getInt("next"));
        CompoundTag tot = tag.getCompound("totals");
        for (String k : tot.getAllKeys()) {
            int[] v = tot.getIntArray(k);
            try { d.totals.put(UUID.fromString(k), v.length == 4 ? v : new int[4]); } catch (IllegalArgumentException ignored) { }
        }
        CompoundTag tr = tag.getCompound("treaty"), br = tag.getCompound("breakers");
        for (String k : tr.getAllKeys()) { long[] v = tr.getLongArray(k); try { if (v.length == 2) d.treaty.put(UUID.fromString(k), v); } catch (IllegalArgumentException ignored) { } }
        for (String k : br.getAllKeys()) { try { d.breakers.put(UUID.fromString(k), br.getLong(k)); } catch (IllegalArgumentException ignored) { } }
        return d;
    }
}
