package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirshipWatch {

    private AirshipWatch() {}

    static final double AIRSHIP_HEADING_MAX = 2_000.0;

    private static final long WARN_BASE = Long.MIN_VALUE / 8, WARN_SPAN = 1L << 40;

    public static long warnId(UUID ship) {
        long h = ship.getMostSignificantBits() ^ ship.getLeastSignificantBits();
        h ^= h >>> 29;
        return WARN_BASE + (h & (WARN_SPAN - 1));
    }

    public static boolean isWarning(long id) { return id >= WARN_BASE && id < WARN_BASE + WARN_SPAN; }

    private static final class Seen {
        long lastTick, lastRefresh;
        ShipFaction.Relation rel;
        boolean boardLine;
    }

    private static final Map<UUID, Map<UUID, Seen>> SEEN = new HashMap<>();

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    private record Watcher(UUID owner, double x, double z, double range, Owned machine, EarlyWarningArrayBlockEntity array) {
        boolean sees(Vec3 p) {
            double dx = p.x - x, dz = p.z - z;
            return dx * dx + dz * dz <= range * range;
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 17 || !SableBridge.active()) return;
        for (ServerLevel level : server.getAllLevels()) scan(server, level);
        if (server.getTickCount() % 1200 == 17) prune(server.overworld().getGameTime());
    }

    private static void scan(MinecraftServer server, ServerLevel level) {
        List<SableBridge.ShipInfo> large = new ArrayList<>();
        for (SableBridge.ShipInfo s : SableBridge.allShips(level)) if (s.isLarge()) large.add(s);
        if (large.isEmpty()) return;
        List<Watcher> ws = new ArrayList<>();
        for (EarlyWarningArrayBlockEntity a : EarlyWarningArrayBlockEntity.all()) {
            if (a.isRemoved() || a.getLevel() != level || a.getOwner() == null || !a.isRunning()) continue;
            Vec3 p = SableBridge.worldPos(level, a.getBlockPos());
            ws.add(new Watcher(a.getOwner(), p.x, p.z, Balance.EWA_RANGE, a, a));
        }
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildingsIn(level)) {
            Vec3 p = SableBridge.worldPos(level, b.hallCentre());
            ws.add(new Watcher(b.getOwner(), p.x, p.z, Balance.CC_REACH, b, null));
        }
        if (ws.isEmpty()) return;
        long now = level.getGameTime();
        Map<UUID, List<Watcher>> byOwner = new HashMap<>();
        for (Watcher w : ws) byOwner.computeIfAbsent(w.owner(), k -> new ArrayList<>()).add(w);
        for (Map.Entry<UUID, List<Watcher>> e : byOwner.entrySet()) {
            UUID owner = e.getKey();
            for (SableBridge.ShipInfo s : large) {
                Vec3 c = s.worldCenter();
                Watcher first = null;
                List<EarlyWarningArrayBlockEntity> arrays = new ArrayList<>();
                for (Watcher w : e.getValue()) {
                    if (!w.sees(c)) continue;
                    if (first == null) first = w;
                    if (w.array() != null) arrays.add(w.array());
                }
                if (first == null) continue;
                watch(server, level, owner, s, first.machine(), arrays, now);
            }
        }
    }

    private static void watch(MinecraftServer server, ServerLevel level, UUID owner, SableBridge.ShipInfo s, Owned machine,
                              List<EarlyWarningArrayBlockEntity> arrays, long now) {
        ShipFaction.Relation rel = ShipFaction.relation(level, s.id(), owner, machine);
        Map<UUID, Seen> m = SEEN.computeIfAbsent(owner, k -> new HashMap<>());
        Seen seen = m.get(s.id());
        boolean fresh = seen == null || now - seen.lastTick > Balance.AIRSHIP_FORGET_TICKS || now < seen.lastTick;
        boolean escalated = !fresh && seen.rel != ShipFaction.Relation.HOSTILE && rel == ShipFaction.Relation.HOSTILE;
        if (fresh || escalated) {
            if (rel != ShipFaction.Relation.FRIENDLY && !allow(owner, now)) return;
            if (seen == null) { seen = new Seen(); m.put(s.id(), seen); }
            seen.boardLine = false;
            announce(server, level, owner, s, rel, arrays, now);
            if (rel == ShipFaction.Relation.HOSTILE) { seen.boardLine = true; seen.lastRefresh = now; }
        } else if (seen.boardLine && rel == ShipFaction.Relation.HOSTILE && now - seen.lastRefresh >= Balance.AIRSHIP_REFRESH_TICKS) {
            CommandCenter.refreshWarning(owner, warning(s, now));
            seen.lastRefresh = now;
        }
        seen.lastTick = now;
        seen.rel = rel;
    }

    private static boolean allow(UUID owner, long now) {
        long[] r = RATE.computeIfAbsent(owner, k -> new long[] { now, 0 });
        if (now - r[0] >= 1200 || now < r[0]) { r[0] = now; r[1] = 0; }
        if (r[1] >= Balance.AIRSHIP_WARN_PER_MIN) return false;
        r[1]++;
        return true;
    }

    static CommandCenter.Warning warning(SableBridge.ShipInfo s, long now) {
        Vec3 c = s.worldCenter(), v = s.linearVelocity();
        double hx = v.x * Balance.AIRSHIP_HEADING_SECONDS, hz = v.z * Balance.AIRSHIP_HEADING_SECONDS, hl = Math.sqrt(hx * hx + hz * hz);
        if (hl > AIRSHIP_HEADING_MAX) { hx *= AIRSHIP_HEADING_MAX / hl; hz *= AIRSHIP_HEADING_MAX / hl; }
        if (!Double.isFinite(hx) || !Double.isFinite(hz)) { hx = 0.0; hz = 0.0; }
        return new CommandCenter.Warning(warnId(s.id()), (int) Math.round(c.x), (int) Math.round(c.z), (int) Math.round(c.x + hx), (int) Math.round(c.z + hz),
                now, now - 1, false);
    }

    private static void announce(MinecraftServer server, ServerLevel level, UUID owner, SableBridge.ShipInfo s, ShipFaction.Relation rel,
                                 List<EarlyWarningArrayBlockEntity> arrays, long now) {
        if (rel == ShipFaction.Relation.FRIENDLY) return;
        boolean hostile = rel == ShipFaction.Relation.HOSTILE;
        Vec3 c = s.worldCenter();
        String x = Long.toString(Math.round(c.x)), z = Long.toString(Math.round(c.z)), blocks = Integer.toString(s.blockCount());
        String name = s.name() == null ? "?" : s.name();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (hostile) {
            CommandCenter.addWarning(server, owner, warning(s, now));
            CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_IN, "log.wmd.airship_hostile", name, blocks, x, z);
            if (p != null) {
                Component msg = Component.translatable("message.wmd.airship.hostile", name, blocks, x, z);
                p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.RED));
                p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
                p.playNotifySound(WmdSounds.CC_ALERT.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            }
        } else {
            CcBattleLog.add(server, owner, CcBattleLog.AIRSHIP, "log.wmd.airship_neutral", name, blocks, x, z);
            if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.airship.neutral", name, blocks, x, z).withStyle(ChatFormatting.GRAY));
        }
        for (EarlyWarningArrayBlockEntity a : arrays) a.noteAirship(Math.round(c.x), Math.round(c.z), s.blockCount(), hostile, now);
    }

    private static void prune(long now) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Seen>>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map<UUID, Seen> m = it.next().getValue();
            m.values().removeIf(s -> now - s.lastTick > Balance.AIRSHIP_FORGET_TICKS || now < s.lastTick);
            if (m.isEmpty()) it.remove();
        }
        RATE.values().removeIf(r -> now - r[0] > 2400 || now < r[0]);
    }

    static Map<UUID, String> known(UUID owner) {
        Map<UUID, String> out = new HashMap<>();
        Map<UUID, Seen> m = owner == null ? null : SEEN.get(owner);
        if (m != null) for (Map.Entry<UUID, Seen> e : m.entrySet()) out.put(e.getKey(), e.getValue().rel == null ? "?" : e.getValue().rel.name());
        return out;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        SEEN.clear();
        RATE.clear();
    }
}
