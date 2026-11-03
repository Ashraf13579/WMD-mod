package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class DroneWarnings {

    private DroneWarnings() {}

    static final int TICKS = 40, HEAD_BLOCKS = 400;
    private static final long DRONE_BASE = Long.MIN_VALUE / 16, BOMB_BASE = Long.MIN_VALUE / 32, SPAN = 1L << 40;
    private static final Set<String> ANNOUNCED = new HashSet<>();
    private static long bombSeq;

    public static boolean isDrone(long id) { return id >= DRONE_BASE && id < DRONE_BASE + SPAN; }
    public static boolean isBomb(long id) { return id >= BOMB_BASE && id < BOMB_BASE + SPAN; }
    public static boolean isWarning(long id) { return isDrone(id) || isBomb(id); }

    static void drones(ServerLevel level, List<Drones.Drone> drones, long now) {
        List<CcBuildingBlockEntity> bs = CommandCenter.activeBuildingsIn(level);
        if (bs.isEmpty()) { ANNOUNCED.clear(); return; }
        MinecraftServer server = level.getServer();
        FlightIntel.Radars radars = new FlightIntel.Radars(level, now);
        double r2 = (double) Balance.CC_DRONE_WARN_R * Balance.CC_DRONE_WARN_R;
        Set<String> seen = new HashSet<>();
        Map<UUID, Set<UUID>> teams = new HashMap<>();
        for (Drones.Drone d : drones) {
            if (d.kind < 3 || !d.airborne()) continue;
            Set<UUID> done = new HashSet<>();
            for (CcBuildingBlockEntity b : bs) {
                UUID o = b.getOwner();
                if (o.equals(d.owner) || done.contains(o)) continue;
                double dx = d.x - (b.getBlockPos().getX() + 0.5), dz = d.z - (b.getBlockPos().getZ() + 0.5);
                if (dx * dx + dz * dz > r2) continue;
                Set<UUID> team = teams.computeIfAbsent(o, CommandCenter::ownersTrusting);
                if (!radars.seesPoint(o, team, d.x, d.z, Double.MAX_VALUE, 1.0)) continue;
                done.add(o);
                String key = o + "|" + d.id;
                seen.add(key);
                double sp = Math.hypot(d.vx, d.vz);
                int hx = (int) (Math.round((d.x + (sp > 1.0E-3 ? d.vx / sp * HEAD_BLOCKS : 0.0)) / 10.0) * 10L);
                int hz = (int) (Math.round((d.z + (sp > 1.0E-3 ? d.vz / sp * HEAD_BLOCKS : 0.0)) / 10.0) * 10L);
                CommandCenter.Warning w = new CommandCenter.Warning(DRONE_BASE + (d.id & (SPAN - 1)), (int) Math.round(d.x), (int) Math.round(d.z), hx, hz, now, now + TICKS * 2,
                        false, CommandCenter.Warning.WHAT_DRONE, d.kind, false, "?", "");
                if (ANNOUNCED.add(key)) CommandCenter.addWarning(server, o, w);
                else CommandCenter.refreshWarning(o, w);
            }
        }
        ANNOUNCED.retainAll(seen);
    }

    static void bomb(ServerLevel level, UUID bomber, double x, double z, double lx, double lz, long ticks) {
        List<CcBuildingBlockEntity> bs = CommandCenter.activeBuildingsIn(level);
        if (bs.isEmpty()) return;
        long now = level.getGameTime();
        FlightIntel.Radars radars = null;
        double r2 = (double) Balance.CC_BOMB_WARN_R * Balance.CC_BOMB_WARN_R;
        Set<UUID> done = new HashSet<>();
        for (CcBuildingBlockEntity b : bs) {
            UUID o = b.getOwner();
            if (o.equals(bomber) || done.contains(o)) continue;
            double dx = lx - (b.getBlockPos().getX() + 0.5), dz = lz - (b.getBlockPos().getZ() + 0.5);
            if (dx * dx + dz * dz > r2) continue;
            if (radars == null) radars = new FlightIntel.Radars(level, now);
            if (!radars.seesPoint(o, CommandCenter.ownersTrusting(o), x, z, Double.MAX_VALUE, 1.0)) continue;
            done.add(o);
            long id = BOMB_BASE + (++bombSeq & (SPAN - 1));
            CommandCenter.addWarning(level.getServer(), o, new CommandCenter.Warning(id, (int) Math.round(x), (int) Math.round(z), (int) (Math.round(lx / 10.0) * 10L),
                    (int) (Math.round(lz / 10.0) * 10L), now, now + Math.max(20L, ticks), false, CommandCenter.Warning.WHAT_BOMB, 0, false, "?", ""));
        }
    }

    static void clear() { ANNOUNCED.clear(); }
}
