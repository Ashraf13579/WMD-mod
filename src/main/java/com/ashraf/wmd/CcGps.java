package com.ashraf.wmd;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcGps {

    private CcGps() {}

    private static final Set<CcGpsRackBlockEntity> RACKS = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Set<UUID> SHOWN = new HashSet<>();

    static void join(CcGpsRackBlockEntity r) { RACKS.add(r); }
    static void leave(CcGpsRackBlockEntity r) { RACKS.remove(r); }

    public static boolean navControlled(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return false;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && s.type().equals("nav") && now < s.endMs() && OrbitData.controlled(s)) return true;
        return false;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % Math.max(20, Balance.CC_GPS_SYNC_TICKS) != 17) return;
        if (RACKS.isEmpty() && SHOWN.isEmpty()) return;
        Map<UUID, GpsPayload> next = new HashMap<>();
        Map<UUID, Boolean> navCache = new HashMap<>();
        Set<UUID> built = new HashSet<>();
        for (CcGpsRackBlockEntity r : RACKS) {
            UUID owner = r.getOwner();
            if (r.isRemoved() || owner == null || r.getLevel() == null || !r.isOnline()) continue;
            if (!navCache.computeIfAbsent(owner, o -> navControlled(server, o))) continue;
            if (!built.add(owner)) continue;
            CcBuildingBlockEntity t = CommandCenter.activeRoomFor(r);
            BlockPos room = t != null ? t.hallCentre() : r.getBlockPos();
            GpsPayload p = minimap(server, r, owner, room, t);
            next.putIfAbsent(owner, p);
            for (UUID u : r.security().trusted().keySet()) next.putIfAbsent(u, p);
        }
        for (Map.Entry<UUID, GpsPayload> e : next.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) PacketDistributor.sendToPlayer(p, e.getValue());
        }
        for (UUID u : SHOWN) {
            if (next.containsKey(u)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) PacketDistributor.sendToPlayer(p, GpsPayload.OFF);
        }
        SHOWN.clear();
        SHOWN.addAll(next.keySet());
    }

    private static GpsPayload minimap(MinecraftServer server, CcGpsRackBlockEntity r, UUID owner, BlockPos room, CcBuildingBlockEntity t) {
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        ids.add(owner);
        for (UUID u : r.security().trusted().keySet()) if (!ids.contains(u)) ids.add(u);
        int[] mates = new int[GpsPayload.MAX_MATES * 2];
        java.util.List<String> names = new java.util.ArrayList<>();
        for (UUID u : ids) {
            if (names.size() >= GpsPayload.MAX_MATES) break;
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp == null || sp.isSpectator() || sp.level() != r.getLevel()) continue;
            mates[names.size() * 2] = (int) Math.floor(sp.getX());
            mates[names.size() * 2 + 1] = (int) Math.floor(sp.getZ());
            names.add(sp.getGameProfile().getName());
        }
        java.util.List<CommandCenter.Warning> ws = CommandCenter.warnings(owner);
        int nw = 0;
        int[] warns = new int[GpsPayload.MAX_WARNS * 4];
        long[] times = new long[GpsPayload.MAX_WARNS * 2];
        for (int i = ws.size() - 1; i >= 0 && nw < GpsPayload.MAX_WARNS; i--) {
            CommandCenter.Warning w = ws.get(i);
            if (w.sat() || AirshipWatch.isWarning(w.id())) continue;
            warns[nw * 4] = w.ix(); warns[nw * 4 + 1] = w.iz(); warns[nw * 4 + 2] = w.ox(); warns[nw * 4 + 3] = w.oz();
            times[nw * 2] = w.launchGt(); times[nw * 2 + 1] = w.impactGt();
            nw++;
        }
        CcTarget des = t != null && t.designated() != null ? t.designated() : CcTarget.of(owner);
        return new GpsPayload(true, room.getX(), room.getY(), room.getZ(), r.getLevel().dimension().location().toString(), r.getOwnerName(),
                java.util.Arrays.copyOf(mates, names.size() * 2), java.util.List.copyOf(names), java.util.Arrays.copyOf(warns, nw * 4),
                java.util.Arrays.copyOf(times, nw * 2), des != null, des != null ? des.x() : 0, des != null ? des.z() : 0);
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        RACKS.clear();
        SHOWN.clear();
    }
}
