package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongUnaryOperator;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipRelink {

    private ShipRelink() {}

    public interface Holder {

        int relink(ServerLevel level, LongUnaryOperator map);
    }

    public static final String STAMP = "wmd_relink_pos";

    static boolean enabled() { return Config.Server.relinkOnAssembly() && SableBridge.active(); }

    public static boolean shipMove(Level level, boolean movedByPiston) {
        return movedByPiston && level != null && !level.isClientSide && level.captureBlockSnapshots && SableBridge.loaded();
    }

    static boolean ours(BlockEntity be) {
        return be != null && !be.isRemoved() && be.getClass().getName().startsWith("com.ashraf.wmd.");
    }

    static void stamp(BlockEntity be) {
        long p = be.getBlockPos().asLong();
        CompoundTag t = be.getPersistentData();
        if (t.contains(STAMP) && t.getLong(STAMP) == p) return;
        t.putLong(STAMP, p);
        be.setChanged();
    }

    static int collect(ServerLevel level, Iterable<BlockEntity> found, String cause) {
        Map<Long, Long> moved = null;
        for (BlockEntity be : found) {
            if (!ours(be)) continue;
            long now = be.getBlockPos().asLong();
            CompoundTag t = be.getPersistentData();
            if (t.contains(STAMP)) {
                long old = t.getLong(STAMP);
                if (old != now) {
                    BlockPos o = BlockPos.of(old);
                    BlockEntity there = level.isLoaded(o) ? level.getBlockEntity(o) : null;
                    if (there == null || there.getType() != be.getType()) {
                        if (moved == null) moved = new HashMap<>();
                        moved.put(old, now);
                    }
                }
            }
            stamp(be);
        }
        if (moved == null) return 0;
        apply(level, moved, cause);
        return moved.size();
    }

    public static void apply(ServerLevel level, Map<Long, Long> moved, String cause) {
        if (level == null || moved == null || moved.isEmpty() || !Config.Server.relinkOnAssembly()) return;
        LongUnaryOperator map = p -> { Long n = moved.get(p); return n == null ? p : n; };
        MinecraftServer server = level.getServer();
        int strikes = 0, held = 0;
        try { strikes = CcStrikeData.get(server).relink(level, map); }
        catch (Throwable t) { Wmd.LOGGER.warn("Ship relink: strike plans not remapped", t); }
        for (long n : moved.values()) {
            BlockEntity be = level.getBlockEntity(BlockPos.of(n));
            if (be instanceof Holder h) {
                try { held += h.relink(level, map); }
                catch (Throwable t) { Wmd.LOGGER.warn("Ship relink: links of {} not remapped", be, t); }
            }
        }
        Forwards.get(server).record(level, moved);
        Wmd.LOGGER.info("Ship relink ({}) in {}: {} WMD block(s) moved (forwarded); strike shots remapped {}, machine links remapped {}",
                cause, level.dimension().location(), moved.size(), strikes, held);
    }

    public static BlockPos forward(Level level, BlockPos pos) {
        if (pos == null || !(level instanceof ServerLevel sl) || !SableBridge.loaded()) return pos;
        Forwards f = Forwards.peek(sl.getServer());
        if (f == null) return pos;
        String dim = sl.dimension().location().toString();
        long p = pos.asLong();
        for (int i = 0; i < 8; i++) {
            Long n = f.next(dim, p);
            if (n == null || n == p) break;
            p = n;
        }
        return p == pos.asLong() ? pos : BlockPos.of(p);
    }

    static final class Forwards extends SavedData {
        static final String NAME = "wmd_ship_relink";
        static final int FORWARD_MAX = 4096;
        private final LinkedHashMap<String, Long> next = new LinkedHashMap<>();

        static Forwards get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Forwards::new, Forwards::load, null), NAME);
        }

        static Forwards peek(MinecraftServer server) {
            return server == null ? null : server.overworld().getDataStorage().get(new SavedData.Factory<>(Forwards::new, Forwards::load, null), NAME);
        }

        private static String key(String dim, long p) { return dim + "|" + p; }

        Long next(String dim, long p) { return next.get(key(dim, p)); }

        void record(ServerLevel level, Map<Long, Long> moved) {
            String dim = level.dimension().location().toString();
            for (Map.Entry<Long, Long> e : moved.entrySet()) {
                String k = key(dim, e.getKey());
                next.remove(k);
                next.put(k, e.getValue());
                next.remove(key(dim, e.getValue()));
            }
            Iterator<Map.Entry<String, Long>> it = next.entrySet().iterator();
            while (next.size() > FORWARD_MAX && it.hasNext()) { it.next(); it.remove(); }
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            for (Map.Entry<String, Long> e : next.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("k", e.getKey());
                c.putLong("n", e.getValue());
                l.add(c);
            }
            tag.put("fwd", l);
            return tag;
        }

        static Forwards load(CompoundTag tag, HolderLookup.Provider registries) {
            Forwards f = new Forwards();
            for (Tag t : tag.getList("fwd", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                f.next.put(c.getString("k"), c.getLong("n"));
            }
            return f;
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !enabled()) return;
        try { SableAssembly.tick(sl); }
        catch (Throwable t) { SableBridge.fail(t); }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !enabled()) return;
        BlockEntity be = sl.getBlockEntity(event.getPos());
        if (ours(be)) stamp(be);
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        if (SableBridge.loaded()) {
            try { SableAssembly.clear(); } catch (Throwable ignored) {}
        }
    }
}
