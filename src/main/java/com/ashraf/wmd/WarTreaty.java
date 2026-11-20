package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class WarTreaty {

    private WarTreaty() {}

    static final int DETECT_ROUND = 500;
    private static int accident;
    private static long lastHeard = Long.MIN_VALUE;
    private static final String M = "message.wmd.war.";

    static void accident(Runnable r) { accident++; try { r.run(); } finally { accident--; } }

    static boolean bound(MinecraftServer server, UUID nation) {
        long[] v = nation == null ? null : WarData.get(server).treaty.get(nation);
        return v != null && (v[1] == 0 || v[1] > WarManager.now());
    }

    static boolean breaker(MinecraftServer server, UUID nation) {
        Long until = nation == null ? null : WarData.get(server).breakers.get(nation);
        return until != null && until > WarManager.now();
    }

    static String sign(ServerPlayer p) {
        UUID me = leader(p);
        if (me == null) return OpacBridge.nationOf(p.server, p.getUUID()) == null ? "no_nation" : "not_leader";
        WarData d = WarData.get(p.server);
        long[] v = d.treaty.get(me);
        if (v != null && v[1] == 0) return "treaty_already";
        d.treaty.put(me, new long[] { WarManager.now(), 0L });
        d.setDirty();
        WarManager.broadcast(p.server, Component.translatable(M + "treaty_signed", WarManager.name(p.server, me)).withStyle(ChatFormatting.AQUA));
        WmdTrigger.fire(p, "treaty");
        return "ok_treaty_signed";
    }

    static String withdraw(ServerPlayer p) {
        UUID me = leader(p);
        if (me == null) return OpacBridge.nationOf(p.server, p.getUUID()) == null ? "no_nation" : "not_leader";
        WarData d = WarData.get(p.server);
        long[] v = d.treaty.get(me);
        if (v == null || v[1] != 0) return "treaty_not";
        v[1] = WarManager.now() + Config.Server.warTreatyNoticeHours() * 3_600_000L;
        d.setDirty();
        WarManager.broadcast(p.server, Component.translatable(M + "treaty_withdraw", WarManager.name(p.server, me), Config.Server.warTreatyNoticeHours()).withStyle(ChatFormatting.GOLD));
        return "ok_treaty_withdraw";
    }

    private static UUID leader(ServerPlayer p) {
        if (!OpacBridge.active() || !OpacBridge.leads(p.server, p.getUUID())) return null;
        return OpacBridge.nationOf(p.server, p.getUUID());
    }

    static void tick(MinecraftServer server) {
        WarData d = WarData.get(server);
        long t = WarManager.now();
        if (d.treaty.values().removeIf(v -> v[1] != 0 && v[1] <= t)) d.setDirty();
        if (d.breakers.values().removeIf(until -> until <= t)) d.setDirty();
    }

    static void detonation(ServerLevel level, int x, int z, WarheadType t) {
        MinecraftServer server = level.getServer();
        WmdApiImpl.nuclear(level, x, z, t, accident > 0);
        if (accident <= 0) WmdTrigger.fire(server, ShipStrike.shooter(), "nuke");
        if (!OpacBridge.active() || !Config.Server.warEnabled()) return;
        long now = level.getGameTime();
        int rx = Math.round(x / (float) DETECT_ROUND) * DETECT_ROUND, rz = Math.round(z / (float) DETECT_ROUND) * DETECT_ROUND;
        if (now != lastHeard) {
            lastHeard = now;
            WarManager.broadcast(server, Component.translatable(M + "nuke_detected", Component.translatable("warhead.wmd." + t.id), rx, rz).withStyle(ChatFormatting.YELLOW));
        }
        if (accident > 0) return;
        UUID nation = OpacBridge.nationOf(server, ShipStrike.shooter());
        if (nation == null || !bound(server, nation)) return;
        UUID here = OpacBridge.nationAt(level, new BlockPos(x, 64, z));
        if (here != null && !here.equals(nation) && WarManager.atWar(server, nation, here)) return;
        WarData d = WarData.get(server);
        d.breakers.put(nation, WarManager.now() + Config.Server.warTreatyMarkHours() * 3_600_000L);
        boolean inWar = false;
        for (WarData.War w : d.wars) {
            int s = w.sideOf(nation);
            if (w.state == WarData.ACTIVE && s != 0) { w.points[s] += Balance.WAR_PTS_BREACH; w.cats[s][WarScore.C_TREATY] += Balance.WAR_PTS_BREACH; inWar = true; break; }
        }
        if (!inWar) d.totals(nation)[0] += Balance.WAR_PTS_BREACH;
        d.setDirty();
        WarManager.broadcast(server, Component.translatable(M + "treaty_breach", WarManager.name(server, nation), rx, rz).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        Wmd.LOGGER.info("[war] TEST BAN BREACH by {} ({} at {} {})", WarManager.name(server, nation), t.id, x, z);
    }
}
