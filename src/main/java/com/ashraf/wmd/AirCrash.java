package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
final class AirCrash {

    private AirCrash() {}

    private static final class Track {
        Vec3 v1, v2;
        long t = Long.MIN_VALUE, excuseUntil, lastHit = Long.MIN_VALUE / 2;
        double minVy;
        Vec3 acc = Vec3.ZERO;
        long minVyAt;
    }

    private static final Map<UUID, Track> TRACK = Statics.server(new HashMap<>());

    private static final List<Object[]> PENDING = Statics.server(new ArrayList<>());

    @net.neoforged.bus.api.SubscribeEvent
    static void endTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Object[]> todo = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Object[] e : todo) {
            ServerLevel sl = (ServerLevel) e[0];
            BlockPos p = (BlockPos) e[1];
            if (sl.isLoaded(p) && !sl.getBlockState(p).isAir()) sl.destroyBlock(p, false);
        }
    }

    private static void breakLater(ServerLevel sl, BlockPos p) { PENDING.add(new Object[] { sl, p.immutable() }); }

    static void excuse(UUID ship, long now, int ticks) {
        if (ship == null) return;
        Track t = TRACK.computeIfAbsent(ship, k -> new Track());
        t.excuseUntil = Math.max(t.excuseUntil, now + ticks);
    }

    static double sinkRate(UUID ship, long now) {
        Track t = ship == null ? null : TRACK.get(ship);
        if (t == null || now - t.minVyAt > 4) return t == null || t.v1 == null ? 0.0 : Math.max(0.0, -t.v1.y);
        return Math.max(0.0, -t.minVy);
    }

    static double loadFactor(UUID ship, Vec3 up) {
        Track t = ship == null ? null : TRACK.get(ship);
        if (t == null || up == null || up.lengthSqr() < 1.0E-9) return 1.0;
        return t.acc.add(0.0, WingTrails.GRAV, 0.0).dot(up.normalize()) / WingTrails.GRAV;
    }

    static void tick(ServerLevel sl, UUID ship) {
        if (sl == null || ship == null) return;
        long now = sl.getGameTime();
        Track tr = TRACK.computeIfAbsent(ship, k -> new Track());
        if (tr.t == now) return;
        if (TRACK.size() > 256) TRACK.entrySet().removeIf(e -> now - e.getValue().t > 200);
        SableBridge.ShipInfo si = SableBridge.shipInfoFresh(sl, ship);
        if (si == null || si.linearVelocity() == null) { tr.t = now; tr.v1 = tr.v2 = null; return; }
        Vec3 v = si.linearVelocity();
        boolean steady = tr.t == now - 1;
        Vec3 p1 = steady ? tr.v1 : null, p2 = steady ? tr.v2 : null;
        tr.v2 = p1; tr.v1 = v; tr.t = now;
        if (p1 != null) tr.acc = tr.acc.scale(0.6).add(v.subtract(p1).scale(20.0 * 0.4));
        if (v.y < tr.minVy || now - tr.minVyAt > 4) { tr.minVy = v.y; tr.minVyAt = now; }
        if (p1 == null || !Config.Server.crashDamage() || !WmdGameRules.crashDamage(sl) || !AirGate.on()) return;
        if (now < tr.excuseUntil || now - tr.lastHit < Balance.CRASH_COOLDOWN) return;
        double s0 = p1.length(), s = v.length();
        if (s0 > 400.0 || s > 400.0) return;
        Vec3 lost1 = p1.subtract(v);
        if (lost1.length() < Balance.CRASH_TRIGGER || s > s0 - 0.5 * Balance.CRASH_TRIGGER) return;
        Vec3 lost = (p2 != null ? p2 : p1).subtract(v);
        int gear = LandingGearBlockEntity.stateAboard(sl, ship);
        if (gear >= 0 && gear % 10 == 2 && Math.abs(lost.y) > 0.7 * lost.length()) return;
        double impact = lost.length();
        if (impact < Balance.CRASH_MIN) return;
        tr.lastHit = now;
        hit(sl, si, ship, lost.normalize(), impact);
    }

    private static void hit(ServerLevel sl, SableBridge.ShipInfo si, UUID ship, Vec3 dir, double impact) {
        int[] box = SableBridge.plotBox(sl, ship);
        if (box == null) return;
        long vol = (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
        if (vol <= 0 || vol > Balance.CRASH_SCAN_MAX) return;

        List<BlockPos> plot = new ArrayList<>();
        List<Vec3> world = new ArrayList<>();
        double best = -1.0E9;
        for (int x = box[0]; x <= box[3]; x++)
            for (int y = box[1]; y <= box[4]; y++)
                for (int z = box[2]; z <= box[5]; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (sl.getBlockState(p).isAir()) continue;
                    Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(p));
                    if (w == null) continue;
                    plot.add(p); world.add(w);
                    best = Math.max(best, w.dot(dir));
                }
        if (plot.isEmpty()) return;
        Vec3 sumP = Vec3.ZERO, sumW = Vec3.ZERO;
        int n = 0;
        for (int i = 0; i < plot.size(); i++)
            if (world.get(i).dot(dir) >= best - 1.0) { sumP = sumP.add(Vec3.atCenterOf(plot.get(i))); sumW = sumW.add(world.get(i)); n++; }
        Vec3 hitPlot = sumP.scale(1.0 / n), hitWorld = sumW.scale(1.0 / n);

        if (!touching(sl, ship, hitWorld, dir)) {
            Wmd.LOGGER.debug("Air crash: craft {} stopped hard at {} b/s with nothing in front (a tool / push): ignored", ship, String.format("%.1f", impact));
            return;
        }
        double r = Math.min(Balance.CRASH_R_MAX, Balance.CRASH_R_MIN + (impact - Balance.CRASH_MIN) * Balance.CRASH_R_PER);
        int broken = 0;

        for (BlockPos p : plot) {
            double d = plotGap(p, hitPlot);
            BlockEntity be = sl.getBlockEntity(p);
            if (be instanceof AirEngineBlockEntity eng && d <= r + 2.0) { eng.damage(sl, (impact - Balance.CRASH_MIN) * Balance.CRASH_ENGINE_DMG); continue; }
            if (d > r || broken >= Balance.CRASH_MAX_BLOCKS || be instanceof JetCockpitBlockEntity) continue;
            if (keeps(sl, p, be)) continue;
            Vec3 w = SableBridge.toWorld(sl, Vec3.atCenterOf(p));
            if (w == null || !Config.Server.canBreakShip(sl, BlockPos.containing(w))) continue;
            breakLater(sl, p);
            broken++;
        }

        int worldBroken = 0;
        if (Config.Server.crashDamagesWorld()) {
            Vec3 c = hitWorld.add(dir.scale(0.8));
            double rw = Math.max(1.0, r * 0.75);
            int ir = (int) Math.ceil(rw);
            BlockPos cb = BlockPos.containing(c);
            for (BlockPos q : BlockPos.betweenClosed(cb.offset(-ir, -ir, -ir), cb.offset(ir, ir, ir))) {
                if (Vec3.atCenterOf(q).distanceTo(c) > rw || worldBroken >= Balance.CRASH_MAX_BLOCKS) continue;
                if (SableBridge.isOnShip(sl, q)) continue;
                BlockState s = sl.getBlockState(q);
                if (!BlockRules.blastRemovable(sl, q, s)) continue;
                breakLater(sl, q);
                worldBroken++;
            }
        }

        int hurt = 0, spared = 0;
        if (Config.Server.crashHurtsCrew()) {
            float dmg = (float) ((impact - Balance.CRASH_MIN) * Balance.CRASH_HURT_PER + Balance.CRASH_HURT_MIN);
            AABB box2 = si.worldBounds() == null ? null : si.worldBounds().inflate(1.5);
            for (ServerPlayer pl : sl.players()) {
                if (!aboard(sl, pl, ship, box2)) continue;
                if (pl.isSpectator() || pl.isCreative()) { spared++; continue; }
                pl.hurt(sl.damageSources().flyIntoWall(), dmg);
                hurt++;
            }
        }
        float loud = Math.min(Balance.SND_RELEASE, Balance.SND_MECH * (float) (impact / Balance.CRASH_MIN));
        sl.playSound(null, hitWorld.x, hitWorld.y, hitWorld.z, WmdSounds.HE_DEBRIS.get(), SoundSource.BLOCKS, loud, 1.1F);
        Wmd.LOGGER.info("Air crash: craft {} hit at {} b/s (radius {}), {} craft blocks off, {} world blocks broken, {} crew hurt{}", ship, String.format("%.1f", impact),
                String.format("%.1f", r), broken, worldBroken, hurt, spared > 0 ? " (" + spared + " in creative / spectator: spared)" : "");
    }

    private static double plotGap(BlockPos p, Vec3 c) {
        double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean keeps(ServerLevel sl, BlockPos p, BlockEntity be) {
        if (be == null || be instanceof WingSectionBlock.BE || be instanceof ControlSurfaceBlock.BE) return false;
        if (be instanceof JetCockpitBlockEntity || be instanceof ProcessUnitBlockEntity || be instanceof net.minecraft.world.Container) return true;
        return sl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, p, null) != null;
    }

    private static boolean aboard(ServerLevel sl, ServerPlayer pl, UUID ship, AABB worldBox) {
        if (ship.equals(SableBridge.shipUnder(pl))) return true;
        if (!pl.isPassenger()) return false;
        if (pl.getVehicle() instanceof CockpitSeatEntity seat && seat.cockpit() != null) return ship.equals(SableBridge.shipIdAt(sl, seat.cockpit()));
        return ship.equals(SableBridge.shipIdAt(sl, pl.getVehicle().blockPosition())) || (worldBox != null && worldBox.contains(pl.position()));
    }

    private static boolean touching(ServerLevel sl, UUID ship, Vec3 hitWorld, Vec3 dir) {
        Vec3 c = hitWorld.add(dir.scale(0.5));
        double t = Balance.CRASH_TOUCH;
        BlockPos lo = BlockPos.containing(c.x - t, c.y - t, c.z - t), hi = BlockPos.containing(c.x + t, c.y + t, c.z + t);
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            if (!sl.isLoaded(q) || SableBridge.isOnShip(sl, q)) continue;
            BlockState s = sl.getBlockState(q);
            if (!s.isAir() && !s.getCollisionShape(sl, q).isEmpty()) return true;
        }
        AABB box = new AABB(c.x - t, c.y - t, c.z - t, c.x + t, c.y + t, c.z + t);
        for (SableBridge.ShipInfo other : SableBridge.shipsIntersecting(sl, box)) if (!ship.equals(other.id())) return true;
        return false;
    }
}
