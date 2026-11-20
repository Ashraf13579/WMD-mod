package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class JetBoom {
    private JetBoom() {}

    private static final class State {
        boolean fast;
        long tick = -1, seen;
        final Map<UUID, Boolean> ahead = new HashMap<>();
        final Map<UUID, Long> last = new HashMap<>();
        Vec3 dir;
        long pullSent = -100;
    }

    private static final Map<UUID, State> STATES = Statics.server(new HashMap<>());

    static void tick(ServerLevel sl, UUID ship, long now) {
        if (now % 200 == 0) STATES.values().removeIf(s -> now - s.seen > 200);
        State s = STATES.computeIfAbsent(ship, k -> new State());
        if (s.tick == now) return;
        long prevTick = s.tick;
        s.tick = now;
        s.seen = now;
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null) return;
        Vec3 v = info.linearVelocity();
        double sp = v.length();
        if (now % 20 == 0 && sp > 15.0) {
            Vec3 c0 = info.worldCenter();
            int ground = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(c0.x), (int) Math.floor(c0.z));
            if (c0.y - ground > 8.0) for (ServerPlayer p : sl.players()) if (aboard(sl, ship, info, p)) WmdTrigger.fire(p, "airborne");
        }
        Vec3 prevDir = s.dir;
        s.dir = sp > 1.0E-3 ? v.scale(1.0 / sp) : null;
        if (!s.fast) {
            if (sp < Balance.JET_BOOM_SPEED) return;
            s.fast = true;
            s.ahead.clear();
            for (ServerPlayer p : sl.players()) if (aboard(sl, ship, info, p)) WmdTrigger.fire(p, "supersonic");
            vapour(sl, ship, info.worldCenter(), s.dir, JetVapourPayload.BARRIER);
        } else if (sp < Balance.JET_BOOM_REARM) { s.fast = false; s.ahead.clear(); return; }
        Vec3 c = info.worldCenter(), dir = v.scale(1.0 / sp);

        if (prevDir != null && prevTick == now - 1 && prevDir.dot(dir) < Balance.JET_VAPOUR_PULL_COS && now - s.pullSent >= Balance.JET_VAPOUR_PULL_EVERY) {
            s.pullSent = now;
            vapour(sl, ship, c, dir, JetVapourPayload.PULL);
        }
        double r2 = Balance.JET_BOOM_R * Balance.JET_BOOM_R;
        for (ServerPlayer p : sl.players()) {
            UUID id = p.getUUID();
            Vec3 d = p.getEyePosition().subtract(c);
            if (p.isSpectator() || d.lengthSqr() > r2 || aboard(sl, ship, info, p)) { s.ahead.remove(id); continue; }
            boolean front = d.dot(dir) > 0.0;
            Boolean was = s.ahead.put(id, front);
            if (was == null || !was || front) continue;
            Long l = s.last.get(id);
            if (l != null && now - l < Balance.JET_BOOM_GAP) continue;
            s.last.put(id, now);
            PacketDistributor.sendToPlayer(p, new LaunchSoundPayload(LaunchSoundPayload.JET_BOOM, c.x, c.y, c.z));
        }
    }

    private static void vapour(ServerLevel sl, UUID ship, Vec3 c, Vec3 dir, int kind) {
        if (dir == null) return;
        JetVapourPayload pay = new JetVapourPayload(ship, kind, (float) dir.x, (float) dir.y, (float) dir.z);
        double r2 = Balance.JET_VAPOUR_R * Balance.JET_VAPOUR_R;
        for (ServerPlayer p : sl.players()) if (p.position().distanceToSqr(c) <= r2) PacketDistributor.sendToPlayer(p, pay);
    }

    private static boolean aboard(ServerLevel sl, UUID ship, SableBridge.ShipInfo info, ServerPlayer p) {
        if (p.getVehicle() instanceof CockpitSeatEntity seat && seat.cockpit() != null && ship.equals(SableBridge.shipIdAt(sl, seat.cockpit()))) return true;
        return info.worldBounds().inflate(1.0).contains(p.position());
    }
}
