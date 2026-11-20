package com.ashraf.wmd;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarCommandsPlayer {

    private WarCommandsPlayer() {}

    private static final String M = "message.wmd.war.";

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("war")
                .then(Commands.literal("nations").executes(c -> nations(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("declare").then(Commands.argument("nation", StringArgumentType.greedyString()).suggests(WarCommandsPlayer::suggest)
                        .executes(c -> order(c, 0))))
                .then(Commands.literal("join").then(Commands.argument("nation", StringArgumentType.greedyString()).suggests(WarCommandsPlayer::suggest)
                        .executes(c -> order(c, 1))))

                .then(Commands.literal("surrender").then(Commands.argument("nation", StringArgumentType.greedyString()).suggests(WarCommandsPlayer::suggest)
                        .executes(c -> order(c, 3))))
                .then(Commands.literal("take").executes(c -> take(c.getSource())))
                .then(Commands.literal("score").executes(c -> score(c.getSource(), -1))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c -> score(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("board").executes(c -> board(c.getSource())))
                .then(Commands.literal("room").executes(c -> { WarRoomPayloads.open(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("treaty")
                        .then(Commands.literal("sign").executes(c -> answer(c.getSource(), WarTreaty.sign(c.getSource().getPlayerOrException()))))
                        .then(Commands.literal("withdraw").executes(c -> answer(c.getSource(), WarTreaty.withdraw(c.getSource().getPlayerOrException()))))
                        .then(Commands.literal("status").executes(c -> treaty(c.getSource())))));
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        MinecraftServer server = c.getSource().getServer();
        String typed = b.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (UUID n : OpacBridge.nations(server)) {
            String name = OpacBridge.nationName(server, n);
            if (!name.isEmpty() && name.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) b.suggest(name);
        }
        return b.buildFuture();
    }

    static UUID resolve(MinecraftServer server, String typed) {
        String t = typed.trim();
        for (UUID n : OpacBridge.nations(server)) if (OpacBridge.nationName(server, n).equalsIgnoreCase(t)) return n;
        if (t.length() >= 6) for (UUID n : OpacBridge.nations(server)) if (n.toString().startsWith(t.toLowerCase(java.util.Locale.ROOT))) return n;
        return null;
    }

    private static int order(CommandContext<CommandSourceStack> c, int kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        UUID target = resolve(p.server, StringArgumentType.getString(c, "nation"));
        String ans = switch (kind) {
            case 0 -> WarManager.declare(p, target);
            case 1 -> target == null ? "no_target" : WarManager.join(p, target);
            default -> target == null ? "no_target" : WarManager.surrender(p, target);
        };
        return answer(c.getSource(), ans);
    }

    private static int take(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return answer(src, WarManager.take(src.getPlayerOrException()));
    }

    private static int answer(CommandSourceStack src, String key) {
        if (key == null || key.isEmpty()) return 1;
        boolean ok = key.startsWith("ok_");
        Component line = Component.translatable(M + key).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (ok) src.sendSuccess(() -> line, false); else src.sendFailure(line);
        return ok ? 1 : 0;
    }

    private static String mins(long ms) { long m = Math.max(0L, (ms + 59_999L) / 60_000L); return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min"; }

    private static int nations(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        if (!OpacBridge.active()) { src.sendFailure(Component.translatable(M + "no_opac")); return 0; }
        StringBuilder b = new StringBuilder();
        for (UUID n : OpacBridge.nations(server)) {
            StringBuilder at = new StringBuilder();
            for (WarData.War w : WarManager.warsOf(server, n)) {
                int s = w.sideOf(n);
                at.append(at.length() == 0 ? "" : ", ").append(WarManager.name(server, w.principal(3 - s))).append(w.state == WarData.DECLARED ? " (declared)" : "");
            }
            b.append("\n  ").append(WarManager.name(server, n)).append(": ").append(OpacBridge.members(server, n).size()).append(" members, ")
                    .append(OpacBridge.online(server, n)).append(" online").append(at.length() == 0 ? "" : " | at war with " + at);
        }
        String text = b.toString();
        src.sendSuccess(() -> Component.translatable(M + "nations_head").append(text), false);
        return 1;
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = p.server;
        if (!OpacBridge.active()) { src.sendFailure(Component.translatable(M + "no_opac")); return 0; }
        UUID me = OpacBridge.nationOf(server, p.getUUID());
        if (me == null) { src.sendFailure(Component.translatable(M + "no_nation")); return 0; }
        long t = WarManager.now();
        StringBuilder b = new StringBuilder(WarManager.name(server, me) + (OpacBridge.leads(server, p.getUUID()) ? " (you lead it)" : ""));
        int n = 0;
        for (WarData.War w : WarData.get(server).wars) {
            int s = w.sideOf(me);
            boolean called = w.live() && (w.callA.contains(me) || w.callB.contains(me));
            boolean taking = w.state == WarData.ENDED && me.equals(w.winner) && w.takeLeft > 0 && w.takeUntil > t;
            if (!(w.live() && s != 0) && !called && !taking) continue;
            n++;
            String sides = names(server, w.sideA) + "  vs  " + names(server, w.sideB);
            if (taking) b.append("\n  #").append(w.id).append(" WON: take ").append(w.takeLeft).append(" more chunk(s) within ").append(mins(w.takeUntil - t)).append(" (/war take)");
            else if (called) b.append("\n  #").append(w.id).append(" CALL TO ARMS: ").append(sides).append(" (/war join ")
                    .append(WarManager.name(server, w.callA.contains(me) ? w.attacker : w.defender)).append(")");
            else {
                b.append("\n  #").append(w.id).append(w.state == WarData.DECLARED ? " starts in " + mins(w.startsAt - t) : " ACTIVE, " + mins(w.endsAt - t) + " left").append(": ").append(sides);
                if (w.state == WarData.ACTIVE) b.append(" | score ").append(w.points[1]).append(" : ").append(w.points[2]).append(" (/war score)");
                if (w.peaceBy != 0 && w.peaceUntil > t) b.append(w.peaceBy == s ? " | your peace offer is open" : " | THEY OFFER PEACE (accept in the War Room: /war room)");
            }
        }
        if (n == 0) b.append("\n  at peace");
        String text = b.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int score(CommandSourceStack src, int id) {
        MinecraftServer server = src.getServer();
        WarData d = WarData.get(server);
        WarData.War w = id > 0 ? d.byId(id) : null;
        if (w == null && src.getEntity() instanceof ServerPlayer p) {
            UUID me = OpacBridge.nationOf(server, p.getUUID());
            for (WarData.War x : d.wars) if (x.sideOf(me) != 0) w = x;
        }
        if (w == null) { src.sendFailure(Component.translatable(M + "no_score")); return 0; }
        String state = w.state == WarData.DECLARED ? "declared" : w.state == WarData.ACTIVE ? "ACTIVE" : "over";
        String text = "War #" + w.id + " (" + state + ")\n  " + names(server, w.sideA) + ": " + WarScore.line(w, 1) + "\n  " + names(server, w.sideB) + ": " + WarScore.line(w, 2)
                + "\n  To win at the end: at least " + Balance.WAR_WIN_MIN + " points and " + Balance.WAR_WIN_MARGIN + " x the other side, else a draw.";
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int treaty(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        WarData d = WarData.get(server);
        long t = WarManager.now();
        StringBuilder b = new StringBuilder();
        for (var e : d.treaty.entrySet())
            b.append("\n  ").append(WarManager.name(server, e.getKey())).append(e.getValue()[1] == 0 ? ": signed" : ": withdrawing, bound for " + mins(e.getValue()[1] - t));
        for (var e : d.breakers.entrySet()) if (e.getValue() > t) b.append("\n  ").append(WarManager.name(server, e.getKey())).append(": TREATY BREAKER (").append(mins(e.getValue() - t)).append(")");
        if (b.length() == 0) b.append("\n  nobody has signed");
        String text = b.toString();
        src.sendSuccess(() -> Component.translatable(M + "treaty_head").append(text), false);
        return 1;
    }

    private static int board(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        java.util.List<java.util.Map.Entry<UUID, int[]>> rows = new java.util.ArrayList<>(WarData.get(server).totals.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (var e : rows) {
            if (++i > 15) break;
            int[] v = e.getValue();
            b.append("\n  ").append(i).append(". ").append(WarManager.name(server, e.getKey())).append(": ").append(v[0]).append(" pts, ").append(v[1]).append(" won, ")
                    .append(v[2]).append(" lost, ").append(v[3]).append(" drawn").append(WarTreaty.breaker(server, e.getKey()) ? "  TREATY BREAKER" : "");
        }
        if (rows.isEmpty()) b.append("\n  no wars yet");
        String text = b.toString();
        src.sendSuccess(() -> Component.translatable(M + "board_head").append(text), false);
        return 1;
    }

    private static String names(MinecraftServer server, java.util.Set<UUID> side) {
        StringBuilder b = new StringBuilder();
        for (UUID n : side) b.append(b.length() == 0 ? "" : " + ").append(WarManager.name(server, n));
        return b.toString();
    }
}
