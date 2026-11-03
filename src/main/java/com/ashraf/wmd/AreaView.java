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
public final class AreaView {

    private AreaView() {}

    public static final int MODE_SAT = 0, MODE_SCOPE = 1, MODE_CAM = 2, MODE_CCTV = 3;
    static final double SCOPE_REACH = 10.0;

    static final int MAX_PENDING = 16;

    private static final class Job {
        final Set<Long> held = new HashSet<>();
        final UUID ticket;
        final ServerLevel level;
        final ServerPlayer p; final int token, cx, cz, n, step, span, x0, z0;
        final short[] h; final int[] rgb;
        final List<long[]> chunks = new ArrayList<>();
        int minY = Integer.MAX_VALUE;

        Job(ServerPlayer p, int token, int cx, int cz, int span, int step) {
            this.p = p; this.level = p.serverLevel(); this.token = token; this.cx = cx; this.cz = cz; this.step = step; this.n = span / step; this.span = n * step;
            this.ticket = UUID.nameUUIDFromBytes(("wmd-area-" + p.getUUID()).getBytes());
            this.x0 = cx - this.span / 2; this.z0 = cz - this.span / 2;
            this.h = new short[n * n]; this.rgb = new int[n * n];
            java.util.Arrays.fill(h, Short.MIN_VALUE);
            for (int a = x0 >> 4; a <= (x0 + this.span - 1) >> 4; a++) for (int b = z0 >> 4; b <= (z0 + this.span - 1) >> 4; b++) chunks.add(new long[] { a, b });
        }
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    private static final Map<UUID, Integer> LAST_JOB = new HashMap<>(), LAST_MARK = new HashMap<>();
    private static final int REQUEST_COOLDOWN = 8;

    private static boolean cooled(Map<UUID, Integer> m, ServerPlayer p) {
        int now = p.server.getTickCount();
        Integer last = m.get(p.getUUID());
        if (last != null && now >= last && now - last < REQUEST_COOLDOWN) return false;
        m.put(p.getUUID(), now);
        if (m.size() > 512) m.entrySet().removeIf(e -> now < e.getValue() || now - e.getValue() > REQUEST_COOLDOWN);
        return true;
    }

    static void clearStatics() { LAST_JOB.clear(); LAST_MARK.clear(); }

    static String deny(ServerPlayer p, int mode, int cx, int cz) {
        ServerLevel level = p.serverLevel();
        if (mode == MODE_CAM) return SpecialShells.cameraSees(p, cx, cz) ? null : "message.wmd.camera.none";
        if (mode == MODE_CCTV) return CcSecurityMonitorBlockEntity.feedAllowed(p, cx, cz) ? null : "message.wmd.cctv.no_feed";
        if (mode == MODE_SCOPE) {
            for (LaserTripodBlockEntity t : LaserTripodBlockEntity.tripodsOf(level, p.getUUID())) {
                Vec3 at = SableBridge.worldPos(level, t.getBlockPos());
                if (at.distanceTo(p.position()) <= SCOPE_REACH && Math.hypot(at.x - cx, at.z - cz) <= Balance.BEAM_RANGE + 32) return null;
            }
            return "message.wmd.area.no_tripod";
        }
        long now = System.currentTimeMillis();
        boolean any = false;
        for (OrbitMath.Sat s : OrbitData.get(level.getServer()).sats()) {
            if (!"designator".equals(s.type()) || !p.getUUID().equals(s.owner()) || now >= s.endMs()) continue;
            any = true;
            if (OrbitData.controlled(s) && OrbitMath.isOverhead(s, cx + 0.5, cz + 0.5, now)) return null;
        }
        return any ? "message.wmd.area.not_overhead" : "message.wmd.area.no_sat";
    }

    static void request(ServerPlayer p, int token, int mode, int cx, int cz, int size, boolean markersOnly) {
        if (!cooled(markersOnly ? LAST_MARK : LAST_JOB, p)) return;
        String why = deny(p, mode, cx, cz);
        if (why != null) { PacketDistributor.sendToPlayer(p, AreaViewPayload.refused(token, why)); return; }
        int span = Math.max(16, Math.min(mode == MODE_CAM ? Balance.CAMERA_VIEW : mode == MODE_CCTV ? Balance.CCTV_VIEW : Balance.AREA_VIEW_SIZE, size)), step = mode == MODE_SAT ? Balance.AREA_SAT_STEP : 1;
        if (markersOnly) { PacketDistributor.sendToPlayer(p, markers(p.serverLevel(), token, cx, cz, span)); return; }
        Job old = JOBS.put(p.getUUID(), new Job(p, token, cx, cz, span, step));
        if (old != null) AsyncChunks.releaseAll(old.level, old.ticket, old.held);
    }

    @SubscribeEvent
    static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || JOBS.isEmpty()) return;
        var it = JOBS.values().iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (j.level != level) continue;
            if (j.p.isRemoved() || j.p.serverLevel() != level) { AsyncChunks.releaseAll(level, j.ticket, j.held); it.remove(); continue; }

