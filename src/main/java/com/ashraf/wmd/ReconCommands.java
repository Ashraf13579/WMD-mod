package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ReconCommands {

    private ReconCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sat").then(Commands.literal("recon")
                        .then(Commands.literal("target")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-29_000_000, 29_000_000))
                                        .then(Commands.argument("z", IntegerArgumentType.integer(-29_000_000, 29_000_000))
                                                .executes(c -> target(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("snap").executes(c -> snap(c.getSource())))
                        .then(Commands.literal("fill").executes(c -> fill(c.getSource()))
                                .then(Commands.literal("stop").executes(c -> fillStop(c.getSource()))))
                        .then(Commands.literal("warn").executes(c -> warn(c.getSource())))
                        .then(Commands.literal("status").executes(c -> status(c.getSource()))))));
    }

    private static int target(CommandSourceStack src, int x, int z) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Recon.cancel(p.getUUID());
        ReconData.get(src.getServer()).setTarget(p.getUUID(), x, z);
        src.sendSuccess(() -> Component.literal("Recon target area for " + p.getGameProfile().getName() + ": " + x + " " + z + " (+- " + Balance.RECON_SNAPSHOT_RADIUS + " blocks)"), true);
        return 1;
    }

    private static int snap(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ReconData.Entry e = ReconData.get(src.getServer()).entry(p.getUUID());
        if (e == null || !e.hasTarget) { src.sendFailure(Component.literal("Set a target first: /wmd sat recon target <x> <z>")); return 0; }
        OrbitMath.Sat s = Recon.sat(src.getServer(), p.getUUID());
        if (!Recon.start(src.getServer(), p.getUUID(), e, s != null ? s.id() : -1)) { src.sendFailure(Component.literal("A snapshot is already being taken")); return 0; }
        src.sendSuccess(() -> Component.literal("Imaging " + e.tx + " " + e.tz + " (loaded chunks only)..."), false);
        return 1;
    }

    private static int fill(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ReconData.Entry e = ReconData.get(src.getServer()).entry(p.getUUID());
        if (!ReconFill.force(src.getServer(), p.getUUID(), e)) { src.sendFailure(Component.literal("Set a target first: /wmd sat recon target <x> <z>")); return 0; }
        src.sendSuccess(() -> Component.literal("Mapping " + e.tx + " " + e.tz + " like Chunky: " + Config.Server.reconFillInFlight() + " chunks at once, paused while the server tick is over "
                + Config.Server.reconFillMaxMspt() + " ms. Progress on the recon map + the log; a chat note when done."), false);
        return 1;
    }

    private static int fillStop(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ReconFill.stop(src.getServer(), p.getUUID(), "stopped by command", true)) { src.sendFailure(Component.literal("No recon fill running for you")); return 0; }
        src.sendSuccess(() -> Component.literal("Recon fill stopped (tickets released)."), false);
        return 1;
    }

    private static int warn(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Recon.warn(p, p.getX() + 2000.0, p.getZ(), (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()), 75, false, p.getRandom());
        return 1;
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ReconData.Entry e = ReconData.get(src.getServer()).entry(p.getUUID());
        long[] st = Recon.status(src.getServer(), p.getUUID(), e);
        String s = switch ((int) st[0]) {
            case Recon.STATUS_NONE -> "no recon satellite";
            case Recon.STATUS_NO_CONTROL -> "recon satellite NOT controlled (no powered ground station)";
            case Recon.STATUS_OVERHEAD -> "recon #" + st[1] + " OVERHEAD the target, " + OrbitMath.mmss(st[2]) + " left";
            case Recon.STATUS_DRONE -> "recon DRONE loitering over the target, " + OrbitMath.mmss(st[2]) + " left";
            default -> "recon #" + st[1] + (e != null && e.hasTarget ? ", next pass over the target in " + OrbitMath.mmss(st[2]) : ", no target set");
        };
        String t = e != null && e.hasTarget ? "target " + e.tx + " " + e.tz : "no target";
        String sn = e != null && e.snap != null ? "snapshot " + OrbitMath.mmss(System.currentTimeMillis() - e.snap.timeMs()) + " ago, " + e.snap.loadedPct() + "% seen, "
                + e.snap.icons().length / 3 + " machines" : "no snapshot";
        src.sendSuccess(() -> Component.literal("Recon: " + s + " | " + t + " | " + sn + " | space tracking " + (Recon.covered(src.getServer(), p.getUUID()) ? "ON" : "off")), false);
        return 1;
    }
}
