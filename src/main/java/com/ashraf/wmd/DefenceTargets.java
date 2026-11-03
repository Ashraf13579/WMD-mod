package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

public final class DefenceTargets {

    private DefenceTargets() {}

    public static boolean trusted(ServerLevel sl, Owned o, Player p) {
        if (o == null || o.getOwner() == null) return false;
        UUID id = p.getUUID();
        if (id.equals(o.getOwner()) || o.security().trusted().containsKey(id)) return true;
        String on = o.getOwnerName();
        PlayerTeam t = on == null || on.isEmpty() ? null : sl.getScoreboard().getPlayersTeam(on);
        return t != null && t == p.getTeam();
    }

    public static boolean hostile(ServerLevel sl, Owned o, LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator() || sl.getDifficulty() == Difficulty.PEACEFUL) return false;
            return !trusted(sl, o, p);
        }
        return e instanceof Enemy;
    }
}
