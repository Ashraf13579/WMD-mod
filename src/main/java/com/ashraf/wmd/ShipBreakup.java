package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipBreakup {

    private ShipBreakup() {}

    private record Check(ServerLevel level, UUID id, Vec3 at, float power, long due) {}

    private record Mark(ServerLevel level, AABB box, long due, UUID parent) {}

    private static final class Burn {
        final ServerLevel level;
        long until;
        Vec3 last;
        double peakFall;
        boolean crashed;

        Burn(ServerLevel level, long until) { this.level = level; this.until = until; }
    }

    private static final Map<UUID, Integer> FIRST = Statics.server(new HashMap<>());
    private static final Map<UUID, Long> FRACTURED = Statics.server(new HashMap<>());
    private static final Map<UUID, Burn> BURNING = Statics.server(new HashMap<>());
    private static final List<Check> CHECKS = Statics.server(new ArrayList<>());
    private static final List<Mark> MARKS = Statics.server(new ArrayList<>());

    static void hit(ServerLevel level, SableBridge.ShipInfo ship, Vec3 at, float power) {
        if (!Config.Server.shipBreakup() || ship == null) return;
        FIRST.putIfAbsent(ship.id(), Math.max(1, ship.blockCount()));
        if (CHECKS.size() < Balance.SHIP_CHECKS_MAX) CHECKS.add(new Check(level, ship.id(), at, power, level.getGameTime() + Balance.SHIP_CHECK_DELAY));
    }

    private static void burn(ServerLevel level, UUID id, long now) {
        Burn b = BURNING.get(id);
        if (b == null) BURNING.put(id, new Burn(level, now + Balance.SHIP_BURN_TICKS));
        else b.until = Math.max(b.until, now + Balance.SHIP_BURN_TICKS);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || (CHECKS.isEmpty() && MARKS.isEmpty() && BURNING.isEmpty())) return;
        long now = sl.getGameTime();
        for (Iterator<Check> it = CHECKS.iterator(); it.hasNext(); ) {
            Check c = it.next();
            if (c.level != sl || now < c.due) continue;
            it.remove();
            judge(sl, c, now);
        }
        for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
            Mark m = it.next();
            if (m.level != sl || now < m.due) continue;
            it.remove();
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, m.box)) {

                if (AirGate.on() && !s.id().equals(m.parent())) AirPicture.markWreck(s.id());
                burn(sl, s.id(), now);
                FRACTURED.putIfAbsent(s.id(), now);
                FIRST.putIfAbsent(s.id(), Math.max(1, s.blockCount()));
            }
        }
        if (now % Balance.SHIP_FIRE_EVERY == 0) fires(sl, now);
    }

    private static void judge(ServerLevel level, Check c, long now) {
        SableBridge.ShipInfo info = SableBridge.shipInfoFresh(level, c.id);
        if (info == null) return;
        int first = FIRST.getOrDefault(c.id, Math.max(1, info.blockCount()));
        double lost = 1.0 - info.blockCount() / (double) first;
        boolean breaks = c.power >= Balance.SHIP_FRACTURE_POWER || lost >= Balance.SHIP_FRACTURE_LOSS;
        if (breaks || lost >= Balance.SHIP_BURN_LOSS) burn(level, c.id, now);
        if (!breaks || now - FRACTURED.getOrDefault(c.id, -100_000L) < Balance.SHIP_FRACTURE_COOLDOWN || LoadGuard.overloaded()) return;
        if (!Config.Server.blockDamage() || !Config.Server.canBreakShip(level, BlockPos.containing(c.at))) return;
        int cut = SableBridge.shipFracture(level, c.id, c.at);
        if (cut <= 0) return;
        FRACTURED.put(c.id, now);
        MARKS.add(new Mark(level, info.worldBounds().inflate(2.0), now + Balance.SHIP_MARK_DELAY, c.id));
        Wmd.LOGGER.info("[ship] {} fractured at {} {} {}: {} blocks cut (lost {} %, blast power {})", info.name(), (int) c.at.x, (int) c.at.y, (int) c.at.z, cut,
                Math.round(lost * 100), Math.round(c.power));
    }

    private static void fires(ServerLevel level, long now) {
        for (Iterator<Map.Entry<UUID, Burn>> it = BURNING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Burn> e = it.next();
            Burn b = e.getValue();
            if (b.level != level) continue;
            SableBridge.ShipInfo info = SableBridge.shipInfo(level, e.getKey());
            if (info == null || now > b.until) { it.remove(); continue; }
            Vec3 c = info.worldCenter(), v = b.last == null ? Vec3.ZERO : c.subtract(b.last).scale(20.0 / Balance.SHIP_FIRE_EVERY);
            b.last = c;
            b.peakFall = Math.max(b.peakFall, -v.y);
            if (!b.crashed && b.peakFall >= Balance.SHIP_CRASH_SPEED && v.length() < Balance.SHIP_CRASH_STOP) { b.crashed = true; crash(level, info, v); }
            AABB box = info.worldBounds();
            float heat = (float) Math.max(0.2, Math.min(1.0, (b.until - now) / (double) Balance.SHIP_BURN_TICKS * 1.5));
            PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, Balance.SHIP_FIRE_VIEW, new ShipFirePayload(e.getKey(), c.x, c.y, c.z,
                    (float) (box.getXsize() * 0.5), (float) (box.getYsize() * 0.5), (float) (box.getZsize() * 0.5), (float) (v.x / 20.0), (float) (v.y / 20.0), (float) (v.z / 20.0), heat));
        }
    }

    private static void crash(ServerLevel level, SableBridge.ShipInfo info, Vec3 v) {
        int blocks = info.blockCount(), tier = blocks < Balance.SHIP_CRASH_T2 ? 1 : blocks < Balance.SHIP_CRASH_T3 ? 2 : 3;
        int x = (int) Math.floor(info.worldCenter().x), y = (int) Math.floor(info.worldBounds().minY), z = (int) Math.floor(info.worldCenter().z);
        double h = Math.hypot(v.x, v.z);
        FlightManager mgr = FlightManager.get(level);
        mgr.holdForTest(level, x, z, WarheadFx.holdRadius(tier, WarheadType.HE));
        WarheadFx.detonate(level, mgr, x, y, z, tier, WarheadType.HE, h > 1.0E-3 ? v.x / h : 0.0, h > 1.0E-3 ? v.z / h : 1.0);
        Wmd.LOGGER.info("[ship] {} crashed at {} {} {} ({} blocks: an HE tier {} blast)", info.name(), x, y, z, blocks, tier);
    }
}
