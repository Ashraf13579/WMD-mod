package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipDefence {

    private ShipDefence() {}

    public record Target(UUID id, String name, Vec3 aim, double dist, boolean designated) {}

    private static final Map<UUID, double[]> SAMPLES = new HashMap<>();

    public static Vec3 velocity(ServerLevel level, SableBridge.ShipInfo s) {
        if (s == null) return Vec3.ZERO;
        long now = level.getGameTime();
        Vec3 c = s.worldCenter();
        double[] a = SAMPLES.get(s.id());
        if (a == null) {
            if (SAMPLES.size() > 256) SAMPLES.clear();
            Vec3 lv = s.linearVelocity().scale(1.0 / 20.0);
            SAMPLES.put(s.id(), new double[] { c.x, c.y, c.z, now, lv.x, lv.y, lv.z });
            return lv;

        }
        if (now - (long) a[3] >= 5) {
            double dt = now - a[3];
            double vx = (c.x - a[0]) / dt, vy = (c.y - a[1]) / dt, vz = (c.z - a[2]) / dt;
            if (dt > 200) { Vec3 lv = s.linearVelocity().scale(1.0 / 20.0); vx = lv.x; vy = lv.y; vz = lv.z; }
            a[0] = c.x; a[1] = c.y; a[2] = c.z; a[3] = now; a[4] = vx; a[5] = vy; a[6] = vz;
        }
        return new Vec3(a[4], a[5], a[6]);
    }

    private static double distToBox(AABB b, Vec3 p) {
        double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX), dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY), dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Vec3 closest(AABB b, Vec3 p) {
        return new Vec3(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY, p.y)), Math.max(b.minZ, Math.min(b.maxZ, p.z)));
    }

    static boolean eligible(ServerLevel sl, UUID owner, UUID ship, long now) {
        return ShipTargets.isDesignated(owner, ship, now) || ShipFaction.isHostileTo(owner, sl, ship);
    }

    private static Set<UUID> candidates(ServerLevel sl, UUID owner, int frequency, long now) {
        Set<UUID> ids = new LinkedHashSet<>(TrackNet.shipTracks(sl, owner, frequency, now));
        ShipTargets.Desig d = ShipTargets.get(owner, now);
        if (d != null) ids.add(d.ship());
        return ids;
    }

    public static Target ciwsTarget(ServerLevel sl, UUID owner, int frequency, Vec3 gun, long now) {
        if (owner == null || !SableBridge.active()) return null;
        if (ShipTargets.get(owner, now) == null && TrackNet.shipTracks(sl, owner, frequency, now).isEmpty()) return null;
        Target best = null;
        for (UUID id : candidates(sl, owner, frequency, now)) {
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, id);
            if (s == null || !eligible(sl, owner, id, now)) continue;
            double d = distToBox(s.worldBounds(), gun);
            if (d > Balance.CIWS_SHIP_RANGE) continue;
            Vec3 c = s.worldCenter();
            double el = Math.toDegrees(Math.atan2(c.y - gun.y, Math.hypot(c.x - gun.x, c.z - gun.z)));
            if (el < -5.0 || el > 85.0) continue;
            if (best == null || d < best.dist()) best = new Target(id, s.name(), c, d, ShipTargets.isDesignated(owner, id, now));
        }
        return best;
    }

    private static final Map<String, Long> LOGGED = new HashMap<>();

    static void logOnce(ServerLevel sl, UUID owner, UUID ship, String key, String... args) {
        long now = sl.getGameTime();
        String k = owner + "|" + ship + "|" + key;
        Long last = LOGGED.get(k);
        if (last != null && now - last < 100) return;
        if (LOGGED.size() > 200) LOGGED.clear();
        LOGGED.put(k, now);
        CcBattleLog.add(sl.getServer(), owner, CcBattleLog.INTERCEPT, key, args);
    }

    private record Pend(long id, UUID ship, String name, Vec3 to, long arrive, int tier, UUID owner, Vec3 from, long launch, Vec3 planned) {
        Pend steered(Vec3 t) { return new Pend(id, ship, name, t, arrive, tier, owner, from, launch, planned); }

        Vec3 at(long now) {
            double u = launch >= arrive ? 1.0 : Math.max(0.0, Math.min(1.0, (now - launch) / (double) (arrive - launch)));
            return from.add(to.subtract(from).scale(u));
        }
    }

    static final int STEER_TICKS = 5;
    static final double HIT_RADIUS = 12.0, STEER_BUDGET = 0.4;

    private static final Map<ResourceKey<Level>, List<Pend>> PEND = new HashMap<>();

    private static final Map<String, Long> READY = new HashMap<>();
    private static boolean warned;

    private static int tierOf(InterceptorSite s) {
        if (s instanceof InterceptorLauncherBlockEntity) return 1;
        if (s instanceof InterceptorBatteryBlockEntity) return 2;
        if (s instanceof InterceptorSiloBlockEntity) return 3;
        if (s instanceof InterceptorSiloT4BlockEntity) return 4;
        return 0;
    }

    private static int kindOf(int tier) { return tier == 1 ? 0 : tier == 2 ? 3 : tier == 3 ? 1 : 2; }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        List<Pend> pend = PEND.get(sl.dimension());
        long now = sl.getGameTime();
        boolean busy = pend != null && !pend.isEmpty();
        if (!busy && now % Balance.SHIP_DEFENCE_SCAN_TICKS != 0) return;
        if (!SableBridge.active()) { if (busy) pend.clear(); return; }
        try {
            if (busy) { steer(sl, pend, now); resolve(sl, pend, now); }
            if (now % Balance.SHIP_DEFENCE_SCAN_TICKS == 0) { scan(sl, now); cruiseFollow(sl, now); }
        } catch (Throwable t) {
            if (!warned) { warned = true; Wmd.LOGGER.error("WMD ship defence failed (interceptors will not engage airships this session)", t); }
            SableBridge.fail(t);
        }
    }

    private static void resolve(ServerLevel sl, List<Pend> pend, long now) {
        for (Pend p : new ArrayList<>(pend)) {
            if (now < p.arrive()) continue;
            pend.remove(p);
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, p.ship());
            double slack = s == null ? 0.0 : velocity(sl, s).length() * STEER_TICKS;
            boolean kill = s != null && distToBox(s.worldBounds(), p.to()) <= HIT_RADIUS + slack;
            Vec3 at = kill ? closest(s.worldBounds(), p.to()) : p.to();
            if (kill) {
                float power = (float) Balance.INT_SHIP_POWER[Math.max(0, Math.min(3, p.tier() - 1))];
                ShipStrike.blast(sl, at, power * 1.75, power, p.owner());
                logOnce(sl, p.owner(), p.ship(), "log.wmd.ship_intercept", p.name(), Integer.toString(p.tier()), Integer.toString((int) at.x), Integer.toString((int) at.z));
            }
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 6000, new InterceptFxPayload(at.x, at.y, at.z, kill, 0));
        }
    }

    private static void steer(ServerLevel sl, List<Pend> pend, long now) {
        if (now % STEER_TICKS != 0) return;
        for (int i = 0; i < pend.size(); i++) {
            Pend p = pend.get(i);
            if (now >= p.arrive() || now < p.launch()) continue;
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, p.ship());
            if (s == null) continue;
            Vec3 here = p.at(now);
            Vec3 aim = closest(s.worldBounds().move(velocity(sl, s).scale(p.arrive() - now)), here);
            double budget = p.from().distanceTo(p.planned()) * STEER_BUDGET;
            if (aim.distanceTo(p.planned()) > budget || aim.distanceTo(p.to()) < 0.75) continue;
            pend.set(i, p.steered(aim));
            PacketDistributor.sendToPlayersNear(sl, null, here.x, here.y, here.z, 6000, new InterceptSteerPayload(p.id(), aim.x, aim.y, aim.z));
        }
    }

    private static void scan(ServerLevel sl, long now) {
        List<Pend> pend = PEND.computeIfAbsent(sl.dimension(), k -> new ArrayList<>());
        if (pend.size() >= 16) return;
        for (InterceptorSite site : CommandCenter.allSites()) {
            if (!(site instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != sl || EmpField.jammed(sl, be.getBlockPos())) continue;
            UUID owner = site.getOwner();
            int tier = tierOf(site);
            if (owner == null || tier == 0 || site.cooldownTicks() > 0) continue;
            String key = sl.dimension().location() + "|" + be.getBlockPos().asLong();
            Long ready = READY.get(key);
            if (ready != null && now < ready) continue;
            if (site.energy() < Balance.INT_SHOT_FE) continue;
            int slot = firstLoaded(site);
            if (slot < 0) continue;
            int freq = site.getFrequency();
            if (!TrackNet.tracks(sl, owner, freq, now).isEmpty()) continue;
            Vec3 from = site instanceof InterceptorSiloBlockEntity s3m ? s3m.mouth() : site instanceof InterceptorSiloT4BlockEntity s4m ? s4m.mouth() : SableBridge.worldPos(sl, be.getBlockPos()).add(0.0, 3.0, 0.0);
            double range = Interceptors.rangeOf(tier, owner);
            SableBridge.ShipInfo bestShip = null;
            double bestD = Double.MAX_VALUE;
            for (UUID id : candidates(sl, owner, freq, now)) {
                SableBridge.ShipInfo s = SableBridge.shipInfo(sl, id);
                if (s == null || !eligible(sl, owner, id, now) || chased(pend, id, owner)) continue;
                double d = distToBox(s.worldBounds(), from);
                if (d > range || from.distanceTo(s.worldCenter()) < Balance.SHIP_INT_MIN_DIST) continue;
                if (d < bestD) { bestD = d; bestShip = s; }
            }
            if (bestShip == null) continue;
            if (site instanceof InterceptorSiloBlockEntity s3) { s3.wakeForShip(now); if (s3.lid() < 0.99F) continue; }
            if (site instanceof InterceptorSiloT4BlockEntity s4) { s4.wakeForShip(now); if (s4.lid() < 0.99F || Math.abs(LauncherGeometry.wrap(slot * 90.0 - s4.drum())) >= 1.0) continue; }
            int rise = tier == 3 ? Balance.SILO3_RISE_TICKS : tier == 4 ? Balance.SILO4_RISE_TICKS : 0;
            long launchAt = now + rise;
            double speed = Balance.INT_SPEED[tier - 1];
            Vec3 c = bestShip.worldCenter(), v = velocity(sl, bestShip);
            int duration = Math.max(12, (int) Math.ceil(from.distanceTo(c) / speed));
            for (int i = 0; i < 2; i++) duration = Math.max(12, (int) Math.ceil(from.distanceTo(c.add(v.scale(rise + duration))) / speed));
            Vec3 to = closest(bestShip.worldBounds().move(v.scale(rise + duration)), from);
            IItemHandler mag = site.magazineHandler();
            ItemStack out = mag.extractItem(slot, 1, false);
            if (out.isEmpty()) continue;
            site.shipShot(Balance.INT_SHOT_FE, Balance.INT_RELOAD_TICKS);
            READY.put(key, now + Balance.INT_RELOAD_TICKS);
            long id = FlightManager.get(sl).nextShotId();
            pend.add(new Pend(id, bestShip.id(), bestShip.name(), to, launchAt + duration, tier, owner, from, launchAt, to));
            PacketDistributor.sendToPlayersNear(sl, null, from.x, from.y, from.z, 4000, new InterceptPayload(id, from.x, from.y, from.z, to.x, to.y, to.z, launchAt, duration, kindOf(tier)));
            Wmd.LOGGER.info("Ship defence: tier {} interceptor from {} at airship {} ({} blocks, {} ticks)", tier, be.getBlockPos().toShortString(), bestShip.name(), (int) from.distanceTo(to), duration);
            if (pend.size() >= 16) return;
        }
    }

    private static boolean chased(List<Pend> pend, UUID ship, UUID owner) {
        int n = 0;
        for (Pend p : pend) if (p.ship().equals(ship) && owner.equals(p.owner())) n++;
        return n >= 2;
    }

    private static int firstLoaded(InterceptorSite s) {
        IItemHandler h = s.magazineHandler();
        for (int i = 0; i < s.slotCount() && i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    static final int CRUISE_FOLLOW_TICKS = 400;

    public static void assignCruiseLock(ServerLevel sl, FlightManager mgr, Flight f, UUID owner) {
        if (owner == null || f == null || !SableBridge.active() || !f.isCruise() || f.isDeadReckoned()) return;
        ShipTargets.Desig d = ShipTargets.get(owner, sl.getGameTime());
        SableBridge.ShipInfo s = d == null ? null : SableBridge.shipInfo(sl, d.ship());
        if (s == null) return;
        Vec3 c = s.worldCenter();
        if (Math.hypot(c.x - (f.tx() + 0.5), c.z - (f.tz() + 0.5)) > Balance.SHIP_LOCK_AIM_RADIUS) return;
        mgr.shipLocks.put(f.id(), d.ship());
        Wmd.LOGGER.info("Cruise: flight {} locks onto airship {} ({})", f.id(), s.name(), d.ship());
    }

    private static void cruiseFollow(ServerLevel sl, long now) {
        FlightManager mgr = FlightManager.get(sl);
        if (mgr.shipLocks.isEmpty()) return;
        List<Flight> list = mgr.flights();
        for (int i = 0; i < list.size(); i++) {
            Flight f = list.get(i);
            UUID lock = f.isCruise() && !f.isDeadReckoned() ? mgr.shipLocks.get(f.id()) : null;
            if (lock == null || now < f.start() || f.endTime() - now > CRUISE_FOLLOW_TICKS || f.endTime() - now < 10) continue;
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, lock);
            if (s == null) continue;
            Vec3 lead = s.worldCenter().add(velocity(sl, s).scale(f.endTime() - now));
            int x = (int) Math.floor(lead.x), z = (int) Math.floor(lead.z);
            CruiseRoute r = f.cruise().withEnd(x, z);
            int dur = r.ticks();
            if (f.start() + dur <= now + 5) continue;
            list.set(i, f.withCruiseEnd(x, z, r, dur, s.worldBounds().minY));
            mgr.setDirty();
            mgr.syncSoon();
        }
    }

    public static void assignLock(ServerLevel sl, FlightManager mgr, Flight f, UUID owner) {
        if (owner == null || f == null || !SableBridge.active() || !f.build().uplink() || f.isCruise() || f.build().hypersonic()) return;
        long now = sl.getGameTime();
        ShipTargets.Desig d = ShipTargets.get(owner, now);
        if (d == null) return;
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, d.ship());
        if (s == null) return;
        Vec3 c = s.worldCenter();
        if (Math.hypot(c.x - (f.tx() + 0.5), c.z - (f.tz() + 0.5)) > Balance.SHIP_LOCK_AIM_RADIUS) return;
        mgr.shipLocks.put(f.id(), d.ship());
        Wmd.LOGGER.info("Uplink: flight {} locks onto airship {} ({})", f.id(), s.name(), d.ship());
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        SAMPLES.clear();
        PEND.clear();
        READY.clear();
        LOGGED.clear();
        warned = false;
    }
}
