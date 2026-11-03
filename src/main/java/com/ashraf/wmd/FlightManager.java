package com.ashraf.wmd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public class FlightManager extends SavedData {

    private static final String NAME = "wmd_flights";
    private static final int PREPARE_TICKS = 600;
    private static final int RELEASE_DELAY = 200;

    private final List<Flight> flights = new ArrayList<>();
    final List<Crater> craters = new ArrayList<>();

    final List<WarheadFx.Task> tasks = new ArrayList<>();

    final Map<Long, Integer> counters = new HashMap<>();
    public int nextCounter;

    public int nextFlags;

    final Map<Long, Integer> mirvSpread = new HashMap<>();
    public int nextMirv;
    public int counterOf(long flightId) { return counters.getOrDefault(flightId, 0); }

    final Map<Long, String> uplinkLocks = new HashMap<>();

    final Map<Long, java.util.UUID> shipLocks = new HashMap<>();

    final Map<Long, Integer> asatTargets = new HashMap<>();

    private final Map<Long, Long> forced = new HashMap<>();

    static final int MAX_HOLD = 6000, HOLD_REFRESH = 100;

    static final int IMPACT_WAIT = 100;

    private boolean ticketsApplied, legacyForced;
    private long nextId = 1;

    final List<Interceptors.Shot> shots = new ArrayList<>();
    private long nextShot = 1;

    public static FlightManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FlightManager::new, FlightManager::load, null), NAME);
    }

    public List<Flight> flights() { return flights; }

    void syncSoon() { syncSoon = true; }

    public Flight find(long id) {
        for (Flight f : flights) if (f.id() == id) return f;
        return null;
    }

    public long nextShotId() { return nextShot++; }

    public void addShot(Interceptors.Shot s) { shots.add(s); }

    private final Map<Long, Float> damage = new HashMap<>();

    public boolean damage(ServerLevel level, Flight f, float amount) {
        float total = damage.merge(f.id(), amount, Float::sum);
        if (total >= MissileStats.health(f.build())) { damage.remove(f.id()); kill(level, f); return true; }
        Wreckage.hitSparks(level, f.position(level.getGameTime()));
        return false;
    }

    public float damageFraction(Flight f) { return Math.min(1.0F, damage.getOrDefault(f.id(), 0.0F) / Math.max(1, MissileStats.health(f.build()))); }

    void kill(ServerLevel level, Flight f) {
        if (!flights.remove(f)) return;
        long gt = level.getGameTime();
        Vec3 wp = f.position(gt);
        Wreckage.spawn(level, wp, wp.subtract(f.position(gt - 1)), f.tier());
        MusicTriggersHook.flight(level, "intercepted", f, net.minecraft.core.BlockPos.containing(f.position(level.getGameTime())));
        CcBattleLog.onKilled(level, f);
        StrikeToasts.flightDone(level, f, false);
        Evade.remove(f.id());
        uplinkLocks.remove(f.id()); shipLocks.remove(f.id()); counters.remove(f.id());
        asatTargets.remove(f.id());
        if (f.prepared()) {
            WarheadType wt = WarheadType.of(f.build().warheadType());
            release(level, f.tx(), f.tz(), WarheadFx.holdRadius(wt.tier, wt), level.getGameTime() + RELEASE_DELAY);
        }
        Wmd.LOGGER.info("Missile shot down: {} | tier {} | {}", f.launcher(), f.tier(), f.build().warheadType());
        setDirty();
        syncSoon = true;
    }

    public boolean chaffHides(double rx, double rz, double px, double pz) {
        return !tasks.isEmpty() && (WarheadFx.inChaff(this, rx, rz) || WarheadFx.inChaff(this, px, pz));
    }

    public void launch(ServerLevel level, int tier, Vec3 from, int tx, int ty, int tz, String launcher, java.util.UUID launcherId) {
        launch(level, tier, from, tx, ty, tz, launcher, launcherId, 0.0, MissileBuild.preset(tier));
    }

    public void launch(ServerLevel level, int tier, Vec3 from, int tx, int ty, int tz, String launcher, java.util.UUID launcherId, double missRadius,
                       MissileBuild build) {
        launch(level, tier, from, tx, ty, tz, launcher, launcherId, missRadius, build, false, 0, 0);
    }

    public long launch(ServerLevel level, int tier, Vec3 from, int tx, int ty, int tz, String launcher, java.util.UUID launcherId, double missRadius,
                       MissileBuild build, boolean hasDecoy, int decoyX, int decoyZ) {
        HeatLog.fired(level, net.minecraft.core.BlockPos.containing(from), launcherId);
        if (missRadius > 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0, r = missRadius * level.random.nextDouble();
            tx += (int) Math.round(Math.cos(a) * r);
            tz += (int) Math.round(Math.sin(a) * r);
        }
        double dx = tx + 0.5 - from.x, dz = tz + 0.5 - from.z;
        int duration = Flight.totalTicks(tier, Math.sqrt(dx * dx + dz * dz), from.y, ty, MissileStats.speed(build), build.hypersonic() ? Balance.HYPER_APEX : 1.0);
        boolean divert = hasDecoy && build.divert();
        if (nextCounter > 0) { counters.put(nextId, nextCounter); nextCounter = 0; }
        Flight nf = new Flight(nextId++, tier, from.x, from.y, from.z, tx, tz, ty, ty, level.getGameTime(), duration, launcher, launcherId == null ? "" : launcherId.toString(), false, build,
                divert ? decoyX : tx, divert ? decoyZ : tz, -1L);
        if (nextFlags != 0) { nf = nf.withFlags(nf.uflags() | nextFlags); nextFlags = 0; }
        if (nextMirv > 0) { if (build.isMirv()) mirvSpread.put(nf.id(), nextMirv); nextMirv = 0; }
        flights.add(divert ? nf.withSwerve() : nf);
        if (!build.hypersonic()) Recon.onLaunch(level, flights.get(flights.size() - 1));
        SatDefence.onLaunch(level, flights.get(flights.size() - 1));
        CcBattleLog.onLaunch(level, flights.get(flights.size() - 1));
        MusicTriggersHook.launch(level, flights.get(flights.size() - 1));
        StrikeToasts.flightOut(level, flights.get(flights.size() - 1));
        setDirty();
        syncSoon = true;
        return nf.id();
    }

    public long launchCruise(ServerLevel level, Vec3 from, CruiseRoute route, int groundY, String launcher, java.util.UUID launcherId, double missRadius, MissileBuild build) {
        HeatLog.fired(level, net.minecraft.core.BlockPos.containing(from), launcherId);
        int n = route.xs.length;
        int tx = route.xs[n - 1], tz = route.zs[n - 1];
        if (missRadius > 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0, r = missRadius * level.random.nextDouble();
            tx += (int) Math.round(Math.cos(a) * r);
            tz += (int) Math.round(Math.sin(a) * r);
        }
        CruiseRoute r = route.withEnd(tx, tz);
        int duration = r.ticks();
        Flight nf = new Flight(nextId++, Math.max(1, Math.min(3, build.calibre())), from.x, from.y, from.z, tx, tz, groundY, groundY, level.getGameTime(), duration,
                launcher, launcherId == null ? "" : launcherId.toString(), false, build, tx, tz, -1L, tx, tz, duration, java.util.List.of(), 0, r);
        flights.add(nf);
        Recon.onLaunch(level, nf);
        CcBattleLog.onLaunch(level, nf);
        MusicTriggersHook.launch(level, nf);
        StrikeToasts.flightOut(level, nf);
        setDirty();
        syncSoon = true;
        return nf.id();
    }

    Flight releg(ServerLevel level, Flight f, int nx, int nz, double k, int set, int clear) {
        int i = -1;
        for (int j = 0; j < flights.size(); j++) if (flights.get(j).id() == f.id()) { i = j; break; }
        if (i < 0) return null;
        long now = level.getGameTime();
        Flight old = flights.get(i);
        Flight nf = old.withFlags((old.uflags() & ~clear) | set).withLeg(now, nx, nz, k, 0);
        if (old.prepared()) {
            WarheadType wt = WarheadType.of(old.build().warheadType());
            release(level, old.tx(), old.tz(), WarheadFx.holdRadius(wt.tier, wt), now + RELEASE_DELAY);

        }
        if (nf.endTime() - now <= PREPARE_TICKS) nf = prepare(level, nf);
        flights.set(i, nf);
        setDirty();
        syncSoon = true;
        return nf;
    }

    private boolean mirvSplit(ServerLevel level, long now) {
        boolean any = false;
        int n0 = flights.size();
        for (int i = 0; i < n0; i++) {
            Flight f = flights.get(i);
            if (!f.build().isMirv() || f.isCruise() || !f.inDive(now)) continue;
            java.util.List<String> ws = f.build().mirvWarheads();
            int spread = mirvSpread.getOrDefault(f.id(), Balance.MIRV_SPREAD_DEFAULT);
            mirvSpread.remove(f.id());
            double ax = f.tx() + 0.5 - f.ox(), az = f.tz() + 0.5 - f.oz(), len = Math.max(1.0, Math.sqrt(ax * ax + az * az)), px = -az / len, pz = ax / len;
            int[][] aims = { { f.tx(), f.tz() }, { (int) Math.round(f.tx() + px * spread), (int) Math.round(f.tz() + pz * spread) },
                    { (int) Math.round(f.tx() - px * spread), (int) Math.round(f.tz() - pz * spread) } };
            if (f.prepared()) {
                WarheadType wt = WarheadType.of(f.build().warheadType());
                release(level, f.tx(), f.tz(), WarheadFx.holdRadius(wt.tier, wt), now + RELEASE_DELAY);
            }
            if (ws.isEmpty()) ws = java.util.List.of("he");
            uplinkLocks.remove(f.id()); shipLocks.remove(f.id());
            for (int k = 0; k < ws.size() && k < aims.length; k++) {
                String w = Config.Server.warheadAllowed(ws.get(k)) ? ws.get(k) : "he";
                Flight base = k == 0 ? f : f.withId(nextId++);
                Flight nf = base.withBuild(MissileBuild.reentry(w)).withLeg(now, aims[k][0], aims[k][1], 1.0, 0);
                if (nf.endTime() - now <= PREPARE_TICKS) nf = prepare(level, nf);
                if (k == 0) flights.set(i, nf); else flights.add(nf);
            }
            ServerPlayer owner = f.ownerId().isEmpty() ? null : level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(f.ownerId()));
            if (owner != null) owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.mirv.split", Math.min(ws.size(), aims.length), spread).withStyle(net.minecraft.ChatFormatting.AQUA), false);
            Vec3 sp = f.position(now);
            level.playSound(null, sp.x, sp.y, sp.z, WmdSounds.MV_SPLIT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 16.0F, 1.0F);
            Wmd.LOGGER.info("[mirv] flight {} split into {} re-entry vehicles ({}), spread {}", f.id(), Math.min(ws.size(), aims.length), ws, spread);
            any = true;
        }
        if (any) { setDirty(); syncSoon = true; }
        return any;
    }

    static boolean alive(net.minecraft.server.MinecraftServer server, long id) {
        for (ServerLevel l : server.getAllLevels()) {
            FlightManager m = l.getDataStorage().get(new SavedData.Factory<>(FlightManager::new, FlightManager::load, null), NAME);
            if (m != null && m.find(id) != null) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FlightManager m = level.getDataStorage().get(new SavedData.Factory<>(FlightManager::new, FlightManager::load, null), NAME);
            if (m != null) m.tick(level);
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = false;
        if (!ticketsApplied) applyTickets(level, now);
        Interceptors.tick(level, this, now);
        Uplink.tick(level, this, now);
        Hypersonic.tick(level, this, now);
        SeekerGuidance.tick(level, this, now);
        TvGuidance.tick(level, this, now);
        MusicTriggersHook.tickFlights(level, flights, now);

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!Satellites.isSatellite(f.build()) || now < Satellites.insertTick(f)) continue;
            it.remove();
            Evade.remove(f.id()); counters.remove(f.id());
            Satellites.insert(level, f);
            changed = true;
        }

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!Crew.isCrew(f.build()) || now < Satellites.insertTick(f)) continue;
            it.remove();
            Evade.remove(f.id()); counters.remove(f.id());
            Crew.arrive(level, f);
            changed = true;
        }
        Crew.tick(level, flights);
        changed |= mirvSplit(level, now);

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!StationKit.isKit(f.build()) || now < Satellites.insertTick(f)) continue;
            it.remove();
            Evade.remove(f.id()); counters.remove(f.id());
            StationKit.arrive(level, f);
            changed = true;
        }

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!Cargo.isCargo(f.build()) || now < Satellites.insertTick(f)) continue;
            it.remove();
            Evade.remove(f.id()); counters.remove(f.id());
            Cargo.arrive(level, f);
            changed = true;
        }
        Cargo.tick(level, flights);

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!Cubesat.isCubesat(f.build()) || now < Satellites.insertTick(f)) continue;
            it.remove();
            Evade.remove(f.id());
            uplinkLocks.remove(f.id()); shipLocks.remove(f.id()); counters.remove(f.id()); counters.remove(f.id());
            Cubesat.insert(level, f);
            changed = true;
        }

        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            if (!Asat.isAsat(f.build()) || now < Asat.resolveTick(f)) continue;
            it.remove();
            Evade.remove(f.id()); counters.remove(f.id());
            Asat.resolve(level, f, asatTargets.remove(f.id()));
            changed = true;
        }

        for (ProjectilePath.FlightHit h : ProjectilePath.flights(level, flights, now)) { if (endEarly(level, h.flight(), h.hit())) changed = true; }

        if (!ForcefieldBlockEntity.LOADED.isEmpty()) for (Flight f : new ArrayList<>(flights)) {
            MissileBuild bld = f.build();
            if (bld == null || bld.isInterceptor() || Satellites.toOrbit(bld) || Asat.isAsat(bld)) continue;
            Forcefield.Crossing c = Forcefield.crossing(level, f.position(now - 1), f.position(now));
            if (c == null) continue;
            WarheadType wt = WarheadType.of(bld.warheadType());
            if (Forcefield.shellHit(level, c, wt.tier, wt)) { kill(level, f); changed = true; }
        }
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            if (!f.prepared() && !Satellites.toOrbit(f.build()) && !Asat.isAsat(f.build()) && f.endTime() - now <= PREPARE_TICKS) {
                Flight p = prepare(level, f);
                if (p != f) { flights.set(i, p); changed = true; }
            } else if (f.prepared() && now % 20 == 0 && ramps.containsKey(f.id()) && Config.Server.chunkLoading())
                hold(level, f.tx(), f.tz(), holdFor(level, f, now));
        }
        if (now % 200 == 0) ramps.keySet().removeIf(id -> find(id) == null);
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            if (now < f.endTime()) continue;
            if (Satellites.toOrbit(f.build()) || Asat.isAsat(f.build())) {
                flights.remove(i--);
                Evade.remove(f.id()); asatTargets.remove(f.id()); counters.remove(f.id()); changed = true; continue;
            }
            if (!f.prepared()) {
                f = prepare(level, f);

                if (!f.prepared() && Config.Server.chunkLoading() && now < f.endTime() + IMPACT_WAIT) { flights.set(i, f); continue; }
                if (!f.prepared()) f = f.withEndY(f.endY());
            }
            flights.remove(i--);
            impact(level, f);
            changed = true;
        }
        WarheadFx.tick(level, this, now);
        for (int i = 0; i < craters.size(); i++) {
            Crater c = craters.get(i);
            long ct0 = ImpactProfiler.start();
            c.step(level);
            ImpactProfiler.add(ImpactProfiler.CRATER, ct0);
            if (c.isDone()) {
                craters.remove(i--);
                release(level, c.cx, c.cz, c.hold, now + RELEASE_DELAY);
            }
            setDirty();
        }
        if (now % HOLD_REFRESH == 0) refreshHolds(level);
        if (!forced.isEmpty()) {
            boolean off = !Config.Server.chunkLoading();

            int let = 0;
            for (Iterator<Map.Entry<Long, Long>> it = forced.entrySet().iterator(); it.hasNext() && (off || let < Balance.HOLD_RELEASE_PER_TICK); ) {
                Map.Entry<Long, Long> e = it.next();
                if (off || e.getValue() <= now) {
                    let++;
                    ChunkPos cp = new ChunkPos(e.getKey());
                    ImpactChunks.force(level, cp.x, cp.z, false);
                    it.remove();
                    setDirty();
                }
            }
        }
        if (changed) {
            setDirty();
            syncSoon = true;
        }

        if (syncSoon || now % 20 == 0 || FlightIntel.swerving(flights, now)) {
            syncSoon = false;
            intel.sync(level, flights, now);
        } else intel.tickCruise(flights, level, now);
    }

    private void refreshHolds(ServerLevel level) {
        if (!Config.Server.chunkLoading()) return;
        for (Flight f : flights) {
            if (!f.prepared() || Satellites.toOrbit(f.build()) || Asat.isAsat(f.build())) continue;
            WarheadType wt = WarheadType.of(f.build().warheadType());
            hold(level, f.tx(), f.tz(), holdFor(level, f, level.getGameTime()));
        }
        for (Crater c : craters) hold(level, c.cx, c.cz, c.hold);
    }

    private void applyTickets(ServerLevel level, long now) {
        ticketsApplied = true;
        if (forced.isEmpty()) { legacyForced = false; return; }
        boolean on = Config.Server.chunkLoading();
        int moved = 0;
        for (Iterator<Map.Entry<Long, Long>> it = forced.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> e = it.next();
            ChunkPos cp = new ChunkPos(e.getKey());
            if (legacyForced && level.getForcedChunks().contains(e.getKey().longValue())) { level.setChunkForced(cp.x, cp.z, false); moved++; }
            if (!on) { it.remove(); continue; }
            if (e.getValue() > now + MAX_HOLD) e.setValue(now + MAX_HOLD);
            ImpactChunks.force(level, cp.x, cp.z, true);
        }
        if (moved > 0) Wmd.LOGGER.info("WMD: moved {} impact chunk(s) in {} from the vanilla forced list to WMD's own tickets", moved, level.dimension().location());
        legacyForced = false;
        setDirty();
    }

    private final Map<Long, double[]> ramps = new HashMap<>();

    private double holdFor(ServerLevel level, Flight f, long now) {
        WarheadType wt = WarheadType.of(f.build().warheadType());
        double full = WarheadFx.holdRadius(wt.tier, wt);
        if (full <= Balance.BIG_HOLD_R) return full;
        double[] r = ramps.get(f.id());
        if (r == null || r[0] != f.tx() || r[1] != f.tz()) { r = new double[] { f.tx(), f.tz(), Math.min(full, Balance.RG_HOLD_RAMP_START), now }; ramps.put(f.id(), r); }
        if (r[2] < full && now - r[3] >= 20) {
            r[3] = now;
            double mspt = level.getServer().getAverageTickTimeNanos() / 1.0E6;
            if (mspt < Balance.RAMP_MSPT && loadedRing(level, f.tx(), f.tz(), r[2] * 0.92) >= Balance.RAMP_LOADED)
                r[2] = Math.min(full, Math.sqrt(r[2] * r[2] + Balance.RAMP_STEP_CHUNKS * 256.0 / Math.PI));
        }
        return r[2];
    }

    private static double loadedRing(ServerLevel level, int x, int z, double r) {
        int n = 0;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            if (level.hasChunk((int) Math.floor(x + Math.cos(a) * r) >> 4, (int) Math.floor(z + Math.sin(a) * r) >> 4)) n++;
        }
        return n / 24.0;
    }

    boolean holds(long chunk) { return forced.containsKey(chunk); }

    private Flight prepare(ServerLevel level, Flight f) {
        double r = holdFor(level, f, level.getGameTime());
        hold(level, f.tx(), f.tz(), r);
        if (!level.hasChunk(f.tx() >> 4, f.tz() >> 4)) return f;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.tx(), f.tz());
        double shipY = shipEndY(level, f);
        return f.withEndY(Double.isNaN(shipY) ? ground : Math.max(ground, shipY));
    }

    double shipEndY(ServerLevel level, Flight f) {
        java.util.UUID ship = shipLocks.get(f.id());
        SableBridge.ShipInfo s = ship == null ? null : SableBridge.shipInfo(level, ship);
        return s == null ? Double.NaN : s.worldBounds().minY;
    }

    void shipEnd(ServerLevel level, Flight f) {
        double y = shipEndY(level, f);
        if (Double.isNaN(y) || !f.prepared()) return;
        for (int j = 0; j < flights.size(); j++) if (flights.get(j).id() == f.id()) { flights.set(j, flights.get(j).withEndY(y)); setDirty(); syncSoon = true; return; }
    }

    void holdForTest(ServerLevel level, int x, int z, double r) { hold(level, x, z, r); setDirty(); }

    private void hold(ServerLevel level, int x, int z, double r) {
        if (!Config.Server.chunkLoading()) return;
        long until = level.getGameTime() + MAX_HOLD;
        int c0x = (int) Math.floor((x - r) / 16), c1x = (int) Math.floor((x + r) / 16);
        int c0z = (int) Math.floor((z - r) / 16), c1z = (int) Math.floor((z + r) / 16);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                Long old = forced.get(key);
                if (old == null) ImpactChunks.force(level, cx, cz, true);
                if (old == null || old < until) forced.put(key, until);
            }
        }
        setDirty();
    }

    void release(ServerLevel level, int x, int z, double r, long when) {
        int c0x = (int) Math.floor((x - r) / 16), c1x = (int) Math.floor((x + r) / 16);
        int c0z = (int) Math.floor((z - r) / 16), c1z = (int) Math.floor((z + r) / 16);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                long key = ChunkPos.asLong(cx, cz);

                boolean needed = false;
                for (Crater c : craters) {
                    double rr = c.hold;
                    if (cx * 16 + 16 >= c.cx - rr && cx * 16 <= c.cx + rr && cz * 16 + 16 >= c.cz - rr && cz * 16 <= c.cz + rr) needed = true;
                }
                if (!needed && forced.containsKey(key)) forced.put(key, when);
            }
        }
    }

    private void impact(ServerLevel level, Flight f) {
        Evade.remove(f.id());
        uplinkLocks.remove(f.id()); shipLocks.remove(f.id()); counters.remove(f.id());
        WarheadType type = WarheadType.of(f.build().warheadType());
        int gy = (int) Math.floor(f.endY());

        double full = WarheadFx.holdRadius(type.tier, type);
        if (full > Balance.BIG_HOLD_R) hold(level, f.tx(), f.tz(), full);
        java.util.UUID prev = ShipStrike.begin(CcBattleLog.ownerOf(level.getServer(), f));
        try {
            if ((f.uflags() & Flight.U_AIRBURST) != 0 && WarheadFx.airburstable(type)) WarheadFx.airburst(level, this, f.tx(), gy, f.tz(), type.tier, type, f.build().refined());
            else WarheadFx.detonate(level, this, f.tx(), gy, f.tz(), type.tier, type, f.tx() + 0.5 - f.ox(), f.tz() + 0.5 - f.oz(), f.build().refined());
        }
        finally { ShipStrike.end(prev); }
        BaySabotage.check(level, this, f.tx(), gy, f.tz(), type);
        CookOff.check(level, this, f.tx(), gy, f.tz(), type);
        CcBattleLog.onImpact(level, f, gy);
        CcBda.onImpact(level, f, gy);
        MusicTriggersHook.flight(level, "impact", f, new net.minecraft.core.BlockPos(f.tx(), gy, f.tz()));
        StrikeToasts.flightDone(level, f, true);

        String line = String.format("%s | IMPACT | %s | tier %d | %s | at %d %d %d | flight %d s%n", Instant.now(), f.launcher(), f.tier(), type.id,
                f.tx(), gy, f.tz(), f.duration() / 20);
        try {
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("wmd_launch_log.txt");
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Wmd.LOGGER.warn("Could not write launch log", e);
        }
        Wmd.LOGGER.info("Missile impact: {}", line.trim());
        setDirty();
    }

    boolean endEarly(ServerLevel level, Flight f, ProjectilePath.PathHit hit) {
        int i = -1;
        for (int j = 0; j < flights.size(); j++) if (flights.get(j).id() == f.id()) { i = j; break; }
        if (i < 0) return false;
        f = flights.remove(i);
        long now = level.getGameTime();
        Evade.remove(f.id());
        uplinkLocks.remove(f.id()); shipLocks.remove(f.id()); counters.remove(f.id());
        asatTargets.remove(f.id());
        damage.remove(f.id());
        WarheadType type = WarheadType.of(f.build().warheadType());
        if (f.prepared()) release(level, f.tx(), f.tz(), WarheadFx.holdRadius(type.tier, type), now + RELEASE_DELAY);
        Vec3 p = hit.pos();
        Vec3 dir = f.position(now).subtract(f.position(now - 1));
        if (dir.lengthSqr() > 1.0E-6) p = p.subtract(dir.normalize().scale(0.25));
        int gx = (int) Math.floor(p.x), gy = (int) Math.floor(p.y), gz = (int) Math.floor(p.z);
        java.util.UUID owner = CcBattleLog.ownerOf(level.getServer(), f);
        ProjectilePath.log("missile #" + f.id() + " (T" + f.tier() + " " + type.id + (f.isCruise() ? ", cruise" : "") + ")", hit);
        double hx = Math.abs(dir.x) + Math.abs(dir.z) > 1.0E-6 ? dir.x : f.tx() + 0.5 - f.ox(), hz = Math.abs(dir.x) + Math.abs(dir.z) > 1.0E-6 ? dir.z : f.tz() + 0.5 - f.oz();
        java.util.UUID prev = ShipStrike.begin(owner);
        try { WarheadFx.detonate(level, this, gx, gy, gz, type.tier, type, hx, hz, f.build().refined()); }
        finally { ShipStrike.end(prev); }
        if (type == WarheadType.CLUSTER) {
            double cr = Missiles.craterRadius(type.tier) * 0.28;
            ShipStrike.blast(level, p, cr, ShipStrike.powerFor(cr), owner);
        }
        BaySabotage.check(level, this, gx, gy, gz, type);
        CookOff.check(level, this, gx, gy, gz, type);
        if (owner != null && ArtilleryManager.shipLogOk(owner, hit.shipId(), now))
            CcBattleLog.add(level.getServer(), owner, CcBattleLog.HIT, "log.wmd.ship_struck", hit.shipName(), Integer.toString(f.tier()),
                    f.build().warheadType().isEmpty() ? "?" : "#warhead.wmd." + f.build().warheadType(), Integer.toString(gx), Integer.toString(gy), Integer.toString(gz));
        StrikeToasts.flightDoneAt(level, f, true, p.x, p.z);
        String line = String.format("%s | IMPACT (SHIP %s) | %s | tier %d | %s | at %d %d %d | flight %d s%n", Instant.now(), hit.shipName(), f.launcher(), f.tier(), type.id,
                gx, gy, gz, (now - f.start()) / 20);
        try {
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("wmd_launch_log.txt");
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Wmd.LOGGER.warn("Could not write launch log", e);
        }
        setDirty();
        syncSoon = true;
        return true;
    }

    private boolean syncSoon;

    private final FlightIntel intel = new FlightIntel();

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    private static void sendTo(ServerPlayer p) {
        FlightManager m = get(p.serverLevel());
        m.intel.sendFull(p, m.flights, p.serverLevel().getGameTime());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag fl = new ListTag();
        for (Flight f : flights) fl.add(f.save());
        tag.put("flights", fl);
        ListTag cr = new ListTag();
        for (Crater c : craters) cr.add(c.save());
        tag.put("craters", cr);
        tag.put("tasks", WarheadFx.saveAll(tasks));
        long[] keys = new long[forced.size()], when = new long[forced.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : forced.entrySet()) { keys[i] = e.getKey(); when[i] = e.getValue(); i++; }
        tag.putLongArray("forced", keys);
        tag.putLongArray("forcedUntil", when);
        tag.putLong("nextId", nextId);
        tag.putBoolean("ticketsV2", true);
        tag.put("evades", Evade.save(flights));
        long[] ck = new long[counters.size()], cv = new long[counters.size()];
        int ci = 0;
        for (Map.Entry<Long, Integer> e : counters.entrySet()) { ck[ci] = e.getKey(); cv[ci] = e.getValue(); ci++; }
        tag.putLongArray("counterKeys", ck);
        tag.putLongArray("counterVals", cv);
        ListTag ul = new ListTag();
        for (Map.Entry<Long, String> e : uplinkLocks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", e.getKey()); c.putString("name", e.getValue());
            ul.add(c);
        }
        tag.put("uplinkLocks", ul);
        long[] ak = new long[asatTargets.size()], av = new long[asatTargets.size()];
        int ai = 0;
        for (Map.Entry<Long, Integer> e : asatTargets.entrySet()) { ak[ai] = e.getKey(); av[ai] = e.getValue(); ai++; }
        tag.putLongArray("asatKeys", ak);
        tag.putLongArray("asatVals", av);
        return tag;
    }

    public static FlightManager load(CompoundTag tag, HolderLookup.Provider registries) {
        FlightManager m = new FlightManager();
        for (Tag t : tag.getList("flights", Tag.TAG_COMPOUND)) {
            try { m.flights.add(Flight.load((CompoundTag) t)); } catch (RuntimeException e) { Wmd.LOGGER.warn("[flights] skipped an unreadable flight in the save: {}", e.toString()); }
        }
        for (Tag t : tag.getList("craters", Tag.TAG_COMPOUND)) {
            try { m.craters.add(Crater.load((CompoundTag) t)); } catch (RuntimeException e) { Wmd.LOGGER.warn("[flights] skipped an unreadable crater in the save: {}", e.toString()); }
        }
        WarheadFx.loadAll(tag.getList("tasks", Tag.TAG_COMPOUND), m.tasks);
        long[] keys = tag.getLongArray("forced"), when = tag.getLongArray("forcedUntil");
        for (int i = 0; i < Math.min(keys.length, when.length); i++) m.forced.put(keys[i], when[i]);
        m.legacyForced = !tag.getBoolean("ticketsV2");
        m.nextId = Math.max(1, tag.getLong("nextId"));
        Evade.load(tag.getList("evades", Tag.TAG_COMPOUND));
        long[] ck = tag.getLongArray("counterKeys"), cv = tag.getLongArray("counterVals");
        for (int i = 0; i < Math.min(ck.length, cv.length); i++) m.counters.put(ck[i], (int) cv[i]);
        for (Tag t : tag.getList("uplinkLocks", Tag.TAG_COMPOUND)) { try { CompoundTag c = (CompoundTag) t; m.uplinkLocks.put(c.getLong("id"), c.getString("name")); } catch (RuntimeException e) { Wmd.LOGGER.warn("[flights] skipped an unreadable uplink lock: {}", e.toString()); } }
        long[] ak = tag.getLongArray("asatKeys"), av = tag.getLongArray("asatVals");
        for (int i = 0; i < Math.min(ak.length, av.length); i++) m.asatTargets.put(ak[i], (int) av[i]);
        return m;
    }
}
