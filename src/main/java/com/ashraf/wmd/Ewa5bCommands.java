package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Ewa5bCommands {

    private Ewa5bCommands() {}

    private static final String[] PHASES = { "climbing", "outbound", "LOITERING", "returning", "landing", "FALLING" };

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ewa")
                        .then(Commands.literal("status").executes(c -> ewaStatus(c.getSource())))
                        .then(Commands.literal("warn").executes(c -> ewaWarn(c.getSource(), 2000))
                                .then(Commands.argument("dist", IntegerArgumentType.integer(100, 30_000)).executes(c -> ewaWarn(c.getSource(), IntegerArgumentType.getInteger(c, "dist"))))))
                .then(Commands.literal("drone")
                        .then(Commands.literal("give").executes(c -> droneGive(c.getSource())))
                        .then(Commands.literal("launch")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> droneLaunch(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("list").executes(c -> droneList(c.getSource())))
                        .then(Commands.literal("kill").executes(c -> droneKill(c.getSource(), 0))
                                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> droneKill(c.getSource(), IntegerArgumentType.getInteger(c, "id")))))));
    }

    private static int ewaStatus(CommandSourceStack src) {
        var l = EarlyWarningArrayBlockEntity.all();
        if (l.isEmpty()) { src.sendSuccess(() -> Component.literal("No early-warning array loaded"), false); return 0; }
        for (EarlyWarningArrayBlockEntity a : l) {
            String line = String.format("Array at %s (%s) | owner %s | %s | range %d | %d warning(s) kept", a.getBlockPos().toShortString(),
                    a.getLevel() != null ? a.getLevel().dimension().location().getPath() : "?", a.getOwnerName(), a.isRunning() ? "POWERED, watching" : "no power",
                    Balance.EWA_RANGE, a.recent().size());
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return l.size();
    }

    private static int ewaWarn(CommandSourceStack src, int dist) {
        Vec3 at = src.getPosition();
        EarlyWarningArrayBlockEntity best = null;
        double bd = 64.0 * 64.0;
        for (EarlyWarningArrayBlockEntity a : EarlyWarningArrayBlockEntity.all()) {
            if (a.isRemoved() || a.getLevel() != src.getLevel()) continue;
            double d = a.getBlockPos().getCenter().distanceToSqr(at);
            if (d < bd) { bd = d; best = a; }
        }
        if (best == null) { src.sendFailure(Component.literal("Stand within 64 blocks of an early-warning array")); return 0; }
        if (best.getOwner() == null) { src.sendFailure(Component.literal("That array has no owner")); return 0; }
        EarlyWarning.testWarn((ServerLevel) best.getLevel(), best, dist);
        final String o = best.getOwnerName();
        src.sendSuccess(() -> Component.literal("Fake launch " + dist + " blocks north of the array: warning sent to " + o), true);
        return 1;
    }

    private static int droneGive(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ItemStack s = new ItemStack(Wmd.RECON_DRONE.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        src.sendSuccess(() -> Component.literal("Gave a recon drone"), false);
        return 1;
    }

    private static int droneLaunch(CommandSourceStack src, int x, int z) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Component why = Drones.launch(p, x, z);
        if (why != null) { src.sendFailure(why); return 0; }
        return 1;
    }

    private static int droneList(CommandSourceStack src) {
        var l = Drones.get(src.getServer()).drones();
        if (l.isEmpty()) { src.sendSuccess(() -> Component.literal("No recon drones in the air"), false); return 0; }
        for (Drones.Drone d : l) {
            String line = String.format("Drone #%d | owner %s | %s | at %d %d %d | target %d %d | health %.1f / %d%s", d.id, d.ownerName,
                    PHASES[Math.max(0, Math.min(PHASES.length - 1, d.phase))], Math.round(d.x), Math.round(d.y), Math.round(d.z), d.tx, d.tz, d.health,
                    Balance.DRONE_HEALTH, d.phase == Drones.LOITER ? " | " + d.ticks / 20 + " s left over the spot" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return l.size();
    }

    private static int droneKill(CommandSourceStack src, int id) {
        int n = Drones.get(src.getServer()).killAll(src.getServer().overworld(), id, "test command");
        src.sendSuccess(() -> Component.literal(n + " drone(s) shot down"), true);
        return n;
    }
}
