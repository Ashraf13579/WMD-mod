package com.ashraf.wmd;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
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
public final class CcCommandsT {

    private CcCommandsT() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cc").requires(TestCmds::test)
                        .then(Commands.literal("defcon").then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(c -> defcon(c.getSource(), IntegerArgumentType.getInteger(c, "level")))))
                        .then(Commands.literal("log").executes(c -> log(c.getSource(), false))
                                .then(Commands.literal("clear").executes(c -> log(c.getSource(), true))))
                        .then(Commands.literal("logbook").executes(c -> logbook(c.getSource())))
                        .then(Commands.literal("bda")
                                .then(Commands.literal("print").executes(c -> bdaPrint(c.getSource())))
                                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> bda(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 1))
                                        .then(Commands.argument("lost", IntegerArgumentType.integer(0, 96))
                                                .executes(c -> bda(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                        IntegerArgumentType.getInteger(c, "lost")))))))
                        .then(Commands.literal("lockdown")
                                .then(Commands.literal("on").executes(c -> lockdown(c.getSource(), 1)))
                                .then(Commands.literal("off").executes(c -> lockdown(c.getSource(), 0)))
                                .then(Commands.literal("now").executes(c -> lockdown(c.getSource(), 2))))
                        .then(Commands.literal("counter").executes(c -> counter(c.getSource())))
                        .then(Commands.literal("livecam").executes(c -> livecam(c.getSource())))));
    }

    private static List<CcBuildingBlockEntity> mine(CommandSourceStack src, ServerPlayer p) {
        List<CcBuildingBlockEntity> bs = CommandCenter.activeBuildings(p.getUUID());
        if (bs.isEmpty()) src.sendFailure(Component.literal("You have no ACTIVE command center (loaded + powered: /wmd cc build or /wmd cc power)"));
        return bs;
    }

    private static int defcon(CommandSourceStack src, int d) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        List<CcBuildingBlockEntity> bs = mine(src, p);
        for (CcBuildingBlockEntity b : bs) {
            int old = b.setDefcon(d);
            if (old != b.defcon()) CcDefcon.changed(p, b, old, b.defcon());
        }
        if (!bs.isEmpty()) src.sendSuccess(() -> Component.literal("DEFCON " + d + " on " + bs.size() + " building(s). Effects: see CcDefcon (4 banner, 3 int-sats ANY + lockdown on alarms, 2 door seals on warnings + sirens, 1 red alert + auto counter-strike)."), false);
        return bs.size();
    }

    private static int log(CommandSourceStack src, boolean clear) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        if (clear) { CcBattleLog.clear(src.getServer(), p.getUUID()); src.sendSuccess(() -> Component.literal("Battle log cleared"), false); return 1; }
        List<CcBattleLog.Event> ev = CcBattleLog.events(src.getServer(), p.getUUID());
        if (ev.isEmpty()) { src.sendSuccess(() -> Component.literal("Your battle log is empty"), false); return 0; }
        for (int i = Math.max(0, ev.size() - 12); i < ev.size(); i++) {
            CcBattleLog.Event e = ev.get(i);
            p.sendSystemMessage(Component.literal("[" + e.kind() + "] ").withStyle(ChatFormatting.GRAY).append(e.text().copy().withStyle(CcMapPrint.logColour(e.kind()))));
        }
        return ev.size();
    }

    private static int logbook(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        ItemStack book = CcMapPrint.printLog(src.getServer(), p.getGameProfile().getName(), CcBattleLog.events(src.getServer(), p.getUUID()));
        if (book == null) { src.sendFailure(Component.literal("Your battle log is empty")); return 0; }
        if (!p.getInventory().add(book)) p.drop(book, false);
        src.sendSuccess(() -> Component.literal("Battle log book printed into your inventory"), false);
        return 1;
    }

    private static int bda(CommandSourceStack src, int x, int z, int lost) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        CcBda.Report r = CcBda.testReport(src.getServer(), p.getUUID(), x, z, lost);
        if (r == null) { src.sendFailure(Component.literal("(x, z) is not inside your recon snapshot (set a target + image it first: /wmd sat recon target <x> <z>, /wmd sat recon snap)")); return 0; }
        src.sendSuccess(() -> Component.literal("Fake damage report #" + r.id() + " at " + x + " " + z + ": " + r.destroyed() + " of " + r.beforeIcons().length / 3
                + " machines 'destroyed'. Look at the recon wall (cycles every 16 s) / the recon map's Damage button / print it (printer DAMAGE mode or /wmd cc bda print)."), false);
        return 1;
    }

    private static int bdaPrint(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        CcBda.Report r = CcBda.latest(src.getServer(), p.getUUID());
        ItemStack map = r != null && r.done() ? CcMapPrint.printBda(src.getServer(), r) : null;
        if (map == null) { src.sendFailure(Component.literal("No finished damage report of yours")); return 0; }
        if (!p.getInventory().add(map)) p.drop(map, false);
        src.sendSuccess(() -> Component.literal("Damage report map printed into your inventory"), false);
        return 1;
    }

    private static int lockdown(CommandSourceStack src, int mode) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        List<CcBuildingBlockEntity> bs = mine(src, p);
        long now = src.getServer().overworld().getGameTime();
        for (CcBuildingBlockEntity b : bs) {
            if (mode == 2) b.lockdown(now + CcDefcon.LOCKDOWN_TICKS);
            else { b.setLockdownToggle(mode == 1); if (mode == 0) b.endLockdown(); }
        }
        if (!bs.isEmpty()) src.sendSuccess(() -> Component.literal(mode == 2 ? "Intruder lockdown: the door is sealed for 30 s (powered buildings)" : "Lockdown toggle " + (mode == 1 ? "ON" : "OFF")), false);
        return bs.size();
    }

    private static int counter(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        CcTarget t = null;
        for (CcBuildingBlockEntity b : mine(src, p)) if (b.designated() != null && b.designated().source() == CcTarget.SRC_COUNTER) t = b.designated();
        if (t == null) { src.sendFailure(Component.literal("No designated LAUNCH SITE: use the warning board's Designate source (or DEFCON 1 + /wmd cc warn)")); return 0; }
        ServerLevel level = src.getLevel();
        Component why = CcStrikePlannerBlockEntity.counter(level, p, p.getUUID(), CcStrikeData.get(src.getServer()), t.x(), t.z());
        if (why != null) { src.sendFailure(why); return 0; }
        return 1;
    }

    private static int livecam(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("Players only")); return 0; }
        ServerLevel level = src.getLevel();
        Flight best = null;
        long now = level.getGameTime();
        for (Flight f : FlightManager.get(level).flights())
            if (f.isOwnedBy(p.getUUID(), p.getGameProfile().getName()) && now < f.endTime() && (best == null || f.start() > best.start())) best = f;
        if (best == null) { src.sendFailure(Component.literal("You have no missile in flight in this dimension")); return 0; }
        CcMissileCamBlockEntity.camStarted(level, p.getUUID(), best);
        long id = best.id();
        src.sendSuccess(() -> Component.literal("Recon walls of your active buildings show LIVE CAM of flight #" + id), false);
        return 1;
    }
}
