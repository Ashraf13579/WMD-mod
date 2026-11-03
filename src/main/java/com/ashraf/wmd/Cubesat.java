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

public final class Cubesat {

    private Cubesat() {}

    public static final String ID = "cubesat";

    public static boolean isCubesat(MissileBuild b) { return b != null && ID.equals(b.warheadType()); }

    static void insert(ServerLevel level, Flight f) {
        MinecraftServer server = level.getServer();
        UUID owner = CcBattleLog.ownerOf(server, f);
        if (owner == null) { Wmd.LOGGER.warn("Cubesat flight {} reached orbit without an owner: lost", f.id()); return; }
        OrbitMath.Sat s = OrbitData.get(server).add(server, owner, f.launcher(), OrbitMath.CUBESAT, level.random);
        if (s == null) return;
        Wmd.LOGGER.info("Cubesat #{} in orbit for {} (from flight {}), {} minutes", s.id(), f.launcher(), f.id(), Balance.CUBESAT_MINUTES);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            ReconData.Entry e = ReconData.get(server).entry(owner);
            boolean target = e != null && e.hasTarget;
            p.sendSystemMessage(Component.translatable(target ? "message.wmd.cubesat.deployed" : "message.wmd.cubesat.deployed_no_target", s.id(), Balance.CUBESAT_MINUTES,
                    target ? e.tx : 0, target ? e.tz : 0).withStyle(ChatFormatting.AQUA));
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    static void burnUp(MinecraftServer server, OrbitMath.Sat s) {
        long now = System.currentTimeMillis();
        double mid = Balance.SAT_OVERHEAD_SECONDS / 2.0;
        for (ServerPlayer p : server.overworld().players()) {
            OrbitMath.Sat here = s.withPhase(OrbitMath.phaseForPass(s, p.getX(), p.getZ(), now, mid));
            PacketDistributor.sendToPlayer(p, new AsatFxPayload(here, now, true));
        }
    }
}
