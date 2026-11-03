package com.ashraf.wmd;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public final class Uplink {

    private Uplink() {}

    private static final java.util.Map<Long, Long> LOCK_SEEN = Statics.server(new java.util.HashMap<>());

    static boolean lockActive(FlightManager mgr, Flight f) {
        return mgr.uplinkLocks.containsKey(f.id()) || (mgr.shipLocks.containsKey(f.id()) && !SeekerGuidance.tracksShip(f.id()));
    }

    private static boolean lockHeld(ServerLevel level, FlightManager mgr, Flight f, long now) {
        if (!lockActive(mgr, f)) { LOCK_SEEN.remove(f.id()); return false; }
        String why = null;
        UUID owner = owner(level.getServer(), f);
        if (owner == null || !active(level.getServer(), owner)) why = "coverage";
        else if (CcJammer.jammed(level, f)) why = "jammed";
        else {
            String name = mgr.uplinkLocks.get(f.id());
            if (name != null) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(name);
                if (p == null || p.level() != level) why = "gone";
                else if (visible(level, p)) LOCK_SEEN.put(f.id(), now);
                else if (now - LOCK_SEEN.computeIfAbsent(f.id(), k -> now) > Balance.UPLINK_LOCK_LOST) why = "hidden";
            } else if (SableBridge.shipInfo(level, mgr.shipLocks.get(f.id())) == null) why = "ship";
        }
        if (why == null) return true;
        String name = mgr.uplinkLocks.remove(f.id());
        if (name == null) mgr.shipLocks.remove(f.id());
        LOCK_SEEN.remove(f.id());
        mgr.setDirty();
        if (owner != null) Drones.tell(level.getServer(), owner, Component.translatable("message.wmd.uplink.lock_lost", f.id()).withStyle(net.minecraft.ChatFormatting.GOLD));
        Wmd.LOGGER.info("Uplink: flight {} lost its lock ({}), its {} seeker takes over", f.id(), why, f.build().seeker());
        return false;
    }

    static final Set<UUID> FORCED = new HashSet<>();

    public static boolean isCell(ItemStack s) { return s.is(Wmd.UPLINK_CELL.get()); }

    public static boolean active(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return false;
        if (FORCED.contains(owner)) return true;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && s.type().equals("nav") && now < s.endMs() && OrbitData.controlled(s)) return true;
        return false;
    }

    public static boolean covers(MinecraftServer server, MissileBuild b, UUID owner) {
        return b != null && b.uplink() && !b.isInterceptor() && !Satellites.toOrbit(b) && active(server, owner);
    }

    public static double missRadius(double base) {
        return Math.max(Math.min(base, Balance.UPLINK_MIN_MISS), base * Balance.UPLINK_MISS_FACTOR);
    }

    public static UUID owner(MinecraftServer server, Flight f) {
        try { if (!f.ownerId().isEmpty()) return UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        ServerPlayer p = server.getPlayerList().getPlayerByName(f.launcher());
        return p == null ? null : p.getUUID();
    }

    private static double vmax(Flight f) { return Flight.V_CRUISE * MissileStats.speed(f.build()); }

    public static double reach(Flight f, long now) {
        Vec3 p = f.basePosition(now);
        double flown = Math.hypot(p.x - f.ox(), p.z - f.oz());
        double fuel = MissileStats.range(f.build()) - flown;
        double time = f.planLeft(now) * Balance.UPLINK_MAX_STRETCH * vmax(f);
        return Math.max(0.0, Math.min(fuel, time));
    }

    public static double legK(Flight f, long now, double dist) {
        double left = f.planLeft(now);
        if (left < 2.0 || dist > reach(f, now) + 0.5) return -1.0;
        double need = Math.max(left, dist / Math.max(0.01, vmax(f)));
        return left / need;
    }

    private static double currentK(Flight f, long now) {
        Flight.Leg l = f.legAt(now);
        return l == null ? 1.0 : l.k();
    }

    private static int[] scatter(ServerLevel level, int x, int z, double r) {
        if (r <= 0) return new int[] { x, z };
        double a = level.random.nextDouble() * Math.PI * 2.0, d = r * level.random.nextDouble();
        return new int[] { x + (int) Math.round(Math.cos(a) * d), z + (int) Math.round(Math.sin(a) * d) };
    }

    private static int[] lockAim(Flight f, ServerPlayer p) {
        long h = (f.id() + 7) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        double a = ((h & 0xFFFFL) / 65535.0) * Math.PI * 2.0, d = (((h >>> 16) & 0xFFFFL) / 65535.0) * missRadius(Config.missRadius(f.build().guidance()));
        return new int[] { (int) Math.floor(p.getX() + Math.cos(a) * d), (int) Math.floor(p.getZ() + Math.sin(a) * d) };
    }

    private static boolean visible(ServerLevel level, ServerPlayer p) {
        return p.level() == level && !p.isSpectator() && level.canSeeSky(BlockPos.containing(p.getEyePosition()));
    }

    public static Component retarget(ServerPlayer player, long id, int x, int z, String lockName) {
        ServerLevel level = player.serverLevel();
        FlightManager mgr = FlightManager.get(level);
        Flight f = mgr.find(id);
        long now = level.getGameTime();
        String P = "message.wmd.uplink.";
        if (f == null || !f.isOwnedBy(player.getUUID(), player.getGameProfile().getName()) || Satellites.toOrbit(f.build()) || now < f.start())
            return Component.translatable(P + "no_flight", id);
        if (!f.build().uplink()) return Component.translatable(P + "no_cell", id);
        if ((f.uflags() & Flight.U_RETARGETED) != 0) return Component.translatable(P + "used", id);
        if (!active(level.getServer(), player.getUUID())) return Component.translatable(P + "no_coverage");
        if (CcJammer.jammed(level, f)) return Component.translatable(P + "jammed", id);
        if (now >= f.endTime() - 20) return Component.translatable(P + "too_late", id);
        boolean hasName = lockName != null && !lockName.trim().isEmpty();
        if (hasName && f.build().hypersonic()) return Component.translatable(P + "hyper_lock", id);
        x = net.minecraft.util.Mth.clamp(x, -29_999_000, 29_999_000);
        z = net.minecraft.util.Mth.clamp(z, -29_999_000, 29_999_000);
        String name = lockName == null ? "" : lockName.trim();
        int[] aim;
        if (!name.isEmpty()) {
            ServerPlayer t = level.getServer().getPlayerList().getPlayerByName(name);
            if (t == null || t.level() != level) return Component.translatable(P + "no_player", name);
            aim = visible(level, t) ? lockAim(f, t) : scatter(level, x, z, missRadius(Config.missRadius(f.build().guidance())));
        } else {
            aim = scatter(level, x, z, missRadius(Config.missRadius(f.build().guidance())));
        }
        Vec3 p = f.basePosition(now);
        double k = legK(f, now, Math.hypot(aim[0] + 0.5 - p.x, aim[1] + 0.5 - p.z));
        if (k < 0) return Component.translatable(P + "out_of_reach", id, (int) reach(f, now));
        boolean override = !name.isEmpty() && !f.build().seeker().isEmpty();
        if (override) SeekerGuidance.release(mgr, id);
        Flight nf = mgr.releg(level, f, aim[0], aim[1], k, Flight.U_RETARGETED | (name.isEmpty() ? 0 : Flight.U_LOCKED),
                Flight.U_HOMING_DONE | Flight.U_HOMED | Flight.U_LOCKED | (override ? Flight.U_SEEK_DONE | Flight.U_SEEK_LOCK | Flight.U_SEEK_LOST : 0));
        if (nf == null) return Component.translatable(P + "no_flight", id);
        LOCK_SEEN.remove(id);
        if (name.isEmpty()) mgr.uplinkLocks.remove(id); else mgr.uplinkLocks.put(id, name);
        Wmd.LOGGER.info("Uplink: {} retargeted flight {} to {} {}{}", player.getGameProfile().getName(), id, aim[0], aim[1], name.isEmpty() ? "" : " (lock " + name + ")");
        return name.isEmpty() ? Component.translatable(P + "retargeted", id, x, z, (nf.endTime() - now) / 20)
                : Component.translatable(P + "locked", id, name, (nf.endTime() - now) / 20);
    }

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        java.util.List<Flight> list = mgr.flights();
        for (int i = 0; i < list.size(); i++) {
            Flight f = list.get(i);
            if (Satellites.toOrbit(f.build()) || now < f.start() || now >= f.endTime() - 2) continue;

            if (!f.isCruise() && (f.uflags() & Flight.U_CHAFFED) == 0 && f.endTime() - now <= Balance.UPLINK_HOMING_SECONDS * 20L) {
                boolean guided = f.build().uplink() && active(level.getServer(), owner(level.getServer(), f)) && !CcJammer.jammed(level, f);
                if (!guided && WarheadFx.inChaff(mgr, f.tx() + 0.5, f.tz() + 0.5)) {
                    int[] aim = scatter(level, f.tx(), f.tz(), Config.missRadius(f.build().guidance()));
                    mgr.releg(level, f, aim[0], aim[1], currentK(f, now), Flight.U_CHAFFED | Flight.U_HOMING_DONE, 0);
                    Wmd.LOGGER.info("Chaff: flight {} confused, new aim {} {}", f.id(), aim[0], aim[1]);
                    continue;
                }
                list.set(i, f = f.withFlags(f.uflags() | Flight.U_CHAFFED));
                mgr.setDirty();
            }
            if (!f.build().uplink()) continue;

            if (!f.build().seeker().isEmpty() && !lockHeld(level, mgr, f, now)) continue;
            if (CcJammer.jammed(level, f)) continue;
            String lock = mgr.uplinkLocks.get(f.id());
            if (lock != null) {
                if ((now + f.id()) % Balance.UPLINK_LOCK_SAMPLE_TICKS != 0) continue;
                ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(lock);
                if (p == null || !visible(level, p)) continue;
                if (!active(level.getServer(), owner(level.getServer(), f))) continue;
                int[] aim = lockAim(f, p);
                if (Math.hypot(aim[0] - f.tx(), aim[1] - f.tz()) < Balance.UPLINK_LOCK_MOVE) continue;
                Vec3 at = f.basePosition(now);
                double k = legK(f, now, Math.hypot(aim[0] + 0.5 - at.x, aim[1] + 0.5 - at.z));
                if (k < 0) continue;
                mgr.releg(level, f, aim[0], aim[1], k, 0, 0);
                continue;
            }
            java.util.UUID shipLock = mgr.shipLocks.get(f.id());
            if (shipLock != null) {
                if ((now + f.id()) % Balance.UPLINK_LOCK_SAMPLE_TICKS != 0) continue;
                if (!active(level.getServer(), owner(level.getServer(), f))) continue;
                SableBridge.ShipInfo ship = SableBridge.shipInfo(level, shipLock);
                if (ship == null) continue;
                Vec3 lead = ship.worldCenter().add(ShipDefence.velocity(level, ship).scale(Math.max(0L, f.endTime() - now)));
                int ax = (int) Math.floor(lead.x), az = (int) Math.floor(lead.z);
                if (Math.hypot(ax - f.tx(), az - f.tz()) < Balance.UPLINK_LOCK_MOVE) { mgr.shipEnd(level, f); continue; }
                Vec3 at = f.basePosition(now);
                double k = legK(f, now, Math.hypot(ax + 0.5 - at.x, az + 0.5 - at.z));
                if (k < 0) continue;
                Flight nf = mgr.releg(level, f, ax, az, k, 0, 0);
                if (nf != null) mgr.shipEnd(level, nf);
                continue;
            }

            if ((f.uflags() & Flight.U_HOMING_DONE) != 0 || f.endTime() - now > Balance.UPLINK_HOMING_SECONDS * 20L) continue;
            UUID owner = owner(level.getServer(), f);
            BlockPos m = owner != null && active(level.getServer(), owner) ? enemyMachine(level, f.tx() + 0.5, f.tz() + 0.5, owner) : null;
            if (m != null && (m.getX() != f.tx() || m.getZ() != f.tz())) {
                mgr.releg(level, f, m.getX(), m.getZ(), currentK(f, now), Flight.U_HOMING_DONE | Flight.U_HOMED, 0);
                Wmd.LOGGER.info("Uplink: flight {} homes onto the machine at {}", f.id(), m.toShortString());
            } else {
                list.set(i, f.withFlags(f.uflags() | Flight.U_HOMING_DONE));
                mgr.setDirty();
            }
        }
    }

    static BlockPos enemyMachine(ServerLevel level, double x, double z, UUID owner) {
        double r = Balance.UPLINK_HOMING_RADIUS, best = r * r;
        BlockPos found = null;
        int c0x = (int) Math.floor((x - r) / 16), c1x = (int) Math.floor((x + r) / 16), c0z = (int) Math.floor((z - r) / 16), c1z = (int) Math.floor((z + r) / 16);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof Owned o) || be.isRemoved()) continue;
                    UUID ow = o.getOwner();
                    if (ow == null || ow.equals(owner)) continue;
                    BlockPos p = be.getBlockPos();
                    double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z, d2 = dx * dx + dz * dz;
                    if (d2 <= best) { best = d2; found = p; }
                }
            }
        }
        return found;
    }
}
