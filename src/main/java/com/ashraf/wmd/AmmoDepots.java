package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class AmmoDepots {

    private AmmoDepots() {}

    private static final Map<ResourceKey<Level>, Set<Long>> AT = Statics.server(new HashMap<>());

    private static final Map<ResourceKey<Level>, Set<Long>> LOADERS = Statics.server(new HashMap<>());

    static void addLoader(ServerLevel sl, BlockPos pos) { LOADERS.computeIfAbsent(sl.dimension(), k -> new HashSet<>()).add(pos.asLong()); }

    static void removeLoader(ServerLevel sl, BlockPos pos) {
        Set<Long> s = LOADERS.get(sl.dimension());
        if (s != null) { s.remove(pos.asLong()); if (s.isEmpty()) LOADERS.remove(sl.dimension()); }
    }

    static java.util.List<OrdnanceLoaderBlockEntity> loaders(ServerLevel sl) {
        java.util.List<OrdnanceLoaderBlockEntity> l = new java.util.ArrayList<>();
        Set<Long> s = LOADERS.get(sl.dimension());
        if (s == null) return l;
        for (long x : Set.copyOf(s)) { BlockPos p = BlockPos.of(x); if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof OrdnanceLoaderBlockEntity o) l.add(o); }
        return l;
    }

    static void add(ServerLevel sl, BlockPos pos) { AT.computeIfAbsent(sl.dimension(), k -> new HashSet<>()).add(pos.asLong()); }

    static void remove(ServerLevel sl, BlockPos pos) {
        Set<Long> s = AT.get(sl.dimension());
        if (s != null) { s.remove(pos.asLong()); if (s.isEmpty()) AT.remove(sl.dimension()); }
    }

    static AmmoDepotBlockEntity nearest(ServerLevel sl, Vec3 at, UUID owner) {
        Set<Long> s = AT.get(sl.dimension());
        if (s == null || owner == null) return null;
        AmmoDepotBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (long l : Set.copyOf(s)) {
            BlockPos p = BlockPos.of(l);
            if (!sl.isLoaded(p) || !(sl.getBlockEntity(p) instanceof AmmoDepotBlockEntity d) || !owner.equals(d.getOwner())) continue;
            Vec3 w = SableBridge.worldPos(sl, p);
            double h = Math.hypot(w.x - at.x, w.z - at.z);
            if (h > Balance.AD_RANGE || Math.abs(w.y - at.y) > Balance.AD_RANGE_Y) continue;
            if (h < bd) { bd = h; best = d; }
        }
        return best;
    }
}
