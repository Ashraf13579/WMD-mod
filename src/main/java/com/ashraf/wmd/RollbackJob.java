package com.ashraf.wmd;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@EventBusSubscriber(modid = Wmd.MODID)
public final class RollbackJob {

    static final long MAX_WAIT = 3_600_000L;

    private final ServerLevel level;

    private final CompletableFuture<Map<Long, List<RollbackLog.Op>>> load;

    private int cursor;

    private java.util.function.Consumer<Boolean> done;
    private final LongPredicate chunkFilter;
    private final UUID reportTo;
    private final String label;
    private final boolean preview;
    private final long born = WarManager.now();
    private ArrayDeque<long[]> chunks;
    private Map<Long, List<RollbackLog.Op>> byChunk;
    private final ArrayDeque<Long> waiting = new ArrayDeque<>();
    private int restored, skipped, chunksDone;
    private long nextRetry;

    private static final List<RollbackJob> JOBS = Statics.server(new ArrayList<>());

    private RollbackJob(ServerLevel level, List<Path> files, long t0, long t1, LongPredicate fileChunkOk, Predicate<UUID> shooterOk, LongPredicate chunkFilter,
                        UUID reportTo, String label, boolean preview) {
        this.level = level;
        this.chunkFilter = chunkFilter;
        this.reportTo = reportTo;
        this.label = label;
        this.preview = preview;
        RollbackLog.flush(level.getServer());
        this.load = CompletableFuture.supplyAsync(() -> group(RollbackLog.read(files, t0, t1, fileChunkOk, shooterOk)), RollbackLog.files());
    }

    static RollbackJob start(ServerLevel level, List<Path> files, long t0, long t1, LongPredicate fileChunkOk, Predicate<UUID> shooterOk, LongPredicate chunkFilter,
                      UUID reportTo, String label, boolean preview) {
        RollbackJob j = new RollbackJob(level, files, t0, t1, fileChunkOk, shooterOk, chunkFilter, reportTo, label, preview);
        JOBS.add(j);
        Wmd.LOGGER.info("[rollback] {} queued ({})", label, level.dimension().location());
        return j;
    }

    private static Map<Long, List<RollbackLog.Op>> group(Map<Long, RollbackLog.Op> ops) {
        Map<Long, List<RollbackLog.Op>> m = new HashMap<>();
        for (RollbackLog.Op op : ops.values())
            m.computeIfAbsent(ChunkPos.asLong(BlockPos.getX(op.pos()) >> 4, BlockPos.getZ(op.pos()) >> 4), k -> new ArrayList<>()).add(op);
        for (List<RollbackLog.Op> l : m.values()) l.sort((a, b) -> Integer.compare(BlockPos.getY(a.pos()), BlockPos.getY(b.pos())));
        return m;
    }

    private static final Map<Integer, int[]> WAR_JOBS = Statics.server(new HashMap<>());
    static boolean warHealRunning(int warId) { return WAR_JOBS.containsKey(warId); }
    private void finished(boolean leftover) { if (done != null) done.accept(leftover); }

    private static final Set<String> WILD_RUNNING = Statics.server(new java.util.HashSet<>());
    static final long WILD_RETRY = 3_600_000L;

    static int running() { return JOBS.size(); }

    static boolean loose(BlockState s) {

        return s.isAir() || s.getBlock() instanceof LiquidBlock || s.getBlock() instanceof BaseFireBlock || s.is(net.minecraft.tags.BlockTags.SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SUSPICIOUS_GRAVEL) || s.is(Blocks.SNOW);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        long t0 = WorkBudget.start();
        try {
            for (Iterator<RollbackJob> it = JOBS.iterator(); it.hasNext(); ) {
                RollbackJob j = it.next();
                if (j.step(t0)) { it.remove(); j.finished(!j.waiting.isEmpty() || j.byChunk == null); }
                if (!WorkBudget.more(t0)) break;
            }
        } finally { WorkBudget.done(t0); }
    }