            int reads = 0;
            for (var ci = j.chunks.iterator(); ci.hasNext() && reads < Balance.AREA_CHUNKS_PER_TICK; ) {
                long[] c = ci.next();
                long key = net.minecraft.world.level.ChunkPos.asLong((int) c[0], (int) c[1]);
                net.minecraft.world.level.chunk.LevelChunk ch = j.held.size() < MAX_PENDING || j.held.contains(key)
                        ? AsyncChunks.get(level, j.ticket, (int) c[0], (int) c[1], j.held) : AsyncChunks.now(level, (int) c[0], (int) c[1]);
                if (ch == null) continue;
                read(level, j, ch);
                ci.remove();
                reads++;
            }
            if (!j.chunks.isEmpty()) continue;
            AsyncChunks.releaseAll(level, j.ticket, j.held);
            it.remove();
            int base = j.minY == Integer.MAX_VALUE ? 0 : j.minY;
            short[] rel = new short[j.h.length];
            for (int i = 0; i < rel.length; i++) rel[i] = (short) (j.h[i] == Short.MIN_VALUE ? 0 : j.h[i] - base);
            PacketDistributor.sendToPlayer(j.p, new AreaViewPayload(j.token, "", j.cx, j.cz, j.n, j.step, base, rel, j.rgb));
        }
    }

    private static void read(ServerLevel level, Job j, ChunkAccess ch) {
        long[] c = { ch.getPos().x, ch.getPos().z };
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = (int) (c[0] << 4) + lx, z = (int) (c[1] << 4) + lz;
            if (x < j.x0 || z < j.z0 || x >= j.x0 + j.span || z >= j.z0 + j.span) continue;
            int i = (x - j.x0) / j.step, k = (z - j.z0) / j.step;
            int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            if (top <= j.h[k * j.n + i]) continue;
            m.set(x, top, z);
            BlockState st = ch.getBlockState(m);
            boolean water = st.getFluidState().is(Fluids.WATER) || st.getFluidState().is(Fluids.FLOWING_WATER);
            int col = st.getMapColor(level, m).col;
            if (col == 0) col = 0x7F7F7F;
            j.h[k * j.n + i] = (short) top;
            j.rgb[k * j.n + i] = (col & 0xFFFFFF) | (water ? 1 << 24 : 0);
            j.minY = Math.min(j.minY, top);
        }
    }

    private static AreaMarkersPayload markers(ServerLevel level, int token, int cx, int cz, int n) {
        List<AreaMarkersPayload.Marker> l = new ArrayList<>();
        double half = n / 2.0;
        long now = level.getGameTime();
        for (ServerPlayer sp : level.players())
            if (in(sp.getX(), sp.getZ(), cx, cz, half)) l.add(new AreaMarkersPayload.Marker(AreaMarkersPayload.PLAYER, (float) sp.getX(), (float) sp.getY(), (float) sp.getZ(), 0.6F));
        for (SableBridge.ShipInfo s : SableBridge.largeShipsNear(level, cx, cz, half * 1.5))
            l.add(new AreaMarkersPayload.Marker(AreaMarkersPayload.AIRSHIP, (float) s.worldCenter().x, (float) s.worldCenter().y, (float) s.worldCenter().z,
                    (float) Math.max(s.size().x, s.size().z) * 0.5F));
        for (Flight f : FlightManager.get(level).flights()) {
            if (now < f.start() || now >= f.endTime()) continue;
            Vec3 at = f.position(now);
            if (in(at.x, at.z, cx, cz, half)) l.add(new AreaMarkersPayload.Marker(AreaMarkersPayload.MISSILE, (float) at.x, (float) at.y, (float) at.z, 0.8F));
        }
        for (LaserSpots.Spot s : LaserSpots.all())
            if (s.dim().equals(level.dimension()) && LaserSpots.get(level, s.id()) != null && in(s.pos().x, s.pos().z, cx, cz, half))
                l.add(new AreaMarkersPayload.Marker(AreaMarkersPayload.LASER, (float) s.pos().x, (float) s.pos().y, (float) s.pos().z, 0.5F));
        for (SmokeScreens.Cloud c : SmokeScreens.of(level)) {
            double r = c.radius(now);
            if (r > 0.0 && in(c.centre().x, c.centre().z, cx, cz, half + r))
                l.add(new AreaMarkersPayload.Marker(AreaMarkersPayload.SMOKE, (float) c.centre().x, (float) c.centre().y, (float) c.centre().z, (float) r));
        }
        if (l.size() > AreaMarkersPayload.MAX) l = l.subList(0, AreaMarkersPayload.MAX);
        return new AreaMarkersPayload(token, List.copyOf(l));
    }

    private static boolean in(double x, double z, int cx, int cz, double half) { return Math.abs(x - cx) <= half && Math.abs(z - cz) <= half; }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { JOBS.clear(); clearStatics(); }
}
