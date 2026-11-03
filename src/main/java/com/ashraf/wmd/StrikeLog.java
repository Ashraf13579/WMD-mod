package com.ashraf.wmd;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class StrikeLog {

    private StrikeLog() {}

    private static final String[] FIRST = { "Ash", "Iron", "Red", "Black", "Glass", "Storm", "Grey", "Ember", "Frost", "Thunder", "Hollow", "Silent" };
    private static final String[] SECOND = { "Basin", "Scar", "Pit", "Bowl", "Maw", "Well", "Mark", "Rift", "Deep", "Gate", "Crown", "Reach" };

    static String name(int x, int z) {
        long h = x * 73856093L ^ z * 19349663L;
        h ^= h >>> 13;
        return FIRST[(int) Math.floorMod(h, (long) FIRST.length)] + " " + SECOND[(int) Math.floorMod(h >>> 8, (long) SECOND.length)];
    }

    static void report(ServerLevel level, UUID owner, String what, int x, int z, double r, double depth) {
        if (level == null) return;
        String nm = name(x, z), rr = Long.toString(Math.round(r)), dd = Long.toString(Math.round(depth));
        String blocks = Long.toString(Math.round(Math.PI * r * r * depth * 2.0 / 3.0 / 1000.0) * 1000L);
        CcBattleLog.add(level.getServer(), owner, CcBattleLog.BDA, "log.wmd.strike.report", "#" + what, x + " " + z, nm, rr, dd, blocks);
        Component card = Component.translatable("message.wmd.strike.report", Component.translatable(what), nm, x, z, rr, dd, blocks).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            boolean near = p.serverLevel() == level && Math.hypot(p.getX() - x, p.getZ() - z) < Balance.STRIKE_REPORT_R;
            if (near || p.getUUID().equals(owner)) p.sendSystemMessage(card);
        }
        Wmd.LOGGER.info("[strike] {} at {} {}: \"{}\", radius {}, depth {}, about {} blocks", what, x, z, nm, rr, dd, blocks);
    }
}
