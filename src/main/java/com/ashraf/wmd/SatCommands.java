package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SatCommands {

    private SatCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sat").requires(TestCmds::test)
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("add").requires(TestCmds::test)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(OrbitData.TYPES, b))
                                        .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "type"), c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("owner", EntityArgument.player())
                                                .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "type"), EntityArgument.getPlayer(c, "owner"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> remove(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("pass").requires(TestCmds::test)
                                .executes(c -> pass(c.getSource(), -1))
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> pass(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))
                        .then(Commands.literal("life").requires(TestCmds::test)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("hours", IntegerArgumentType.integer(0, 100_000))
                                                .executes(c -> life(c.getSource(), IntegerArgumentType.getInteger(c, "id"), IntegerArgumentType.getInteger(c, "hours"))))))));
    }

    private static int list(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        OrbitData d = OrbitData.get(server);
        Vec3 at = src.getPosition();
        long now = System.currentTimeMillis();
        src.sendSuccess(() -> Component.literal("Orbit: " + d.count() + " / " + Balance.SAT_MAX + " satellites, " + OrbitData.climbing(server) + " rocket(s) climbing"), false);
        for (OrbitMath.Sat s : d.sats()) {
            long left = Math.max(0L, s.endMs() - now);
            long over = OrbitMath.overheadLeft(s, at.x, at.z, now);
            double[] sky = OrbitMath.sky(s, at.x, at.z, now);
            String pass = over > 0 ? String.format("OVERHEAD (%s left, az %.0f, el %.0f)", OrbitMath.mmss(over), sky[0], sky[1])
                    : "next pass over you in " + OrbitMath.mmss(OrbitMath.nextPass(s, at.x, at.z, now));
            String line = String.format("#%d %s | owner %s | life %dd %dh | %s | heading %.0f | %s", s.id(), s.type(), s.ownerName(),
                    left / 86_400_000L, (left / 3_600_000L) % 24, pass, Math.toDegrees(s.heading()), OrbitData.controlled(s) ? "in control" : "NO control (needs a powered ground station + an active command center)");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return d.count();
    }

    private static int add(CommandSourceStack src, String type, ServerPlayer owner) {
        if (!OrbitData.TYPES.contains(type)) { src.sendFailure(Component.literal("Type must be one of " + String.join(", ", OrbitData.TYPES))); return 0; }
        OrbitMath.Sat s = OrbitData.get(src.getServer()).add(src.getServer(), owner.getUUID(), owner.getGameProfile().getName(), type, src.getLevel().random);
        if (s == null) { src.sendFailure(Component.translatable("message.wmd.sat.orbit_full", Balance.SAT_MAX, Balance.SAT_MAX)); return 0; }
        src.sendSuccess(() -> Component.literal("Satellite #" + s.id() + " (" + type + ") in orbit for " + owner.getGameProfile().getName()), true);
        return 1;
    }

    private static int remove(CommandSourceStack src, int id) {
        if (!OrbitData.get(src.getServer()).remove(src.getServer(), id)) { src.sendFailure(Component.literal("No satellite #" + id)); return 0; }
        src.sendSuccess(() -> Component.literal("Satellite #" + id + " removed from orbit"), true);
        return 1;
    }

    private static int pass(CommandSourceStack src, int id) {
        OrbitData d = OrbitData.get(src.getServer());
        Vec3 at = src.getPosition();

        int[] des = src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? SatDesignator.target(sp.getUUID()) : null;
        String kind = "your designated spot";
        if (des == null && src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp2) {
            for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(sp2.getUUID()))
                if (b.designated() != null) { des = new int[] { b.designated().x(), b.designated().z() }; kind = "your command center's target"; break; }
        }
        if (des != null) at = new Vec3(des[0] + 0.5, at.y, des[1] + 0.5);
        final String where = des != null ? kind + " " + des[0] + " " + des[1] : "you";
        long now = System.currentTimeMillis();
        int n = 0;
        for (OrbitMath.Sat s : d.sats()) {
            if (id > 0 && s.id() != id) continue;
            d.replace(src.getServer(), s.withPhase(OrbitMath.phaseForPass(s, at.x, at.z, now, 10.0)));
            n++;
        }
        if (n == 0) { src.sendFailure(Component.literal(id > 0 ? "No satellite #" + id : "No satellites in orbit")); return 0; }
        final int count = n;
        src.sendSuccess(() -> Component.literal(count + " satellite(s) now passing over " + where + " (overhead for " + OrbitMath.mmss(OrbitMath.windowMs() - 10_000L) + ")"), true);
        return n;
    }

    private static int life(CommandSourceStack src, int id, int hours) {
        OrbitData d = OrbitData.get(src.getServer());
        OrbitMath.Sat s = d.byId(id);
        if (s == null) { src.sendFailure(Component.literal("No satellite #" + id)); return 0; }

        d.replace(src.getServer(), s.withLaunchMs(System.currentTimeMillis() + hours * 3_600_000L - s.lifeMs()));
        src.sendSuccess(() -> Component.literal("Satellite #" + id + ": " + hours + " h of RTG life left"), true);
        return 1;
    }
}
