package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TvGuidance {

    private TvGuidance() {}

    private record Win(int cx, int cz, long last) {}

    private static final Map<Long, Win> WINDOWS = Statics.server(new HashMap<>());

    private static final java.util.Set<Long> STEERED = Statics.server(new java.util.HashSet<>());

    public static boolean isTv(Flight f) { return f != null && f.build() != null && MissileBuild.SEEK_TV.equals(f.build().seeker()); }

    public static boolean window(Flight f, long now) {
        if (!isTv(f) || f.isCruise() || now < f.start() || f.endTime() - now < 10) return false;
        return f.inDive(now);
    }

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        if (now % 5 != 0) return;
        for (Flight f : mgr.flights()) {
            if (WINDOWS.containsKey(f.id()) || !window(f, now)) continue;
            WINDOWS.put(f.id(), new Win(f.tx(), f.tz(), now - Balance.TV_STEP));
            ServerPlayer owner = owner(level, f);
            if (owner != null) PacketDistributor.sendToPlayer(owner, new TvWindowPayload(f.id(), f.tx(), f.tz(), f.isCruise()));
        }
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && !WINDOWS.isEmpty())
            WINDOWS.keySet().removeIf(id -> !FlightManager.alive(level.getServer(), id));
    }

    private static ServerPlayer owner(ServerLevel level, Flight f) {
        if (f.ownerId() == null || f.ownerId().isEmpty()) return null;
        try { return level.getServer().getPlayerList().getPlayer(UUID.fromString(f.ownerId())); } catch (IllegalArgumentException e) { return null; }
    }

    public static void steer(ServerPlayer sp, long id, int x, int z, boolean dive) {
        ServerLevel level = sp.serverLevel();
        FlightManager mgr = FlightManager.get(level);
        Flight f = mgr.find(id);
        long now = level.getGameTime();
        Win w = WINDOWS.get(id);
        if (f == null || w == null || !sp.getUUID().toString().equals(f.ownerId()) || !window(f, now)) return;
        if (now - w.last() < Balance.TV_STEP) return;
        WINDOWS.put(id, new Win(w.cx(), w.cz(), now));

        Vec3 p = f.basePosition(now);
        double r = Math.tan(Math.toRadians(Balance.TV_CONE)) * Math.max(0.0, p.y - f.endY());
        double ax = f.tx() + 0.5, az = f.tz() + 0.5, dx = x + 0.5 - ax, dz = z + 0.5 - az, d = Math.hypot(dx, dz);
        double step = Math.min(Math.min(d, r), Math.max(Balance.TV_SLEW_MIN, r * Balance.TV_SLEW));
        if (d < 0.75 || step < 0.75) return;
        int nx = (int) Math.floor(ax + dx / d * step), nz = (int) Math.floor(az + dz / d * step);
        if (nx == f.tx() && nz == f.tz()) return;
        Flight.Leg leg = f.legAt(now);
        mgr.releg(level, f, nx, nz, leg == null ? 1.0 : leg.k(), Flight.U_HOMING_DONE, 0);
        if (mgr.uplinkLocks.remove(id) != null || (mgr.shipLocks.containsKey(id) && !SeekerGuidance.tracksShip(id) && mgr.shipLocks.remove(id) != null))
            Wmd.LOGGER.info("TV: flight {} steered by hand: its uplink lock is dropped", id);
        if (STEERED.add(id)) Wmd.LOGGER.info("TV: flight {} steered by {} towards {} {} (now {} {}, circle radius {})", id, sp.getGameProfile().getName(), x, z, nx, nz, (int) r);
        if (STEERED.size() > 256) STEERED.clear();
    }
}
