package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipFaction {

    private ShipFaction() {}

    public enum Relation {
        HOSTILE, NEUTRAL, FRIENDLY;

        public int code() { return ordinal(); }

        public static Relation of(int code) { return code == 0 ? HOSTILE : code == 2 ? FRIENDLY : NEUTRAL; }
    }

    public static final int CACHE_TICKS = 20;

    static final int MAX_BLOCK_ENTITIES = 4096;

    public record Occupants(long tick, Map<UUID, Integer> machineOwners, Map<UUID, String> ownerNames, Map<UUID, String> players, int machines) {
        public boolean empty() { return machineOwners.isEmpty() && players.isEmpty(); }
    }

    private static final Map<UUID, Occupants> CACHE = new HashMap<>();

    private static final Map<String, Object[]> PLAYERS = new HashMap<>();

    public static Occupants occupants(ServerLevel level, UUID shipId) {
        long now = level.getGameTime();
        Occupants c = CACHE.get(shipId);
        if (c != null && now - c.tick() >= 0 && now - c.tick() < CACHE_TICKS) return c;
        Map<UUID, Integer> owners = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        int machines = 0, seen = 0;
        for (BlockPos p : SableBridge.blockEntitiesOn(level, shipId)) {
            if (++seen > MAX_BLOCK_ENTITIES) break;
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof Owned o) || be.isRemoved()) continue;
            machines++;
            UUID u = o.getOwner();
            if (u == null) continue;
            owners.merge(u, 1, Integer::sum);
            if (!names.containsKey(u)) names.put(u, o.getOwnerName() == null ? "" : o.getOwnerName());
        }
        Map<UUID, String> players = new LinkedHashMap<>();
        for (ServerPlayer sp : playersOn(level, now).getOrDefault(shipId, List.of())) players.put(sp.getUUID(), sp.getGameProfile().getName());
        Occupants o = new Occupants(now, Map.copyOf(owners), names, Map.copyOf(players), machines);
        if (CACHE.size() > 512) CACHE.clear();
        CACHE.put(shipId, o);
        return o;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, List<ServerPlayer>> playersOn(ServerLevel level, long now) {
        String key = level.dimension().location().toString();
        Object[] hit = PLAYERS.get(key);
        if (hit != null && (Long) hit[0] == now) return (Map<UUID, List<ServerPlayer>>) hit[1];
        Map<UUID, List<ServerPlayer>> m = new HashMap<>();
        for (ServerPlayer sp : level.players()) {
            if (sp.isSpectator()) continue;
            UUID ship = SableBridge.shipUnder(sp);
            if (ship != null) m.computeIfAbsent(ship, k -> new ArrayList<>()).add(sp);
        }
        PLAYERS.put(key, new Object[] { now, m });
        return m;
    }

    static boolean trusted(UUID defender, Set<UUID> team, Owned machine, UUID who) {
        if (who == null) return true;
        if (who.equals(defender)) return true;
        if (team.contains(who)) return true;
        return machine != null && machine.security() != null && machine.security().trusted().containsKey(who);
    }

    public static Relation relation(ServerLevel level, UUID shipId, UUID defender, Owned machine) {
        if (level == null || shipId == null) return Relation.NEUTRAL;
        Occupants o = occupants(level, shipId);
        if (o.empty()) return Relation.NEUTRAL;
        Set<UUID> team = CommandCenter.trustedBy(defender);
        for (UUID u : o.machineOwners().keySet()) if (!trusted(defender, team, machine, u)) return Relation.HOSTILE;
        for (UUID u : o.players().keySet()) if (!trusted(defender, team, machine, u)) return Relation.HOSTILE;
        return Relation.FRIENDLY;
    }

    public static boolean isHostileTo(UUID defenderOwner, ServerLevel level, UUID shipId) {
        return relation(level, shipId, defenderOwner, null) == Relation.HOSTILE;
    }

    public static boolean isHostileTo(Owned defender, ServerLevel level, UUID shipId) {
        return defender != null && relation(level, shipId, defender.getOwner(), defender) == Relation.HOSTILE;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        CACHE.clear();
        PLAYERS.clear();
    }
}
