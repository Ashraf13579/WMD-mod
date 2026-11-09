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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ParkedShips {

    private ParkedShips() {}

    public record Parked(UUID id, double x, double y, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                         boolean sub, boolean submerged, double depth, boolean wet, int blocks, long since) {
        Vec3 centre() { return new Vec3(x, y, z); }
    }

    static final class Data extends SavedData {
        static final String NAME = "wmd_parked_ships";
        final Map<UUID, Parked> ships = new HashMap<>();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            for (Parked p : ships.values()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", p.id());
                t.putDouble("x", p.x()); t.putDouble("y", p.y()); t.putDouble("z", p.z());
                t.putDouble("x0", p.minX()); t.putDouble("y0", p.minY()); t.putDouble("z0", p.minZ());
                t.putDouble("x1", p.maxX()); t.putDouble("y1", p.maxY()); t.putDouble("z1", p.maxZ());
                t.putBoolean("sub", p.sub()); t.putBoolean("down", p.submerged()); t.putDouble("d", p.depth()); t.putBoolean("wet", p.wet());
                t.putInt("n", p.blocks()); t.putLong("t", p.since());
                l.add(t);
            }
            tag.put("ships", l);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag x : tag.getList("ships", Tag.TAG_COMPOUND)) {
                if (!(x instanceof CompoundTag t) || !t.hasUUID("id")) continue;
                Parked p = new Parked(t.getUUID("id"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getDouble("x0"), t.getDouble("y0"), t.getDouble("z0"),
                        t.getDouble("x1"), t.getDouble("y1"), t.getDouble("z1"), t.getBoolean("sub"), t.getBoolean("down"), t.getDouble("d"), t.getBoolean("wet"),
                        t.getInt("n"), t.getLong("t"));
                d.ships.put(p.id(), p);
            }
            return d;
        }
    }

    static void park(ServerLevel level, Parked p) {
        Data d = Data.get(level);
        d.ships.put(p.id(), p);
        d.setDirty();
        Wmd.LOGGER.info("[parked] ship {} parked at {} {} {} ({}, {} blocks, {})", p.id(), (int) p.x(), (int) p.y(), (int) p.z(),
                p.sub() ? "submarine" : "ship", p.blocks(), p.wet() ? (p.submerged() ? "submerged " + (int) p.depth() + " deep" : "on the water") : "not in water");
    }

    static void unpark(ServerLevel level, UUID id) {
        Data d = Data.get(level);
        if (d.ships.remove(id) != null) d.setDirty();
    }

    public static List<Parked> near(ServerLevel level, Vec3 at, double r) {
        List<Parked> out = new ArrayList<>();
        if (!SableBridge.active()) return out;
        for (Parked p : Data.get(level).ships.values()) if (p.centre().distanceTo(at) <= r) out.add(p);
        return out;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !SableBridge.active()) return;
        try { SableParked.tick(sl); }
        catch (Throwable t) { SableBridge.fail(t); }
    }
}
