package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

final class ClaimGuard {

    private ClaimGuard() {}

    private static final long CACHE_TICKS = 600L;
    private static final int CACHE_MAX = 50_000;

    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> CACHE = new HashMap<>();
    private static boolean probing;

    private static long failedAt = -1L;
    private static int failures;
    private static final long RETRY_TICKS = 6000L;

    private static ResourceKey<Level> lastDim;
    private static long lastChunk = Long.MIN_VALUE, lastUntil;
    private static boolean lastProtected;

    static boolean probing() { return probing; }

    static void clearStatics() { CACHE.clear(); lastDim = null; lastChunk = Long.MIN_VALUE; failedAt = -1L; failures = 0; probing = false; }

    static boolean protectedAt(ServerLevel level, BlockPos p) {
        if (probing) return false;
        long now = level.getGameTime();
        if (failedAt >= 0L) {
            if (now >= failedAt && now < failedAt + RETRY_TICKS) return false;
            failedAt = -1L;
            CACHE.clear();
            lastChunk = Long.MIN_VALUE;
        }
        long chunk = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
        ResourceKey<Level> dim = level.dimension();
        if (chunk == lastChunk && dim == lastDim && now < lastUntil) return lastProtected;
        Long2LongOpenHashMap map = CACHE.computeIfAbsent(dim, k -> { Long2LongOpenHashMap m = new Long2LongOpenHashMap(); m.defaultReturnValue(-1L); return m; });
        long v = map.get(chunk);
        boolean prot;
        if (v >= 0 && (v >>> 1) > now) prot = (v & 1L) != 0L;
        else {
            prot = ask(level, p);
            if (map.size() > CACHE_MAX) map.clear();
            map.put(chunk, ((now + CACHE_TICKS) << 1) | (prot ? 1L : 0L));
        }
        lastDim = dim;
        lastChunk = chunk;
        lastUntil = now + CACHE_TICKS;
        lastProtected = prot;
        return prot;
    }

    private static boolean ask(ServerLevel level, BlockPos p) {
        BlockPos probe = p.immutable();
        try {
            probing = true;
            Explosion e = new Explosion(level, null, probe.getX() + 0.5, probe.getY() + 0.5, probe.getZ() + 0.5, 1.0F, false, Explosion.BlockInteraction.DESTROY);
            e.getToBlow().add(probe);
            NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, e, new ArrayList<>()));
            return !e.getToBlow().contains(probe);
        } catch (Throwable t) {
            failedAt = level.getGameTime();
            failures++;
            if (failures == 1) Wmd.LOGGER.error("WMD claims check failed (blasts ignore claims for the next " + RETRY_TICKS / 1200 + " minutes, then it tries again)", t);
            else Wmd.LOGGER.warn("WMD claims check failed again (#{}): {}; blasts ignore claims for the next {} minutes", failures, t.toString(), RETRY_TICKS / 1200);
            return false;
        } finally {
            probing = false;
        }
    }

    private static long placeWarnAt = -1L;

    static boolean mayPlace(Level level, Player player, java.util.Collection<BlockPos> cells, BlockPos origin) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !Config.Server.respectClaimsPlacing()) return true;
        BlockPos bad = deniedPlacement(sl, sp, cells, origin);
        if (bad == null) return true;
        sp.displayClientMessage(Component.translatable("message.wmd.place.claimed", bad.getX(), bad.getY(), bad.getZ()), true);
        return false;
    }

    static boolean mayPlaceQuiet(ServerLevel level, ServerPlayer player, BlockPos c) {
        return !Config.Server.respectClaimsPlacing() || deniedPlacement(level, player, java.util.List.of(c), null) == null;
    }

    static boolean mayPlaceBox(Level level, Player player, BlockPos a, BlockPos b, BlockPos origin) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        int y = Math.min(a.getY(), b.getY());
        java.util.List<BlockPos> corners = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                corners.add(new BlockPos(Math.max(x0, Math.min(x1, cx << 4)), y, Math.max(z0, Math.min(z1, cz << 4))));

        return mayPlace(level, player, corners, origin);
    }

    private static BlockPos deniedPlacement(ServerLevel level, ServerPlayer player, java.util.Collection<BlockPos> cells, BlockPos origin) {
        long skip = origin == null ? Long.MIN_VALUE : ChunkPos.asLong(origin.getX() >> 4, origin.getZ() >> 4);
        Map<Long, BlockPos> reps = new java.util.LinkedHashMap<>();
        for (BlockPos c : cells) {
            long k = ChunkPos.asLong(c.getX() >> 4, c.getZ() >> 4);
            if (k != skip) reps.putIfAbsent(k, c);
        }
        for (BlockPos c : reps.values()) {
            try {
                BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, c);
                BlockEvent.EntityPlaceEvent ev = new BlockEvent.EntityPlaceEvent(snap, level.getBlockState(c.below()), player);
                NeoForge.EVENT_BUS.post(ev);
                if (ev.isCanceled()) return c.immutable();
            } catch (Throwable t) {
                long now = level.getGameTime();
                if (placeWarnAt < 0L || now < placeWarnAt || now - placeWarnAt > 1200L) { placeWarnAt = now; Wmd.LOGGER.warn("WMD placement claim check failed (placement allowed): {}", t.toString()); }
                return null;
            }
        }
        return null;
    }
}
