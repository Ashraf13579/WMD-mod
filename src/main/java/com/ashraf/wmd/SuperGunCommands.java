package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SuperGunCommands {

    private SuperGunCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("art")
                        .then(Commands.literal("super")
                                .then(Commands.literal("give").executes(c -> give(c.getSource())))
                                .then(Commands.literal("ready").executes(c -> ready(c.getSource())))
                                .then(Commands.literal("fire")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(c -> fire(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))))))));
    }

    private static int give(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        p.getInventory().add(new ItemStack(Wmd.SUPER_GUN_ITEM.get()));
        p.getInventory().add(new ItemStack(Wmd.SHELL_SUPER_HE.get(), Balance.SUPER_GUN_MAGAZINE));
        p.getInventory().add(new ItemStack(Wmd.SUPER_CHARGE.get(), Balance.SUPER_GUN_CHARGE_SLOT));
        src.sendSuccess(() -> Component.literal("Gave the super gun + " + Balance.SUPER_GUN_MAGAZINE + " shells + " + Balance.SUPER_GUN_CHARGE_SLOT + " charges"), true);
        return 1;
    }

    private static SuperGunBlockEntity nearest(CommandSourceStack src, ServerPlayer p) {
        SuperGunBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        Vec3 at = src.getPosition();
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.all()) {
            if (!(g instanceof SuperGunBlockEntity s) || g.getLevel() != src.getLevel() || !p.getUUID().equals(g.getOwner())) continue;
            double d = g.getBlockPos().getCenter().distanceToSqr(at);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    private static int fire(CommandSourceStack src, int x, int z) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        SuperGunBlockEntity g = nearest(src, p);
        if (g == null) { src.sendFailure(Component.literal("No loaded super gun of yours in this dimension (/wmd art super give)")); return 0; }
        if (g.ammo(Artillery.HE) < 1 || g.propellant() < Balance.SUPER_GUN_CHARGES) g.testLoad(Artillery.HE);
        int st = g.order(x, z, Artillery.HE, 1, p.getGameProfile().getName());
        src.sendSuccess(() -> Component.literal(String.format("Super gun at %s: round at %d %d, %d m, %d charge(s), spread %.1f%s -> ", g.getBlockPos().toShortString(), x, z,
                g.distance(), g.usedCharge(), g.spreadShown(), g.spotted() ? " (spotted)" : "")).append(Component.translatable("gui.wmd.art.st." + st)), true);
        return st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING ? 1 : 0;
    }

    private static int ready(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        SuperGunBlockEntity g = nearest(src, p);
        if (g == null) { src.sendFailure(Component.literal("No loaded super gun of yours in this dimension")); return 0; }
        g.testReady();
        src.sendSuccess(() -> Component.literal("Super gun at " + g.getBlockPos().toShortString() + ": reload skipped"), true);
        return 1;
    }
}
