package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

public final class WardenMusic {

    private WardenMusic() {}

    public static final List<String> TRACKS = List.of("boss_intro", "boss_stage_1", "boss_stage_2", "boss_stage_3", "boss_enrage", "boss_purge", "boss_defeated");

    static void send(ServerLevel sl, BlockPos pos, Collection<ServerPlayer> to, String what) {
        int track = TRACKS.indexOf(what);
        if (track < 0) return;
        WardenMusicPayload p = new WardenMusicPayload(track);
        if (to != null && !to.isEmpty()) {
            for (ServerPlayer pl : to) PacketDistributor.sendToPlayer(pl, p);
            return;
        }
        double r2 = Balance.WARDEN_SCALE_R * Balance.WARDEN_SCALE_R;
        for (ServerPlayer pl : sl.players())
            if (pl.blockPosition().distSqr(pos) < r2) PacketDistributor.sendToPlayer(pl, p);
    }
}
