package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AntiShipMissiles {

    private AntiShipMissiles() {}

    static final int SEEK_TICKS = 10;

    static final class State {
        final UUID owner, ownShip, cue;
        boolean acquired, decoyed;
        State(UUID owner, UUID ownShip, UUID cue) { this.owner = owner; this.ownShip = ownShip; this.cue = cue; }
    }

    private static final Map<ResourceKey<Level>, Map<Long, State>> STATES = Statics.server(new HashMap<>());

    static MissileBuild build(boolean refined) {
        return new MissileBuild(3, 3, 3, 3, "he", false, false, refined, false, 0, MissileBuild.KIND_CRUISE).withSeeker(MissileBuild.SEEK_RADAR);
    }

    static boolean isAshm(MissileBuild b) { return b != null && b.isCruise() && MissileBuild.SEEK_RADAR.equals(b.seeker()); }

    static boolean surfaceShip(ServerLevel sl, SableBridge.ShipInfo s, UUID ownShip, UUID shooter) {
        if (!AirGate.on() || s == null || s.id().equals(ownShip)) return false;
        if (AirPicture.isAircraft(sl, s.id()) || NavalLayer.submerged(sl, s)) return false;
        if (shooter != null && shooter.equals(Crafts.owner(sl, s.id()))) return false;
        return !(s.blockCount() <= Balance.DEBRIS_TREE_MAX && SableBridge.debrisRemnant(sl, s.id()));
    }

    static Vec3 lead(ServerLevel sl, SableBridge.ShipInfo s, Vec3 from) {
        Vec3 c = s.worldCenter(), v = ShipDefence.velocity(sl, s);
        double t = Math.hypot(c.x - from.x, c.z - from.z) / Balance.ASHM_SPEED;
        Vec3 p = c.add(v.scale(t));
        t = Math.hypot(p.x - from.x, p.z - from.z) / Balance.ASHM_SPEED;
        return c.add(v.scale(t));
    }

    static List<UUID> seaContacts(ServerLevel sl, NoseRadarBlockEntity nr, UUID ownShip, UUID shooter, double cone) {
        List<UUID> out = new ArrayList<>();
        if (!AirGate.on() || nr == null || !nr.on() || !SableBridge.active()) return out;
        Vec3 at = nr.at();
        double reach = nr.lockRange() * Balance.AIR_SIZE_MAX;
        List<SableBridge.ShipInfo> near = SableBridge.shipsIntersecting(sl, new AABB(at, at).inflate(reach, reach, reach));
        near.sort((a, b) -> Double.compare(a.worldCenter().distanceTo(at), b.worldCenter().distanceTo(at)));
        for (SableBridge.ShipInfo s : near) if (lockable(sl, nr, s, ownShip, shooter, cone)) out.add(s.id());
        return out;
    }

    static Vec3 seaKeep(ServerLevel sl, NoseRadarBlockEntity nr, UUID ship, UUID ownShip, UUID shooter) {
        if (!AirGate.on() || nr == null || !nr.on() || ship == null) return null;
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        return s != null && lockable(sl, nr, s, ownShip, shooter, Balance.COCKPIT_LOCK_KEEP) ? s.worldCenter() : null;
    }

    private static boolean lockable(ServerLevel sl, NoseRadarBlockEntity nr, SableBridge.ShipInfo s, UUID ownShip, UUID shooter, double cone) {
        if (!AirGate.on() || !surfaceShip(sl, s, ownShip, shooter)) return false;
        Vec3 at = nr.at(), d = s.worldCenter().subtract(at);
        double l = d.length();
        if (l > AirJam.lockRange(sl, s.id(), nr.lockRange() * AirPicture.sizeFactor(s.blockCount()))) return false;
        double off = l < 1.0E-6 ? 0.0 : Math.toDegrees(Math.acos(Mth.clamp(d.dot(nr.boresight()) / l, -1.0, 1.0)));
        return off <= Math.min(cone, nr.lockGimbal());
    }

    private static SableBridge.ShipInfo shipAt(ServerLevel sl, Vec3 p, UUID ownShip) {
        if (p == null) return null;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(p, p).inflate(1.5)))
            if (!s.id().equals(ownShip)) return s;
        return null;
    }

    static String precheck(ServerLevel sl, UUID owner) {
        if (!AirGate.on()) return "1.2 air battles are off";
        if (owner == null) return "the station has no owner";
        Component why = Config.Server.refuse(build(false));
        if (why != null) return why.getString();
        if (inAir(sl, owner) >= Balance.ASHM_MAX_PER_OWNER) return "too many anti-ship missiles in the air";
        return null;
    }

    private static int inAir(ServerLevel sl, UUID owner) {
        Map<Long, State> m = STATES.get(sl.dimension());
        if (m == null) return 0;
        int n = 0;
        for (State s : m.values()) if (owner.equals(s.owner)) n++;
        return n;
    }

    static String launch(ServerLevel sl, UUID owner, UUID ownShip, Vec3 at, Vec3 fwd, UUID target, boolean refined, BooleanSupplier pay) {
        String pre = precheck(sl, owner);
        if (pre != null) return pre;
        Vec3 dir = new Vec3(fwd.x, 0.0, fwd.z);
        if (dir.lengthSqr() < 1.0E-6) return "the station points straight up or down";
        dir = dir.normalize();
        long now = sl.getGameTime();

        SableBridge.ShipInfo ship = null;
        String cue = "bearing";
        if (target != null) { SableBridge.ShipInfo s = SableBridge.shipInfo(sl, target); if (surfaceShip(sl, s, ownShip, owner)) { ship = s; cue = "lock"; } }
        if (ship == null) { SableBridge.ShipInfo s = shipAt(sl, TargetingPodBlockEntity.spotAboard(sl, ownShip), ownShip); if (surfaceShip(sl, s, ownShip, owner)) { ship = s; cue = "pod"; } }
        if (ship == null) {
            ShipTargets.Desig d = ShipTargets.get(owner, now);
            SableBridge.ShipInfo s = d == null ? null : SableBridge.shipInfo(sl, d.ship());
            if (surfaceShip(sl, s, ownShip, owner)) { ship = s; cue = "designated"; }
        }
        Vec3 aim = ship != null ? lead(sl, ship, at) : at.add(dir.scale(Balance.ASHM_RANGE - CruiseRoute.EXIT_D));
        int ax = Mth.floor(aim.x), az = Mth.floor(aim.z);
        double flat = Math.hypot(aim.x - at.x, aim.z - at.z);
        if (flat < Balance.ASHM_MIN_RANGE) return "the target is too close (" + (int) flat + " < " + (int) Balance.ASHM_MIN_RANGE + ")";
        CruiseRoute r = new CruiseRoute(at.x, at.z, dir.x, dir.z, new int[] { ax }, new int[] { az }, (float) Balance.ASHM_SPEED, null).withAir(true);
        if (r.length() > Balance.ASHM_RANGE + 0.5) return "out of range (" + (int) r.length() + " > " + (int) Balance.ASHM_RANGE + ")";
        r = r.withAlt(CoastalBatteryBlockEntity.seaSkim(sl, r));
        if (!pay.getAsBoolean()) return "no charge in the power core";
        FlightManager mgr = FlightManager.get(sl);
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
        String name = p != null ? p.getGameProfile().getName() : "?";
        long id = mgr.launchCruise(sl, at, r, CruiseRoute.groundAt(sl, ax, az), name, owner, 0.0, build(refined));
        boolean jam = ship != null && AirJam.on(sl, ship.id());
        if (ship != null && !jam) mgr.shipLocks.put(id, ship.id());
        STATES.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).put(id, new State(owner, ownShip, ship == null ? null : ship.id()));
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.ASHM_IGNITE.get(), SoundSource.HOSTILE, Balance.SND_RELEASE, 1.0F);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.ashm.away." + cue, ship == null ? "" : ship.name(), (int) Math.round(r.length()),
                r.ticks() / 20), false);
        Wmd.LOGGER.info("Anti-ship missile {} by {} from {} {} {}: cue {} {}, aim {} {}, {} blocks{}", id, name, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), cue,
                ship == null ? "-" : ship.name(), ax, az, (int) r.length(), jam ? " (target jamming: no lock until the seeker sees it)" : "");
        return null;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!AirGate.on()) { STATES.remove(sl.dimension()); return; }
        if (sl.getGameTime() % SEEK_TICKS != 0) return;
        try {
            tick(sl, sl.getGameTime());
        } catch (Throwable t) {
            Wmd.LOGGER.error("WMD anti-ship missiles: the seeker tick failed (their seekers stop this tick)", t);
        }
    }

    static void tick(ServerLevel sl, long now) {
        FlightManager mgr = FlightManager.get(sl);
        Map<Long, State> m = STATES.get(sl.dimension());
        List<Flight> list = mgr.flights();
        for (Flight f : list) {
            if (f.isCruise() && f.cruise() != null && f.cruise().air && isAshm(f.build()) && (m == null || !m.containsKey(f.id()))) {
                if (m == null) { m = new HashMap<>(); STATES.put(sl.dimension(), m); }
                UUID o = null;
                try { o = f.ownerId() == null || f.ownerId().isEmpty() ? null : UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) { }
                m.put(f.id(), new State(o, null, null));
            }
        }
        if (m == null || m.isEmpty()) return;
        for (var it = m.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Flight f = mgr.find(e.getKey());
            if (f == null || f.cruise() == null || f.isDeadReckoned()) { it.remove(); continue; }
            State st = e.getValue();
            if (st.acquired || st.decoyed) continue;
            double left = f.cruise().length() - f.cruise().distanceAt(now - f.start());
            UUID lock = mgr.shipLocks.get(f.id());
            if (lock != null && SableBridge.shipInfo(sl, lock) != null && !AirJam.on(sl, lock)) {
                if (left <= Balance.ASHM_SEEKER_ON) st.acquired = true;
                continue;
            }
            if (left > Balance.ASHM_SEEKER_ON || left < Balance.ASHM_TERMINAL) continue;
            SableBridge.ShipInfo pick = seek(sl, f, st, left);
            if (pick == null) continue;
            mgr.shipLocks.put(f.id(), pick.id());
            st.acquired = true;
            Wmd.LOGGER.info("Anti-ship missile {}: seeker locked {} ({} blocks, {} blocks out)", f.id(), pick.name(), pick.blockCount(), (int) left);
        }
    }

    private static SableBridge.ShipInfo seek(ServerLevel sl, Flight f, State st, double left) {
        Vec3 aim = new Vec3(f.tx() + 0.5, f.endY(), f.tz() + 0.5);
        double w = Balance.ASHM_WINDOW;
        SableBridge.ShipInfo best = null;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(aim, aim).inflate(w, 96.0, w))) {
            if (!surfaceShip(sl, s, st.ownShip, st.owner)) continue;
            if (Math.hypot(s.worldCenter().x - aim.x, s.worldCenter().z - aim.z) > w + s.worldBounds().getSize() / 2.0) continue;
            if (AirJam.on(sl, s.id()) && left > Balance.ASHM_SEEKER_ON * 0.5) continue;
            if (s.id().equals(st.cue)) return s;
            if (best == null || s.blockCount() > best.blockCount()) best = s;
        }
        return best;
    }

    static int chaff(ServerLevel sl, UUID ship, Vec3 at, long now) {
        Map<Long, State> m = STATES.get(sl.dimension());
        if (!AirGate.on() || m == null || ship == null || at == null) return 0;
        FlightManager mgr = FlightManager.get(sl);
        int n = 0;
        for (var e : m.entrySet()) {
            State st = e.getValue();
            if (st.decoyed || !ship.equals(mgr.shipLocks.get(e.getKey()))) continue;
            Flight f = mgr.find(e.getKey());
            if (f == null || f.cruise() == null || f.isDeadReckoned()) continue;
            Vec3 p = f.position(now);
            if (p.distanceTo(at) > Balance.ASHM_CHAFF_REACH) continue;
            if (sl.random.nextDouble() >= Balance.COUNTER_MATCH) continue;
            if (!bendAside(sl, mgr, f, now)) continue;
            st.decoyed = true;
            mgr.shipLocks.remove(f.id());
            n++;
            Wmd.LOGGER.info("Anti-ship missile {} decoyed by chaff from {}", f.id(), ship);
            if (st.owner != null) CcBattleLog.add(sl.getServer(), st.owner, CcBattleLog.LOST, "log.wmd.ashm_decoyed", Integer.toString(Mth.floor(at.x)), Integer.toString(Mth.floor(at.z)));
        }
        return n;
    }

    private static boolean bendAside(ServerLevel sl, FlightManager mgr, Flight f, long now) {
        CruiseRoute old = f.cruise();
        double s = old.distanceAt(now - f.start()), L = old.length();
        double ahead = Math.min(CruiseRoute.TURN_R, Math.max(4.0, (L - s) * 0.4));
        if (L - s < ahead + 8.0) return false;
        double[] c = old.ground(s + ahead), e = old.ground(L), b = old.ground(Math.max(0.0, s));
        double hx = e[0] - b[0], hz = e[1] - b[1], hl = Math.hypot(hx, hz);
        if (hl < 1.0E-3) return false;
        double side = sl.random.nextBoolean() ? 1.0 : -1.0;
        int nx = Mth.floor(e[0] - hz / hl * Balance.ASHM_CHAFF_OFFSET * side), nz = Mth.floor(e[1] + hx / hl * Balance.ASHM_CHAFF_OFFSET * side);
        CruiseRoute r = new CruiseRoute(old.ox, old.oz, old.fx, old.fz, new int[] { Mth.floor(c[0]), nx }, new int[] { Mth.floor(c[1]), nz }, old.speed, null)
                .withAir(true);
        r = r.withAlt(CoastalBatteryBlockEntity.seaSkim(sl, r));
        int dur = r.ticks();
        if (f.start() + dur <= now + 5) return false;
        Flight nf = f.withCruiseEnd(nx, nz, r, dur, CruiseRoute.groundAt(sl, nx, nz));
        mgr.flights().replaceAll(q -> q.id() == f.id() ? nf : q);
        mgr.setDirty();
        mgr.syncSoon();
        return true;
    }
}
