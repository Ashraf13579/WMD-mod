package com.ashraf.wmd;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class UplinkCommands {

    private UplinkCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("uplink").requires(TestCmds::test)
                        .then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("give")
                                .executes(c -> give(c.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                        .executes(c -> give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "tier")))))
                        .then(Commands.literal("force")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(c -> force(c.getSource().getPlayerOrException(), BoolArgumentType.getBool(c, "on")))))
                        .then(Commands.literal("retarget")
                                .then(Commands.argument("id", LongArgumentType.longArg(1))
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(c -> retarget(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "id"),
                                                                IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), ""))
                                                        .then(Commands.argument("player", StringArgumentType.word())
                                                                .executes(c -> retarget(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "id"),
                                                                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), StringArgumentType.getString(c, "player"))))))))
                        .then(Commands.literal("homing")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> homing(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))));
    }

    private static int status(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean on = Uplink.active(level.getServer(), p.getUUID());
        p.sendSystemMessage(Component.literal("Uplink coverage: " + (on ? "ONLINE" : "offline") + (Uplink.FORCED.contains(p.getUUID()) ? " (forced)" : "")));
        long now = level.getGameTime();
        FlightManager mgr = FlightManager.get(level);
        int n = 0;
        for (Flight f : mgr.flights()) {
            if (!f.build().uplink() || !f.isOwnedBy(p.getUUID(), p.getGameProfile().getName())) continue;
            n++;
            String lock = mgr.uplinkLocks.get(f.id());
            p.sendSystemMessage(Component.literal(String.format("#%d tier %d %s -> %d %d | %d s | legs %d | reach %d | %s%s%s", f.id(), f.tier(), f.build().warheadType(),
                    f.tx(), f.tz(), Math.max(0L, (f.endTime() - now) / 20), f.legs().size(), (int) Uplink.reach(f, now),
                    (f.uflags() & Flight.U_RETARGETED) != 0 ? "retargeted" : "retarget free", (f.uflags() & Flight.U_HOMED) != 0 ? " | homed" : "",
                    lock != null ? " | lock " + lock : "")));
        }
        if (n == 0) p.sendSystemMessage(Component.literal("No uplinked missiles of yours in flight here"));
        return n;
    }

    private static int give(ServerPlayer p, int tier) {
        ItemStack m = new ItemStack(Missiles.itemFor(tier));
        m.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(tier, tier, tier, tier, "he", true, false));
        if (!p.getInventory().add(m)) p.drop(m, false);
        ItemStack cell = new ItemStack(Wmd.UPLINK_CELL.get());
        if (!p.getInventory().add(cell)) p.drop(cell, false);
        p.sendSystemMessage(Component.literal("Gave an uplinked tier " + tier + " missile and one uplink cell"));
        return 1;
    }

    private static int force(ServerPlayer p, boolean on) {
        if (on) Uplink.FORCED.add(p.getUUID()); else Uplink.FORCED.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("Uplink coverage forced " + (on ? "ON" : "off") + " for you (until the server restarts)"));
        return 1;
    }

    private static int retarget(ServerPlayer p, long id, int x, int z, String player) {
        p.sendSystemMessage(Uplink.retarget(p, id, x, z, player));
        return 1;
    }

    private static int homing(ServerPlayer p, int x, int z) {
        var m = Uplink.enemyMachine(p.serverLevel(), x + 0.5, z + 0.5, p.getUUID());
        p.sendSystemMessage(Component.literal(m == null ? "No enemy machine within " + (int) Balance.UPLINK_HOMING_RADIUS + " blocks of " + x + " " + z + " (loaded chunks)"
                : "Terminal homing would hit the machine at " + m.toShortString() + " (" + p.serverLevel().getBlockState(m).getBlock().getName().getString() + ")"));
        return m == null ? 0 : 1;
    }
}
