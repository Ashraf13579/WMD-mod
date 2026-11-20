package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class VnWars {

    private VnWars() {}

    private record War(String attacker, String defender, int[][] counts) {}

    private static final Map<String, War> WARS = Statics.server(new HashMap<>());
    private static final Map<Long, Integer> DEFCON_BEFORE = Statics.server(new HashMap<>());
    static volatile boolean anyWar;

    public static void register() { VnBridge.listen(VnWars::on); }

    private static String key(String a, String d) { return a + "|" + d; }

    private static void on(int kind, String attacker, String defender, String warId, String winner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            if (kind == 0) defcon(server, attacker, defender, 2);
            else if (kind == 1) {
                defcon(server, attacker, defender, 1);
                WARS.put(key(attacker, defender), new War(attacker, defender, new int[2][WarScore.CATS]));
                anyWar = true;
            } else {
                restore(server, attacker, defender);
                War w = WARS.remove(key(attacker, defender));
                anyWar = !WARS.isEmpty();
                if (w != null) report(server, w, winner);
            }
        });
    }

    private static void defcon(MinecraftServer server, String a, String d, int level) {
        for (ServerLevel lv : server.getAllLevels())
            for (CcBuildingBlockEntity b : CommandCenter.buildingsIn(lv)) {
                String c = VnBridge.countryOf(b.getOwner());
                if (c == null || !(c.equals(a) || c.equals(d))) continue;
                long k = b.getBlockPos().asLong();
                DEFCON_BEFORE.putIfAbsent(k, b.defcon());
                if (b.defcon() > level) b.setDefcon(level);
            }
    }

    private static void restore(MinecraftServer server, String a, String d) {
        for (ServerLevel lv : server.getAllLevels())
            for (CcBuildingBlockEntity b : CommandCenter.buildingsIn(lv)) {
                String c = VnBridge.countryOf(b.getOwner());
                if (c == null || !(c.equals(a) || c.equals(d))) continue;
                Integer before = DEFCON_BEFORE.remove(b.getBlockPos().asLong());
                if (before != null) b.setDefcon(before);
            }
    }

    static void count(String by, String against, int cat, int pts) {
        if (by == null || against == null || by.equals(against)) return;
        War w = WARS.get(key(by, against));
        if (w != null) { w.counts()[0][cat] += pts; return; }
        w = WARS.get(key(against, by));
        if (w != null) w.counts()[1][cat] += pts;
    }

    static boolean fighting(String a, String b) { return a != null && b != null && (WARS.containsKey(key(a, b)) || WARS.containsKey(key(b, a))); }

    private static void report(MinecraftServer server, War w, String winner) {
        Component head = Component.translatable("message.wmd.vn.report_head", VnBridge.name(w.attacker()), VnBridge.name(w.defender()),
                winner.isEmpty() ? Component.translatable("message.wmd.vn.no_winner") : VnBridge.name(winner)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component la = line(w.attacker(), w.counts()[0]), ld = line(w.defender(), w.counts()[1]);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String c = VnBridge.countryOf(p.getUUID());
            if (c == null || !(c.equals(w.attacker()) || c.equals(w.defender()))) continue;
            p.sendSystemMessage(head);
            p.sendSystemMessage(la);
            p.sendSystemMessage(ld);
        }
        Wmd.LOGGER.info("[vn] war report {} vs {} sent", w.attacker(), w.defender());
    }

    private static Component line(String country, int[] c) {
        return Component.translatable("message.wmd.vn.report_line", VnBridge.name(country), c[WarScore.C_BLOCKS] * Balance.WAR_BLOCKS_PER_POINT, c[WarScore.C_MACHINES] > 0 ? c[WarScore.C_MACHINES] : 0,
                c[WarScore.C_KILLS] / Math.max(1, Balance.WAR_PTS_KILL), c[WarScore.C_INTERCEPTS], c[WarScore.C_CRAFTS]).withStyle(ChatFormatting.GRAY);
    }

    static void countPlayers(UUID by, UUID against, int cat, int pts) { count(VnBridge.countryOf(by), VnBridge.countryOf(against), cat, pts); }
}
