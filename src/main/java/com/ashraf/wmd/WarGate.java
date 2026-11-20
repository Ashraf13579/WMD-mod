package com.ashraf.wmd;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarGate {

    private WarGate() {}

    static volatile boolean anyActive;

    private static final Map<UUID, Long> LAST_ONLINE = Statics.server(new HashMap<>());
    private static final Map<Long, Boolean> BLAST_CACHE = Statics.server(new HashMap<>());
    private static long cacheTick = Long.MIN_VALUE;
    private static UUID cacheShooter;
    private static final Map<UUID, Byte> HUD_SENT = Statics.server(new HashMap<>());
    private static final Map<Explosion, Snap> SNAPS = new WeakHashMap<>();

    private record Snap(List<BlockPos> blocks, List<Entity> entities) {}

    static boolean isOpen(MinecraftServer server, UUID nation) {
        if (nation == null) return false;
        String mode = Config.Server.warOpenMode();
        boolean window = inWindow(LocalTime.now(), Config.Server.warWindow());
        if (mode.equals("WINDOW")) return window;
        boolean online = onlineOrGrace(server, nation);
        return mode.equals("BOTH") ? online && window : online;
    }

    private static boolean onlineOrGrace(MinecraftServer server, UUID nation) {
        long t = WarManager.now();
        if (OpacBridge.online(server, nation) > 0) { LAST_ONLINE.put(nation, t); return true; }
        Long last = LAST_ONLINE.get(nation);
        return last != null && t - last < Config.Server.warGraceMinutes() * 60_000L;
    }

    static boolean inWindow(LocalTime now, String window) {
        try {
            String[] p = window.split("-");
            LocalTime a = LocalTime.parse(pad(p[0])), b = LocalTime.parse(pad(p[1]));
            if (a.equals(b)) return true;
            return a.isBefore(b) ? !now.isBefore(a) && now.isBefore(b) : !now.isBefore(a) || now.isBefore(b);
        } catch (RuntimeException e) {
            if (!badWindowLogged) { badWindowLogged = true; Wmd.LOGGER.warn("[wmd] war.window \"{}\" is not HH:MM-HH:MM: claims stay CLOSED outside online play until it is fixed", window); }
            return false;
        }
    }
    private static boolean badWindowLogged;

    private static String pad(String hm) { String s = hm.trim(); if (s.equals("24:00")) return "00:00"; return s.indexOf(':') == 1 ? "0" + s : s; }

    static void touch(MinecraftServer server, UUID nation) { if (OpacBridge.online(server, nation) > 0) LAST_ONLINE.put(nation, WarManager.now()); }

    static boolean openBetween(MinecraftServer server, UUID byNation, UUID claimNation) {
        return anyActive && byNation != null && claimNation != null && !byNation.equals(claimNation)
                && WarManager.atWar(server, byNation, claimNation) && isOpen(server, claimNation);
    }

    static boolean blastAllowed(ServerLevel level, BlockPos pos) {
        if (!anyActive) return false;
        UUID shooter = ShipStrike.shooter();
        if (shooter == null) return false;
        long tick = level.getGameTime();
        if (tick != cacheTick || !shooter.equals(cacheShooter)) { BLAST_CACHE.clear(); cacheTick = tick; cacheShooter = shooter; }
        long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4) ^ ((long) level.dimension().location().hashCode() << 1);
        Boolean c = BLAST_CACHE.get(key);
        if (c != null) return c;
        MinecraftServer server = level.getServer();
        boolean ok = openBetween(server, OpacBridge.nationOf(server, shooter), OpacBridge.nationAt(level, pos));
        BLAST_CACHE.put(key, ok);
        return ok;
    }

    private static final Map<Long, Boolean> NATION_CACHE = Statics.server(new HashMap<>());
    private static long nCacheTick = Long.MIN_VALUE;
    private static UUID nCacheNation;

    static boolean blastAllowedFor(ServerLevel level, BlockPos pos, UUID byNation) {
        if (!anyActive || byNation == null) return false;
        long tick = level.getGameTime();
        if (tick != nCacheTick || !byNation.equals(nCacheNation)) { NATION_CACHE.clear(); nCacheTick = tick; nCacheNation = byNation; }
        long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4) ^ ((long) level.dimension().location().hashCode() << 1);
        return NATION_CACHE.computeIfAbsent(key, k -> openBetween(level.getServer(), byNation, OpacBridge.nationAt(level, pos)));
    }

    static boolean playerMay(MinecraftServer server, UUID accessor, UUID claimOwner) {
        if (!anyActive || accessor == null || claimOwner == null) return false;
        return openBetween(server, OpacBridge.nationOf(server, accessor), OpacBridge.nationOf(server, claimOwner));
    }

    static boolean frozen(MinecraftServer server, UUID player) {
        UUID n = OpacBridge.nationOf(server, player);
        if (n == null) return false;
        if (!WarManager.warsOf(server, n).isEmpty()) return true;
        long t = WarManager.now();
        for (WarData.War w : WarData.get(server).wars)
            if (w.state == WarData.ENDED && w.winner != null && w.takeLeft > 0 && w.takeUntil > t && w.sideOf(n) != 0 && w.sideOf(n) != w.sideOf(w.winner)) return true;
        return false;
    }

    static boolean healCovers(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || !anyActive || !Config.Server.rollbackLog() || !Config.Server.warHeal()) return false;
        UUID n = OpacBridge.nationAt(sl, pos);
        if (n == null) return false;
        for (WarData.War w : WarData.get(sl.getServer()).wars) if (w.state == WarData.ACTIVE && w.sideOf(n) != 0) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void before(ExplosionEvent.Detonate event) {
        if (!anyActive || ClaimGuard.probing() || !(event.getLevel() instanceof ServerLevel)) return;
        SNAPS.put(event.getExplosion(), new Snap(new ArrayList<>(event.getAffectedBlocks()), new ArrayList<>(event.getAffectedEntities())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void after(ExplosionEvent.Detonate event) {
        Snap s = SNAPS.remove(event.getExplosion());
        if (s == null || !(event.getLevel() instanceof ServerLevel level)) return;
        try {
            Entity igniter = event.getExplosion().getIndirectSourceEntity();
            UUID by = igniter instanceof Player p ? p.getUUID() : ShipStrike.shooter();
            if (by == null) return;
            MinecraftServer server = level.getServer();
            UUID byNation = OpacBridge.nationOf(server, by);
            if (byNation == null) return;
            Map<Long, Boolean> open = new HashMap<>();
            Set<BlockPos> now = new HashSet<>(event.getAffectedBlocks());
            for (BlockPos b : s.blocks()) {
                if (now.contains(b)) continue;
                long ck = net.minecraft.world.level.ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4);
                if (open.computeIfAbsent(ck, k -> openBetween(server, byNation, OpacBridge.nationAt(level, b)))) event.getAffectedBlocks().add(b);
            }
            Set<Entity> nowE = new HashSet<>(event.getAffectedEntities());
            for (Entity e : s.entities()) {
                if (nowE.contains(e)) continue;
                BlockPos b = e.blockPosition();
                long ck = net.minecraft.world.level.ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4);
                if (open.computeIfAbsent(ck, k -> openBetween(server, byNation, OpacBridge.nationAt(level, b)))) event.getAffectedEntities().add(e);
            }
        } catch (Throwable t) { Wmd.LOGGER.warn("[war] explosion re-add failed: {}", t.toString()); }
    }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) { HUD_SENT.remove(event.getEntity().getUUID()); }

    static void hud(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            byte st = 0;
            if (anyActive) {
                UUID mine = OpacBridge.nationOf(server, p.getUUID()), here = OpacBridge.nationAt(p.serverLevel(), p.blockPosition());
                if (mine != null && here != null && !mine.equals(here) && WarManager.atWar(server, mine, here)) st = isOpen(server, here) ? (byte) 1 : (byte) 2;
            }
            Byte last = HUD_SENT.get(p.getUUID());
            if (last != null && last == st) continue;
            HUD_SENT.put(p.getUUID(), st);
            PacketDistributor.sendToPlayer(p, new WarZonePayload(st));
        }
    }
}
