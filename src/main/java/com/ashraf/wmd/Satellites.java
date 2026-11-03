package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class Satellites {

    private Satellites() {}

    public static final String PREFIX = "sat_";

    public static final java.util.List<String> TYPES = java.util.List.of("nav", "recon", "relay", "interceptor", "designator");

    public static String validType(String type) { return type != null && TYPES.contains(type) ? type : TYPES.get(0); }

    public static boolean isSatellite(MissileBuild b) { return b != null && b.warheadType().startsWith(PREFIX); }

    public static boolean toOrbit(MissileBuild b) { return isSatellite(b) || Cubesat.isCubesat(b) || Crew.isCrew(b) || StationKit.isKit(b) || Cargo.isCargo(b); }

    public static String typeOf(MissileBuild b) { return b.warheadType().substring(PREFIX.length()); }

    public static long insertTick(Flight f) {
        double[] p = Flight.plan(f.tier(), f.distance(), f.oy(), f.aimY());
        double k = f.duration() / Math.max(1.0, p[1] + p[2] + p[3]);
        long climb = Math.round(p[1] * k);
        return f.start() + Math.max(200L, Math.min(climb, f.duration() - 20L));
    }

    public static void insert(ServerLevel level, Flight f) {
        String type = typeOf(f.build());
        UUID owner = null;
        try { if (!f.ownerId().isEmpty()) owner = UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) {}
        ServerPlayer p = owner != null ? level.getServer().getPlayerList().getPlayer(owner) : level.getServer().getPlayerList().getPlayerByName(f.launcher());
        if (owner == null && p != null) owner = p.getUUID();
        if (owner == null) { Wmd.LOGGER.warn("Satellite rocket {} reached orbit without an owner: lost", f.id()); return; }
        OrbitData d = OrbitData.get(level.getServer());
        OrbitMath.Sat s = d.add(level.getServer(), owner, f.launcher(), type, level.random);
        if (s == null) {
            Wmd.LOGGER.info("Satellite rocket {} ({}) reached orbit but the orbit is full: lost", f.id(), type);
            if (p != null) p.sendSystemMessage(Component.translatable("message.wmd.sat.lost_full", OrbitData.typeName(type)).withStyle(ChatFormatting.RED));
            return;
        }
        Wmd.LOGGER.info("Satellite #{} ({}) in orbit for {} (from flight {})", s.id(), type, f.launcher(), f.id());
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.wmd.sat.deployed", OrbitData.typeName(type), s.id(), d.count(), Balance.SAT_MAX,
                    Balance.SAT_PASS_MINUTES).withStyle(ChatFormatting.AQUA));
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }
}
