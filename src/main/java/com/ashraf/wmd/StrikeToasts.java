package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StrikeToasts {

    private StrikeToasts() {}

    static final int QUIET_TICKS = 40, STALE_TICKS = 20 * 60 * 20;

    private static final class Acc {
        final Set<Long> flights = new HashSet<>(), shells = new HashSet<>();

        final Map<Long, ResourceKey<Level>> guns = new HashMap<>();
        int missiles, rounds, hits, intercepted, n;
        double sx, sz;
        long startMs = System.currentTimeMillis(), lastTick;
    }

    private static final Map<UUID, Acc> ACC = new HashMap<>();

    private static boolean real(UUID o) { return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L); }

    private static Acc acc(MinecraftServer server, UUID owner) {
        Acc a = ACC.computeIfAbsent(owner, k -> new Acc());
        a.lastTick = server.getTickCount();
        return a;
    }

    private static boolean groundAttack(Flight f) {
        return f != null && f.build() != null && !f.build().isInterceptor() && !Satellites.toOrbit(f.build()) && !Asat.isAsat(f.build());
    }

    static void flightOut(ServerLevel level, Flight f) {
        if (!groundAttack(f)) return;
        UUID o = CcBattleLog.ownerOf(level.getServer(), f);
        if (!real(o)) return;
        Acc a = acc(level.getServer(), o);
        a.flights.add(f.id());
        a.missiles++;
    }

    static void flightDone(ServerLevel level, Flight f, boolean hit) {
        flightDoneAt(level, f, hit, f == null ? 0.0 : f.tx() + 0.5, f == null ? 0.0 : f.tz() + 0.5);
    }

    static void flightDoneAt(ServerLevel level, Flight f, boolean hit, double x, double z) {
        if (!groundAttack(f)) return;
        UUID o = CcBattleLog.ownerOf(level.getServer(), f);
        if (!real(o)) return;
        Acc a = acc(level.getServer(), o);
        if (!a.flights.remove(f.id())) a.missiles++;
        if (hit) { a.hits++; a.sx += x; a.sz += z; a.n++; }
        else a.intercepted++;
    }

    static void shellOut(ServerLevel level, ArtShell s) {
        if (s == null || !real(s.owner())) return;
        Acc a = acc(level.getServer(), s.owner());
        a.shells.add(s.id());
        a.rounds++;
        if (s.gunPos() != 0L) {
            if (s.last()) a.guns.remove(s.gunPos());
            else a.guns.put(s.gunPos(), level.dimension());
        }
    }

    static void shellDone(ServerLevel level, ArtShell s, boolean hit) {
        shellDoneAt(level, s, hit, s == null ? 0.0 : s.tx(), s == null ? 0.0 : s.tz());
    }

    static void shellDoneAt(ServerLevel level, ArtShell s, boolean hit, double x, double z) {
        if (s == null || !real(s.owner())) return;
        Acc a = acc(level.getServer(), s.owner());
        if (!a.shells.remove(s.id())) a.rounds++;
        if (hit) { a.hits++; a.sx += x; a.sz += z; a.n++; }
        else a.intercepted++;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACC.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 10 != 0) return;
        for (Iterator<Map.Entry<UUID, Acc>> it = ACC.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Acc> e = it.next();
            Acc a = e.getValue();
            if (now - a.lastTick >= STALE_TICKS) { it.remove(); continue; }
            a.guns.entrySet().removeIf(g -> !firing(server, g.getValue(), g.getKey()));
            if (!a.flights.isEmpty() || !a.shells.isEmpty() || !a.guns.isEmpty() || now - a.lastTick < QUIET_TICKS) continue;
            it.remove();
            if (a.hits + a.intercepted <= 0) continue;
            send(server, e.getKey(), a);
        }
    }

    private static boolean firing(MinecraftServer server, ResourceKey<Level> dim, long pos) {
        ServerLevel l = server.getLevel(dim);
        BlockPos bp = BlockPos.of(pos);
        return l != null && l.hasChunkAt(bp) && l.getBlockEntity(bp) instanceof ArtilleryGunBlockEntity g && !g.isRemoved() && g.mission() != 0;
    }

    private static void send(MinecraftServer server, UUID owner, Acc a) {
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p == null) return;
        int kind = a.missiles > 0 && a.rounds > 0 ? StrikeToastPayload.MIXED : a.missiles > 0 ? StrikeToastPayload.MISSILES : StrikeToastPayload.ARTILLERY;
        int bda = a.missiles > 0 ? CcBda.openedSince(server, owner, a.startMs - 1_000L) : -1;
        int x = a.n > 0 ? (int) Math.round(a.sx / a.n) : 0, z = a.n > 0 ? (int) Math.round(a.sz / a.n) : 0;
        PacketDistributor.sendToPlayer(p, new StrikeToastPayload(kind, a.missiles, a.rounds, a.hits, a.intercepted, a.n > 0, x, z, bda));
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { ACC.clear(); }
}
