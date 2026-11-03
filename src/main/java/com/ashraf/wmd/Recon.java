package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Recon {

    private Recon() {}

    public static final int ICON_LAUNCH = 0, ICON_DEFENCE = 1, ICON_RADAR = 2, ICON_STATION = 3, ICON_OTHER = 4;

    public static final int ICON_AIRSHIP = 5;

    static final int MAX_SHIPS = 16;
    public static final int STATUS_NONE = 0, STATUS_NO_CONTROL = 1, STATUS_WAITING = 2, STATUS_OVERHEAD = 3;

    public static final int STATUS_DRONE = 4;

    public static final int STATUS_STATION = 5;

    static boolean stationSees(MinecraftServer server, UUID owner, ReconData.Entry e) {
        if (e == null || !e.hasTarget) return false;
        int slot = Orbit.slotOf(server, owner);
        if (slot < 0 || !SensorArray.working(server, slot)) return false;
        Orbit.Ground g = Orbit.ground(server, slot);
        return g.dim() == net.minecraft.world.level.Level.OVERWORLD && Math.abs(e.tx - g.x()) <= Balance.SENSOR_GROUND_R && Math.abs(e.tz - g.z()) <= Balance.SENSOR_GROUND_R;
    }
    static final int MAX_ICONS = 96, MAX_DOTS = 32, MAX_FLIGHTS = 32, ROWS_PER_TICK = 8, CHUNK_ROWS_PER_TICK = 2;

    public static int samples() { return Math.max(8, Math.min(256, 2 * Balance.RECON_SNAPSHOT_RADIUS / Math.max(1, Balance.RECON_SNAPSHOT_STEP))); }

    static boolean reconLike(OrbitMath.Sat s) { return s.type().equals("recon") || s.isCubesat(); }

    static long passLeft(OrbitMath.Sat s, double x, double z, long now) {
        return s.isCubesat() ? Math.max(0L, s.endMs() - now) : OrbitMath.overheadLeft(s, x, z, now);
    }

    public static OrbitMath.Sat sat(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return null;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && reconLike(s) && now < s.endMs() && OrbitData.controlled(s)) return s;
        return null;
    }

    public static boolean covered(MinecraftServer server, UUID owner) { return sat(server, owner) != null; }

    public static boolean tracking(ServerLevel level, UUID owner) {
        return owner != null && level.dimension() == Level.OVERWORLD && covered(level.getServer(), owner) && CommandCenter.hasModule(owner, CcModule.AIR_DEFENCE);
    }

    static boolean inArea(ReconData.Entry e, double x, double z) {
        int r = Balance.RECON_SNAPSHOT_RADIUS;
        return e != null && e.hasTarget && Math.abs(x - (e.tx + 0.5)) <= r && Math.abs(z - (e.tz + 0.5)) <= r;
    }

    static OrbitMath.Sat overTarget(MinecraftServer server, UUID owner, ReconData.Entry e, long now) {
        if (e == null || !e.hasTarget) return null;
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && reconLike(s) && now < s.endMs() && passLeft(s, e.tx + 0.5, e.tz + 0.5, now) > 0 && OrbitData.controlled(s)) return s;
        return null;
    }

    static long[] status(MinecraftServer server, UUID owner, ReconData.Entry e) {
        long[] st = satStatus(server, owner, e);
        if (st[0] == STATUS_OVERHEAD) return st;
        if (stationSees(server, owner, e)) return new long[] { STATUS_STATION, 0, 0 };
        long drone = Drones.loiterLeftMs(server, owner);
        return drone > 0 ? new long[] { STATUS_DRONE, 0, drone } : st;
    }

    private static long[] satStatus(MinecraftServer server, UUID owner, ReconData.Entry e) {
        long now = System.currentTimeMillis();
        boolean any = false;
        OrbitMath.Sat best = null;
        long bestMs = Long.MAX_VALUE;
        for (OrbitMath.Sat s : OrbitData.get(server).sats()) {
            if (!owner.equals(s.owner()) || !reconLike(s) || now >= s.endMs()) continue;
            any = true;
            if (!OrbitData.controlled(s)) continue;
            if (e == null || !e.hasTarget) { if (best == null) { best = s; bestMs = 0L; } continue; }
            long left = passLeft(s, e.tx + 0.5, e.tz + 0.5, now);
            if (left > 0) return new long[] { STATUS_OVERHEAD, s.id(), left };
            long next = OrbitMath.nextPass(s, e.tx + 0.5, e.tz + 0.5, now);
            if (next < bestMs) { best = s; bestMs = next; }
        }
        if (best != null) return new long[] { STATUS_WAITING, best.id(), bestMs };
        return new long[] { any ? STATUS_NO_CONTROL : STATUS_NONE, 0, 0 };
    }

    static void onLaunch(ServerLevel level, Flight f) {
        if (f == null || (f.build() != null && f.build().isInterceptor())) return;
        Set<UUID> warned = new HashSet<>();
        if (level.dimension() == Level.OVERWORLD) reconWarn(level, f, warned);
        SensorArray.onLaunch(level, f, warned);
        EarlyWarning.onLaunch(level, f, warned);
    }

    static boolean climbsToOrbit(Flight f) { return Satellites.toOrbit(f.build()) || Asat.isAsat(f.build()); }

    private static void reconWarn(ServerLevel level, Flight f, Set<UUID> warned) {
        MinecraftServer server = level.getServer();
        Set<UUID> owners = new HashSet<>();
        for (OrbitMath.Sat s : OrbitData.get(server).sats()) if (reconLike(s)) owners.add(s.owner());
        if (owners.isEmpty()) return;
        boolean sat = climbsToOrbit(f);
        int secs = Math.max(0, f.duration() / 20);
        for (UUID o : owners) {

            if (!covered(server, o) || !CommandCenter.hasModule(o, CcModule.WARNING_BOARD)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (f.isOwnedBy(o, p != null ? p.getGameProfile().getName() : null)) continue;
            warned.add(o);
            long[] imp = blurred(f.fx(), f.fz(), level.random);
            CommandCenter.addWarning(server, o, CommandCenter.Warning.ofFlight(f, imp, sat, intel(server, o, f.ox(), f.oz())));
            if (p != null) warnAt(p, f.ox(), f.oz(), imp[0], imp[1], secs, sat);
        }
    }

    static boolean intel(MinecraftServer server, UUID owner, double x, double z) {
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && reconLike(s) && now < s.endMs() && OrbitData.controlled(s) && passLeft(s, x, z, now) > 0) return true;
        return false;
    }

    static long[] blurred(int tx, int tz, RandomSource rnd) {
        int blur = Balance.RECON_IMPACT_BLUR;
        double a = rnd.nextDouble() * Math.PI * 2.0, r = blur * Math.sqrt(rnd.nextDouble());
        return new long[] { Math.round((tx + Math.cos(a) * r) / 10.0) * 10L, Math.round((tz + Math.sin(a) * r) / 10.0) * 10L };
    }

    static void warn(ServerPlayer p, double ox, double oz, int tx, int tz, int secs, boolean sat, RandomSource rnd) {
        long[] imp = blurred(tx, tz, rnd);
        warnAt(p, ox, oz, imp[0], imp[1], secs, sat);
    }

    static void warnAt(ServerPlayer p, double ox, double oz, long px, long pz, int secs, boolean sat) {
        int blur = Balance.RECON_IMPACT_BLUR;
        Component msg = sat ? Component.translatable("message.wmd.recon.launch_sat", Math.round(ox), Math.round(oz))
                : Component.translatable("message.wmd.recon.launch", Math.round(ox), Math.round(oz), px, pz, blur, secs);
        p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.RED));
        p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        p.playNotifySound(WmdSounds.CC_ALERT.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
    }

    private static final Map<String, Long> ALERTED = new HashMap<>();
    private static final List<Job> JOBS = new ArrayList<>();

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!JOBS.isEmpty()) runJob(server);
        if (ReconFill.active()) ReconFill.tick(server);
        if (server.getTickCount() % 20 != 11) return;
        stationPasses(server);
        List<OrbitMath.Sat> sats = OrbitData.get(server).sats();
        boolean any = false;
        for (OrbitMath.Sat s : sats) if (reconLike(s)) { any = true; break; }
        if (!any) return;
        ReconData d = ReconData.get(server);
        long now = System.currentTimeMillis();
        Set<UUID> done = new HashSet<>();
        for (OrbitMath.Sat s : sats) {
            if (!reconLike(s) || now >= s.endMs() || done.contains(s.owner())) continue;
            ReconData.Entry e = d.entry(s.owner());
            if (e == null || !e.hasTarget) continue;

            if (OrbitData.controlled(s) && CommandCenter.hasModule(s.owner(), CcModule.RECON_WALL)) ReconFill.want(server, s.owner(), e);
            long left = passLeft(s, e.tx + 0.5, e.tz + 0.5, now);
            if (left <= 0 || !OrbitData.controlled(s)) continue;
            done.add(s.owner());
            long passEnd = now + left;
            boolean newPass = Math.abs(passEnd - e.lastPassEnd) > 60_000L;
            if (newPass) { e.lastPassEnd = passEnd; e.lastShotMs = 0L; d.setDirty(); }

            if (now - e.lastShotMs >= Balance.RECON_RETAKE_SECONDS * 1000L && !jobFor(s.owner()) && CommandCenter.hasModule(s.owner(), CcModule.RECON_WALL)) {
                e.lastShotMs = now;
                start(server, s.owner(), e, newPass ? s.id() : 0);
            }
            alertImaged(server, s, e, passEnd);
        }
        if (ALERTED.size() > 512) ALERTED.clear();
    }

    private static void stationPasses(MinecraftServer server) {
        ReconData d = ReconData.get(server);
        long now = System.currentTimeMillis();
        for (int slot : Station.Data.get(server).slots.keySet()) {
            UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
            if (owner == null) continue;
            ReconData.Entry e = d.entry(owner);
            if (!stationSees(server, owner, e) || !CommandCenter.hasModule(owner, CcModule.RECON_WALL)) continue;
            ReconFill.want(server, owner, e);
            if (now - e.lastShotMs >= Balance.RECON_RETAKE_SECONDS * 1000L && !jobFor(owner)) {
                e.lastShotMs = now;
                d.setDirty();
                start(server, owner, e, 0);
            }
        }
    }

    private static void alertImaged(MinecraftServer server, OrbitMath.Sat s, ReconData.Entry e, long passEnd) {
        for (ServerPlayer p : server.overworld().players()) {
            if (p.isSpectator() || p.getUUID().equals(s.owner()) || !inArea(e, p.getX(), p.getZ())) continue;
            if (!GroundStationBlockEntity.hasPoweredStation(p.getUUID())) continue;
            String key = p.getUUID() + "|" + s.owner();
            Long last = ALERTED.get(key);
            if (last != null && Math.abs(last - passEnd) <= 60_000L) continue;
            ALERTED.put(key, passEnd);
            p.displayClientMessage(Component.translatable("message.wmd.recon.imaged", OrbitMath.mmss(passEnd - System.currentTimeMillis()))
                    .withStyle(ChatFormatting.GOLD), true);
            p.playNotifySound(WmdSounds.CC_ALERT.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
            MusicTriggersHook.fire(p.serverLevel(), "wmd:being_imaged", p.blockPosition());
        }
    }

    private static final class Job {
        final UUID owner;
        final int x0, z0, step, n, notifySat;

        final byte[] base;
        final int[] ys;
        final java.util.BitSet touched;
        final int minCx, maxCx, minCz, maxCz;
        final List<int[]> icons = new ArrayList<>();
        final Set<Long> scanned = new HashSet<>();
        int row, chunkRow, loaded;

        Job(UUID owner, int x0, int z0, int step, int n, int notifySat) {
            this.owner = owner; this.x0 = x0; this.z0 = z0; this.step = step; this.n = n; this.notifySat = notifySat;
            base = new byte[n * n];
            ys = new int[n * n];
            touched = new java.util.BitSet(n * n);
            minCx = x0 >> 4; maxCx = (x0 + n * step - 1) >> 4;
            minCz = z0 >> 4; maxCz = (z0 + n * step - 1) >> 4;
        }

        boolean done() { return row >= n && minCz + chunkRow > maxCz; }
    }

    private static boolean jobFor(UUID owner) {
        for (Job j : JOBS) if (j.owner.equals(owner)) return true;
        return false;
    }

    static boolean start(MinecraftServer server, UUID owner, ReconData.Entry e, int notifySat) {
        if (e == null || !e.hasTarget || jobFor(owner)) return false;
        int step = Math.max(1, Balance.RECON_SNAPSHOT_STEP), n = samples(), r = n * step / 2;
        JOBS.add(new Job(owner, e.tx - r, e.tz - r, step, n, notifySat));
        return true;
    }

    static long sampleColumn(ServerLevel level, LevelChunk ch, int x, int z, BlockPos.MutableBlockPos pos) {
        long camo = Camo.sample(level, x, z);
        if (camo != Long.MIN_VALUE) return camo;
        int minY = level.getMinBuildHeight();
        int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), y = top;
        MapColor mc = MapColor.NONE;
        for (; y >= minY && y > top - 4; y--) {
            pos.set(x, y, z);
            BlockState st = ch.getBlockState(pos);
            mc = st.getMapColor(level, pos);
            if (mc != MapColor.NONE) break;
        }
        return ((long) mc.id << 32) | (y & 0xFFFFFFFFL);
    }

    private static void runJob(MinecraftServer server) {
        Job j = JOBS.get(0);
        ServerLevel level = server.overworld();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < ROWS_PER_TICK && j.row < j.n; k++, j.row++) {
            int z = j.z0 + j.row * j.step + j.step / 2;
            for (int i = 0; i < j.n; i++) {
                int x = j.x0 + i * j.step + j.step / 2;
                LevelChunk ch = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (ch == null) continue;
                long v = sampleColumn(level, ch, x, z, pos);
                int idx = j.row * j.n + i;
                j.base[idx] = (byte) (v >>> 32);
                j.ys[idx] = (int) v;
                j.touched.set(idx);
                j.loaded++;
            }
        }
        for (int k = 0; k < CHUNK_ROWS_PER_TICK && j.minCz + j.chunkRow <= j.maxCz; k++, j.chunkRow++) scanMachines(level, j, j.minCz + j.chunkRow);
        if (j.done()) { JOBS.remove(0); finish(server, j); }
    }

    private static void scanMachines(ServerLevel level, Job j, int cz) {
        for (int cx = j.minCx; cx <= j.maxCx; cx++) {
            LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            j.scanned.add(ChunkPos.asLong(cx, cz));
            scanChunk(ch, j.owner, j.x0, j.z0, j.x0 + j.n * j.step, j.z0 + j.n * j.step, j.icons);
        }
    }

    static void scanChunk(LevelChunk ch, UUID owner, int ax, int az, int bx, int bz, List<int[]> out) {
        for (BlockEntity be : ch.getBlockEntities().values()) {
            if (!(be instanceof Owned o) || be.isRemoved() || o.getOwner() == null || o.getOwner().equals(owner)) continue;
            if (SableBridge.isOnShip(ch.getLevel(), be.getBlockPos())) continue;
            if (Camo.hiddenFromRecon(be)) continue;
            BlockPos bp = be.getBlockPos();
            CcIntel.note(bp.getX(), bp.getZ(), o.getOwner());
            if (bp.getX() < ax || bp.getX() >= bx || bp.getZ() < az || bp.getZ() >= bz) continue;
            out.add(new int[] { bp.getX(), bp.getZ(), kind(be) });
        }
    }

    static int kind(BlockEntity be) {
        if (be instanceof DecoyBlockEntity d) return d.reconKind();
        if (be instanceof LaunchTerminalBlockEntity) return ICON_LAUNCH;
        if (be instanceof InterceptorSite) return ICON_DEFENCE;
        if (be instanceof RadarBlockEntity) return ICON_RADAR;
        if (be instanceof GroundStationBlockEntity) return ICON_STATION;
        return ICON_OTHER;
    }

    static int kindOf(net.minecraft.world.level.block.entity.BlockEntityType<?> t) {
        if (t == null) return ICON_OTHER;
        if (t == Wmd.DECOY_LAUNCHER_BE.get()) return ICON_LAUNCH;
        if (t == Wmd.DECOY_RADAR_BE.get()) return ICON_RADAR;
        if (t == Wmd.LAUNCH_TERMINAL_BE.get()) return ICON_LAUNCH;
        if (t == Wmd.LAUNCHER_BE.get() || t == Wmd.SILO_BE.get() || t == Wmd.SILO4_BE.get() || t == Wmd.HEAVY_BATTERY_BE.get() || t == Wmd.CIWS_BE.get()) return ICON_DEFENCE;
        if (t == Wmd.RADAR_BE.get()) return ICON_RADAR;
        if (t == Wmd.GROUND_STATION_BE.get()) return ICON_STATION;
        return ICON_OTHER;
    }

    private static void finish(MinecraftServer server, Job j) {
        ReconData.Entry e = ReconData.get(server).entry(j.owner);
        if (e == null || !e.hasTarget || e.tx - j.n * j.step / 2 != j.x0 || e.tz - j.n * j.step / 2 != j.z0) return;
        ServerPlayer p = server.getPlayerList().getPlayer(j.owner);
        if (j.loaded == 0) {
            if (j.notifySat != 0 && p != null)
                p.sendSystemMessage(Component.translatable("message.wmd.recon.not_loaded", Math.max(0, j.notifySat), e.tx, e.tz).withStyle(ChatFormatting.GRAY));
            return;
        }
        int[] idx = new int[j.loaded];
        int c = 0;
        for (int i = j.touched.nextSetBit(0); i >= 0 && c < idx.length; i = j.touched.nextSetBit(i + 1)) idx[c++] = i;
        byte[] base = new byte[c];
        int[] ys = new int[c];
        for (int k = 0; k < c; k++) { base[k] = j.base[idx[k]]; ys[k] = j.ys[idx[k]]; }

        int[] box = { j.x0, j.z0, j.x0 + j.n * j.step, j.z0 + j.n * j.step };
        Map<Long, String> labels = new HashMap<>();
        j.icons.addAll(0, shipIcons(server.overworld(), box, labels));
        ReconData.Snapshot s = merge(server, j.owner, j.x0, j.z0, j.step, j.n, idx, base, ys, c, j.scanned, j.icons, ReconFill.heights(j.owner, j.x0, j.z0, j.step, j.n),
                box, labels);
        if (s != null && j.notifySat != 0 && p != null)
            p.sendSystemMessage(Component.translatable("message.wmd.recon.snapshot", Math.max(0, j.notifySat), e.tx, e.tz, s.icons().length / 3, s.loadedPct())
                    .withStyle(ChatFormatting.AQUA));
    }

    static ReconData.Snapshot merge(MinecraftServer server, UUID owner, int x0, int z0, int step, int n, int[] idx, byte[] base, int[] ys, int cnt,
                                    Set<Long> scanned, List<int[]> newIcons, int[] heights) {
        return merge(server, owner, x0, z0, step, n, idx, base, ys, cnt, scanned, newIcons, heights, null, null);
    }

    public static long shipKey(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    static List<int[]> shipIcons(ServerLevel level, int[] box, Map<Long, String> labels) {
        List<int[]> out = new ArrayList<>();
        if (!SableBridge.active()) return out;
        net.minecraft.world.phys.AABB q = new net.minecraft.world.phys.AABB(box[0], level.getMinBuildHeight() - 512.0, box[1], box[2], level.getMaxBuildHeight() + 2048.0, box[3]);
        for (SableBridge.ShipInfo s : SableBridge.largeShipsIntersecting(level, q)) {
            if (out.size() >= MAX_SHIPS) break;
            int x = (int) Math.floor(s.worldCenter().x), z = (int) Math.floor(s.worldCenter().z);
            if (x < box[0] || x >= box[2] || z < box[1] || z >= box[3]) continue;
            String name = s.name() == null ? "?" : s.name().replace('|', '/');
            if (name.length() > 32) name = name.substring(0, 32);
            out.add(new int[] { x, z, ICON_AIRSHIP });
            labels.put(shipKey(x, z), name + "|" + s.blockCount());
        }
        return out;
    }

    static void imageShips(MinecraftServer server, UUID owner, int cx, int cz, int r) {
        if (server == null || owner == null || !SableBridge.active()) return;
        ReconData.Entry e = ReconData.get(server).entry(owner);
        if (e == null || !e.hasTarget) return;
        int step = Math.max(1, Balance.RECON_SNAPSHOT_STEP), n = samples(), half = n * step / 2, x0 = e.tx - half, z0 = e.tz - half;
        int[] box = { Math.max(x0, cx - r), Math.max(z0, cz - r), Math.min(x0 + n * step, cx + r + 1), Math.min(z0 + n * step, cz + r + 1) };
        if (box[0] >= box[2] || box[1] >= box[3]) return;
        Map<Long, String> labels = new HashMap<>();
        List<int[]> icons = shipIcons(server.overworld(), box, labels);
        boolean before = false;
        if (e.snap != null) {
            int[] oi = e.snap.icons();
            for (int i = 0; i + 2 < oi.length && !before; i += 3)
                before = oi[i + 2] == ICON_AIRSHIP && oi[i] >= box[0] && oi[i] < box[2] && oi[i + 1] >= box[1] && oi[i + 1] < box[3];
        }
        if (icons.isEmpty() && !before) return;
        merge(server, owner, x0, z0, step, n, new int[0], new byte[0], new int[0], 0, Set.of(), icons, null, box, labels);
    }

    static ReconData.Snapshot merge(MinecraftServer server, UUID owner, int x0, int z0, int step, int n, int[] idx, byte[] base, int[] ys, int cnt,
                                    Set<Long> scanned, List<int[]> newIcons, int[] heights, int[] shipBox, Map<Long, String> shipLabels) {
        ReconData d = ReconData.get(server);
        ReconData.Entry e = d.entry(owner);
        if (e == null || !e.hasTarget || e.tx - n * step / 2 != x0 || e.tz - n * step / 2 != z0) return null;
        ReconData.Snapshot old = e.snap;
        boolean same = old != null && old.sameArea(x0, z0, step, n);
        byte[] col = same ? old.col().clone() : new byte[n * n];

        short[] hts = same && old.hts() != null && old.hts().length == n * n ? old.hts().clone() : null;
        if (hts == null) { hts = new short[n * n]; java.util.Arrays.fill(hts, ReconData.NO_H); }
        for (int k = 0; k < cnt; k++) hts[idx[k]] = (short) Math.max(-32000, Math.min(32000, ys[k]));
        if (heights == null || heights.length != n * n) {
            heights = new int[n * n];
            for (int i = 0; i < n * n; i++) heights[i] = hts[i] == ReconData.NO_H ? Integer.MIN_VALUE : hts[i];
        }
        for (int k = 0; k < cnt; k++) heights[idx[k]] = ys[k];
        for (int k = 0; k < cnt; k++) {
            int i = idx[k];
            MapColor mc = MapColor.byId(base[k] & 0x3F);
            if (mc == MapColor.NONE) { col[i] = 0; continue; }
            int north = i >= n ? heights[i - n] : Integer.MIN_VALUE;
            MapColor.Brightness b = MapColor.Brightness.NORMAL;
            if (mc != MapColor.WATER && north != Integer.MIN_VALUE)
                b = ys[k] > north ? MapColor.Brightness.HIGH : ys[k] < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            col[i] = mc.getPackedId(b);
        }
        List<int[]> icons = new ArrayList<>();
        Set<Long> cells = new HashSet<>();
        for (int[] ic : newIcons) if (icons.size() < MAX_ICONS && cells.add(ChunkPos.asLong(ic[0] >> 3, ic[1] >> 3))) icons.add(ic);
        if (same) {
            int[] oi = old.icons();
            for (int i = 0; i + 2 < oi.length && icons.size() < MAX_ICONS; i += 3) {
                if (oi[i + 2] == ICON_AIRSHIP) {
                    if (shipBox != null && oi[i] >= shipBox[0] && oi[i] < shipBox[2] && oi[i + 1] >= shipBox[1] && oi[i + 1] < shipBox[3]) continue;
                } else if (scanned.contains(ChunkPos.asLong(oi[i] >> 4, oi[i + 1] >> 4))) continue;
                if (cells.add(ChunkPos.asLong(oi[i] >> 3, oi[i + 1] >> 3))) icons.add(new int[] { oi[i], oi[i + 1], oi[i + 2] });
            }
        }
        Map<Long, String> ships = new HashMap<>();
        for (int[] ic : icons) {
            if (ic[2] != ICON_AIRSHIP) continue;
            long k = shipKey(ic[0], ic[1]);
            String l = shipLabels != null ? shipLabels.get(k) : null;
            if (l == null && same) l = old.ships().get(k);
            if (l != null) ships.put(k, l);
        }
        int[] flat = new int[icons.size() * 3];
        for (int i = 0; i < icons.size(); i++) { flat[i * 3] = icons.get(i)[0]; flat[i * 3 + 1] = icons.get(i)[1]; flat[i * 3 + 2] = icons.get(i)[2]; }
        int seen = 0;
        for (byte b : col) if ((b & 0xFF) >= 4) seen++;
        int pct = (int) Math.round(100.0 * seen / (n * (double) n));
        e.snap = new ReconData.Snapshot(x0, z0, step, n, col, System.currentTimeMillis(), flat, pct, hts, ships);
        d.setDirty();
        CcBda.onMerge(server, owner, e.snap, idx, cnt);
        return e.snap;
    }

    private static final Map<UUID, long[]> LAST_ASK = new HashMap<>();

    static void answer(ServerPlayer sp, UUID owner, boolean withMap, boolean force) {
        MinecraftServer server = sp.server;
        long tick = server.getTickCount();
        long[] last = LAST_ASK.computeIfAbsent(sp.getUUID(), k -> new long[] { -1000, -1000 });
        int slot = withMap ? 0 : 1;
        if (!force && tick - last[slot] < (withMap ? 20 : 10)) return;
        last[slot] = tick;
        if (LAST_ASK.size() > 64) LAST_ASK.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);

        ReconData.Entry e = ReconData.get(server).entry(owner);
        long[] st = status(server, owner, e);
        long now = System.currentTimeMillis();
        ReconData.Snapshot s = e != null ? e.snap : null;
        int[] dots = new int[0], flights = new int[0];
        boolean drone = st[0] == STATUS_DRONE;
        if ((st[0] == STATUS_OVERHEAD || st[0] == STATUS_STATION || drone) && e != null) {
            List<Integer> l = new ArrayList<>();
            ServerLevel ow = server.overworld();
            for (ServerPlayer p : ow.players()) {
                if (l.size() >= MAX_DOTS * 2) break;
                if (p.isSpectator() || p.getUUID().equals(owner) || !(drone ? Drones.sees(server, owner, p.getX(), p.getZ()) : inArea(e, p.getX(), p.getZ()))) continue;
                if (!ow.canSeeSky(BlockPos.containing(p.getEyePosition()))) continue;
                l.add((int) Math.floor(p.getX())); l.add((int) Math.floor(p.getZ()));
            }
            dots = new int[l.size()];
            for (int i = 0; i < dots.length; i++) dots[i] = l.get(i);

            List<Integer> fl = new ArrayList<>();
            long gt = ow.getGameTime();
            for (Flight f : FlightManager.get(ow).flights()) {
                if (fl.size() >= MAX_FLIGHTS * 3) break;
                if (gt < f.start() || gt > f.endTime() || Hypersonic.hidden(f, gt)) continue;
                net.minecraft.world.phys.Vec3 v = f.position(gt);
                if (!(drone ? Drones.sees(server, owner, v.x, v.z) : inArea(e, v.x, v.z))) continue;
                fl.add((int) Math.floor(v.x)); fl.add((int) Math.floor(v.z)); fl.add(f.isOwnedBy(owner, null) ? 1 : 0);
            }
            flights = new int[fl.size()];
            for (int i = 0; i < flights.length; i++) flights[i] = fl.get(i);
        }
        boolean map = withMap && s != null;
        PacketDistributor.sendToPlayer(sp, new ReconMapPayload((int) st[0], (int) st[1], st[2],
                e != null && e.hasTarget, e != null ? e.tx : 0, e != null ? e.tz : 0,
                s != null ? s.timeMs() : 0L, s != null ? Math.max(0L, now - s.timeMs()) : 0L,
                map ? s.x0() : 0, map ? s.z0() : 0, map ? s.step() : 0, map ? s.n() : 0, map ? s.loadedPct() : 0,
                map ? s.col() : new byte[0], map ? s.icons() : new int[0], dots, flights, map, ReconFill.state(owner), map ? s.hts() : null,
                map ? s.ships() : Map.of()));
    }

    static void clearRuntime(MinecraftServer server) { JOBS.clear(); ALERTED.clear(); LAST_ASK.clear(); ReconFill.clear(server); }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { clearRuntime(event.getServer()); }

    static void cancel(UUID owner) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) if (it.next().owner.equals(owner)) it.remove();
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) ReconFill.forget(server, owner);
    }
}
