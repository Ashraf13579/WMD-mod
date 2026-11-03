package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;

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
public final class CcCommandsB {

    private CcCommandsB() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cc")
                        .then(Commands.literal("strike")
                                .then(Commands.literal("cancel").executes(c -> cancel(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> strike(c.getSource(), c.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("cam").executes(c -> cam(c.getSource(), c.getSource().getPlayerOrException())))
                        .then(Commands.literal("reach")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> reach(c.getSource(), c.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("campod")
                                .executes(c -> give(c.getSource().getPlayerOrException(), 1, "he"))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
                                        .executes(c -> give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "tier"), "he"))
                                        .then(Commands.argument("warhead", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(WARHEADS, b))
                                                .executes(c -> give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "tier"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "warhead"))))))));
    }

    private static int strike(CommandSourceStack src, ServerPlayer p, int x, int z) {
        ServerLevel level = p.serverLevel();
        CcStrikeData data = CcStrikeData.get(level.getServer());
        List<Long> pads = new ArrayList<>();
        int skipped = 0;
        for (LaunchTerminalBlockEntity t : CcLaunch.terminalsOf(p.getUUID())) {
            boolean ok = t.getLevel() == level && t.isFormed() && t.predictTicks(x, z) >= 0 && !data.busy(level, t.getBlockPos())
                    && CcReach.reach(level, p.getUUID(), t.getBlockPos());
            if (ok && pads.size() < CcStrikePlannerBlockEntity.MAX_PADS) pads.add(t.getBlockPos().asLong()); else skipped++;
        }
        if (pads.size() < CcStrikePlannerBlockEntity.MIN_PADS) {
            src.sendFailure(Component.literal("Need at least " + CcStrikePlannerBlockEntity.MIN_PADS + " loaded, formed terminals of yours in reach with a missile (found "
                    + pads.size() + ", " + skipped + " not usable). /wmd cc reach <x> <z> explains the reach."));
            return 0;
        }
        Component bad = CcStrikePlannerBlockEntity.plan(level, p, p.getUUID(), data, pads, x, z);
        if (bad != null) { src.sendFailure(bad); return 0; }
        final int left = skipped;
        if (left > 0) src.sendSuccess(() -> Component.literal(left + " other terminal(s) left out (not formed, no missile, already planned, out of reach or another dimension)"), false);
        return 1;
    }

    private static int cancel(ServerPlayer p) {
        int n = CcStrikeData.get(p.getServer()).cancel(p.getUUID());
        p.displayClientMessage(Component.translatable(n > 0 ? "message.wmd.cc.strike.cancelled" : "message.wmd.cc.strike.nothing", n), false);
        return n;
    }

    private static int cam(CommandSourceStack src, ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<Flight> l = CcMissileCamBlockEntity.camFlights(level, p.getUUID(), p.getGameProfile().getName());
        if (l.isEmpty()) { src.sendFailure(Component.literal("No cam-pod missile of yours is in flight here. A T4 rocket only appears after its countdown and lift-off, and the cam pod must be in the bay's camera slot when its UPPER STAGE is built (the stage's tooltip says Cam pod). /wmd cc campod gives a test missile")); return 0; }
        Flight f = l.get(l.size() - 1);
        Component answer = CcMissileCamBlockEntity.watch(p, level, p.getUUID(), p.getGameProfile().getName(), f.id());
        src.sendSuccess(() -> answer, false);
        return 1;
    }

    private static int reach(CommandSourceStack src, ServerPlayer p, int x, int z) {
        CcReach.Result r = CcReach.check(p.serverLevel(), p.getUUID(), x + 0.5, z + 0.5);
        Component head = Component.literal((r.ok() ? "IN REACH: " : "OUT OF REACH: ")).append(r.text())
                .append(Component.literal(" | active rooms " + CommandCenter.activeBuildings(p.getUUID()).size()
                        + ", relay satellite in control " + CcReach.relay(p.getServer(), p.getUUID())));
        src.sendSuccess(() -> head, false);
        return r.ok() ? 1 : 0;
    }

    private static final java.util.List<String> WARHEADS = java.util.List.of("he", "frag", "incendiary", "decoy", "thermobaric", "cluster", "breacher", "chaff",
            "bunker", "emp", "rod", "nuke", "thermo", "hemp", "seismic", "asat");

    private static int give(ServerPlayer p, int tier, String warhead) {
        if (!WARHEADS.contains(warhead)) { p.displayClientMessage(Component.literal("Unknown warhead " + warhead + " (try " + String.join(", ", WARHEADS) + ")"), false); return 0; }
        if (Asat.ID.equals(warhead) && tier < 4) { p.displayClientMessage(Component.literal("The ASAT warhead rides the T4 rocket: use tier 4"), false); return 0; }
        if (tier >= 4) {
            ItemStack core = new ItemStack(Wmd.STAGE_CORE.get());
            core.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(4, 4, 1, 1, "he", false, false, false));
            ItemStack upper = new ItemStack(Wmd.STAGE_UPPER.get());
            upper.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(4, 4, 4, 4, warhead, false, false, false).withCamPod(true));
            java.util.List<ItemStack> l = new java.util.ArrayList<>(java.util.List.of(core, upper));
            for (int i = 0; i < Balance.BOOSTER_STD; i++) {
                ItemStack b = new ItemStack(Wmd.STAGE_BOOSTER.get());
                b.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(3, 3, 1, 1, "he", false, false, false));
                l.add(b);
            }
            for (ItemStack st : l) if (!p.getInventory().add(st)) p.drop(st, false);
            p.displayClientMessage(Component.literal("T4 rocket (" + warhead + ", cam pod): core stage + upper stage + " + Balance.BOOSTER_STD + " boosters: stack them in a T4 terminal"), false);
            return 1;
        }
        ItemStack m = new ItemStack(Missiles.itemFor(tier));
        m.set(WmdComponents.MISSILE_BUILD.get(), new MissileBuild(tier, tier, tier, tier, warhead, false, false).withCamPod(true));
        if (!p.getInventory().add(m)) p.drop(m, false);
        ItemStack pod = new ItemStack(Wmd.CAM_POD.get());
        if (!p.getInventory().add(pod)) p.drop(pod, false);
        p.displayClientMessage(Component.literal("Tier " + tier + " missile (" + warhead + ") with a cam pod + one cam pod"), false);
        return 1;
    }
}
