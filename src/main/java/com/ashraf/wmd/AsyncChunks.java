package com.ashraf.wmd;

import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class AsyncChunks {

    private AsyncChunks() {}

    static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "async_read"), (level, helper) -> {
        for (var e : java.util.List.copyOf(helper.getEntityTickets().entrySet())) {
            for (long c : new LongArrayList(e.getValue().nonTicking())) helper.removeTicket(e.getKey(), c, false);
            for (long c : new LongArrayList(e.getValue().ticking())) helper.removeTicket(e.getKey(), c, true);
        }
    });

    record Key(long owner, long chunk) {}

    static final net.minecraft.server.level.TicketType<Key> TICKET = net.minecraft.server.level.TicketType.create("wmd_load",
            java.util.Comparator.comparingLong(Key::owner).thenComparingLong(Key::chunk));

    static void ticket(ServerLevel level, long owner, int cx, int cz, boolean add) {
        ChunkPos p = new ChunkPos(cx, cz);
        Key key = new Key(owner, p.toLong());
        if (add) level.getChunkSource().addRegionTicket(TICKET, p, 0, key);
        else level.getChunkSource().removeRegionTicket(TICKET, p, 0, key);
    }

    private static long id(UUID owner) { return owner.getMostSignificantBits() ^ owner.getLeastSignificantBits(); }

    static LevelChunk get(ServerLevel level, UUID owner, int cx, int cz, Set<Long> held) {
        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
        long k = ChunkPos.asLong(cx, cz);
        if (c != null) {
            if (held.remove(k)) ticket(level, id(owner), cx, cz, false);
            return c;
        }
        if (held.add(k)) ticket(level, id(owner), cx, cz, true);
        return null;
    }

    static LevelChunk now(ServerLevel level, int cx, int cz) { return level.getChunkSource().getChunkNow(cx, cz); }

    static void release(ServerLevel level, UUID owner, long key, Set<Long> held) {
        if (held.remove(key)) ticket(level, id(owner), ChunkPos.getX(key), ChunkPos.getZ(key), false);
    }

    static void releaseAll(ServerLevel level, UUID owner, Set<Long> held) {
        for (long k : held) ticket(level, id(owner), ChunkPos.getX(k), ChunkPos.getZ(k), false);
        held.clear();
    }

    private static final java.util.Map<String, int[]> REGIONS = Statics.server(new java.util.HashMap<>());

    static boolean saved(ServerLevel level, int cx, int cz) {
        int rx = cx >> 5, rz = cz >> 5;
        int[] t = REGIONS.computeIfAbsent(level.dimension().location() + "|" + rx + "|" + rz, k -> header(level, rx, rz));
        return t.length > 0 && t[(cx & 31) + (cz & 31) * 32] != 0;
    }

    private static int[] header(ServerLevel level, int rx, int rz) {
        java.nio.file.Path dir = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("region");
        java.nio.file.Path f = dir.resolve("r." + rx + "." + rz + ".mca");
        if (!java.nio.file.Files.isRegularFile(f)) return new int[0];
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(f), 4096))) {
            int[] t = new int[1024];
            for (int i = 0; i < 1024; i++) t[i] = in.readInt();
            return t;
        } catch (java.io.IOException e) {
            return new int[0];
        }
    }

    static void forgetRegions() { REGIONS.clear(); }
}
