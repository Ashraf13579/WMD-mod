package com.ashraf.wmd;

import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class NavalCommands {

    private NavalCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test)
                        .then(Commands.literal("subs").requires(TestCmds::test).executes(c -> subs(c.getSource())))
                        .then(Commands.literal("water").requires(TestCmds::test).executes(c -> water(c.getSource())))
                        .then(Commands.literal("ballast").requires(TestCmds::test).then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .executes(c -> ballast(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent")))))
                        .then(Commands.literal("testsub").requires(TestCmds::test).executes(c -> testSub(c.getSource(), 14))
                                .then(Commands.argument("length", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 40))
                                        .executes(c -> testSub(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "length")))))
                        .then(Commands.literal("noise").requires(TestCmds::test).then(Commands.argument("db", DoubleArgumentType.doubleArg(10.0, 250.0))
                                .executes(c -> noise(c.getSource(), DoubleArgumentType.getDouble(c, "db")))))
                        .then(Commands.literal("listen").requires(TestCmds::test).executes(c -> listen(c.getSource(), 512.0))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(8.0, 8192.0))
                                        .executes(c -> listen(c.getSource(), DoubleArgumentType.getDouble(c, "range")))))));
    }

    private static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    private static int subs(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        say(src, String.format("Sable %s, Deep Seas %s (bridge %s). Holes %s, implosion %s, radar sees submerged %s.",
                SableBridge.loaded() ? (SableBridge.active() ? "on" : "off") : "absent", DeepSeasBridge.loaded() ? "installed" : "absent",
                DeepSeasBridge.active() ? "on" : "off", Config.Server.navalSubHoles() ? "ON" : "off", Config.Server.navalSubImplosion() ? "ON" : "off",
                Config.Server.navalRadarSeesSubmerged() ? "yes" : "no"));
        if (!SableBridge.active()) return 0;
        List<SableBridge.ShipInfo> ships = new java.util.ArrayList<>(SableBridge.shipsIntersecting(level, new AABB(p.position(), p.position()).inflate(256.0)));

        java.util.UUID under = SableBridge.shipUnder(p);
        ships.sort(Comparator.comparingDouble(s -> s.id().equals(under) ? -1.0 : s.worldCenter().distanceTo(p.position())));
        if (ships.isEmpty()) { say(src, "No Sable ship within 256 blocks."); return 0; }
        int shown = 0;
        for (SableBridge.ShipInfo s : ships) {
            if (++shown > 4) { say(src, "... " + (ships.size() - 4) + " more further away"); break; }
            boolean sub = NavalLayer.submerged(level, s);
            String line = String.format("%s%s %d bl, mass %.0f, %.0f away, y %.0f..%.0f: %s %.1f, radar %s",
                    s.id().equals(under) ? "[YOURS] " : "", s.id().toString().substring(0, 8), s.blockCount(), s.mass(), s.worldCenter().distanceTo(p.position()),
                    s.worldBounds().minY, s.worldBounds().maxY, sub ? "SUBMERGED" : "SURFACED", NavalLayer.shipDepth(level, s),
                    !s.isLarge() ? "-(small)" : NavalLayer.visibleAboveWater(level, s) ? "SEES" : "blind");
            line += String.format(", speed %.2f (up %.2f) %s", Math.hypot(s.linearVelocity().x, s.linearVelocity().z), s.linearVelocity().y,
                    SonarNet.stopped(s) ? "SILENT" : s.speed() < Balance.SILENT_SPEED ? "quiet" : "LOUD");
            DeepSeasBridge.SubState st = DeepSeasBridge.state(s.id());
            if (st != null) line += String.format(" | SUB d%d sealed %s breach %s cracks %d%s, implode >= %.1f",
                    st.depth(), st.sealed() ? "Y" : "N", st.breached() ? "Y" : "N", st.cracks(), st.imploding() ? " IMPLODING" : "",
                    SubHits.implodePower(Math.max(st.depth(), NavalLayer.shipDepth(level, s))));
            say(src, line);
        }
        return ships.size();
    }

    private static int ballast(CommandSourceStack src, int pct) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        java.util.UUID id = SableBridge.shipUnder(p);
        if (id == null) {
            SableBridge.ShipInfo best = null;
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, new AABB(p.position(), p.position()).inflate(64.0)))
                if (DeepSeasBridge.isSub(s.id()) && (best == null || s.worldCenter().distanceTo(p.position()) < best.worldCenter().distanceTo(p.position()))) best = s;
            if (best != null) id = best.id();
        }
        if (id == null) { src.sendFailure(Component.literal("No Deep Seas submarine under you or within 64 blocks.")); return 0; }
        int n = DeepSeasBridge.setBallast(level, id, pct / 100.0);
        if (n < 0) { src.sendFailure(Component.literal("Deep Seas bridge not active (or its tank methods moved): see the log.")); return 0; }
        SableBridge.ShipInfo s = SableBridge.shipInfoFresh(level, id);
        final int fn = n;
        say(src, String.format("Ballast set to %d%% on %d tank(s)%s. Empty tanks lift, full ones sink.", pct, fn,
                s == null ? "" : String.format(" (ship mass %.0f vs %d tanks x ballastLiftPerTank)", s.mass(), fn)));
        return n;
    }

    private static int testSub(CommandSourceStack src, int len) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        if (!SableBridge.active()) { src.sendFailure(Component.literal("Sable is not active: a test sub needs Sable (and Deep Seas for the submarine part).")); return 0; }
        net.minecraft.core.Direction dir = p.getDirection();
        net.minecraft.core.Direction side = dir.getClockWise();
        net.minecraft.core.BlockPos start = p.blockPosition().relative(dir, 4).below(3);
        double R = 2.5;
        java.util.List<net.minecraft.core.BlockPos> hull = new java.util.ArrayList<>();
        java.util.List<net.minecraft.core.BlockPos> inside = new java.util.ArrayList<>();
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> set = new java.util.LinkedHashMap<>();

        net.minecraft.world.level.block.state.BlockState iron = net.minecraft.world.level.block.Blocks.COPPER_BLOCK.defaultBlockState();

        net.minecraft.world.level.block.Block pg = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_submarine", "iron_pressurizer"));
        net.minecraft.world.level.block.state.BlockState glass = (pg != null && pg != net.minecraft.world.level.block.Blocks.AIR ? pg : net.minecraft.world.level.block.Blocks.GLASS).defaultBlockState();
        for (int s = 0; s < len; s++) {
            double r = R * Math.min(1.0, Math.min((s + 1) / 3.0, (len - s) / 3.0)) + 0.35;
            for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) {
                double d = Math.sqrt(u * u + v * v);
                if (d > r) continue;
                net.minecraft.core.BlockPos b = start.relative(dir, s).relative(side, u).above(v);
                boolean shell = d > r - 1.05 || s == 0 || s == len - 1;
                if (shell) set.put(b, s == 0 && Math.abs(u) <= 1 && v == 0 ? glass : iron);
                else inside.add(b);
            }
        }
        for (int s = len / 3; s < len / 3 + 3; s++) for (int u = -1; u <= 0; u++) for (int v = 3; v <= 5; v++)
            set.put(start.relative(dir, s).relative(side, u).above(v), iron);
        net.minecraft.world.level.block.Block tank = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_submarine", "ballast_tank"));
        boolean ballast = tank != null && tank != net.minecraft.world.level.block.Blocks.AIR;

        int tanks = 0;

        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
        for (net.minecraft.core.BlockPos b : inside) before.put(b.immutable(), level.getBlockState(b));
        for (net.minecraft.core.BlockPos b : set.keySet()) before.put(b.immutable(), level.getBlockState(b));
        for (net.minecraft.core.BlockPos b : inside) {
            int v = b.getY() - start.getY();
            boolean t = ballast && v <= 0;
            level.setBlock(b, t ? tank.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            if (t) { hull.add(b); tanks++; }
        }
        for (var e : set.entrySet()) { level.setBlock(e.getKey(), e.getValue(), 3); hull.add(e.getKey()); }
        final int nt = tanks;
        java.util.UUID id = SableBridge.assembleShip(level, start.relative(dir, len / 2), hull, "wmd_test_sub");
        for (var e : before.entrySet())
            if (level.getBlockState(e.getKey()).isAir() && !e.getValue().isAir()) level.setBlock(e.getKey(), e.getValue(), 3);
        final int n = hull.size();
        if (id == null) { src.sendFailure(Component.literal("Built the hull (" + n + " blocks) but Sable did not assemble it: see the log.")); return 0; }
        say(src, String.format("Test sub assembled: %d blocks, ship %s%s. Give it a moment, then /wmd dev subs (shows its mass vs the tanks' lift).", n, id.toString().substring(0, 8),
                ballast ? ", with " + nt + " Deep Seas ballast tanks (registers as a submarine; empty tanks lift it)" : " (Deep Seas not installed: a plain Sable ship)"));
        return 1;
    }

    private static int water(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int top = NavalLayer.waterTop(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
        say(src, top == NavalLayer.NO_WATER ? "No water at the top of this column." : String.format("Water top y %d, you are %.1f below it, underwater: %s.",
                top, NavalLayer.depthBelowSurface(level, at), NavalLayer.underwater(level, at) ? "yes" : "no"));
        return 1;
    }

    private static int noise(CommandSourceStack src, double db) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        NoiseNet.pulse(p.serverLevel(), p.position(), db, NoiseNet.Kind.MACHINE, p.getUUID(), 200);
        say(src, String.format("Test noise %.0f dB at your feet for 10 s (%d sources in this dimension). Heard above %.0f dB out to %.0f blocks.",
                db, NoiseNet.count(p.serverLevel()), Balance.NOISE_FLOOR_DB, Math.pow(10.0, (db - Balance.NOISE_FLOOR_DB) / 20.0)));
        return 1;
    }

    private static int listen(CommandSourceStack src, double range) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<NoiseNet.Heard> h = NoiseNet.heard(p.serverLevel(), p.position(), range, Balance.NOISE_FLOOR_DB, null, null);
        if (h.isEmpty()) { say(src, "Silence (nothing above " + (int) Balance.NOISE_FLOOR_DB + " dB within " + (int) range + " blocks)."); return 0; }
        for (NoiseNet.Heard x : h.subList(0, Math.min(12, h.size())))
            say(src, String.format("%s %.0f dB at source -> %.1f dB here, %.0f blocks, bearing %03.0f", x.src().kind(), x.src().db(), x.level(), x.dist(), x.bearing()));
        return h.size();
    }
}
