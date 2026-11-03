package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class S5Commands {

    private S5Commands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sat")
                        .then(Commands.literal("asat")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> asat(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("hack")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> hack(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"), false))
                                        .then(Commands.literal("instant")
                                                .executes(c -> hack(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"), true)))))
                        .then(Commands.literal("rekey")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> rekey(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("hacks").executes(c -> hacks(c.getSource()))))
                .then(Commands.literal("cc")
                        .then(Commands.literal("intercept")
                                .then(Commands.literal("test").executes(c -> test(c.getSource(), c.getSource().getPlayerOrException())))
                                .then(Commands.literal("engage")
                                        .then(Commands.literal("any").executes(c -> engage(c.getSource(), c.getSource().getPlayerOrException(), SatDefence.MODE_ANY)))
                                        .then(Commands.literal("own").executes(c -> engage(c.getSource(), c.getSource().getPlayerOrException(), SatDefence.MODE_ZONE)))))));
    }

    private static int asat(CommandSourceStack src, int id) {
        OrbitMath.Sat s = OrbitData.get(src.getServer()).byId(id);
        if (s == null) { src.sendFailure(Component.literal("No satellite #" + id)); return 0; }
        boolean hit = src.getLevel().random.nextInt(100) < Balance.ASAT_HIT_PCT;
        ServerPlayer p = src.getPlayer();
        Asat.strike(src.getServer(), s, hit, p != null ? p.getGameProfile().getName() : "Test ASAT", p);
        src.sendSuccess(() -> Component.literal("ASAT at satellite #" + id + ": " + (hit ? "HIT (destroyed)" : "MISS") + " (" + Balance.ASAT_HIT_PCT + " % roll)"), true);
        return 1;
    }

    private static int hack(CommandSourceStack src, ServerPlayer p, int id, boolean instant) {
        OrbitMath.Sat s = OrbitData.get(src.getServer()).byId(id);
        if (s == null) { src.sendFailure(Component.literal("No satellite #" + id)); return 0; }
        if (p.getUUID().equals(s.owner())) { src.sendFailure(Component.literal("Satellite #" + id + " is already yours")); return 0; }
        if (instant) {
            SatHack.succeed(src.getServer(), s, p.getUUID(), p.getGameProfile().getName());
            src.sendSuccess(() -> Component.literal("Satellite #" + id + " hacked instantly: it is yours now"), true);
            return 1;
        }
        GroundStationBlockEntity st = null;
        double bd = 64.0 * 64.0;
        for (BlockPos pos : GroundStationBlockEntity.stationsOf(p.level(), p.getUUID())) {
            double d = pos.distToCenterSqr(p.position());
            if (d <= bd && p.level().getBlockEntity(pos) instanceof GroundStationBlockEntity g) { bd = d; st = g; }
        }
        if (st == null) { src.sendFailure(Component.literal("No ground station of yours within 64 blocks (or use: instant)")); return 0; }
        Component answer = SatHack.start(p, st, id, false);
        if (answer != null) src.sendSuccess(() -> answer, false);
        return 1;
    }

    private static int rekey(CommandSourceStack src, int id) {
        Component answer = SatHack.rekey(src.getServer(), null, id, true);
        src.sendSuccess(() -> answer, true);
        return 1;
    }

    private static int hacks(CommandSourceStack src) {
        var l = SatHack.describe(src.getServer());
        src.sendSuccess(() -> Component.literal(l.isEmpty() ? "No hacks running" : l.size() + " hack(s): " + String.join(" | ", l)), false);
        return l.size();
    }

    private static int test(CommandSourceStack src, ServerPlayer p) {
        Component answer = SatDefence.testLaunch(p);
        src.sendSuccess(() -> answer, false);
        return 1;
    }

    private static int engage(CommandSourceStack src, ServerPlayer p, int mode) {
        SatDefenceData.get(src.getServer()).setMode(p.getUUID(), mode);
        boolean console = CommandCenter.hasModule(p.getUUID(), CcModule.INT_SAT_CONSOLE);
        src.sendSuccess(() -> Component.literal("Engage mode " + (mode == SatDefence.MODE_ANY ? "ANY" : "OWN AREA") + " saved"
                + (console ? "" : " (it counts only while your interceptor console is online: now OWN AREA)")), false);
        return 1;
    }
}