    private boolean step(long t0) {
        if (byChunk == null) {
            if (!load.isDone()) return false;
            if (load.isCompletedExceptionally()) { Wmd.LOGGER.error("[rollback] {}: reading the log failed (see above); nothing put back", label); return true; }
            byChunk = new HashMap<>(load.getNow(Map.of()));
            byChunk.keySet().removeIf(ck -> !chunkFilter.test(ck));
            int n = 0;
            for (List<RollbackLog.Op> l : byChunk.values()) n += l.size();
            if (preview) { report(Component.translatable("message.wmd.rollback.preview", label, n, byChunk.size())); return true; }
            chunks = new ArrayDeque<>();
            for (Long ck : byChunk.keySet()) chunks.add(new long[] { ck });
            Wmd.LOGGER.info("[rollback] {}: {} blocks in {} chunks to check", label, n, byChunk.size());
            if (n == 0) { report(Component.translatable("message.wmd.rollback.done", label, 0, 0)); return true; }
        }
        long now = WarManager.now();
        if (!waiting.isEmpty() && now >= nextRetry) { while (!waiting.isEmpty()) chunks.add(new long[] { waiting.poll() }); nextRetry = now + 5_000L; }
        while (!chunks.isEmpty() && WorkBudget.more(t0)) {
            long ck = chunks.peek()[0];
            if (!level.hasChunk(ChunkPos.getX(ck), ChunkPos.getZ(ck))) { chunks.poll(); waiting.add(ck); cursor = 0; continue; }
            List<RollbackLog.Op> l = byChunk.get(ck);
            int i = cursor;
            while (i < l.size() && (i - cursor < 256 || WorkBudget.more(t0))) apply(l.get(i++));
            if (i < l.size()) { cursor = i; break; }
            cursor = 0;
            chunks.poll();
            byChunk.remove(ck);
            chunksDone++;
        }
        if (chunks.isEmpty() && (waiting.isEmpty() || now - born > MAX_WAIT)) {
            if (!waiting.isEmpty()) Wmd.LOGGER.info("[rollback] {}: {} chunk(s) were never loaded and stay as they are", label, waiting.size());
            report(Component.translatable("message.wmd.rollback.done", label, restored, skipped));
            Wmd.LOGGER.info("[rollback] {} done: {} blocks put back, {} left (a player changed them), {} chunks", label, restored, skipped, chunksDone);
            return true;
        }
        return false;
    }

    private void apply(RollbackLog.Op op) {
        BlockPos p = BlockPos.of(op.pos());
        BlockState cur = level.getBlockState(p);
        if (cur == op.before()) return;
        if (cur != op.after() && !loose(cur)) { skipped++; return; }

        boolean standing = cur.getBlock() == op.before().getBlock() && cur.hasBlockEntity() && level.getBlockEntity(p) != null;
        level.setBlock(p, op.before(), Block.UPDATE_CLIENTS);
        restored++;
        if (op.nbt() != null && !standing) {
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) {
                try { be.loadWithComponents(op.nbt(), level.registryAccess()); } catch (RuntimeException e) { Wmd.LOGGER.warn("[rollback] block entity at {}: {}", p, e.toString()); }
                empty(level, p);
                be.setChanged();
                level.sendBlockUpdated(p, op.before(), op.before(), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void empty(ServerLevel level, BlockPos p) {
        if (level.getBlockEntity(p) instanceof net.minecraft.world.Container c) c.clearContent();
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
        if (items != null) for (int i = 0; i < items.getSlots(); i++) {
            if (items instanceof IItemHandlerModifiable m) m.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
            else for (int k = 0; k < 64 && !items.getStackInSlot(i).isEmpty(); k++) if (items.extractItem(i, 64, false).isEmpty()) break;
        }
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, p, null);
        if (fluids != null) for (int k = 0; k < 16; k++) if (fluids.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).isEmpty()) break;
    }

    private void report(Component line) {
        if (reportTo == null) { Wmd.LOGGER.info("[rollback] {}", line.getString()); return; }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(reportTo);
        if (p != null) p.sendSystemMessage(line);
    }

    static void warHeal(MinecraftServer server, WarData.War w) {
        Set<UUID> nations = new java.util.HashSet<>(w.sideA);
        nations.addAll(w.sideB);
        String label = "war heal #" + w.id + (w.healTries > 0 ? " (try " + (w.healTries + 1) + ")" : "");
        int[] c = new int[2];
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            List<Path> files = RollbackLog.allRegions(server, dim);
            if (files.isEmpty()) continue;
            RollbackJob j = start(level, files, w.startsAt - 60_000L, w.endedAt + 60_000L, ck -> true, null,
                    ck -> !(dim.equals(w.takenDim) && w.taken.contains(ck)) && nations.contains(OpacBridge.nationAt(level, new BlockPos(ChunkPos.getX(ck) << 4, 64, ChunkPos.getZ(ck) << 4))),
                    null, label, false);
            int id = w.id;
            j.done = leftover -> {
                if (leftover) c[1] = 1;
                if (--c[0] > 0) return;
                WAR_JOBS.remove(id);
                WarManager.healFinished(server, id, c[1] != 0);
            };
            c[0]++;
        }
        if (c[0] == 0) { WarManager.healFinished(server, w.id, false); return; }
        WAR_JOBS.put(w.id, c);
    }

    static void admin(ServerLevel level, BlockPos at, int radius, int minutes, UUID shooter, UUID reportTo, boolean preview) {
        int cx0 = (at.getX() - radius) >> 4, cz0 = (at.getZ() - radius) >> 4, cx1 = (at.getX() + radius) >> 4, cz1 = (at.getZ() + radius) >> 4;
        long t1 = WarManager.now(), t0 = t1 - minutes * 60_000L;
        double r2 = (double) radius * radius;
        LongPredicate inRadius = ck -> {
            double dx = (ChunkPos.getX(ck) << 4) + 8 - at.getX(), dz = (ChunkPos.getZ(ck) << 4) + 8 - at.getZ();
            return dx * dx + dz * dz <= r2 + 256.0;
        };
        start(level, RollbackLog.regionsFor(level.getServer(), level.dimension().location().toString(), cx0, cz0, cx1, cz1), t0, t1, inRadius,
                shooter == null ? null : u -> shooter.equals(u), ck -> !vnLand(level, ck), reportTo, "rollback r" + radius + " " + minutes + " min" + (shooter == null ? "" : " (one shooter)"), preview);
    }

