package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Asat {

    private Asat() {}

    public static final String ID = "asat";
    public static final int NO_SAT = 16, NO_STATION = 17, NOT_OVERHEAD = 18, STATION_FAR = 24, STATION_UNSEEN = 25;

    public static int stationPick(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return -1;
        Integer p = Station.Data.get(server).asatPick.get(Orbit.slotOf(server, owner));
        return p == null ? -1 : p;
    }

    public static int stationStatus(MinecraftServer server, UUID owner, double x, double z) {
        int pick = stationPick(server, owner);
        Station.Module t = pick < 0 ? null : Station.byId(server, pick);
        int mySlot = Orbit.slotOf(server, owner);
        if (t == null || !t.built() || Station.slotOf(server, t) == mySlot) return NO_SAT;
        if (!SensorArray.working(server, mySlot) && !Recon.covered(server, owner) && !Rendezvous.close(server, mySlot, Station.slotOf(server, t))) return STATION_UNSEEN;
        Orbit.Ground g = Orbit.ground(server, Station.slotOf(server, t));
        return Math.hypot(x - g.x(), z - g.z()) > Balance.ASAT_STATION_R ? STATION_FAR : 0;
    }

    public static boolean isAsat(MissileBuild b) { return b != null && ID.equals(b.warheadType()); }

    public static int status(MinecraftServer server, UUID owner, int satId, double x, double z) {
        OrbitMath.Sat s = satId > 0 ? OrbitData.get(server).byId(satId) : null;
        if (s == null || owner == null || owner.equals(s.owner())) return NO_SAT;
        if (!GroundStationBlockEntity.hasPoweredStation(owner)) return NO_STATION;
        if (!OrbitMath.isOverhead(s, x, z, System.currentTimeMillis())) return NOT_OVERHEAD;
        return 0;
    }

    public static boolean pickable(MinecraftServer server, UUID owner, int satId) {
        OrbitMath.Sat s = OrbitData.get(server).byId(satId);
        return s != null && owner != null && !owner.equals(s.owner());
    }

    public static long resolveTick(Flight f) {
        return f.start() + Math.max(40L, Math.min(Balance.ASAT_CLIMB_S * 20L, f.duration() - 20L));
    }

    static void launched(ServerLevel level, long flightId, int satId, UUID launcher, String launcherName) {
        MinecraftServer server = level.getServer();
        FlightManager mgr = FlightManager.get(level);
        if (satId < 0) { stationLaunched(level, mgr, flightId, -satId - 1, launcher, launcherName); return; }
        mgr.asatTargets.put(flightId, satId);
        mgr.setDirty();
        OrbitMath.Sat s = OrbitData.get(server).byId(satId);
        ServerPlayer p = launcher != null ? server.getPlayerList().getPlayer(launcher) : server.getPlayerList().getPlayerByName(launcherName);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.asat.away", satId, Balance.ASAT_CLIMB_S).withStyle(ChatFormatting.AQUA), false);
        if (s != null && GroundStationBlockEntity.hasPoweredStation(s.owner())) {
            ServerPlayer o = server.getPlayerList().getPlayer(s.owner());
            if (o != null) {
                Component m = Component.translatable("message.wmd.asat.warning", OrbitData.typeName(s.type()), s.id(), Balance.ASAT_CLIMB_S).withStyle(ChatFormatting.RED);
                o.sendSystemMessage(m);
                o.displayClientMessage(m, true);
                o.playNotifySound(WmdSounds.ASAT_ALERT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        Wmd.LOGGER.info("ASAT flight {} of {} climbing towards satellite #{}", flightId, launcherName, satId);
    }

    static void resolve(ServerLevel level, Flight f, Integer satId) {
        MinecraftServer server = level.getServer();
        if (satId != null && satId < 0) { stationResolve(level, f, -satId - 1); return; }
        ServerPlayer shooter = SatDefence.flightOwner(server, f);
        OrbitData od = OrbitData.get(server);
        OrbitMath.Sat s = satId == null ? null : od.byId(satId);
        if (s == null || f.isOwnedBy(s.owner(), s.ownerName())) {
            if (shooter != null) shooter.sendSystemMessage(Component.translatable("message.wmd.asat.no_target", satId == null ? 0 : satId).withStyle(ChatFormatting.YELLOW));
            return;
        }
        boolean hit = level.random.nextInt(100) < Balance.ASAT_HIT_PCT;
        strike(server, s, hit, f.launcher(), shooter);
    }

    private static void stationLaunched(ServerLevel level, FlightManager mgr, long flightId, int moduleId, UUID launcher, String launcherName) {
        MinecraftServer server = level.getServer();
        mgr.asatTargets.put(flightId, -moduleId - 1);
        mgr.setDirty();
        Station.Module t = Station.byId(server, moduleId);
        ServerPlayer p = launcher != null ? server.getPlayerList().getPlayer(launcher) : server.getPlayerList().getPlayerByName(launcherName);
        if (t == null) return;
        int ts = Station.slotOf(server, t);
        if (p != null) p.displayClientMessage(Component.translatable("message.wmd.asat.away_station", StationLaser.name(server, t),
                Component.translatable("station.wmd.module." + t.type()), Balance.ASAT_CLIMB_S).withStyle(ChatFormatting.AQUA), false);
        Flight f = mgr.find(flightId);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (f != null && orbit != null) SensorArray.incoming(server, ts, "asat", launcherName, orbit.getGameTime() + (resolveTick(f) - level.getGameTime()));
        Wmd.LOGGER.info("ASAT flight {} of {} climbing towards station module {} (slot {})", flightId, launcherName, moduleId, ts);
    }

    private static void stationResolve(ServerLevel level, Flight f, int moduleId) {
        MinecraftServer server = level.getServer();
        ServerPlayer shooter = SatDefence.flightOwner(server, f);
        Station.Module t = Station.byId(server, moduleId);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (t == null || !t.built() || orbit == null) {
            if (shooter != null) shooter.sendSystemMessage(Component.translatable("message.wmd.asat.no_station_target").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int ts = Station.slotOf(server, t);
        Component typ = Component.translatable("station.wmd.module." + t.type());
        UUID tOwner = Orbit.OrbitSlots.get(server).ownerOf(ts);
        if (Turret.intercepted(server, ts)) {
            VacuumBurst.send(orbit, net.minecraft.world.phys.Vec3.atCenterOf(Station.middle(t)).add(0.0, -30.0, 0.0), 5.0);
            if (shooter != null) shooter.sendSystemMessage(Component.translatable("message.wmd.turret.asat_down_shooter").withStyle(ChatFormatting.YELLOW));
            StationLife.tell(orbit, ts, Component.translatable("message.wmd.turret.asat_down").withStyle(ChatFormatting.GREEN));
            if (tOwner != null) CcBattleLog.add(server, tOwner, CcBattleLog.INTERCEPT, "log.wmd.turret.asat_down", f.launcher());
            return;
        }
        boolean hit = level.random.nextInt(100) < Balance.ASAT_HIT_PCT;
        net.minecraft.core.BlockPos c = Station.middle(t);
        if (hit) {
            int after = StationLife.damage(orbit, ts, t, Balance.ASAT_STATION_DMG);
            VacuumBurst.send(orbit, net.minecraft.world.phys.Vec3.atCenterOf(c), 8.0);
            orbit.playSound(null, c, WmdSounds.ASAT_HIT.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
            if (shooter != null) shooter.sendSystemMessage(Component.translatable("message.wmd.asat.hit_station", StationLaser.name(server, t), typ, Math.max(0, after)).withStyle(ChatFormatting.GREEN));
            if (tOwner != null) CcBattleLog.add(server, tOwner, CcBattleLog.ALARM, "log.wmd.asat.hit_station", f.launcher(), "#station.wmd.module." + t.type());
        } else if (shooter != null) shooter.sendSystemMessage(Component.translatable("message.wmd.asat.miss_station", StationLaser.name(server, t), typ).withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == ts)
            p.sendSystemMessage(Component.translatable(hit ? "message.wmd.asat.station_hit" : "message.wmd.asat.station_missed", f.launcher(), typ).withStyle(hit ? ChatFormatting.RED : ChatFormatting.GOLD));
        Wmd.LOGGER.info("ASAT of {} {} station module {} (slot {})", f.launcher(), hit ? "hit" : "missed", moduleId, ts);
    }

    static void strike(MinecraftServer server, OrbitMath.Sat s, boolean hit, String shooterName, ServerPlayer shooter) {
        CcBattleLog.onAsat(server, s, hit, shooterName, shooter);
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner());
        if (hit) {
            OrbitData.get(server).remove(server, s.id());
            SatHack.cancelAll(server, s.id(), "message.wmd.hack.cancel.gone");
            PacketDistributor.sendToPlayersInDimension(server.overworld(), new AsatFxPayload(s, System.currentTimeMillis(), true));
            Wmd.LOGGER.info("ASAT of {} destroyed satellite #{} ({}, owner {})", shooterName, s.id(), s.type(), s.ownerName());
        } else {
            PacketDistributor.sendToPlayersInDimension(server.overworld(), new AsatFxPayload(s, System.currentTimeMillis(), false));
            Wmd.LOGGER.info("ASAT of {} missed satellite #{} ({}, owner {})", shooterName, s.id(), s.type(), s.ownerName());
        }
        if (shooter != null) {
            shooter.sendSystemMessage(Component.translatable(hit ? "message.wmd.asat.hit" : "message.wmd.asat.miss", OrbitData.typeName(s.type()), s.id(), s.ownerName())
                    .withStyle(hit ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        if (owner != null && owner != shooter) {
            owner.sendSystemMessage(Component.translatable(hit ? "message.wmd.asat.lost" : "message.wmd.asat.survived", OrbitData.typeName(s.type()), s.id(), shooterName)
                    .withStyle(hit ? ChatFormatting.RED : ChatFormatting.GOLD));
            if (hit) owner.playNotifySound(WmdSounds.ASAT_HIT.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }
}
