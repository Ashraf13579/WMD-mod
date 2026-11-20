package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarCommands {

    private WarCommands() {}

    @SubscribeEvent
    static void registerRollback(RegisterCommandsEvent event) {
        var I = com.mojang.brigadier.arguments.IntegerArgumentType.class;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("rollback")
                        .then(Commands.literal("status").executes(c -> rbStatus(c.getSource())))
                        .then(Commands.literal("preview").then(rbArgs(true)))
                        .then(rbArgs(false))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> rbArgs(boolean preview) {
        return Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 2048))
                .then(Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 525_600))
                        .executes(c -> rollback(c.getSource(), c, null, preview))
                        .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> rollback(c.getSource(), c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "player"), preview))));
    }

    private static int rollback(CommandSourceStack src, com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String player, boolean preview) {
        int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"), minutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "minutes");
        UUID shooter = null;
        if (player != null) {
            var prof = src.getServer().getProfileCache() == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : src.getServer().getProfileCache().get(player);
            if (prof.isEmpty()) { src.sendFailure(Component.literal("Unknown player " + player)); return 0; }
            shooter = prof.get().getId();
        }
        UUID to = src.getEntity() instanceof ServerPlayer sp ? sp.getUUID() : null;
        RollbackJob.admin(src.getLevel(), net.minecraft.core.BlockPos.containing(src.getPosition()), radius, minutes, shooter, to, preview);
        src.sendSuccess(() -> Component.translatable(preview ? "message.wmd.rollback.counting" : "message.wmd.rollback.started", radius, minutes), true);
        if (!Config.Server.rollbackLogAll()) src.sendSuccess(() -> Component.translatable("message.wmd.rollback.lean").withStyle(net.minecraft.ChatFormatting.GRAY), false);
        return 1;
    }

    private static int rbStatus(CommandSourceStack src) {
        RollbackIndex idx = RollbackIndex.get(src.getServer());
        long n = 0;
        for (RollbackIndex.Seg s : idx.segs) n += s.n;
        long total = n;
        src.sendSuccess(() -> Component.literal("Rollback log: " + total + " block changes in " + idx.segs.size() + " segments (kept " + Config.Server.rollbackKeepDays()
                + " days), " + RollbackJob.running() + " job(s) running, logging " + (Config.Server.rollbackLog() ? "on" : "OFF") + ", wilderness heal "
                + (Config.Server.wildernessHeal() ? "on (" + Config.Server.wildernessHealHours() + " h)" : "off")), false);
        return 1;
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("war").then(Commands.literal("nation").executes(c -> nation(c.getSource())))
                        .then(Commands.literal("writes").executes(c -> writes(c.getSource()))
                                .then(Commands.literal("reset").executes(c -> { BlastLog.reset(); c.getSource().sendSuccess(() -> Component.literal("Blast write counters reset"), false); return 1; })))
                        .then(Commands.literal("start").then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(c -> admin(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "id"), true))))
                        .then(Commands.literal("end").then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(c -> admin(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "id"), false))))));
    }

    private static String name(MinecraftServer server, UUID nation) {
        if (nation == null) return "none";
        String n = OpacBridge.nationName(server, nation);
        return (n.isEmpty() ? "?" : n) + " (" + nation.toString().substring(0, 8) + ")";
    }

    private static int admin(CommandSourceStack src, int id, boolean start) {
        boolean ok = start ? WarManager.adminStart(src.getServer(), id) : WarManager.adminEnd(src.getServer(), id);
        if (ok) src.sendSuccess(() -> Component.literal("War #" + id + (start ? " started" : " ended")), true);
        else src.sendFailure(Component.literal("No such " + (start ? "declared" : "running") + " war #" + id + " (/war nations, /war status)"));
        return ok ? 1 : 0;
    }

    private static int writes(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        StringBuilder b = new StringBuilder("Blast block changes since start: " + BlastLog.total() + " (no known shooter: " + BlastLog.unknown() + ")");
        for (var e : BlastLog.byShooter().entrySet()) {
            var p = server.getPlayerList().getPlayer(e.getKey());
            b.append("\n  ").append(p != null ? p.getGameProfile().getName() : e.getKey().toString().substring(0, 8)).append(": ").append(e.getValue()[0]);
        }
        String line = b.toString();
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int nation(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        if (!OpacBridge.active()) { src.sendFailure(Component.literal("Open Parties and Claims is not installed (or failed): no nations, no wars")); return 0; }
        UUID me = OpacBridge.nationOf(server, p.getUUID());
        StringBuilder allies = new StringBuilder();
        for (UUID a : OpacBridge.allies(server, me)) allies.append(allies.length() == 0 ? "" : ", ").append(name(server, a))
                .append(OpacBridge.mutualAllies(server, me, a) ? "" : " (one-way)");
        UUID here = OpacBridge.nationAt(p.serverLevel(), p.blockPosition());
        UUID claimer = OpacBridge.claimOwner(p.serverLevel(), p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4);
        String line = "Nation: " + name(server, me) + (me == null ? "" : " | you lead it: " + OpacBridge.leads(server, p.getUUID())
                + " | members " + OpacBridge.members(server, me).size() + ", online " + OpacBridge.online(server, me)
                + " | allies: " + (allies.length() == 0 ? "none" : allies))
                + "\nThis chunk: " + (claimer == null ? "unclaimed" : "claimed (owner " + claimer.toString().substring(0, 8) + ", nation " + name(server, here) + ")")
                + " | nations on the server: " + OpacBridge.nations(server).size();
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
