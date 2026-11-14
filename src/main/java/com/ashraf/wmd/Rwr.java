package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Rwr {

    private Rwr() {}

    public static final int SEARCH = 0, LOCK = 1, LAUNCH = 2;
    static final String[] KIND_NAMES = { "SEARCH", "LOCK", "LAUNCH" };

    public record Event(int kind, String key, Vec3 from, UUID emitterOwner, String source, long first, long last, long until, boolean active) {}

    private static final class Receiver {
        final ResourceKey<Level> dim;
        final Map<String, Event> events = new LinkedHashMap<>();
        boolean sentSome;
        Receiver(ResourceKey<Level> dim) { this.dim = dim; }
    }

    private static final Map<UUID, Receiver> SETS = Statics.server(new HashMap<>());

    public static void search(ServerLevel level, UUID aircraft, Vec3 from, UUID owner, String source, long emitter, long now) {
        put(level, aircraft, SEARCH, "s|" + source + "|" + emitter, from, owner, source, now, Balance.RWR_SEARCH_TTL, false);
    }

    public static void lock(ServerLevel level, UUID aircraft, Vec3 from, UUID owner, String source, long emitter, long now) {
        put(level, aircraft, LOCK, "l|" + source + "|" + emitter, from, owner, source, now, Balance.RWR_LOCK_TTL, false);
    }

    public static void launch(ServerLevel level, UUID aircraft, long missileId, Vec3 from, UUID owner, String source, boolean active, long now) {
        put(level, aircraft, LAUNCH, "m|" + missileId, from, owner, source, now, Balance.RWR_LAUNCH_TTL, active);
    }

    public static void missileGone(UUID aircraft, long missileId) {
        Receiver s = aircraft == null ? null : SETS.get(aircraft);
        if (s != null) s.events.remove("m|" + missileId);
    }

    private static String key(Vec3 p) { return (int) Math.floor(p.x) + "," + (int) Math.floor(p.y) + "," + (int) Math.floor(p.z); }

    private static void put(ServerLevel level, UUID aircraft, int kind, String key, Vec3 from, UUID owner, String source, long now, int ttl, boolean active) {
        if (!AirGate.on() || level == null || aircraft == null || from == null) return;
        Receiver s = SETS.get(aircraft);
        if (s == null || s.dim != level.dimension()) {
            if (SETS.size() >= Balance.RWR_MAX_AIRCRAFT) return;
            s = new Receiver(level.dimension());
            SETS.put(aircraft, s);
        }
        Event old = s.events.get(key);
        if (old == null && s.events.size() >= Balance.RWR_MAX_EVENTS) return;
        s.events.put(key, new Event(kind, key, from, owner, source == null ? "" : source, old == null ? now : old.first(), now, now + ttl, active || (old != null && old.active())));
    }

    public static List<Event> of(ServerLevel level, UUID aircraft, long now) {
        Receiver s = aircraft == null ? null : SETS.get(aircraft);
        if (!AirGate.on() || s == null || s.dim != level.dimension()) return List.of();
        List<Event> out = new ArrayList<>();
        for (Event e : s.events.values()) if (e.until() >= now) out.add(e);
        Vec3 at = position(level, aircraft);
        out.sort(Comparator.comparingInt((Event e) -> -e.kind()).thenComparingDouble(e -> at == null ? 0.0 : at.distanceToSqr(e.from())));
        return out;
    }

    public static List<UUID> aircraftWithEvents(ServerLevel level, long now) {
        List<UUID> out = new ArrayList<>();
        if (!AirGate.on()) return out;
        for (Map.Entry<UUID, Receiver> e : SETS.entrySet())
            if (e.getValue().dim == level.dimension() && e.getValue().events.values().stream().anyMatch(x -> x.until() >= now)) out.add(e.getKey());
        return out;
    }

    public static double bearing(Vec3 at, Vec3 from) {
        double b = Math.toDegrees(Math.atan2(from.x - at.x, -(from.z - at.z)));
        return b < 0.0 ? b + 360.0 : b;
    }

    static Vec3 position(ServerLevel level, UUID aircraft) {
        SableBridge.ShipInfo si = SableBridge.shipInfo(level, aircraft);
        if (si != null) return si.worldCenter();
        AirPicture.Aircraft a = AirPicture.get(level, aircraft);
        return a == null ? null : a.pos();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!AirGate.on()) { if (!SETS.isEmpty()) SETS.clear(); return; }
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (SETS.isEmpty() || now % Balance.RWR_SYNC_TICKS != 0) return;
        try {
            sync(sl, now);
        } catch (Throwable t) {
            Wmd.LOGGER.error("WMD RWR sync failed (warnings cleared)", t);
            SETS.clear();
        }
    }

    private static void sync(ServerLevel sl, long now) {
        for (Iterator<Map.Entry<UUID, Receiver>> it = SETS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Receiver> en = it.next();
            Receiver s = en.getValue();
            if (s.dim != sl.dimension()) continue;
            s.events.values().removeIf(e -> e.until() < now);
            Vec3 at = position(sl, en.getKey());
            List<RwrPayload.Line> lines = new ArrayList<>();
            if (at != null)
                for (Event e : of(sl, en.getKey(), now)) {
                    if (lines.size() >= RwrPayload.MAX) break;
                    lines.add(new RwrPayload.Line(e.kind(), (float) bearing(at, e.from()), (float) at.distanceTo(e.from()), (float) (e.from().y - at.y), e.source(), e.active()));
                }
            if (!lines.isEmpty() || s.sentSome) {
                RwrPayload p = new RwrPayload(en.getKey(), lines);
                for (UUID pid : ShipFaction.occupants(sl, en.getKey()).players().keySet()) {
                    ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(pid);
                    if (sp != null && sp.serverLevel() == sl) PacketDistributor.sendToPlayer(sp, p);
                }
                s.sentSome = !lines.isEmpty();
            }
            if (s.events.isEmpty() && !s.sentSome) it.remove();
        }
    }
}
