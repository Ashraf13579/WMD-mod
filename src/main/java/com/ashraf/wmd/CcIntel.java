package com.ashraf.wmd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcIntel {

    private CcIntel() {}

    private static final int MAX = 8192;
    private static final Map<Long, UUID> OWNERS = new LinkedHashMap<>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, UUID> e) { return size() > MAX; }
    };

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    public static synchronized void note(int x, int z, UUID owner) {
        if (owner != null) OWNERS.put(key(x, z), owner);
    }

    public static synchronized UUID ownerAt(MinecraftServer server, int x, int z) {
        UUID u = OWNERS.get(key(x, z));
        if (u != null || server == null) return u;
        ServerLevel ow = server.overworld();
        LevelChunk ch = ow.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (ch == null) return null;
        for (BlockEntity be : ch.getBlockEntities().values()) {
            BlockPos p = be.getBlockPos();
            if (p.getX() == x && p.getZ() == z && be instanceof Owned o && o.getOwner() != null) {
                OWNERS.put(key(x, z), o.getOwner());
                return o.getOwner();
            }
        }
        return null;
    }

    public static String name(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        if (server.getProfileCache() == null) return null;
        Optional<GameProfile> g = server.getProfileCache().get(id);
        return g.map(GameProfile::getName).orElse(null);
    }

    public static String ownerName(MinecraftServer server, int x, int z) {
        String n = name(server, ownerAt(server, x, z));
        return n == null ? "" : n;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        synchronized (CcIntel.class) { OWNERS.clear(); }
    }
}
