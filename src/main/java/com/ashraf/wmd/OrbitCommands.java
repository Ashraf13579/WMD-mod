package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class OrbitCommands {

    private OrbitCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("orbit").requires(TestCmds::test)
                        .then(Commands.literal("go")
                                .executes(c -> go(c.getSource(), false))
                                .then(Commands.literal("platform").executes(c -> go(c.getSource(), true))))
                        .then(Commands.literal("back").executes(c -> back(c.getSource())))
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))
                        .then(Commands.literal("suit").executes(c -> suit(c.getSource())))
                        .then(Commands.literal("planet").then(Commands.literal("refresh").executes(c -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer p) { Planet.refresh(p); c.getSource().sendSuccess(() -> Component.literal("The planet below is read again"), false); }
                            return 1; })))
                        .then(Commands.literal("oxygen")
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, Balance.O2_FULL / 20))
                                        .executes(c -> oxygen(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds")))))));
    }

    private static int go(CommandSourceStack src, boolean platform) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        Orbit.goUp(p, platform);
        return 1;
    }

    private static int back(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        if (!Orbit.isOrbit(p.level())) { src.sendFailure(Component.literal("You are not in orbit")); return 0; }
        Orbit.goDown(p);
        return 1;
    }

    private static int suit(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        net.minecraft.world.item.ItemStack torso = new net.minecraft.world.item.ItemStack(Wmd.EVA_TORSO.get());
        EvaSuitItem.setOxygen(torso, Balance.O2_FULL);
        for (net.minecraft.world.item.ItemStack s : new net.minecraft.world.item.ItemStack[] { new net.minecraft.world.item.ItemStack(Wmd.EVA_HELMET.get()), torso,
                new net.minecraft.world.item.ItemStack(Wmd.EVA_LEGS.get()), new net.minecraft.world.item.ItemStack(Wmd.EVA_BOOTS.get()), OxygenTankItem.full(Wmd.OXYGEN_TANK.get()) })
            if (!p.getInventory().add(s)) p.drop(s, false);
        src.sendSuccess(() -> Component.literal("EVA suit + oxygen tank given").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int oxygen(CommandSourceStack src, int seconds) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        net.minecraft.world.item.ItemStack t = EvaSuitItem.torso(p);
        if (t.isEmpty()) { src.sendFailure(Component.literal("Wear the EVA suit torso first")); return 0; }
        EvaSuitItem.setOxygen(t, seconds * 20);
        src.sendSuccess(() -> Component.literal("Suit oxygen set to " + EvaSuitItem.clock(seconds * 20)).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int n = Orbit.slotOf(p.server, p.getUUID());
        BlockPos c = Orbit.slotCentre(n);
        src.sendSuccess(() -> Component.literal("Orbit slot " + n + ": centre x " + c.getX() + ", y " + c.getY() + ", z " + c.getZ() + "; build radius "
                + Balance.ORBIT_BUILD_R + "; you are " + (Orbit.isOrbit(p.level()) ? "IN ORBIT" : "on " + p.level().dimension().location())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}
