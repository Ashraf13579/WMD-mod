package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TabletChunks {

    private TabletChunks() {}

    static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tablet"), (level, helper) -> {
        int n = 0;
        for (var e : List.copyOf(helper.getBlockTickets().entrySet())) {
            for (long c : new LongArrayList(e.getValue().ticking())) { helper.removeTicket(e.getKey(), c, true); n++; }
            for (long c : new LongArrayList(e.getValue().nonTicking())) { helper.removeTicket(e.getKey(), c, false); n++; }
        }
        if (n > 0) Wmd.LOGGER.info("WMD: dropped {} stale remote tablet chunk ticket(s) in {}", n, level.dimension().location());
    });

    private record Key(ResourceKey<Level> dim, long pos) {}

    private static final class Hold {
        long until, missionUntil;
        long[] chunks;
    }

    private record Pending(UUID player, ResourceKey<Level> dim, BlockPos pos, long deadline, Consumer<ServerPlayer> then) {}

    private static final Map<Key, Hold> HOLDS = Statics.server(new HashMap<>());
    private static final List<Pending> PENDING = Statics.server(new ArrayList<>());

    private static long[] chunksOf(BlockPos pos) {
        LongLinkedOpenHashSet s = new LongLinkedOpenHashSet();
        for (int dx = -4; dx <= 4; dx += 8) for (int dz = -4; dz <= 4; dz += 8) s.add(ChunkPos.asLong((pos.getX() + dx) >> 4, (pos.getZ() + dz) >> 4));
        return s.toLongArray();
    }

    static boolean hold(ServerLevel level, BlockPos pos, int ticks, boolean mission) {
        if (!Config.Server.chunkLoading()) return false;
        Key k = new Key(level.dimension(), pos.asLong());
        long now = level.getGameTime();
        Hold h = HOLDS.get(k);
        if (h == null) {
            h = new Hold();
            h.chunks = chunksOf(pos);
            for (long c : h.chunks) CONTROLLER.forceChunk(level, pos, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
            HOLDS.put(k, h);
        }
        h.until = Math.max(h.until, now + ticks);
        if (mission) h.missionUntil = now + Balance.TABLET_MISSION_HOLD;
        return true;
    }

    static void wake(ServerPlayer p, BlockPos pos, Consumer<ServerPlayer> then) {
        PENDING.removeIf(u -> u.player().equals(p.getUUID()) && u.pos().equals(pos));
        PENDING.add(new Pending(p.getUUID(), p.level().dimension(), pos.immutable(), p.level().getGameTime() + Balance.TABLET_WAKE_TICKS, then));
    }

    private static void release(ServerLevel level, BlockPos pos, Hold h) {
        for (long c : h.chunks) CONTROLLER.forceChunk(level, pos, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!PENDING.isEmpty()) {
            List<Pending> due = new ArrayList<>();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending u = it.next();
                ServerLevel sl = server.getLevel(u.dim());
                ServerPlayer sp = server.getPlayerList().getPlayer(u.player());
                if (sl == null || sp == null) { it.remove(); continue; }
                if (sl.isLoaded(u.pos()) && sl.getBlockEntity(u.pos()) != null) { it.remove(); due.add(u); continue; }
                if (sl.getGameTime() > u.deadline()) { it.remove(); sp.displayClientMessage(Component.translatable("message.wmd.tablet.never_loaded", u.pos().toShortString()), true); }
            }
            for (Pending u : due) {
                ServerPlayer sp = server.getPlayerList().getPlayer(u.player());
                if (sp != null) u.then().accept(sp);
            }
        }
        if (HOLDS.isEmpty() || server.getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<Key, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Hold> e = it.next();
            ServerLevel sl = server.getLevel(e.getKey().dim());
            if (sl == null) { it.remove(); continue; }
            Hold h = e.getValue();
            long now = sl.getGameTime();
            if (now < h.until) continue;
            BlockPos pos = BlockPos.of(e.getKey().pos());
            if (now < h.missionUntil && sl.isLoaded(pos) && sl.getBlockEntity(pos) instanceof ArtilleryGunBlockEntity g && g.mission() != 0) { h.until = now + 40; continue; }
            release(sl, pos, h);
            it.remove();
        }
    }
}
