package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirMissiles {

    private AirMissiles() {}

    public static final int HEAT = 0, RADAR = 1, ARM = 2, AGM = 3;
    public static final String[] NAMES = { "heat", "radar", "arm", "agm" };

    static final String HIT = "hit", MISS = "self-destruct", DUD = "hit the ground", LOST = "lost the lock", DECOYED = "decoyed", GONE = "target gone";

    static boolean warns(int kind) { return kind == RADAR; }

    public static int kindOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    public interface LockSource {

        boolean holds(ServerLevel level, Shot s, Vec3 targetPos, long now);

        Vec3 at(ServerLevel level);
        UUID owner();
        String label();
    }

    static final class PlayerLock implements LockSource {
        final UUID player;
        final ServerLevel level;
        PlayerLock(ServerPlayer p) { player = p.getUUID(); level = p.serverLevel(); }
        private ServerPlayer p() { ServerPlayer sp = level.getServer().getPlayerList().getPlayer(player); return sp != null && sp.serverLevel() == level && sp.isAlive() ? sp : null; }
        @Override public boolean holds(ServerLevel l, Shot s, Vec3 t, long now) {
            ServerPlayer sp = p();
            return sp != null && t != null && lockable(l, sp.getEyePosition(), sp.getLookAngle(), t, s.targetBlocks(), s.targetShip());
        }
        @Override public Vec3 at(ServerLevel l) { ServerPlayer sp = p(); return sp == null ? null : sp.getEyePosition(); }
        @Override public UUID owner() { return player; }
        @Override public String label() { return "nose"; }
    }

    static boolean lockable(ServerLevel level, Vec3 eye, Vec3 look, Vec3 target, int blocks) { return lockable(level, eye, look, target, blocks, null); }

    static boolean lockable(ServerLevel level, Vec3 eye, Vec3 look, Vec3 target, int blocks, UUID targetShip) {
        Vec3 d = target.subtract(eye);
        double dist = d.length();
        if (dist > AirJam.lockRange(level, targetShip, Balance.AIR_NOSE_LOCK_RANGE * (blocks > 0 ? AirPicture.sizeFactor(blocks) : 1.0))) return false;
        if (dist > 1.0 && Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, d.scale(1.0 / dist).dot(look.normalize()))))) > Balance.AIR_NOSE_GIMBAL) return false;
        return !FlightManager.get(level).chaffHides(eye.x, eye.z, target.x, target.z);
    }

    public static final class Shot {
        final long id;
        final int kind;
        final UUID owner;
        final ResourceKey<Level> dim;
        final long launch;
        final UUID ownShip;
        final UUID ship;
        final UUID entity;
        final BlockPos emitter;
        final LockSource lock;
        final UUID devPlayer;
        final String targetName;
        Vec3 pos, vel;
        Vec3 aim, aimVel = Vec3.ZERO, lastSeen;
        double flown, speed, best = Double.MAX_VALUE;
        final double launchSpeed;
        boolean pitbull, lockLost, armQuiet;
        Vec3 decoy;
        long decoyAt;
        int blocks;
        int[] plot;
        long plotAt = Long.MIN_VALUE;
        String end;
        int laser = -1;
        Vec3 gpsAim;
        final int drop;
        final boolean refined;

        Shot(long id, int kind, UUID owner, ResourceKey<Level> dim, long launch, UUID ownShip, UUID ship, UUID entity, BlockPos emitter, LockSource lock,
             UUID devPlayer, String targetName, Vec3 pos, Vec3 vel, Vec3 aim) {
            this.id = id; this.kind = kind; this.owner = owner; this.dim = dim; this.launch = launch; this.ownShip = ownShip; this.ship = ship;
            this.entity = entity; this.emitter = emitter; this.lock = lock; this.devPlayer = devPlayer; this.targetName = targetName;
            this.pos = pos; this.vel = vel; this.aim = aim; this.lastSeen = aim; this.speed = vel.length(); this.launchSpeed = this.speed;
            this.refined = nextRefined;
            this.drop = nextDrop;
        }
        public long id() { return id; }
        public int kind() { return kind; }
        public UUID targetShip() { return ship; }
        public int targetBlocks() { return blocks; }
        public boolean pitbull() { return pitbull; }
        public Vec3 position() { return pos; }
    }

    private static final List<Shot> SHOTS = Statics.server(new ArrayList<>());

    private static final Map<UUID, Long> LAST_DECOY = Statics.server(new HashMap<>());
    private static boolean warned;

    public static List<Shot> inFlight(ServerLevel level) {
        List<Shot> out = new ArrayList<>();
        for (Shot s : SHOTS) if (s.dim == level.dimension()) out.add(s);
        return out;
    }

    private static boolean nextRefined;

    private static int nextDrop;

    public static String launchStyle(int drop, java.util.function.Supplier<String> call) {
        nextDrop = Math.max(0, drop);
        try { return call.get(); } finally { nextDrop = 0; }
    }

    public static String refinedShot(boolean refined, java.util.function.Supplier<String> call) {
        nextRefined = refined;
        try { return call.get(); } finally { nextRefined = false; }
    }

    static double refinedFactor(boolean refined) { return refined ? 1.0 + Balance.AAM_REFINED_BONUS : 1.0; }

    public static String fire(ServerLevel level, int kind, UUID owner, Vec3 from, Vec3 dir, Vec3 carrierVel, UUID ownShip,
                              UUID ship, UUID entity, BlockPos emitter, LockSource lock, UUID devPlayer, String targetName) {
        if (!AirGate.on()) return "1.2 air battles are off (AirGate)";
        if (kind < 0 || kind > 2) return "unknown seeker";
        if (tooMany(level, owner)) return "too many missiles in the air";
        Vec3 t = targetPoint(level, ship, entity, emitter);
        if (t == null) return "no target";
        if (from.distanceTo(t) > Balance.AAM_RANGE[kind]) return "out of range (" + (int) from.distanceTo(t) + " > " + (int) Balance.AAM_RANGE[kind] + ")";
        if (kind == RADAR && lock == null) return "a radar missile needs a launcher that holds a lock";
        Vec3 v = dir.normalize().scale(Balance.AAM_LAUNCH_SPEED).add(carrierVel == null ? Vec3.ZERO : carrierVel);
        long now = level.getGameTime();
        Shot s = new Shot(FlightManager.get(level).nextShotId(), kind, owner, level.dimension(), now, ownShip, ship, entity, emitter, lock, devPlayer,
                targetName == null ? "" : targetName, from, v, t);
        SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(level, ship);
        s.blocks = si == null ? 0 : si.blockCount();
        if (kind == RADAR && !lock.holds(level, s, t, now)) return "no lock";
        SHOTS.add(s);
        if (ship != null && warns(kind)) Rwr.launch(level, ship, s.id, from, owner, "mrm", false, now);
        level.playSound(null, from.x, from.y, from.z, WmdSounds.AAM_LAUNCH.get(), net.minecraft.sounds.SoundSource.HOSTILE,
                Balance.SND_RELEASE, 1.1F);
        Wmd.LOGGER.info("Air missile {} ({}) fired at {} ({} blocks)", s.id, NAMES[kind], s.targetName, (int) from.distanceTo(t));
        return null;
    }

    private static boolean tooMany(ServerLevel level, UUID owner) {
        int mine = 0, here = 0;
        for (Shot s : SHOTS) { if (s.dim == level.dimension()) here++; if (owner != null && owner.equals(s.owner)) mine++; }
        return mine >= Balance.AAM_MAX_PER_OWNER || here >= Balance.AAM_MAX_PER_DIM;
    }

    public static String fireAgm(ServerLevel level, UUID owner, Vec3 from, Vec3 dir, Vec3 carrierVel, UUID ownShip, int spot, UUID devPlayer) {
        if (!AirGate.on()) return "1.2 air battles are off (AirGate)";
        LaserSpots.Spot sp = LaserSpots.get(level, spot);
        if (sp == null || owner == null || !owner.equals(sp.owner())) return "no laser spot";
        if (tooMany(level, owner)) return "too many missiles in the air";
        Vec3 t = sp.pos();
        if (from.distanceTo(t) > Balance.AAM_RANGE[AGM]) return "out of range (" + (int) from.distanceTo(t) + " > " + (int) Balance.AAM_RANGE[AGM] + ")";
        Vec3 v = dir.normalize().scale(Balance.AAM_LAUNCH_SPEED).add(carrierVel == null ? Vec3.ZERO : carrierVel);
        long now = level.getGameTime();
        Shot s = new Shot(FlightManager.get(level).nextShotId(), AGM, owner, level.dimension(), now, ownShip, null, null, null, null, devPlayer,
                "laser spot " + BlockPos.containing(t).toShortString(), from, v, t);
        s.laser = spot;
        SHOTS.add(s);
        level.playSound(null, from.x, from.y, from.z, WmdSounds.AAM_LAUNCH.get(), net.minecraft.sounds.SoundSource.HOSTILE, Balance.SND_RELEASE, 0.9F);
        Wmd.LOGGER.info("Air missile {} (agm) fired at {} ({} blocks)", s.id, s.targetName, (int) from.distanceTo(t));
        return null;
    }

    public static String fireAgmGps(ServerLevel level, UUID owner, Vec3 from, Vec3 dir, Vec3 carrierVel, UUID ownShip, Vec3 point, String label, UUID devPlayer) {
        if (!AirGate.on()) return "1.2 air battles are off (AirGate)";
        if (point == null || owner == null) return "no GPS target";
        if (tooMany(level, owner)) return "too many missiles in the air";
        if (from.distanceTo(point) > Balance.AAM_RANGE[AGM]) return "out of range (" + (int) from.distanceTo(point) + " > " + (int) Balance.AAM_RANGE[AGM] + ")";
        double miss = agmGpsMiss(level, owner), a = level.random.nextDouble() * Math.PI * 2.0, r = miss * Math.sqrt(level.random.nextDouble());
        Vec3 aim = point.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
        Vec3 v = dir.normalize().scale(Balance.AAM_LAUNCH_SPEED).add(carrierVel == null ? Vec3.ZERO : carrierVel);
        long now = level.getGameTime();
        Shot s = new Shot(FlightManager.get(level).nextShotId(), AGM, owner, level.dimension(), now, ownShip, null, null, null, null, devPlayer,
                "GPS " + (label == null ? "" : label + " ") + BlockPos.containing(point).toShortString(), from, v, aim);
        s.gpsAim = aim;
        SHOTS.add(s);
        level.playSound(null, from.x, from.y, from.z, WmdSounds.AAM_LAUNCH.get(), net.minecraft.sounds.SoundSource.HOSTILE, Balance.SND_RELEASE, 0.9F);
        Wmd.LOGGER.info("Air missile {} (agm GPS, miss {}) fired at {} ({} blocks)", s.id, String.format("%.1f", miss), s.targetName, (int) from.distanceTo(point));
        return null;
    }

    static double agmGpsMiss(ServerLevel level, UUID owner) {
        return Uplink.active(level.getServer(), owner) ? Uplink.missRadius(Balance.AGM_GPS_MISS) : Balance.AGM_INS_MISS;
    }

    private static Vec3 laserPoint(ServerLevel level, Shot s) {
        if (s.gpsAim != null) return s.gpsAim;
        if (s.armQuiet || s.laser < 0) return null;
        LaserSpots.Spot sp = LaserSpots.get(level, s.laser);
        return sp == null ? null : sp.pos();
    }

    private static void agmBlast(ServerLevel level, Vec3 at, Shot s) {
        WarheadType w = WarheadType.HE;
        UUID prev = ShipStrike.begin(s.owner);
        try {
            WarheadFx.detonate(level, FlightManager.get(level), net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.y), net.minecraft.util.Mth.floor(at.z), w.tier, w, s.vel.x, s.vel.z, s.refined);
        } finally {
            ShipStrike.end(prev);
        }
    }

    static Vec3 targetPoint(ServerLevel level, UUID ship, UUID entity, BlockPos emitter) {
        if (ship != null) { SableBridge.ShipInfo si = SableBridge.shipInfo(level, ship); return si == null ? null : si.worldCenter(); }
        if (entity != null) { Entity e = level.getEntity(entity); return e == null || !e.isAlive() ? null : e.getBoundingBox().getCenter(); }
        if (emitter != null) return SableBridge.worldPos(level, emitter);
        return null;
    }

    public static final int FLARE = 1, CHAFF = 2;

    public static final int COOLING = -1, EMPTY = -2;

    private static final Map<UUID, int[]> LOAD = Statics.server(new HashMap<>());

    private static int cap(int type) { return type == FLARE ? Balance.AAM_DISPENSER_FLARES : Balance.AAM_DISPENSER_CHAFF; }

    private static int[] loadOf(UUID aircraft) {
        if (LOAD.size() > Balance.RWR_MAX_AIRCRAFT && !LOAD.containsKey(aircraft)) LOAD.clear();
        return LOAD.computeIfAbsent(aircraft, k -> new int[] {Balance.AAM_DISPENSER_FLARES, Balance.AAM_DISPENSER_CHAFF});
    }

    public static int loadLeft(UUID aircraft, int type) {
        if (aircraft == null || (type != FLARE && type != CHAFF)) return 0;
        return loadOf(aircraft)[type == FLARE ? 0 : 1];
    }

    private static boolean take(UUID aircraft, int type) {
        int[] l = loadOf(aircraft);
        int i = type == FLARE ? 0 : 1;
        if (l[i] <= 0) return false;
        l[i]--;
        return true;
    }

    public static int refill(ServerLevel level, UUID aircraft, int type, int count) {
        if (!AirGate.on() || aircraft == null || (type != FLARE && type != CHAFF) || count <= 0) return 0;
        if (AirPicture.isAircraft(level, aircraft)) return -1;
        int[] l = loadOf(aircraft);
        int i = type == FLARE ? 0 : 1, add = Math.min(count, cap(type) - l[i]);
        l[i] += add;
        return add;
    }

    public static int deploy(ServerLevel level, UUID aircraft, Vec3 at, int type) {
        if (!AirGate.on() || aircraft == null || at == null || (type != FLARE && type != CHAFF)) return 0;
        long now = level.getGameTime();
        Long last = LAST_DECOY.get(aircraft);
        if (last != null && now - last < Balance.AAM_DECOY_COOLDOWN) return COOLING;
        if (!take(aircraft, type)) return EMPTY;
        return throwSalvo(level, aircraft, at, type, now);
    }

    public static int salvo(ServerLevel level, UUID aircraft, Vec3 at, int type) {
        if (!AirGate.on() || aircraft == null || at == null || (type != FLARE && type != CHAFF)) return 0;
        long now = level.getGameTime();
        Long last = LAST_DECOY.get(aircraft);
        if (last != null && now - last < Balance.AAM_DECOY_COOLDOWN) return COOLING;
        return throwSalvo(level, aircraft, at, type, now);
    }

    private static int throwSalvo(ServerLevel level, UUID aircraft, Vec3 at, int type, long now) {
        if (LAST_DECOY.size() > 256) LAST_DECOY.clear();
        LAST_DECOY.put(aircraft, now);

        AirPicture.Aircraft ac = AirPicture.get(level, aircraft);
        Vec3 av = ac == null || ac.vel() == null ? Vec3.ZERO : ac.vel();
        AamDecoyPayload dp = new AamDecoyPayload(at.x, at.y, at.z, (float) av.x, (float) av.y, (float) av.z, type);
        double fr2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) if (worldDistSq(level, sp, at) <= fr2) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, dp);
        level.playSound(null, at.x, at.y, at.z, (type == FLARE ? WmdSounds.FLARE_POP : WmdSounds.CHAFF_POP).get(), net.minecraft.sounds.SoundSource.HOSTILE, Balance.SND_RELEASE, 1.0F);
        int fooled = 0;
        for (Shot s : SHOTS) {
            if (s.dim != level.dimension() || !aircraft.equals(s.ship) || s.decoy != null || s.kind == ARM) continue;
            if (s.pos.distanceTo(at) > Balance.AAM_DECOY_REACH) continue;
            boolean match = (type == FLARE) == (s.kind == HEAT);
            if (level.random.nextDouble() >= (match ? Balance.COUNTER_MATCH : Balance.COUNTER_MISMATCH)) continue;
            s.decoy = at.add((level.random.nextDouble() - 0.5) * 6.0, -1.0, (level.random.nextDouble() - 0.5) * 6.0);
            s.decoyAt = now;
            fooled++;
            Wmd.LOGGER.info("Air missile {} ({}) decoyed by {}", s.id, NAMES[s.kind], type == FLARE ? "flares" : "chaff");
        }
        if (type == CHAFF) fooled += AntiShipMissiles.chaff(level, aircraft, at, now);
        return fooled;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!AirGate.on()) { if (!SHOTS.isEmpty()) SHOTS.clear(); return; }
        if (SHOTS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        try {
            for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
                Shot s = it.next();
                if (s.dim != sl.dimension()) continue;
                if (s.end == null) step(sl, s, now);
                if (s.end != null) { it.remove(); ended(sl, s); }
            }
            broadcast(sl);
        } catch (Throwable t) {
            if (!warned) { warned = true; Wmd.LOGGER.error("WMD air missiles failed (missiles in the air dropped)", t); }
            SHOTS.removeIf(s -> s.dim == sl.dimension());
        }
    }

    private static void broadcast(ServerLevel level) {
        java.util.List<Shot> here = new java.util.ArrayList<>();
        for (Shot s : SHOTS) if (s.dim == level.dimension() && s.end == null && s.pos != null) here.add(s);
        if (here.isEmpty()) return;
        long now = level.getGameTime();
        double r2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
            java.util.List<AamPayload.Entry> l = new java.util.ArrayList<>();
            for (Shot s : here) {
                if (worldDistSq(level, sp, s.pos) > r2 || l.size() >= AamPayload.MAX) continue;
                Vec3 v = s.vel == null ? Vec3.ZERO : s.vel;
                l.add(new AamPayload.Entry(s.id, s.kind, s.pos.x, s.pos.y, s.pos.z, (float) v.x, (float) v.y, (float) v.z, now - s.launch < Balance.AAM_BURN[s.kind]));
            }
            if (!l.isEmpty()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new AamPayload(l));
        }
    }

    private static void ended(ServerLevel level, Shot s) {
        if (s.pos != null) {
            boolean kill = s.kind != AGM && (HIT.equals(s.end) || DUD.equals(s.end));
            double r2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
            AamBurstPayload p = new AamBurstPayload(s.id, s.pos.x, s.pos.y, s.pos.z, kill);
            for (net.minecraft.server.level.ServerPlayer sp : level.players()) if (worldDistSq(level, sp, s.pos) <= r2) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, p);
        }
        if (s.ship != null) Rwr.missileGone(s.ship, s.id);
        Wmd.LOGGER.info("Air missile {} ({}) at {}: {} after {} ticks, {} blocks flown, closest {}", s.id, NAMES[s.kind], s.targetName, s.end,
                level.getGameTime() - s.launch, (int) s.flown, s.best == Double.MAX_VALUE ? "-" : String.format("%.1f", s.best));
        if (s.devPlayer != null) {
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(s.devPlayer);
            if (sp != null) sp.sendSystemMessage(Component.literal(String.format("[aam %d %s] %s: %s (%d ticks, %d blocks flown, closest %s)", s.id, NAMES[s.kind],
                    s.targetName, s.end, level.getGameTime() - s.launch, (int) s.flown, s.best == Double.MAX_VALUE ? "-" : String.format("%.1f", s.best))));
        }
    }

    static double speedAt(int kind, long age, double prev, double launchSpeed) {
        double max = Math.max(Balance.AAM_SPEED[kind], launchSpeed);
        if (age < Balance.AAM_BOOST_TICKS) return launchSpeed + (max - launchSpeed) * (age + 1) / (double) Balance.AAM_BOOST_TICKS;
        if (age < Balance.AAM_BURN[kind]) return max;
        return prev * (1.0 - Balance.AAM_DECAY[kind]);
    }

    private static void step(ServerLevel level, Shot s, long now) {
        long age = now - s.launch;
        if (age >= Balance.AAM_MAX_TICKS) { s.end = MISS; return; }
        if (age < s.drop) {
            s.vel = s.vel.scale(0.99).add(0.0, -Balance.STATION_DROP_G, 0.0);
            Vec3 to = s.pos.add(s.vel);
            BlockPos tb = BlockPos.containing(to);
            if (level.getChunkSource().getChunkNow(tb.getX() >> 4, tb.getZ() >> 4) == null && !AirRockets.reach(level, to)) { s.end = MISS; return; }
            s.flown += s.vel.length();
            s.pos = to;
            s.speed = s.vel.length();
            return;
        }
        age -= s.drop;

        Vec3 t = s.decoy == null ? (s.kind == AGM ? laserPoint(level, s) : targetPoint(level, s.ship, s.entity, s.emitter)) : null;
        Vec3 tv = Vec3.ZERO;
        if (t != null && s.lastSeen != null) tv = t.subtract(s.lastSeen);
        boolean renew = false;
        if (s.decoy != null) {
            s.aim = s.decoy.add(0.0, -0.08 * (now - s.decoyAt), 0.0);
            s.aimVel = new Vec3(0.0, -0.08, 0.0);
            if (s.pos.distanceTo(s.aim) < 4.0 || now - s.decoyAt > Balance.AAM_DECOY_TICKS) { selfDestruct(level, s); s.end = DECOYED; return; }
        } else if (t == null) {
            if (s.kind == AGM && !s.armQuiet) { s.armQuiet = true; s.aimVel = Vec3.ZERO; Wmd.LOGGER.info("Air missile {} (agm): the laser spot went out, flying to the last spot", s.id); }
            else if (s.kind != ARM && s.kind != AGM) { s.end = GONE; return; }
        } else {
            switch (s.kind) {
                case HEAT -> renew = inGimbal(s, t) && !SmokeScreens.insideColumn(level, t.x, t.z)
                        && (s.ship == null || s.pos.distanceTo(t) <= Balance.AAM_FUZE[HEAT] || AirHeat.rearAspect(level, s.ship, t, tv, s.pos, now));
                case RADAR -> {
                    if (!s.pitbull) {
                        renew = !s.lockLost && s.lock.holds(level, s, t, now);
                        if (!renew && !s.lockLost) { s.lockLost = true; Wmd.LOGGER.info("Air missile {} (radar) lost its launcher's lock before pitbull", s.id); }
                        if (renew && s.ship != null && (now + s.id) % 10 == 0) {
                            Vec3 la = s.lock.at(level);
                            if (la != null) { Rwr.lock(level, s.ship, la, s.lock.owner(), s.lock.label(), s.id, now); Rwr.launch(level, s.ship, s.id, la, s.owner, "mrm", false, now); }
                        }
                        if (renew && s.pos.distanceTo(t) <= Balance.AAM_PITBULL) {
                            s.pitbull = true;
                            if (s.ship != null) Rwr.launch(level, s.ship, s.id, s.pos, s.owner, "mrm", true, now);
                            Wmd.LOGGER.info("Air missile {} (radar) PITBULL at {} blocks", s.id, (int) s.pos.distanceTo(t));
                        }
                    } else {
                        renew = inGimbal(s, t) && !FlightManager.get(level).chaffHides(s.pos.x, s.pos.z, t.x, t.z);
                        if (renew && s.ship != null && (now + s.id) % 10 == 0) Rwr.launch(level, s.ship, s.id, s.pos, s.owner, "mrm", true, now);
                    }
                }
                case ARM -> {
                    BlockEntity be = level.getBlockEntity(s.emitter);
                    renew = !s.armQuiet && be != null && RadarEmit.emitting(be);
                    if (!renew && !s.armQuiet) { s.armQuiet = true; s.aimVel = Vec3.ZERO; Wmd.LOGGER.info("Air missile {} (arm): the radar went quiet, flying to its last spot", s.id); }
                }
                case AGM -> {
                    renew = s.gpsAim != null || inGimbal(s, t);
                    if (!renew) { s.armQuiet = true; s.aimVel = Vec3.ZERO; Wmd.LOGGER.info("Air missile {} (agm): the spot left the seeker's view", s.id); }
                }
                default -> {}
            }
        }
        if (renew) { s.aim = t; s.aimVel = s.aimVel.scale(0.5).add(tv.scale(0.5)); }
        else if (s.decoy == null) s.aim = s.aim.add(s.aimVel);
        if (t != null) s.lastSeen = t;
        if (s.kind == RADAR && s.lockLost && s.pos.distanceTo(s.aim) < Math.max(8.0, s.speed * 1.5)) { selfDestruct(level, s); s.end = LOST; return; }

        s.speed = speedAt(s.kind, age, s.speed, s.launchSpeed);
        if (age >= Balance.AAM_BOOST_TICKS && s.speed < Balance.AAM_MIN_SPEED) { selfDestruct(level, s); s.end = MISS; return; }
        s.vel = steer(s.pos, s.vel, s.aim, s.aimVel, s.speed, Balance.AAM_LAT[s.kind]);

        Vec3 from = s.pos, to = s.pos.add(s.vel);

        if (level.getChunkSource().getChunkNow(BlockPos.containing(to).getX() >> 4, BlockPos.containing(to).getZ() >> 4) == null && !AirRockets.reach(level, to)) { s.end = MISS; return; }
        double hitFrac = 1.0;
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        UUID hitShip = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            hitShip = SableBridge.shipIdAt(level, hit.getBlockPos());
            if (hitShip != null && hitShip.equals(s.ownShip) && age < 20) hit = null;
            else hitFrac = Math.max(0.0, Math.min(1.0, hit.getLocation().distanceTo(from) / Math.max(1.0E-6, to.distanceTo(from))));
        } else hit = null;
        if (s.decoy == null && s.aim != null) {
            double fuze = s.kind == ARM ? Balance.AAM_CONTACT : Math.max(Balance.AAM_CONTACT, Balance.AAM_FUZE[s.kind]);
            int n = Math.max(1, (int) Math.ceil(s.speed * hitFrac));
            double bd = Double.MAX_VALUE;
            int bi = -1;
            Vec3 bp = null;
            for (int i = 1; i <= n; i++) {
                double f = hitFrac * i / (double) n;
                Vec3 p = from.add(to.subtract(from).scale(f));
                double d = t == null || s.kind == AGM ? p.distanceTo(s.aim) - 0.5 : distToTarget(level, s, p.add(tv.scale(1.0 - f)), now);
                if (d < bd) { bd = d; bi = i; bp = p; }
            }
            s.best = Math.min(s.best, bd);
            boolean passing = bi < n || hit != null;
            if (bd <= fuze && (passing || bd <= Balance.AAM_CONTACT) && !(age < 4 && s.ship != null && s.ship.equals(s.ownShip))) {
                s.flown += from.distanceTo(bp);
                s.pos = bp;
                if (s.kind == AGM) agmBlast(level, bp, s);
                else if (s.kind == ARM) {
                    UUID onShip = SableBridge.shipIdAt(level, s.emitter);
                    if (onShip == null) AirFrag.ground(level, s.emitter, s.vel, s.owner, s.refined);
                    else AirFrag.burst(level, bp, s.vel, s.kind, s.owner, onShip, null, null, s.refined);
                } else AirFrag.burst(level, bp, s.vel, s.kind, s.owner, s.ship, s.entity, s.plot, s.refined);
                s.end = HIT;
                return;
            }
        }
        if (hit != null) {
            s.flown += from.distanceTo(hit.getLocation());
            s.pos = hit.getLocation();
            if (s.kind == AGM) { agmBlast(level, s.pos, s); s.end = s.aim != null && s.pos.distanceTo(s.aim) <= 3.0 ? HIT : DUD; return; }
            if (hitShip != null) AirFrag.burst(level, s.pos, s.vel, s.kind, s.owner, hitShip, null, hitShip.equals(s.ship) ? s.plot : null, s.refined);
            else if (s.kind == ARM) AirFrag.ground(level, hit.getBlockPos(), s.vel, s.owner, s.refined);
            else AirFrag.burst(level, s.pos, s.vel, s.kind, s.owner, null, null, null, s.refined);
            s.end = hitShip != null && hitShip.equals(s.ship) ? HIT : DUD;
            return;
        }
        s.flown += s.speed;
        s.pos = to;
        if (s.flown > Balance.AAM_RANGE[s.kind] * Balance.AAM_REACH_SPARE) { selfDestruct(level, s); s.end = MISS; return; }
    }

    private static boolean inGimbal(Shot s, Vec3 t) {
        Vec3 d = t.subtract(s.pos);
        double l = d.length(), v = s.vel.length();
        if (l < 1.0 || v < 1.0E-6) return true;
        return d.dot(s.vel) / (l * v) >= Math.cos(Math.toRadians(Balance.AAM_SEEKER_GIMBAL));
    }

    static Vec3 steer(Vec3 pos, Vec3 vel, Vec3 aim, Vec3 aimVel, double speed, double lat) {
        Vec3 r = aim.subtract(pos);
        double R = r.length();
        double vl = vel.length();
        if (R < 1.0E-6 || vl < 1.0E-6) return vel.lengthSqr() < 1.0E-12 ? r.normalize().scale(speed) : vel.normalize().scale(speed);
        Vec3 rh = r.scale(1.0 / R), vh = vel.scale(1.0 / vl);
        Vec3 a;
        if (vh.dot(rh) < Math.cos(Math.toRadians(Balance.AAM_PURSUIT_DEG))) {
            a = rh.subtract(vh.scale(rh.dot(vh)));
            a = a.lengthSqr() < 1.0E-12 ? new Vec3(-vh.z, 0.0, vh.x) : a.normalize().scale(lat);
        } else {
            Vec3 vr = aimVel.subtract(vel);
            Vec3 omega = r.cross(vr).scale(1.0 / (R * R));
            double vc = Math.max(-vr.dot(rh), speed * 0.5);
            a = omega.cross(rh).scale(Balance.AAM_PN * vc);
            a = a.subtract(vh.scale(a.dot(vh)));
        }
        double al = a.length();
        if (al > lat) a = a.scale(lat / al);
        Vec3 nv = vel.add(a);
        return nv.lengthSqr() < 1.0E-12 ? vh.scale(speed) : nv.normalize().scale(speed);
    }

    private static double distToTarget(ServerLevel level, Shot s, Vec3 p, long now) {
        if (s.ship != null) {
            if (s.plot == null || now - s.plotAt >= 20) { s.plot = SableBridge.plotBox(level, s.ship); s.plotAt = now; }
            Vec3 q = s.plot == null ? null : SableBridge.toShip(level, s.ship, p);
            if (q != null) return distToBox(new AABB(s.plot[0], s.plot[1], s.plot[2], s.plot[3] + 1.0, s.plot[4] + 1.0, s.plot[5] + 1.0), q);
            SableBridge.ShipInfo si = SableBridge.shipInfo(level, s.ship);
            return si == null ? Double.MAX_VALUE : distToBox(si.worldBounds(), p);
        }
        if (s.entity != null) {
            Entity e = level.getEntity(s.entity);
            return e == null ? Double.MAX_VALUE : distToBox(e.getBoundingBox(), p);
        }
        return p.distanceTo(SableBridge.worldPos(level, s.emitter)) - 0.5;
    }

    static double distToBox(AABB b, Vec3 p) {
        double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX), dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY), dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void selfDestruct(ServerLevel level, Shot s) { AirFrag.puff(level, s.pos); }

    static double worldDistSq(ServerLevel level, net.minecraft.server.level.ServerPlayer sp, Vec3 p) {
        Vec3 w = SableBridge.toWorld(level, sp.position());
        return (w == null ? sp.position() : w).distanceToSqr(p);
    }

    private static void trail(ServerLevel level, Shot s, Vec3 from, Vec3 to, long age) {
        boolean burning = age < Balance.AAM_BURN[s.kind];
        int n = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 1.5));
        for (int i = 0; i < n; i++) {
            Vec3 p = from.add(to.subtract(from).scale(i / (double) n));
            fx(level, p, WmdParticles.TRAIL_SMOKE.get(), 1, 0.05, 0.005);
            if (burning && i == 0) fx(level, p, WmdParticles.ROCKET_FLAME.get(), 2, 0.05, 0.01);
        }
    }

    static void fx(ServerLevel level, Vec3 p, ParticleOptions type, int count, double spread, double speed) {
        double r2 = Balance.AAM_FX_RANGE * Balance.AAM_FX_RANGE;
        for (ServerPlayer sp : level.players())
            if (sp.distanceToSqr(p) <= r2) level.sendParticles(sp, type, true, p.x, p.y, p.z, count, spread, spread, spread, speed);
    }
}
