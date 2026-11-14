package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcContactWatch {

    private CcContactWatch() {}

    public static final int WHAT_AIRCRAFT = 3, WHAT_TORPEDO = 4, WHAT_SONAR = 5;

    private static final long NAVAL_BASE = Long.MIN_VALUE / 64, SPAN = 1L << 40;

    public static boolean isNaval(long id) { return id >= NAVAL_BASE && id < NAVAL_BASE + SPAN; }

    private static long navalId(long h) { h ^= h >>> 29; return NAVAL_BASE + (h & (SPAN - 1)); }

    private static final Map<UUID, Map<Object, long[]>> SEEN = Statics.server(new HashMap<>());

    public static boolean isContact(CommandCenter.Warning w) {
        return isNaval(w.id()) || (AirshipWatch.isWarning(w.id()) && w.what() == WHAT_AIRCRAFT);
    }

    static void aircraft(ServerLevel level, UUID owner, AirPicture.Aircraft a, long now) {
        if (!AirGate.on() || owner == null || a == null || !CommandCenter.active(owner)) return;
        Vec3 c = a.pos(), v = a.vel() == null ? Vec3.ZERO : a.vel().scale(20.0);
        double hx = v.x * Balance.AIRSHIP_HEADING_SECONDS, hz = v.z * Balance.AIRSHIP_HEADING_SECONDS, hl = Math.sqrt(hx * hx + hz * hz);
        if (hl > AirshipWatch.AIRSHIP_HEADING_MAX) { hx *= AirshipWatch.AIRSHIP_HEADING_MAX / hl; hz *= AirshipWatch.AIRSHIP_HEADING_MAX / hl; }
        if (!Double.isFinite(hx) || !Double.isFinite(hz)) { hx = 0.0; hz = 0.0; }
        CommandCenter.Warning w = new CommandCenter.Warning(AirshipWatch.warnId(a.id()), (int) Math.round(c.x), (int) Math.round(c.z),
                (int) Math.round(c.x + hx), (int) Math.round(c.z + hz), now, now - 1, false, WHAT_AIRCRAFT, 0, false, "?", "");
        String name = a.name() == null || a.name().isEmpty() ? "?" : a.name();
        post(level.getServer(), owner, a.id(), w, now, "log.wmd.aircraft_hostile", name, Long.toString(Math.round(c.x)), Long.toString(Math.round(c.z)));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        for (CcBuildingBlockEntity b : CommandCenter.buildings()) {
            if (b.isRemoved() || !(b.getLevel() instanceof ServerLevel level) || !b.isActive() || b.getOwner() == null) continue;
            UUID owner = b.getOwner();
            long now = level.getGameTime();
            Vec3 at = Vec3.atCenterOf(b.getBlockPos());
            double r = Balance.CC_NAVAL_RANGE;
            for (TorpedoEntity t : level.getEntitiesOfClass(TorpedoEntity.class, new AABB(at, at).inflate(r, 256.0, r))) {
                if (owner.equals(t.owner()) || t.position().distanceTo(at) > r) continue;
                naval(server, owner, "t" + t.getUUID(), WHAT_TORPEDO, t.position(), t.getDeltaMovement().scale(20.0), now, "log.wmd.torpedo_near");
            }
            for (SonarNet.Fix f : SonarNet.ownerFixes(level, owner)) {
                Vec3 p = new Vec3(f.x(), f.y(), f.z());
                if (p.distanceTo(at) > r) continue;
                String key = f.ship() != null ? "s" + f.ship() : "f" + Math.round(f.x() / 16.0) + "," + Math.round(f.z() / 16.0);
                naval(server, owner, key, WHAT_SONAR, p, Vec3.ZERO, now, "log.wmd.sonar_near");
            }
        }
        if (server.getTickCount() % 1200 == 7) {
            long now = server.overworld().getGameTime();
            for (Map<Object, long[]> m : SEEN.values())
                for (Iterator<long[]> it = m.values().iterator(); it.hasNext(); ) if (now - it.next()[0] > Balance.AIRSHIP_FORGET_TICKS) it.remove();
        }
    }

    private static void naval(MinecraftServer server, UUID owner, String key, int what, Vec3 p, Vec3 v, long now, String logKey) {
        double hx = v.x * Balance.AIRSHIP_HEADING_SECONDS, hz = v.z * Balance.AIRSHIP_HEADING_SECONDS;
        if (!Double.isFinite(hx) || !Double.isFinite(hz)) { hx = 0.0; hz = 0.0; }
        CommandCenter.Warning w = new CommandCenter.Warning(navalId(key.hashCode() * 0x9E3779B97F4A7C15L), (int) Math.round(p.x), (int) Math.round(p.z),
                (int) Math.round(p.x + hx), (int) Math.round(p.z + hz), now, now - 1, false, what, 0, false, "?", "");
        post(server, owner, key, w, now, logKey, Long.toString(Math.round(p.x)), Long.toString(Math.round(p.z)));
    }

    private static void post(MinecraftServer server, UUID owner, Object key, CommandCenter.Warning w, long now, String logKey, String... args) {
        if (server == null) return;
        Map<Object, long[]> m = SEEN.computeIfAbsent(owner, k -> new HashMap<>());
        long[] e = m.get(key);
        if (e == null || now - e[0] > Balance.AIRSHIP_FORGET_TICKS) {
            m.put(key, new long[] { now, now });
            CommandCenter.addWarning(server, owner, w);
            CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_IN, logKey, args);
            return;
        }
        e[0] = now;
        if (now - e[1] >= Balance.AIRSHIP_REFRESH_TICKS) { e[1] = now; CommandCenter.refreshWarning(owner, w); }
    }
}
