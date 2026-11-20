package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WardenCommands {

    private WardenCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("boss")
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("tp").executes(c -> tp(c.getSource())))
                        .then(Commands.literal("place")
                                .then(Commands.literal("here").executes(c -> {
                                    BlockPos at = BlockPos.containing(c.getSource().getPosition());
                                    return place(c.getSource(), at.getX(), at.getZ());
                                }))
                                .then(Commands.argument("x", IntegerArgumentType.integer(-29_000_000, 29_000_000))
                                        .then(Commands.argument("z", IntegerArgumentType.integer(-29_000_000, 29_000_000))
                                                .executes(c -> place(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("respawn").requires(TestCmds::test).executes(c -> respawn(c.getSource())))
                        .then(Commands.literal("wake").requires(TestCmds::test).executes(c -> wake(c.getSource())))
                        .then(Commands.literal("stage").requires(TestCmds::test)
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, 3))
                                        .executes(c -> stage(c.getSource(), IntegerArgumentType.getInteger(c, "stage")))))
                        .then(Commands.literal("kill").requires(TestCmds::test)
                                .executes(c -> kill(c.getSource(), -1))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86_400))
                                        .executes(c -> kill(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))));
    }

    private static WardenState state(CommandSourceStack src) { return WardenState.get(src.getServer()); }

    private static int status(CommandSourceStack src) {
        WardenState d = state(src);
        StringBuilder b = new StringBuilder("Warden: ").append(WardenState.PHASES[d.phase]);
        if (d.phase == WardenState.LAY || d.placed) {
            b.append("\nBunker centre: ").append(d.ox).append(' ').append(d.oy == Integer.MIN_VALUE ? "?" : String.valueOf(d.oy)).append(' ').append(d.oz);
            if (src.getLevel() == src.getServer().overworld()) {
                double dx = src.getPosition().x - d.ox, dz = src.getPosition().z - d.oz;
                b.append(" (").append(Math.round(Math.sqrt(dx * dx + dz * dz))).append(" blocks from you)");
            }
        }
        if (d.phase == WardenState.LAY) b.append("\n").append(d.repair ? "Repair" : "Build").append(": ").append(Math.round(d.progress() * 100)).append(" %");
        if (d.phase == WardenState.DOWN) {
            long s = d.msToRespawn() / 1000;
            b.append("\nRespawn in ").append(s / 3600).append(" h ").append(s / 60 % 60).append(" min ").append(s % 60).append(" s (real time)");
        }
        if (d.gaveUp) b.append("\nNo dry ground was found: place it with /wmd boss place");
        if (!Config.wardenBunker() && !d.placed) b.append("\nWorld generation of the bunker is off (config wardenBunker)");
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private static int place(CommandSourceStack src, int x, int z) {
        ServerLevel ow = src.getServer().overworld();
        if (src.getLevel() != ow && src.getEntity() != null) { src.sendFailure(Component.literal("The Warden's bunker can only be in the overworld")); return 0; }
        WardenState d = state(src);
        if (d.phase == WardenState.FIGHT) { src.sendFailure(Component.literal("The Warden is fighting: beat it first")); return 0; }
        boolean had = d.placed;
        d.placeAt(ow, x, z);
        src.sendSuccess(() -> Component.literal("Building the Warden's bunker at " + x + " " + z + " (the ground is measured first; watch /wmd boss status)"
                + (had ? ". The old bunker stays where it was." : "")), true);
        return 1;
    }

    private static int tp(CommandSourceStack src) {
        WardenState d = state(src);
        if (!d.placed) { src.sendFailure(Component.literal("There is no Warden bunker yet")); return 0; }
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Only a player can be moved")); return 0; }
        BlockPos at = d.baySpot();
        p.teleportTo(src.getServer().overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        return 1;
    }

    private static int respawn(CommandSourceStack src) {
        WardenState d = state(src);
        if (!d.placed) { src.sendFailure(Component.literal("There is no Warden bunker yet")); return 0; }
        WardenEntity w = WardenFight.find(src.getServer().overworld(), d);
        if (w != null) w.discard();
        d.startLay(true);
        src.sendSuccess(() -> Component.literal("Repairing the Warden's bunker; the Warden is armed again when it is done"), true);
        return 1;
    }

    private static int wake(CommandSourceStack src) {
        WardenState d = state(src);
        ServerLevel ow = src.getServer().overworld();
        WardenEntity w = d.placed ? WardenFight.find(ow, d) : null;
        if (d.phase != WardenState.ARMED || w == null) {
            src.sendFailure(Component.literal("No sleeping Warden here (phase: " + WardenState.PHASES[d.phase] + "; a player must be within "
                    + (int) Balance.WARDEN_WAKE_R + " blocks of the bunker for it to be put in its pit)"));
            return 0;
        }
        WardenFight.wake(ow, d, w);
        src.sendSuccess(() -> Component.literal("The Warden wakes"), true);
        return 1;
    }

    private static int stage(CommandSourceStack src, int st) {
        WardenState d = state(src);
        WardenEntity w = d.placed ? WardenFight.find(src.getServer().overworld(), d) : null;
        if (w == null || w.mode() != WardenEntity.ACTIVE) { src.sendFailure(Component.literal("The Warden is not fighting")); return 0; }
        w.toStage(st);
        src.sendSuccess(() -> Component.literal("The Warden is in stage " + st), true);
        return 1;
    }

    private static int kill(CommandSourceStack src, int seconds) {
        WardenState d = state(src);
        if (!d.placed) { src.sendFailure(Component.literal("There is no Warden bunker yet")); return 0; }
        WardenEntity w = WardenFight.find(src.getServer().overworld(), d);
        if (w != null) w.discard();
        long now = System.currentTimeMillis();
        d.beaten(seconds < 0 ? now : now - Balance.WB_RESPAWN_MS + seconds * 1000L);
        src.sendSuccess(() -> Component.literal("The Warden counts as beaten; it comes back " + (seconds < 0 ? "in 24 h (real time)" : "in " + seconds + " s")), true);
        return 1;
    }
}
