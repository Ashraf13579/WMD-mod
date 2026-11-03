package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CruiseCommands {

    private CruiseCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cruise")
                        .then(Commands.literal("give").executes(c -> give(c.getSource(), 3, false))
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] { "t2", "t3", "t2tv", "t3tv" }, b))
                                        .executes(c -> give(c.getSource(), tier(StringArgumentType.getString(c, "tier")), StringArgumentType.getString(c, "tier").endsWith("tv")))))
                        .then(Commands.literal("load").executes(c -> load(c.getSource(), 3, false))
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] { "t2", "t3", "t2tv", "t3tv" }, b))
                                        .executes(c -> load(c.getSource(), tier(StringArgumentType.getString(c, "tier")), StringArgumentType.getString(c, "tier").endsWith("tv")))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> fire(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4))
                                                        .executes(c -> fire(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                                IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("enemy")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 2000))
                                                .then(Commands.argument("distance", IntegerArgumentType.integer(Balance.CRUISE_MIN_RANGE, 5000))
                                                        .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                                IntegerArgumentType.getInteger(c, "distance")))))))
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))));
    }

    private static int tier(String s) { return s.startsWith("t2") ? 2 : 3; }

    private static MissileBuild build(int tier, boolean tv) {
        return new MissileBuild(tier, 2, tier, tier, "he", false, false, false, false, 0, MissileBuild.KIND_CRUISE).withSeeker(tv ? MissileBuild.SEEK_TV : "");
    }

    private static int give(CommandSourceStack src, int tier, boolean tv) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        p.getInventory().add(new ItemStack(Wmd.CRUISE_LAUNCHER_ITEM.get()));
        p.getInventory().add(new ItemStack(Wmd.ALKYLATION_UNIT_ITEM.get()));
        for (int i = 0; i < 4; i++) {
            ItemStack m = new ItemStack(Wmd.CRUISE_MISSILE.get());
            m.set(WmdComponents.MISSILE_BUILD.get(), build(tier, tv));
            p.getInventory().add(m);
        }
        p.getInventory().add(new ItemStack(Wmd.ALKYLATE_CANISTER.get(), 16));
        p.getInventory().add(new ItemStack(Wmd.ACID_CATALYST.get(), 16));
        p.getInventory().add(new ItemStack(Wmd.FUEL_CANISTER.get(), 16));
        p.getInventory().add(new ItemStack(Wmd.JET_ENGINE.get()));
        src.sendSuccess(() -> Component.literal("Cruise kit (T" + tier + (tv ? ", TV" : "") + ") given"), false);
        return 1;
    }

    private static CruiseLauncherBlockEntity nearest(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return null;
        CruiseLauncherBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (CruiseLauncherBlockEntity l : CruiseLaunchers.launchersOf(p.getUUID())) {
            if (l.getLevel() != p.level()) continue;
            double d = l.getBlockPos().distToCenterSqr(p.position());
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }

    private static int load(CommandSourceStack src, int tier, boolean tv) {
        CruiseLauncherBlockEntity l = nearest(src);
        if (l == null) { src.sendFailure(Component.literal("No loaded cruise launcher of yours (place one, give it power once so it reports in)")); return 0; }
        l.testLoad(build(tier, tv));
        src.sendSuccess(() -> Component.literal("Loaded the launcher at " + l.getBlockPos().toShortString() + " with 4 T" + tier + (tv ? " TV" : "") + " cruise missiles + fuel"), false);
        return 1;
    }

    private static int fire(CommandSourceStack src, int x, int z, int count) {
        CruiseLauncherBlockEntity l = nearest(src);
        if (l == null) { src.sendFailure(Component.literal("No loaded cruise launcher of yours")); return 0; }
        int st = l.order(x, z, count, src.getTextName());
        src.sendSuccess(() -> Component.literal("Launcher " + l.getBlockPos().toShortString() + ": ").append(Component.translatable("gui.wmd.cruise.st." + st)), false);
        return 1;
    }

    private static int enemy(CommandSourceStack src, int x, int z, int distance) {
        ServerLevel level = src.getLevel();
        double ox = x - distance + 0.5, oz = z + 0.5;
        int gy = CruiseRoute.groundAt(level, (int) Math.floor(ox), (int) Math.floor(oz));
        CruiseRoute r0 = new CruiseRoute(ox, oz, 1.0, 0.0, new int[] { x }, new int[] { z }, (float) Balance.CRUISE_SPEED, null);
        final CruiseRoute r = r0.withAlt(r0.sampleAltitude(level));
        long id = FlightManager.get(level).launchCruise(level, new Vec3(ox, gy + 2.0, oz), r, CruiseRoute.groundAt(level, x, z), "Enemy (test)",
                java.util.UUID.nameUUIDFromBytes("wmd-test-enemy".getBytes()), 0.0, build(3, false));
        src.sendSuccess(() -> Component.literal("Enemy cruise missile " + id + " from " + (int) ox + " " + (int) oz + " to " + x + " " + z + ", ~" + r.ticks() / 20 + " s"), true);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p != null) for (CruiseLauncherBlockEntity l : CruiseLaunchers.launchersOf(p.getUUID()))
            src.sendSuccess(() -> Component.literal(l.getBlockPos().toShortString() + ": ").append(Component.translatable("gui.wmd.cruise.st." + l.launcherStatus()))
                    .append(Component.literal(" | " + (l.hasTarget() ? "to " + l.targetX() + " " + l.targetZ() + " via " + l.waypoints() + " WP, " + l.routeLength() + " blocks" : "no route")
                            + " | missiles " + l.loaded() + ", fuel " + l.fuelCanisters())), false);
        long now = src.getLevel().getGameTime();
        int n = 0;
        for (Flight f : FlightManager.get(src.getLevel()).flights()) {
            if (!f.isCruise()) continue;
            n++;
            Vec3 at = f.position(now);
            src.sendSuccess(() -> Component.literal("cruise " + f.id() + " (" + f.launcher() + ") at " + (int) at.x + " " + (int) at.y + " " + (int) at.z + " -> " + f.tx() + " "
                    + f.tz() + ", " + Math.max(0, f.endTime() - now) / 20 + " s left"), false);
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("No cruise missile in the air"), false);
        return 1;
    }
}