    static void wildernessSweep(MinecraftServer server) {
        if (!Config.Server.wildernessHeal()) return;
        if (VnBridge.present() && (VnWars.anyWar || !VnBridge.wars().isEmpty())) return;
        RollbackIndex idx = RollbackIndex.get(server);
        long now = WarManager.now(), cutoff = now - Config.Server.wildernessHealHours() * 3_600_000L;
        RollbackLog.flush(server);
        for (RollbackIndex.Seg s : new ArrayList<>(idx.segs)) {
            if (s.wild || s.t1 > cutoff || s.wildNext > now) continue;
            String key = s.dim + "|" + s.rx + "|" + s.rz + "|" + s.t0;
            if (WILD_RUNNING.contains(key)) continue;
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(s.dim)) level = l;
            if (level == null) continue;
            ServerLevel lv = level;
            WILD_RUNNING.add(key);
            RollbackJob j = start(lv, List.of(RollbackLog.region(server, s.dim, s.rx, s.rz)), s.t0, s.t1, ck -> true, null, ck -> wild(lv, ck), null, "wilderness heal", false);

            j.done = leftover -> {
                WILD_RUNNING.remove(key);
                if (leftover) s.wildNext = WarManager.now() + WILD_RETRY; else s.wild = true;
                RollbackIndex.get(server).setDirty();
            };
        }
    }

    static boolean vnLand(ServerLevel level, long ck) {
        return VnBridge.present() && VnBridge.countryAt(level, new BlockPos((ChunkPos.getX(ck) << 4) + 8, 64, (ChunkPos.getZ(ck) << 4) + 8)) != null;
    }

    static boolean wildChunk(ServerLevel level, long ck) { return wild(level, ck); }

    private static boolean wild(ServerLevel level, long ck) {
        int cx = ChunkPos.getX(ck), cz = ChunkPos.getZ(ck);
        if (vnLand(level, ck)) return false;
        if (OpacBridge.active()) return OpacBridge.claimOwner(level, cx, cz) == null;
        return !ClaimGuard.protectedAt(level, new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8));
    }

    static boolean used(RollbackIndex.Seg s, List<WarData.War> wars) {
        if (Config.Server.rollbackLogAll()) return false;
        if (Config.Server.wildernessHeal() && !s.wild) return false;
        if (!Config.Server.warHeal()) return true;
        for (WarData.War w : wars) {
            if (w.healDone || (!w.live() && w.healAt == 0)) continue;
            long from = w.startsAt - 60_000L, to = w.live() ? Long.MAX_VALUE : w.endedAt + 60_000L;
            if (s.t1 >= from && s.t0 <= to) return false;
        }
        return true;
    }

    static void retention(MinecraftServer server) {
        RollbackIndex idx = RollbackIndex.get(server);
        long cutoff = WarManager.now() - Config.Server.rollbackKeepDays() * 86_400_000L;
        if (idx.segs.removeIf(s -> s.t1 < cutoff)) idx.setDirty();

        Map<String, Long> usedUntil = new HashMap<>();
        Map<String, List<RollbackIndex.Seg>> byRegion = new HashMap<>();
        for (RollbackIndex.Seg s : idx.segs) byRegion.computeIfAbsent(s.dim + "|" + s.rx + "|" + s.rz, k -> new ArrayList<>()).add(s);
        List<WarData.War> wars = WarData.get(server).wars;
        for (Map.Entry<String, List<RollbackIndex.Seg>> e : byRegion.entrySet()) {
            List<RollbackIndex.Seg> l = e.getValue();
            l.sort((a, b) -> Long.compare(a.t0, b.t0));
            long u = Long.MIN_VALUE;
            for (RollbackIndex.Seg s : l) { if (!used(s, wars)) break; u = s.t1; }
            if (u != Long.MIN_VALUE) usedUntil.put(e.getKey(), u);
        }

        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            for (Path f : RollbackLog.allRegions(server, dim)) {
                String[] n = f.getFileName().toString().split("\\.");
                Long u = n.length == 4 ? usedUntil.get(dim + "|" + n[1] + "|" + n[2]) : null;
                long cut = u == null ? cutoff : Math.max(cutoff, u + 1);
                RollbackLog.files().execute(() -> RollbackLog.trim(f, cut));
            }
        }
    }

    @SubscribeEvent
    static void onSlowTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 12_000 == 6_000) wildernessSweep(server);
        if (t % 72_000 == 600) retention(server);
    }
}
