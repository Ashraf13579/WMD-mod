package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ShipTargetCommands {

    private ShipTargetCommands() {}

    private static final double RANGE = 600.0;

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("airship")
                .then(Commands.literal("target").executes(c -> target(c, false)).then(Commands.literal("confirm").executes(c -> target(c, true))))
                .then(Commands.literal("clear").executes(ShipTargetCommands::clear))
                .then(Commands.literal("status").executes(ShipTargetCommands::status)));
    }

    private static int target(CommandContext<CommandSourceStack> c, boolean confirm) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) { c.getSource().sendFailure(Component.literal("Players only.")); return 0; }
        if (!SableBridge.active()) { c.getSource().sendFailure(Component.translatable("message.wmd.ship.no_sable")); return 0; }
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        UUID pick = null;
        List<SableBridge.ShipInfo> along = SableBridge.shipsAlongSegment(sl, eye, eye.add(look.scale(RANGE)));
        double best = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : along) {
            if (!s.isLarge()) continue;
            double d = s.worldCenter().distanceTo(eye);
            if (d < best) { best = d; pick = s.id(); }
        }
        if (pick == null) {
            for (SableBridge.ShipInfo s : SableBridge.largeShipsNear(sl, p.getX(), p.getZ(), RANGE)) {
                double d = s.worldCenter().distanceTo(eye);
                if (d < best) { best = d; pick = s.id(); }
            }
        }
        if (pick == null) { c.getSource().sendFailure(Component.translatable("message.wmd.ship.none_near")); return 0; }
        ShipTargets.Result r = ShipTargets.designate(sl, p.getUUID(), pick, confirm);
        if (r.ok()) c.getSource().sendSuccess(r::msg, false); else c.getSource().sendFailure(r.msg());
        return r.ok() ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        ShipTargets.clear(p.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable("message.wmd.ship.cleared"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        ShipTargets.Desig d = ShipTargets.get(p.getUUID(), p.serverLevel().getGameTime());
        if (d == null) c.getSource().sendSuccess(() -> Component.translatable("message.wmd.ship.none"), false);
        else {
            int secs = (int) Math.max(0, (d.until() - p.serverLevel().getGameTime()) / 20);
            c.getSource().sendSuccess(() -> Component.translatable("message.wmd.ship.status", d.name().isEmpty() ? d.ship().toString().substring(0, 8) : d.name(), secs / 60, secs % 60), false);
        }
        return 1;
    }
}
