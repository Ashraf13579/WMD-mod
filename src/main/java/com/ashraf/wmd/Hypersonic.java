package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class Hypersonic {

    private Hypersonic() {}

    public static boolean isHyper(Flight f) { return f != null && f.build() != null && f.build().hypersonic(); }

    public static Component refuse(ServerLevel level, MissileBuild b, UUID owner, int tx, int tz) {
        if (b == null || !b.hypersonic()) return null;
        if (!MissileBuild.SEEK_LASER.equals(b.seeker())) return Component.translatable("message.wmd.hyper.need_seeker");
        boolean satOnly = b.calibre() >= 4 || b.staged();
        for (LaserSpots.Spot s : LaserSpots.all()) {
            if (owner == null || !owner.equals(s.owner()) || !s.dim().equals(level.dimension()) || LaserSpots.get(level, s.id()) == null) continue;
            if (Math.hypot(s.pos().x - (tx + 0.5), s.pos().z - (tz + 0.5)) > Balance.SEEKER_RADIUS) continue;
            if (satOnly && s.source() != LaserSpots.SRC_SATELLITE) continue;
            return null;
        }
        return Component.translatable(satOnly ? "message.wmd.hyper.need_sat" : "message.wmd.hyper.need_spot", Balance.SEEKER_RADIUS);
    }

    private static final java.util.Set<Long> WARNED = new java.util.HashSet<>();

    static void clearStatics() { WARNED.clear(); }

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        if (now % 5 != 0) return;
        for (Flight f : mgr.flights()) {
            if (!isHyper(f) || hidden(f, now) || now >= f.endTime() || !WARNED.add(f.id())) continue;
            Recon.onLaunch(level, f);
            Wmd.LOGGER.info("Hypersonic: flight {} is seen now, {} s before impact", f.id(), (f.endTime() - now) / 20);
            incoming(level, f, now);
        }
        if (WARNED.size() > 512) WARNED.removeIf(id -> !FlightManager.alive(level.getServer(), id));
    }

    public static final double INCOMING_RANGE = 1000.0;

    private static void incoming(ServerLevel level, Flight f, long now) {
        long end = f.endTime(), dive = end;
        for (long t = now; t < end; t++) if (f.inDive(t)) { dive = t; break; }
        int n = HyperIncomingPayload.DIVE_POINTS;
        float[] pts = new float[3 * (n + 1)];
        net.minecraft.world.phys.Vec3 p = f.position(now);
        pts[0] = (float) p.x; pts[1] = (float) p.y; pts[2] = (float) p.z;
        for (int k = 0; k < n; k++) {
            net.minecraft.world.phys.Vec3 q = f.position(dive + (end - dive) * (double) k / (n - 1));
            pts[3 + 3 * k] = (float) q.x; pts[4 + 3 * k] = (float) q.y; pts[5 + 3 * k] = (float) q.z;
        }
        HyperIncomingPayload pl = new HyperIncomingPayload(f.id(), now, dive, end, pts);
        double tx = f.tx() + 0.5, tz = f.tz() + 0.5;
        for (net.minecraft.server.level.ServerPlayer sp : level.players())
            if (Math.hypot(sp.getX() - tx, sp.getZ() - tz) <= INCOMING_RANGE || sp.getUUID().toString().equals(f.ownerId()))
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, pl);
    }

    public static boolean hidden(Flight f, long now) { return isHyper(f) && f.endTime() - now > Balance.HYPER_WARN * 20L; }
}
