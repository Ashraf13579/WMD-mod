package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirPicture {

    private AirPicture() {}

    public record Aircraft(UUID id, String name, Vec3 pos, Vec3 vel, int blocks, AABB bounds, double agl, UUID owner, String ownerName, boolean piloted, long seen) {
        public double speedBps() { return vel.length() * 20.0; }
        public double sizeFactor() { return AirPicture.sizeFactor(blocks); }
    }

    public record Seen(Aircraft a, double dist, double range) {}

    private static final Map<ResourceKey<Level>, Map<UUID, Aircraft>> PICTURE = Statics.server(new HashMap<>());

    private static final Map<ResourceKey<Level>, Long> BUILT = Statics.server(new HashMap<>());
    private static boolean warned;

    private static final Set<UUID> WRECKS = Statics.server(new HashSet<>());

    public static boolean isWreck(UUID id) { return id != null && WRECKS.contains(id); }

    static void markWreck(UUID id) { if (id != null && WRECKS.size() < 4096) WRECKS.add(id); }

    public static double sizeFactor(int blocks) {
        double f = Math.pow(Math.max(1, blocks) / Balance.AIR_SIZE_REF, 0.25);
        return Math.max(Balance.AIR_SIZE_MIN, Math.min(Balance.AIR_SIZE_MAX, f));
    }

    public static double detectRange(double base, int blocks) { return base * sizeFactor(blocks); }

    public static List<Aircraft> all(ServerLevel level) {
        if (!AirGate.on() || level == null) return List.of();
        Map<UUID, Aircraft> m = PICTURE.get(level.dimension());
        return m == null ? List.of() : List.copyOf(m.values());
    }

    public static Aircraft get(ServerLevel level, UUID id) {
        if (!AirGate.on() || level == null || id == null) return null;
        Map<UUID, Aircraft> m = PICTURE.get(level.dimension());
        return m == null ? null : m.get(id);
    }

    public static boolean isAircraft(ServerLevel level, UUID id) { return get(level, id) != null; }

    public static long builtAt(ServerLevel level) { Long t = BUILT.get(level.dimension()); return t == null ? -1L : t; }

    public static List<Seen> seenFrom(ServerLevel level, Vec3 sensor, double base, UUID ownShip) {
        List<Seen> out = new ArrayList<>();
        for (Aircraft a : all(level)) {
            if (a.id().equals(ownShip)) continue;
            double d = a.pos().distanceTo(sensor), r = detectRange(base, a.blocks());
            if (d <= r) out.add(new Seen(a, d, r));
        }
        out.sort(Comparator.comparingDouble(Seen::dist));
        return out;
    }

    public static List<SableBridge.ShipInfo> smallContacts(ServerLevel level, Vec3 sensor, double base, UUID ownShip) {
        List<SableBridge.ShipInfo> out = new ArrayList<>();
        if (!AirGate.on()) return out;
        for (Seen s : seenFrom(level, sensor, base, ownShip)) {
            SableBridge.ShipInfo si = SableBridge.shipInfo(level, s.a().id());
            if (si != null && !si.isLarge()) out.add(si);
        }
        return out;
    }

    public static List<Seen> allFrom(ServerLevel level, Vec3 sensor, double base) {
        List<Seen> out = new ArrayList<>();
        for (Aircraft a : all(level)) out.add(new Seen(a, a.pos().distanceTo(sensor), detectRange(base, a.blocks())));
        out.sort(Comparator.comparingDouble(Seen::dist));
        return out;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!AirGate.on()) { if (!PICTURE.isEmpty()) { PICTURE.clear(); BUILT.clear(); } return; }
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now % Balance.AIR_SCAN_TICKS != 0) return;
        if (!SableBridge.active()) { PICTURE.remove(sl.dimension()); return; }
        try {
            rebuild(sl, now);
        } catch (Throwable t) {
            if (!warned) { warned = true; Wmd.LOGGER.error("WMD air picture failed (no aircraft contacts this session)", t); }
            PICTURE.remove(sl.dimension());
        }
    }

    private static void rebuild(ServerLevel sl, long now) {
        List<SableBridge.ShipInfo> ships = SableBridge.allShips(sl);
        if (ships.isEmpty()) { PICTURE.remove(sl.dimension()); BUILT.put(sl.dimension(), now); return; }
        Set<UUID> debris = new HashSet<>();
        for (SableBridge.DebrisScan d : SableBridge.debrisScan(sl)) if (d.until() >= 0) debris.add(d.id());
        Map<UUID, Aircraft> out = new LinkedHashMap<>();
        for (SableBridge.ShipInfo s : ships) {
            if (out.size() >= Balance.AIR_PICTURE_MAX) break;
            if (debris.contains(s.id())) continue;
            if (s.blockCount() <= Balance.DEBRIS_TREE_MAX && SableBridge.debrisRemnant(sl, s.id())) continue;
            if (!NavalLayer.visibleAboveWater(sl, s)) continue;
            Vec3 v = ShipDefence.velocity(sl, s);
            double agl = heightOverGround(sl, s);
            boolean airborne = (!Double.isNaN(agl) && agl >= Balance.AIR_MIN_AGL) || v.length() * 20.0 >= Balance.AIR_MIN_SPEED;
            if (!airborne) continue;
            UUID owner = null;
            String ownerName = "";
            boolean piloted = false;
            ShipFaction.Occupants o = ShipFaction.occupants(sl, s.id());
            if (!o.players().isEmpty()) {
                Map.Entry<UUID, String> e = o.players().entrySet().iterator().next();
                owner = e.getKey(); ownerName = e.getValue(); piloted = true;
            } else {
                int best = 0;
                for (Map.Entry<UUID, Integer> e : o.machineOwners().entrySet())
                    if (e.getValue() > best) { best = e.getValue(); owner = e.getKey(); ownerName = o.ownerNames().getOrDefault(e.getKey(), ""); }
            }
            out.put(s.id(), new Aircraft(s.id(), s.name() == null ? "" : s.name(), s.worldCenter(), v, s.blockCount(), s.worldBounds(), agl, owner, ownerName, piloted, now));
        }

        Map<UUID, Aircraft> prev = PICTURE.get(sl.dimension());
        if (prev != null)
            for (Aircraft a : out.values()) {
                if (prev.containsKey(a.id()) || WRECKS.contains(a.id())) continue;
                for (Aircraft p : prev.values()) {
                    Aircraft pn = out.get(p.id());
                    if ((pn == null || pn.blocks() < p.blocks()) && p.bounds() != null && p.bounds().inflate(Balance.WRECK_NEAR).contains(a.pos())) {
                        WRECKS.add(a.id());
                        if (prev.containsKey(p.id()) && WRECKS.contains(p.id())) break;
                        Wmd.LOGGER.debug("Air picture: {} ({} blocks) split off {} = wreckage", a.id(), a.blocks(), p.id());
                        break;
                    }
                }
            }
        if (!WRECKS.isEmpty()) {
            Set<UUID> alive = new HashSet<>();
            for (SableBridge.ShipInfo s : ships) alive.add(s.id());
            WRECKS.retainAll(alive);
        }
        if (out.isEmpty()) PICTURE.remove(sl.dimension());
        else PICTURE.put(sl.dimension(), out);
        BUILT.put(sl.dimension(), now);
    }

    static double heightOverGround(ServerLevel sl, SableBridge.ShipInfo s) {
        int x = (int) Math.floor(s.worldCenter().x), z = (int) Math.floor(s.worldCenter().z);
        LevelChunk ch = sl.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (ch == null) return Double.NaN;
        int top = ch.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        return s.worldBounds().minY - (top + 1.0);
    }
}
