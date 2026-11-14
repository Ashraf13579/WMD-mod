package com.ashraf.wmd;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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
public final class S53Commands {

    private S53Commands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("mlrs").requires(TestCmds::test)
                        .then(Commands.literal("give").executes(c -> giveMlrs(c.getSource())))
                        .then(Commands.literal("state").executes(c -> state(c.getSource())))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> fire(c, "he", 12))
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] { "he", "guided" }, b))
                                                        .executes(c -> fire(c, StringArgumentType.getString(c, "type"), 12))
                                                        .then(Commands.argument("rockets", IntegerArgumentType.integer(1, 64))
                                                                .executes(c -> fire(c, StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "rockets")))))))))
                .then(Commands.literal("camo").requires(TestCmds::test)
                        .then(Commands.literal("give").executes(c -> giveCamo(c.getSource())))
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("blast")
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(c -> blast(c, 8.0))
                                                        .then(Commands.argument("reach", DoubleArgumentType.doubleArg(0.5, 64.0))
                                                                .executes(c -> blast(c, DoubleArgumentType.getDouble(c, "reach")))))))))
        );
    }

    private static int giveMlrs(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        p.getInventory().add(new ItemStack(Wmd.MLRS_ITEM.get()));
        p.getInventory().add(new ItemStack(Wmd.MLRS_POD.get(), Balance.MLRS_MAGAZINE));
        p.getInventory().add(new ItemStack(Wmd.MLRS_POD_GUIDED.get(), Balance.MLRS_MAGAZINE));
        p.getInventory().add(new ItemStack(Wmd.SHELL_MORTAR_GUIDED.get(), 8));
        p.getInventory().add(new ItemStack(Wmd.SHELL_HOWITZER_GUIDED.get(), 8));
        src.sendSuccess(() -> Component.literal("Gave the rocket launcher, " + Balance.MLRS_MAGAZINE + " HE + " + Balance.MLRS_MAGAZINE + " guided pods and guided shells"), true);
        return 1;
    }

    private static int giveCamo(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        p.getInventory().add(new ItemStack(Wmd.CAMO_NET_ITEM.get(), 2));
        p.getInventory().add(new ItemStack(Wmd.DECOY_LAUNCHER_ITEM.get()));
        p.getInventory().add(new ItemStack(Wmd.DECOY_RADAR_ITEM.get()));
        src.sendSuccess(() -> Component.literal("Gave 2 camo nets + both decoys"), true);
        return 1;
    }

    private static MlrsBlockEntity nearest(ServerPlayer p) {
        MlrsBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(p.getUUID())) {
            if (!(g instanceof MlrsBlockEntity m) || g.getLevel() != p.level()) continue;
            double d = g.getBlockPos().distSqr(p.blockPosition());
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private static int fire(CommandContext<CommandSourceStack> c, String typeName, int rockets) {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        int t = "guided".equals(typeName) ? Artillery.GUIDED : "he".equals(typeName) ? Artillery.HE : -1;
        if (t < 0) { src.sendFailure(Component.literal("he or guided")); return 0; }
        MlrsBlockEntity g = nearest(p);
        if (g == null) { src.sendFailure(Component.literal("No loaded rocket launcher of yours in this dimension")); return 0; }
        int x = IntegerArgumentType.getInteger(c, "x"), z = IntegerArgumentType.getInteger(c, "z");
        if (g.ammo(t) < rockets) g.testLoad(t);
        int st = g.order(x, z, t, rockets, p.getGameProfile().getName());
        src.sendSuccess(() -> Component.literal(String.format("Rocket launcher at %s: %d %s rocket(s) at %d %d, %d m: ", g.getBlockPos().toShortString(), rockets,
                Artillery.TYPES[t], x, z, g.distance())).append(Component.translatable("gui.wmd.art.st." + st)), false);
        return st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_FIRING ? 1 : 0;
    }

    private static int state(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        int n = 0;
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(p.getUUID())) {
            if (!(g instanceof MlrsBlockEntity m)) continue;
            n++;
            String line = String.format("launcher at %s | pods W %s %d, E %s %d | magazine HE %d, guided %d rockets | %s | nav %s | status %d",
                    m.getBlockPos().toShortString(), name(m.podType(0)), m.podLeft(0), name(m.podType(1)), m.podLeft(1), m.ammo(Artillery.HE) - m.loaded(Artillery.HE),
                    m.ammo(Artillery.GUIDED) - m.loaded(Artillery.GUIDED), m.getLevel() != null && m.reloading(m.getLevel().getGameTime()) ? "reloading" : "ready",
                    m.navOk() ? "yes" : "no", m.gunStatus());
            src.sendSuccess(() -> Component.literal(line), false);
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("No loaded rocket launcher of yours"), false);
        return n;
    }

    private static String name(int t) { return t < 0 ? "-" : Artillery.TYPES[Artillery.clampType(t)]; }

    private static int list(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int n = 0;
        for (CamoNetBlockEntity net : Camo.nets(level)) {
            n++;
            String line = net.has() ? String.format("net at %s: covers %d..%d, %d..%d (y %d..%d), roof %.1f%s", net.getBlockPos().toShortString(), net.x0(), net.x1(),
                    net.z0(), net.z1(), net.baseY(), net.topY(), net.roofY(), net.torn() ? ", TORN" : "") : "net at " + net.getBlockPos().toShortString() + ": no machine next to it";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        for (DecoyBlockEntity d : DecoyBlockEntity.LOADED) {
            if (d.isRemoved() || d.getLevel() != level) continue;
            n++;
            String line = String.format("decoy %s at %s (owner %s): %s", d.id(), d.getBlockPos().toShortString(), d.getOwnerName(), d.popped() ? "popped" : "inflated");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("No loaded camo nets or decoys in this dimension"), false);
        return n;
    }

    private static int blast(CommandContext<CommandSourceStack> c, double reach) {
        CommandSourceStack src = c.getSource();
        Vec3 pos = new Vec3(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"));
        Camo.blast(src.getLevel(), pos, reach);
        src.sendSuccess(() -> Component.literal(String.format("Test burst at %.1f %.1f %.1f, reach %.1f (no damage)", pos.x, pos.y, pos.z, reach)), true);
        return 1;
    }
}
