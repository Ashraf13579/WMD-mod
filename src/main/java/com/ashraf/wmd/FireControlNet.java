package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class FireControlNet {

    private FireControlNet() {}

    static final long NET_TTL = 60L;
    private static final Map<String, Map<Long, Long>> LAUNCHERS = Statics.server(new HashMap<>());

    static String key(ServerLevel level, UUID owner, int freq) { return (owner == null ? "-" : owner.toString()) + "|" + freq + "|" + level.dimension().location(); }

    static void beat(ServerLevel level, UUID owner, int freq, long pos) {
        LAUNCHERS.computeIfAbsent(key(level, owner, freq), k -> new HashMap<>()).put(pos, level.getGameTime());
    }

    private record Aim(net.minecraft.world.phys.Vec3 at, long when) {}

    private static final Map<String, Map<Long, Aim>> AIMS = Statics.server(new HashMap<>());

    static void aim(ServerLevel level, UUID owner, int freq, long console, net.minecraft.world.phys.Vec3 at) {
        String k = key(level, owner, freq);
        if (at == null) { Map<Long, Aim> m = AIMS.get(k); if (m != null) { m.remove(console); if (m.isEmpty()) AIMS.remove(k); } }
        else AIMS.computeIfAbsent(k, x -> new HashMap<>()).put(console, new Aim(at, level.getGameTime()));
    }

    public static net.minecraft.world.phys.Vec3 aimOf(ServerLevel level, UUID owner, int freq) {
        Map<Long, Aim> m = AIMS.get(key(level, owner, freq));
        if (m == null) return null;
        long now = level.getGameTime();
        m.values().removeIf(a -> now - a.when() > NET_TTL);
        Aim best = null;
        for (Aim a : m.values()) if (best == null || a.when() > best.when()) best = a;
        return best == null ? null : best.at();
    }

    static void forget(ServerLevel level, UUID owner, int freq, long pos) {
        Map<Long, Long> m = LAUNCHERS.get(key(level, owner, freq));
        if (m != null) m.remove(pos);
    }

    private static final Map<String, Map<Long, Long>> CELLS = Statics.server(new HashMap<>());

    static void beatCell(ServerLevel level, UUID owner, int freq, long pos) {
        CELLS.computeIfAbsent(key(level, owner, freq), k -> new HashMap<>()).put(pos, level.getGameTime());
    }

    static void forgetCell(ServerLevel level, UUID owner, int freq, long pos) {
        Map<Long, Long> m = CELLS.get(key(level, owner, freq));
        if (m != null) m.remove(pos);
    }

    public static List<LaunchCellBlockEntity> cells(ServerLevel level, UUID owner, int freq) {
        Map<Long, Long> m = CELLS.get(key(level, owner, freq));
        List<LaunchCellBlockEntity> out = new ArrayList<>();
        if (m == null) return out;
        long now = level.getGameTime();
        m.entrySet().removeIf(e -> now - e.getValue() > NET_TTL);
        List<Long> keys = new ArrayList<>(m.keySet());
        java.util.Collections.sort(keys);
        for (long p : keys) {
            BlockPos bp = BlockPos.of(p);
            if (!level.isLoaded(bp)) continue;
            if (level.getBlockEntity(bp) instanceof LaunchCellBlockEntity c && !c.isRemoved() && c.frequency() == freq
                    && java.util.Objects.equals(c.getOwner(), owner)) out.add(c);
        }
        return out;
    }

    private static final Map<String, Map<Long, Long>> BATTERIES = Statics.server(new HashMap<>());

    static void beatBattery(ServerLevel level, UUID owner, int freq, long pos) {
        BATTERIES.computeIfAbsent(key(level, owner, freq), k -> new HashMap<>()).put(pos, level.getGameTime());
    }

    static void forgetBattery(ServerLevel level, UUID owner, int freq, long pos) {
        Map<Long, Long> m = BATTERIES.get(key(level, owner, freq));
        if (m != null) m.remove(pos);
    }

    public static List<CoastalBatteryBlockEntity> batteries(ServerLevel level, UUID owner, int freq) {
        Map<Long, Long> m = BATTERIES.get(key(level, owner, freq));
        List<CoastalBatteryBlockEntity> out = new ArrayList<>();
        if (m == null) return out;
        long now = level.getGameTime();
        m.entrySet().removeIf(e -> now - e.getValue() > NET_TTL);
        List<Long> keys = new ArrayList<>(m.keySet());
        java.util.Collections.sort(keys);
        for (long p : keys) {
            BlockPos bp = BlockPos.of(p);
            if (!level.isLoaded(bp)) continue;
            if (level.getBlockEntity(bp) instanceof CoastalBatteryBlockEntity c && !c.isRemoved() && c.frequency() == freq
                    && java.util.Objects.equals(c.getOwner(), owner)) out.add(c);
        }
        return out;
    }

    record Strike(int x, int z, long console, long setAt, long when) {}
    private static final Map<String, Strike> STRIKES = Statics.server(new HashMap<>());

    static void strike(ServerLevel level, UUID owner, int freq, BlockPos console, boolean set, int x, int z, long setAt) {
        String k = key(level, owner, freq);
        Strike old = STRIKES.get(k);
        boolean mine = old != null && old.console() == console.asLong();
        if (!set) { if (mine) STRIKES.remove(k); return; }
        boolean stale = old == null || level.getGameTime() - old.when() > NET_TTL;
        if (mine || stale || setAt >= old.setAt()) STRIKES.put(k, new Strike(x, z, console.asLong(), setAt, level.getGameTime()));
    }

    public static Strike strikeOf(ServerLevel level, UUID owner, int freq, BlockPos viewer) {
        Strike s = STRIKES.get(key(level, owner, freq));
        if (s == null || level.getGameTime() - s.when() > NET_TTL) return null;
        return CommsLink.linked(level, viewer, BlockPos.of(s.console())) ? s : null;
    }

    public static List<TorpedoLauncherBlockEntity> launchers(ServerLevel level, UUID owner, int freq) {
        Map<Long, Long> m = LAUNCHERS.get(key(level, owner, freq));
        List<TorpedoLauncherBlockEntity> out = new ArrayList<>();
        if (m == null) return out;
        long now = level.getGameTime();
        m.entrySet().removeIf(e -> now - e.getValue() > NET_TTL);
        List<Long> keys = new ArrayList<>(m.keySet());
        java.util.Collections.sort(keys);
        for (long p : keys) {
            BlockPos bp = BlockPos.of(p);
            if (!level.isLoaded(bp)) continue;
            if (level.getBlockEntity(bp) instanceof TorpedoLauncherBlockEntity t && !t.isRemoved() && t.frequency() == freq
                    && java.util.Objects.equals(t.getOwner(), owner)) out.add(t);
        }
        return out;
    }
}
