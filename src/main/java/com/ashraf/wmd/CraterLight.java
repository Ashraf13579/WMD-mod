package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CraterLight {

    private CraterLight() {}

    private static final int LIGHT_WAIT = 100;

    private record Pending(ServerLevel level, LevelChunk chunk, CompletableFuture<?> lit, long since) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    static void sendWhenLit(ServerLevel level, LevelChunk ch) {
        PENDING.removeIf(p -> p.level() == level && p.chunk().getPos().equals(ch.getPos()));
        var light = level.getChunkSource().getLightEngine();
        light.tryScheduleUpdate();
        PENDING.add(new Pending(level, ch, light.waitForPendingTasks(ch.getPos().x, ch.getPos().z), level.getGameTime()));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        long pt0 = ImpactProfiler.start();
        try { send(); } finally { ImpactProfiler.add(ImpactProfiler.LIGHT, pt0); }
    }

    private static void send() {
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!p.lit().isDone() && p.level().getGameTime() - p.since() < LIGHT_WAIT) continue;
            it.remove();
            var pkt = new ClientboundLevelChunkWithLightPacket(p.chunk(), p.level().getChunkSource().getLightEngine(), null, null);
            for (ServerPlayer pl : p.level().getChunkSource().chunkMap.getPlayers(p.chunk().getPos(), false)) pl.connection.send(pkt);
        }
    }

    @SubscribeEvent
    static void onStop(ServerStoppingEvent event) { PENDING.clear(); }
}
