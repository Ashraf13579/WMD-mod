package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

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
public final class ArtCommands {

    private ArtCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("art")
                        .then(Commands.literal("give")
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] { "mortar", "howitzer", "shell_press", "counter_battery_radar" }, b))
                                        .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "what")))))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> fire(c, null))
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Artillery.TYPES, b))
                                                        .executes(c -> fire(c, StringArgumentType.getString(c, "type")))))))
                        .then(Commands.literal("spot")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> spot(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("enemy")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 1500, 1))
                                                .then(Commands.argument("distance", IntegerArgumentType.integer(120, 2000))
                                                        .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                                IntegerArgumentType.getInteger(c, "distance"), 1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 12))
                                                                .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                                        IntegerArgumentType.getInteger(c, "distance"), IntegerArgumentType.getInteger(c, "count"))))))))));
    }

    private static int give(CommandSourceStack src, String what) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        switch (what) {
            case "mortar" -> {
                p.getInventory().add(new ItemStack(Wmd.MORTAR_ITEM.get()));
                for (int t = 0; t < Artillery.TYPES.length; t++) p.getInventory().add(new ItemStack(Artillery.shellItem(Artillery.MORTAR, t), Balance.MORTAR_MAGAZINE));
            }
            case "howitzer" -> {
                p.getInventory().add(new ItemStack(Wmd.HOWITZER_ITEM.get()));
                for (int t = 0; t < Artillery.TYPES.length; t++) p.getInventory().add(new ItemStack(Artillery.shellItem(Artillery.HOWITZER, t), Balance.HOWITZER_MAGAZINE));
                p.getInventory().add(new ItemStack(ItemProcessBlockEntity.wmd("propellant_charge"), Balance.HOWITZER_CHARGE_SLOT));
            }
            case "shell_press" -> p.getInventory().add(new ItemStack(Wmd.SHELL_PRESS_ITEM.get()));
            case "counter_battery_radar" -> p.getInventory().add(new ItemStack(Wmd.COUNTER_BATTERY_RADAR_ITEM.get()));
            default -> { src.sendFailure(Component.literal("mortar, howitzer, shell_press or counter_battery_radar")); return 0; }
        }
        src.sendSuccess(() -> Component.literal("Gave " + what + (what.equals("shell_press") || what.equals("counter_battery_radar") ? "" : " + a full load of every shell type")), true);
        return 1;
    }

    private static int typeOf(String s) {
        if (s == null) return -1;
        for (int t = 0; t < Artillery.TYPES.length; t++) if (Artillery.TYPES[t].equals(s)) return t;
        return -2;
    }

    private static int fire(CommandContext<CommandSourceStack> c, String typeName) {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        int x = IntegerArgumentType.getInteger(c, "x"), z = IntegerArgumentType.getInteger(c, "z");
        int type = typeOf(typeName);
        if (type == -2) { src.sendFailure(Component.literal("Type: he, smoke, illum or cluster")); return 0; }
        ArtilleryGunBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        Vec3 at = src.getPosition();
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.all()) {
            if (g.getLevel() != src.getLevel() || !p.getUUID().equals(g.getOwner())) continue;
            if (g.gun() >= Artillery.GUNS.length) continue;
            double d = g.getBlockPos().getCenter().distanceToSqr(at);
            if (d < bd) { bd = d; best = g; }
        }
        if (best == null) { src.sendFailure(Component.literal("No loaded gun of yours in this dimension (place one: /wmd art give mortar)")); return 0; }
        int t = type >= 0 ? type : best.picked();
        if (best.ammo(t) < 1) best.testLoad(t);
        int st = best.order(x, z, t, 1, p.getGameProfile().getName());
        final ArtilleryGunBlockEntity g = best;
        final int stf = st;
        src.sendSuccess(() -> Component.literal(String.format("%s at %s: %s round at %d %d, %d m, spread %.1f%s -> ",
                Artillery.GUNS[g.gun()], g.getBlockPos().toShortString(), Artillery.TYPES[t], x, z, g.distance(), g.spreadShown(), g.spotted() ? " (spotted)" : ""))
                .append(Component.translatable("gui.wmd.art.st." + stf)), true);
        return stf == ArtilleryGunBlockEntity.S_LAYING || stf == ArtilleryGunBlockEntity.S_RELOADING || stf == ArtilleryGunBlockEntity.S_FIRING ? 1 : 0;
    }

    private record Later(long at, Runnable run) {}
    private static final java.util.List<Later> LATER = new java.util.ArrayList<>();

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (LATER.isEmpty()) return;
        long now = event.getServer().getTickCount();
        for (Later l : java.util.List.copyOf(LATER)) if (now >= l.at()) { LATER.remove(l); l.run().run(); }
    }

    @SubscribeEvent
    static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { LATER.clear(); }

    private static int enemy(CommandSourceStack src, int x, int z, int dist, int count) {
        ServerLevel level = src.getLevel();
        int ox = x - dist, oz = z;
        net.minecraft.core.BlockPos probeO = new net.minecraft.core.BlockPos(ox, level.getMinBuildHeight(), oz), probeT = new net.minecraft.core.BlockPos(x, level.getMinBuildHeight(), z);
        int oy = level.hasChunkAt(probeO) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz) : 80;
        int ty = level.hasChunkAt(probeT) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : oy;
        int ch = Artillery.autoCharge(dist, ty - (oy + 2.0));
        if (ch < 0) { src.sendFailure(Component.literal("No howitzer solution for that distance / height")); return 0; }
        double v = Artillery.velocity(Artillery.HOWITZER, ch), el = Artillery.elevation(v, dist, ty - (oy + 2.0), false);
        int ticks = Artillery.flightTicks(v, dist, el);
        net.minecraft.core.BlockPos gun = new net.minecraft.core.BlockPos(ox, oy, oz);
        for (int i = 0; i < count; i++) {
            final int k = i;
            LATER.add(new Later(level.getServer().getTickCount() + 20L * i, () ->
                    ArtilleryManager.get(level).fire(level, null, "Enemy (test)", Artillery.HOWITZER, Artillery.HE, new Vec3(ox + 0.5, oy + 2.0, oz + 0.5),
                            x + 0.5 + (k % 3 - 1) * 3.0, ty, z + 0.5 + (k / 3 % 3 - 1) * 3.0, ticks, gun, k + 1, k == count - 1)));
        }
        src.sendSuccess(() -> Component.literal(String.format("%d enemy test shell(s) from %d %d %d at %d %d: charge %d, %.1f deg, %.1f s flight", count, ox, oy, oz, x, z,
                ch, el, ticks / 20.0)), true);
        return count;
    }

    private static int spot(CommandSourceStack src, int x, int z) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        ServerLevel level = src.getLevel();
        ArtilleryManager.get(level).setSpot(p.getUUID(), x, z, level.getGameTime() + 6_000L);
        src.sendSuccess(() -> Component.literal("Spotting mark on " + x + " " + z + " for 5 min (radius " + Balance.ART_SPOT_RADIUS + ", spread x " + Balance.ART_SPOT_FACTOR + ")"), true);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        int n = 0;
        if (p != null) {
            for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(p.getUUID())) {
                if (g.gun() >= Artillery.GUNS.length) {
                    String sl = String.format("super_gun at %s | target %s | shells %d | charges %d | status %d", g.getBlockPos().toShortString(),
                            g.hasTarget() ? g.targetX() + " " + g.targetZ() : "none", g.ammo(Artillery.HE), g instanceof SuperGunBlockEntity sg ? sg.propellant() : 0, g.gunStatus());
                    src.sendSuccess(() -> Component.literal(sl), false);
                    n++;
                    continue;
                }
                StringBuilder ammo = new StringBuilder();
                for (int t = 0; t < Artillery.TYPES.length; t++) ammo.append(t == 0 ? "" : " ").append(Artillery.TYPES[t]).append(' ').append(g.ammo(t));
                String line = String.format("%s at %s | %s | target %s | %s | status %d%s", Artillery.GUNS[g.gun()], g.getBlockPos().toShortString(),
                        g.getLevel() != null ? g.getLevel().dimension().location().getPath() : "?", g.hasTarget() ? g.targetX() + " " + g.targetZ() : "none", ammo,
                        g.gunStatus(), g instanceof HowitzerBlockEntity h ? " | charges " + h.propellant() : "");
                src.sendSuccess(() -> Component.literal(line), false);
                n++;
            }
        }
        ServerLevel level = src.getLevel();
        long now = level.getGameTime();
        for (ArtShell s : ArtilleryManager.get(level).shells()) {
            Vec3 pos = s.position(now);
            if (s.gun() >= Artillery.GUNS.length) {
                String sl = String.format("shell #%d super_gun by %s | at %.0f %.0f %.0f | lands %.0f %.0f in %.1f s", s.id(), s.ownerName(), pos.x, pos.y, pos.z, s.tx(), s.tz(), (s.endTime() - now) / 20.0);
                src.sendSuccess(() -> Component.literal(sl), false);
                n++;
                continue;
            }
            String line = String.format("shell #%d %s %s by %s | at %.0f %.0f %.0f | lands %.0f %.0f in %.1f s", s.id(), Artillery.GUNS[s.gun()], Artillery.TYPES[s.type()],
                    s.ownerName(), pos.x, pos.y, pos.z, s.tx(), s.tz(), (s.endTime() - now) / 20.0);
            src.sendSuccess(() -> Component.literal(line), false);
            n++;
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("No guns of yours loaded, no shells in the air"), false);
        return n;
    }
}
