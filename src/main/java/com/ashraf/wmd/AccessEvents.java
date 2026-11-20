package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AccessEvents {

    private AccessEvents() {}

    private record At(ResourceKey<Level> dim, long pos) {}

    private static final ArrayDeque<At> PLACED = Statics.server(new ArrayDeque<>());
    private static final ArrayDeque<At> CHUNKS = Statics.server(new ArrayDeque<>());
    static final int INDEX_PER_TICK = 8, QUEUE_MAX = 4_096;

    @SubscribeEvent
    static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer) || !(event.getLevel() instanceof ServerLevel sl)) return;
        if (PLACED.size() < QUEUE_MAX) PLACED.add(new At(sl.dimension(), event.getPos().asLong()));
    }

    @SubscribeEvent
    static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !(event.getChunk() instanceof LevelChunk ch)) return;
        if (CHUNKS.size() < QUEUE_MAX) CHUNKS.add(new At(sl.dimension(), ch.getPos().toLong()));
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (PLACED.isEmpty() && CHUNKS.isEmpty()) return;
        AccessStore store = AccessStore.get(event.getServer());
        while (!PLACED.isEmpty()) {
            At a = PLACED.poll();
            ServerLevel sl = event.getServer().getLevel(a.dim());
            BlockPos p = BlockPos.of(a.pos());
            if (sl != null && sl.isLoaded(p) && sl.getBlockEntity(p) instanceof Owned o) store.index(o);
        }
        for (int n = 0; n < INDEX_PER_TICK && !CHUNKS.isEmpty(); n++) {
            At a = CHUNKS.poll();
            ServerLevel sl = event.getServer().getLevel(a.dim());
            if (sl == null) continue;
            ChunkPos cp = new ChunkPos(a.pos());
            LevelChunk ch = sl.getChunkSource().getChunkNow(cp.x, cp.z);
            if (ch == null) continue;
            List<BlockEntity> bes = new ArrayList<>(ch.getBlockEntities().values());
            for (BlockEntity be : bes) if (be instanceof Owned o && o.getOwner() != null) store.index(o);
            store.prune(sl, cp.x, cp.z);
        }
    }

    @SubscribeEvent
    static void stopping(ServerStoppingEvent event) { PLACED.clear(); CHUNKS.clear(); AccessStore.clearCache(); }
}
