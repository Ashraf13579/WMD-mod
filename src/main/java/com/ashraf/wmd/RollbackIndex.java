package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class RollbackIndex extends SavedData {

    private static final String NAME = "wmd_rollback_index";

    static final class Seg {
        String dim;
        int rx, rz, n;
        long t0, t1;
        boolean wild;

        long wildNext;
    }

    final List<Seg> segs = new ArrayList<>();

    public static RollbackIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RollbackIndex::new, RollbackIndex::load, null), NAME);
    }

    void add(String dim, int rx, int rz, long t0, long t1, int n) {
        Seg s = new Seg();
        s.dim = dim; s.rx = rx; s.rz = rz; s.t0 = t0; s.t1 = t1; s.n = n;
        segs.add(s);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Seg s : segs) {
            CompoundTag t = new CompoundTag();
            t.putString("d", s.dim); t.putInt("x", s.rx); t.putInt("z", s.rz); t.putLong("a", s.t0); t.putLong("b", s.t1); t.putInt("n", s.n);
            if (s.wild) t.putBoolean("w", true);
            if (s.wildNext > 0) t.putLong("wn", s.wildNext);
            l.add(t);
        }
        tag.put("segs", l);
        return tag;
    }

    public static RollbackIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        RollbackIndex r = new RollbackIndex();
        for (Tag x : tag.getList("segs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            Seg s = new Seg();
            s.dim = t.getString("d"); s.rx = t.getInt("x"); s.rz = t.getInt("z"); s.t0 = t.getLong("a"); s.t1 = t.getLong("b"); s.n = t.getInt("n"); s.wild = t.getBoolean("w"); s.wildNext = t.getLong("wn");
            r.segs.add(s);
        }
        return r;
    }
}
