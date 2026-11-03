package com.ashraf.wmd;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

public final class ImpactChunks {

    private ImpactChunks() {}

    private static final BlockPos OWNER = BlockPos.ZERO;

    static final TicketController CONTROLLER = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "impact"), ImpactChunks::validate);

    static void force(ServerLevel level, int cx, int cz, boolean add) {
        AsyncChunks.ticket(level, 0L, cx, cz, add);
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        FlightManager fm = null;
        int dropped = 0;
        for (var e : java.util.List.copyOf(helper.getBlockTickets().entrySet())) {
            BlockPos owner = e.getKey();
            TicketSet set = e.getValue();
            LongList drop = new LongArrayList(), dropTicking = new LongArrayList();
            for (long c : set.nonTicking()) if (fm == null || !owner.equals(OWNER) || !fm.holds(c)) drop.add(c);
            for (long c : set.ticking()) dropTicking.add(c);
            for (long c : drop) helper.removeTicket(owner, c, false);
            for (long c : dropTicking) helper.removeTicket(owner, c, true);
            dropped += drop.size() + dropTicking.size();
        }
        for (var e : java.util.List.copyOf(helper.getEntityTickets().entrySet())) {
            java.util.UUID owner = e.getKey();
            TicketSet set = e.getValue();
            LongList a = new LongArrayList(set.nonTicking()), b = new LongArrayList(set.ticking());
            for (long c : a) helper.removeTicket(owner, c, false);
            for (long c : b) helper.removeTicket(owner, c, true);
            dropped += a.size() + b.size();
        }
        if (dropped > 0) Wmd.LOGGER.info("WMD: dropped {} stale impact chunk ticket(s) in {}", dropped, level.dimension().location());
    }

    static final TicketController LAUNCH = new TicketController(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch"), (level, helper) -> {
        for (var e : java.util.List.copyOf(helper.getBlockTickets().entrySet())) {
            for (long c : new LongArrayList(e.getValue().ticking())) helper.removeTicket(e.getKey(), c, true);
            for (long c : new LongArrayList(e.getValue().nonTicking())) helper.removeTicket(e.getKey(), c, false);
        }
    });

    static void launchHold(ServerLevel level, BlockPos pad, boolean hold) {
        LAUNCH.forceChunk(level, pad, pad.getX() >> 4, pad.getZ() >> 4, hold, true);
    }

    static long chunkOf(int x, int z) { return ChunkPos.asLong(x >> 4, z >> 4); }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); event.register(LAUNCH); event.register(AsyncChunks.CONTROLLER); event.register(StationChunks.CONTROLLER); }
    }
}
