package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CamTerrain {

    private CamTerrain() {}

    static final double CAM_REACH = 400.0;
    static final int CELL = 2, CELLS = 16 / CELL, MAX_PENDING = 8;

    private static final class Watch {
        ServerLevel level; int x, z; long seen; UUID ticket;
        final Set<Long> sent = new HashSet<>(), held = new HashSet<>();
    }

    private static final Map<UUID, Watch> WATCH = new HashMap<>();

    private static final Map<UUID, Integer> LAST_ASK = new HashMap<>();
    private static final int REQUEST_COOLDOWN = 8;

    static void clearStatics() { LAST_ASK.clear(); }

    static void request(ServerPlayer p, int x, int z, boolean stop) {
        if (stop) { Watch w = WATCH.remove(p.getUUID()); if (w != null && w.level != null) AsyncChunks.releaseAll(w.level, w.ticket, w.held); return; }
        ServerLevel level = p.serverLevel();
        int tick = p.server.getTickCount();
        Integer last = LAST_ASK.get(p.getUUID());
        if (last != null && tick >= last && tick - last < REQUEST_COOLDOWN) return;
        LAST_ASK.put(p.getUUID(), tick);
        if (LAST_ASK.size() > 512) LAST_ASK.entrySet().removeIf(e -> tick < e.getValue() || tick - e.getValue() > REQUEST_COOLDOWN);
        if (!ownCameraNear(level, p, x, z)) return;
        Watch w = WATCH.computeIfAbsent(p.getUUID(), u -> new Watch());
        if (w.ticket == null) w.ticket = UUID.nameUUIDFromBytes(("wmd-cam-" + p.getUUID()).getBytes());
        if (w.level != level) { if (w.level != null) AsyncChunks.releaseAll(w.level, w.ticket, w.held); w.sent.clear(); }
        w.level = level; w.x = x; w.z = z; w.seen = level.getGameTime();
    }

    private static boolean ownCameraNear(ServerLevel level, ServerPlayer p, int x, int z) {
        if (RailgunBlockEntity.cutsceneNear(level, x, z, CAM_REACH)) return true;
        BlockPos cctv = CctvNet.viewing(p.getUUID());
        if (cctv != null && Math.hypot(cctv.getX() - x, cctv.getZ() - z) <= CAM_REACH) return true;
        String me = p.getUUID().toString();
        long now = level.getGameTime();
        for (Flight f : FlightManager.get(level).flights()) {
            if (!me.equals(f.ownerId()) || f.build() == null || !f.build().hasCamera() || now < f.start() || now >= f.endTime() + 60) continue;
            Vec3 at = f.position(Math.min(now, f.endTime()));
            if (Math.hypot(at.x - x, at.z - z) <= CAM_REACH) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || WATCH.isEmpty()) return;
        long now = level.getGameTime();
        var it = WATCH.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Watch w = e.getValue();
            if (w.level != level) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || now - w.seen > 100) { AsyncChunks.releaseAll(level, w.ticket, w.held); it.remove(); continue; }
            if (w.sent.size() > 4000) w.sent.clear();
            int ccx = w.x >> 4, ccz = w.z >> 4, r = Balance.CAM_TERRAIN_CHUNKS, budget = Balance.CAM_CHUNKS_PER_TICK;

            for (Long k : java.util.List.copyOf(w.held))
                if (Math.abs(ChunkPos.getX(k) - ccx) > r + 2 || Math.abs(ChunkPos.getZ(k) - ccz) > r + 2) AsyncChunks.release(level, w.ticket, k, w.held);
            for (int[] c : nearestFirst(r)) {
                if (budget <= 0) break;
                int cx = ccx + c[0], cz = ccz + c[1];
                long key = ChunkPos.asLong(cx, cz);
                if (w.sent.contains(key)) continue;
                net.minecraft.world.level.chunk.LevelChunk ch = w.held.size() < MAX_PENDING || w.held.contains(key)
                        ? AsyncChunks.get(level, w.ticket, cx, cz, w.held) : AsyncChunks.now(level, cx, cz);
                if (ch == null) continue;
                w.sent.add(key);
                PacketDistributor.sendToPlayer(p, read(level, ch));
                budget--;
            }
        }
    }

    private static List<int[]> ORDER;
    private static int orderR = -1;

    private static List<int[]> nearestFirst(int r) {
        if (ORDER != null && orderR == r) return ORDER;
        List<int[]> l = new ArrayList<>();
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) if (a * a + b * b <= r * r) l.add(new int[] { a, b });
        l.sort((p, q) -> Integer.compare(p[0] * p[0] + p[1] * p[1], q[0] * q[0] + q[1] * q[1]));
        ORDER = l; orderR = r;
        return l;
    }

    private static CamChunkPayload read(ServerLevel level, ChunkAccess ch) {
        int cx = ch.getPos().x, cz = ch.getPos().z;
        short[] h = new short[CELLS * CELLS];
        int[] rgb = new int[CELLS * CELLS];
        java.util.Arrays.fill(h, Short.MIN_VALUE);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int i = (lz / CELL) * CELLS + lx / CELL;
            int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            if (top <= h[i]) continue;
            m.set((cx << 4) + lx, top, (cz << 4) + lz);
            BlockState st = ch.getBlockState(m);
            if (st.isAir()) { m.setY(top - 1); st = ch.getBlockState(m); }
            boolean water = st.getFluidState().is(Fluids.WATER) || st.getFluidState().is(Fluids.FLOWING_WATER);
            int col = st.getMapColor(level, m).col;
            if (col == 0) col = 0x7F7F7F;
            h[i] = (short) top;
            rgb[i] = (col & 0xFFFFFF) | (water ? 1 << 24 : 0);
        }
        return new CamChunkPayload(cx, cz, h, rgb);
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { WATCH.clear(); clearStatics(); }
}
