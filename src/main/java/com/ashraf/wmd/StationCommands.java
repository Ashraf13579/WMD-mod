package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StationCommands {

    private StationCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {

        event.getDispatcher().register(Commands.literal("wmdcam")
                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("r", IntegerArgumentType.integer(0, 200)).executes(c -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                            if (!p.hasPermissions(2) && !Orbit.isOrbit(p.level())) { p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.camera.none").withStyle(net.minecraft.ChatFormatting.RED), true); return 0; }
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new CutscenePayload(CutscenePayload.KIND_CAM, IntegerArgumentType.getInteger(c, "r"), 0.0F, 0.0F,
                                    IntegerArgumentType.getInteger(c, "x") + 0.5F, IntegerArgumentType.getInteger(c, "y") + 0.5F, IntegerArgumentType.getInteger(c, "z") + 0.5F,
                                    0, Balance.STATION_CAM_TICKS, ""));
                            return 1;
                        }))))));
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("station")
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("core").executes(c -> spawn(c.getSource(), Station.CORE, -1)))
                                .then(Commands.literal("habitat").executes(c -> spawn(c.getSource(), Station.HABITAT, -1)))
                                .then(Commands.literal("wing").executes(c -> spawn(c.getSource(), Station.WING, -1)))
                                .then(Commands.literal("sensor").executes(c -> spawn(c.getSource(), Station.SENSOR, -1)))
                                .then(Commands.literal("laser").executes(c -> spawn(c.getSource(), Station.LASER, -1)))
                                .then(Commands.literal("rod").executes(c -> spawn(c.getSource(), Station.ROD, -1)))
                                .then(Commands.literal("mirror").executes(c -> spawn(c.getSource(), Station.MIRROR, -1)))
                                .then(Commands.literal("bay").executes(c -> spawn(c.getSource(), Station.BAY, -1)))
                                .then(Commands.literal("turret").executes(c -> spawn(c.getSource(), Station.TURRET, -1)))
                                .then(Commands.literal("all").executes(c -> spawnAll(c.getSource()))))
                        .then(Commands.literal("threat").then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(c -> threat(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))
                        .then(Commands.literal("tug").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(c -> tug(c.getSource(), IntegerArgumentType.getInteger(c, "percent")))))
                        .then(Commands.literal("asteroid").executes(c -> asteroid(c.getSource())))
                        .then(Commands.literal("tugskip").executes(c -> tugSkip(c.getSource())))
                        .then(Commands.literal("ground").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> ground(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("sun")
                                .then(Commands.literal("day").executes(c -> sun(c.getSource(), OrbitClock.DAY)))
                                .then(Commands.literal("night").executes(c -> sun(c.getSource(), OrbitClock.NIGHT)))
                                .then(Commands.literal("auto").executes(c -> sun(c.getSource(), OrbitClock.AUTO))))
                        .then(Commands.literal("drop").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> drop(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 180))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(30, 600))
                                        .executes(c -> drop(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), IntegerArgumentType.getInteger(c, "seconds")))))))
                        .then(Commands.literal("split").executes(c -> split(c.getSource())))
                        .then(Commands.literal("enemy")
                                .then(Commands.literal("spawn").executes(c -> enemySpawn(c.getSource(), 600))
                                        .then(Commands.argument("away", IntegerArgumentType.integer(0, 20_000)).executes(c -> enemySpawn(c.getSource(), IntegerArgumentType.getInteger(c, "away")))))
                                .then(Commands.literal("ram").executes(c -> enemyRam(c.getSource())))
                                .then(Commands.literal("drop").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> enemyDrop(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), 180))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(30, 600))
                                                .executes(c -> enemyDrop(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), IntegerArgumentType.getInteger(c, "seconds")))))))
                                .then(Commands.literal("clear").executes(c -> enemyClear(c.getSource()))))
                        .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))));
    }

    private static ServerPlayer inOrbit(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return null; }
        if (!Orbit.isOrbit(p.level())) { src.sendFailure(Component.literal("Go up first: /wmd orbit go")); return null; }
        return p;
    }

    private static int threat(CommandSourceStack src, int seconds) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        if (!SensorArray.test(p, seconds)) { src.sendFailure(Component.literal("Your station's sensor array is not working (none, deploying, wrecked or no power): the station is blind")); return 0; }
        return 1;
    }

    private static int spawnAll(CommandSourceStack src) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        ServerLevel orbit = p.serverLevel();
        int slot = Orbit.slotOf(p.server, p.getUUID());
        if (Station.core(p.server, slot) == null) {
            String why = Station.spawnCore(orbit, slot, p.getUUID());
            if (why != null) { src.sendFailure(Component.literal(why)); return 0; }
        }
        int n = 0;
        StringBuilder skipped = new StringBuilder();
        for (String t : new String[] { Station.HABITAT, Station.WING, Station.WING, Station.BAY, Station.ROD, Station.LASER, Station.SENSOR, Station.MIRROR, Station.TURRET }) {
            String why = Station.spawnOnCore(orbit, slot, t, -1, p.getUUID());
            if (why == null) n++; else skipped.append(' ').append(why).append(';');
        }
        final int placed = n;
        src.sendSuccess(() -> Component.literal("Station: " + placed + " module(s) placed" + (skipped.length() > 0 ? ". Skipped:" + skipped : "")).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int spawn(CommandSourceStack src, String type, int port) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        ServerLevel orbit = p.serverLevel();
        int slot = Orbit.slotOf(p.server, p.getUUID());
        String why = Station.CORE.equals(type) ? Station.spawnCore(orbit, slot, p.getUUID()) : Station.spawnOnCore(orbit, slot, type, port, p.getUUID());
        if (why != null) { src.sendFailure(Component.literal(why)); return 0; }
        src.sendSuccess(() -> Component.literal("Station " + type + (port >= 0 ? " on port " + port : "") + " placed").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int tug(CommandSourceStack src, int percent) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        int slot = Orbit.slotOf(p.server, p.getUUID());
        Station.Module m = AsteroidBay.module(p.server, slot);
        if (m == null) { src.sendFailure(Component.literal("Your station has no asteroid bay")); return 0; }
        Station.Data d = Station.Data.get(p.server);
        int[] t = d.bay.computeIfAbsent(m.id(), k -> new int[AsteroidBay.P_N]);
        t[AsteroidBay.P_AM] = Balance.TUG_ANTIMATTER; t[AsteroidBay.P_DT] = Balance.TUG_DT; t[AsteroidBay.P_CORE] = Balance.TUG_CORES;
        t[AsteroidBay.P_PROG] = (int) ((long) Balance.TUG_BUILD_TICKS * percent / 100L);
        t[AsteroidBay.P_STATE] = percent >= 100 ? AsteroidBay.READY : AsteroidBay.BUILDING;
        d.setDirty();
        src.sendSuccess(() -> Component.literal("Tug build set to " + percent + " %").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int asteroid(CommandSourceStack src) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        Station.Data d = Station.Data.get(p.server);
        if (d.pass != null && p.serverLevel().getGameTime() <= d.pass[AsteroidRedirect.A_END]) { src.sendFailure(Component.literal("An asteroid is passing already")); return 0; }
        d.pass = null;
        AsteroidRedirect.startPass(p.serverLevel(), d);
        src.sendSuccess(() -> Component.literal("An asteroid passes now").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int ground(CommandSourceStack src, int x, int z) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int slot = Orbit.slotOf(p.server, p.getUUID());
        Orbit.Ground g = Orbit.ground(p.server, slot);
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = Orbit.isOrbit(p.level()) ? g.dim() : p.level().dimension();
        Orbit.setGround(p.server, slot, dim, x, z);
        src.sendSuccess(() -> Component.literal("Your station now hangs over " + x + " " + z + " (" + dim.location() + ")").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int sun(CommandSourceStack src, int mode) {
        OrbitClock.set(src.getServer(), mode);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.translatable(mode == OrbitClock.DAY ? "message.wmd.station.sun_day"
                : mode == OrbitClock.NIGHT ? "message.wmd.station.sun_night" : "message.wmd.station.sun_auto"), true);
        return 1;
    }

    private static int tugSkip(CommandSourceStack src) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        int slot = Orbit.slotOf(p.server, p.getUUID());
        Station.Module m = AsteroidBay.module(p.server, slot);
        Station.Data d = Station.Data.get(p.server);
        long[] f = m == null ? null : d.bayT.get(m.id());
        int[] t = m == null ? null : d.bay.get(m.id());
        if (f == null || t == null || t[AsteroidBay.P_STATE] < AsteroidBay.LAUNCH || t[AsteroidBay.P_STATE] == AsteroidBay.PARKED) { src.sendFailure(Component.literal("Your tug is not flying")); return 0; }
        if (t[AsteroidBay.P_STATE] == AsteroidBay.FALL) f[AsteroidRedirect.T_T0] = p.serverLevel().getGameTime() - (Balance.ASTEROID_FALL_TICKS - 1_600);
        else f[AsteroidRedirect.T_T0] = p.serverLevel().getGameTime() - Balance.TUG_PUSH_TICKS - 20;
        d.setDirty();
        src.sendSuccess(() -> Component.literal("Tug phase skipped").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int drop(CommandSourceStack src, int x, int z, int seconds) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int slot = Orbit.slotOf(p.server, p.getUUID());
        String why = AsteroidDrop.testDrop(p.server, slot, x, z, seconds);
        if (why != null) { src.sendFailure(Component.literal(why)); return 0; }
        ServerLevel gl = p.server.getLevel(Orbit.ground(p.server, slot).dim());
        AsyncChunks.forgetRegions();
        if (gl != null && !AsyncChunks.saved(gl, x >> 4, z >> 4))
            src.sendSuccess(() -> Component.literal("Warning: nobody has explored " + x + " " + z + " yet: the server generates that land during the countdown (lag, an empty cutscene). Test on explored land.").withStyle(ChatFormatting.YELLOW), false);
        src.sendSuccess(() -> Component.literal("Asteroid falling on " + x + " " + z + ": impact in " + seconds / 60 + ":" + String.format("%02d", seconds % 60)).withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int split(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int slot = Orbit.slotOf(p.server, p.getUUID());
        if (!AsteroidDrop.split(p.server, slot, p.getGameProfile().getName())) {
            src.sendFailure(Component.literal("No rock of yours is falling, or it is in 8 pieces already (/wmd station drop <x> <z> first)"));
            return 0;
        }
        return 1;
    }

    static final java.util.UUID ENEMY = java.util.UUID.nameUUIDFromBytes("wmd:test_enemy".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static int enemySlot(net.minecraft.server.MinecraftServer server) {
        if (server.getProfileCache() != null) server.getProfileCache().add(new com.mojang.authlib.GameProfile(ENEMY, "TestEnemy"));
        return Orbit.slotOf(server, ENEMY);
    }

    private static int enemySpawn(CommandSourceStack src, int away) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        ServerLevel orbit = p.server.getLevel(Orbit.ORBIT);
        if (orbit == null) { src.sendFailure(Component.literal("The orbit dimension is missing")); return 0; }
        int slot = enemySlot(p.server);
        Orbit.Ground g = Orbit.ground(p.server, Orbit.slotOf(p.server, p.getUUID()));
        Orbit.setGround(p.server, slot, g.dim(), g.x() + away, g.z());
        if (Station.core(p.server, slot) == null) {
            String why = Station.spawnCore(orbit, slot, ENEMY);
            if (why != null) { src.sendFailure(Component.literal(why)); return 0; }
        }
        int n = 0;
        for (String t : new String[] { Station.HABITAT, Station.WING, Station.WING, Station.BAY, Station.ROD, Station.LASER, Station.SENSOR, Station.MIRROR, Station.TURRET })
            if (Station.spawnOnCore(orbit, slot, t, -1, ENEMY) == null) n++;
        int built = n;
        src.sendSuccess(() -> Component.literal("Enemy station of TestEnemy built in orbit slot " + slot + " (x " + Orbit.slotCentre(slot).getX() + ", " + built
                + " modules), " + away + " blocks east of your ground point " + g.x() + " " + g.z() + " (in orbit: within " + Balance.RDV_SIGHT + " you see it, within "
                + Balance.RDV_CLOSE + " close range). Your laser's target list now shows its modules; "
                + "/wmd station enemy drop <x> <z> drops its asteroid on you.").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int enemyRam(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        ServerLevel orbit = p.server.getLevel(Orbit.ORBIT);
        int slot = enemySlot(p.server), mine = Orbit.slotOf(p.server, p.getUUID());
        if (orbit == null || Station.core(p.server, slot) == null) { src.sendFailure(Component.literal("No enemy station yet: /wmd station enemy spawn")); return 0; }
        Orbit.Ground e = Orbit.ground(p.server, slot), g = Orbit.ground(p.server, mine);
        double dist = Math.hypot(g.x() - e.x(), g.z() - e.z());
        if (!e.dim().equals(g.dim()) || dist < 16.0) { src.sendFailure(Component.literal("TestEnemy is not over your planet, or already on top of you (/wmd station enemy spawn 600)")); return 0; }
        long now = orbit.getGameTime();
        Station.Data d = Station.Data.get(p.server);
        d.reboost.put(slot, new long[] { e.x(), e.z(), g.x(), g.z(), now, now + Reboost.ticks(dist) });
        d.setDirty();
        src.sendSuccess(() -> Component.literal("TestEnemy RAMS your station: " + Math.round(dist) + " blocks, " + Reboost.ticks(dist) / 20 + " s").withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int enemyDrop(CommandSourceStack src, int x, int z, int seconds) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int slot = enemySlot(p.server);
        if (Station.core(p.server, slot) == null) { src.sendFailure(Component.literal("No enemy station yet: /wmd station enemy spawn")); return 0; }
        String why = AsteroidDrop.testDrop(p.server, slot, x, z, seconds);
        if (why != null) { src.sendFailure(Component.literal(why)); return 0; }
        Orbit.Ground g = Orbit.ground(p.server, Orbit.slotOf(p.server, p.getUUID()));
        if (Math.abs(x - g.x()) > Balance.SENSOR_GROUND_R || Math.abs(z - g.z()) > Balance.SENSOR_GROUND_R)
            src.sendSuccess(() -> Component.literal("Warning: " + x + " " + z + " is outside your sensor's ground area (" + Balance.SENSOR_GROUND_R + " round " + g.x() + " " + g.z()
                    + "): your laser will not see this rock.").withStyle(ChatFormatting.YELLOW), false);
        AsyncChunks.forgetRegions();
        ServerLevel gl = p.server.getLevel(Orbit.ground(p.server, slot).dim());
        if (gl != null && !AsyncChunks.saved(gl, x >> 4, z >> 4))
            src.sendSuccess(() -> Component.literal("Warning: nobody has explored " + x + " " + z + " yet (lag, an empty cutscene). Test on explored land.").withStyle(ChatFormatting.YELLOW), false);
        src.sendSuccess(() -> Component.literal("TestEnemy's asteroid falls on " + x + " " + z + ": impact in " + seconds / 60 + ":" + String.format("%02d", seconds % 60)
                + ". Set your laser to AUTO to fight it (the last " + Balance.ASTEROID_INT_WINDOW / 1200 + " min).").withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int enemyClear(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        ServerLevel orbit = p.server.getLevel(Orbit.ORBIT);
        if (orbit == null) { src.sendFailure(Component.literal("The orbit dimension is missing")); return 0; }
        int n = Station.clear(orbit, enemySlot(p.server));
        src.sendSuccess(() -> Component.literal("Enemy station removed (" + n + " module(s))"), false);
        return 1;
    }

    private static int clear(CommandSourceStack src) {
        ServerPlayer p = inOrbit(src);
        if (p == null) return 0;
        int n = Station.clear(p.serverLevel(), Orbit.slotOf(p.server, p.getUUID()));
        src.sendSuccess(() -> Component.literal(n + " module(s) removed"), false);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) { src.sendFailure(Component.literal("Players only")); return 0; }
        int slot = Orbit.slotOf(p.server, p.getUUID());
        var list = Station.modules(p.server, slot);
        src.sendSuccess(() -> Component.literal("Station of slot " + slot + ": " + list.size() + " module(s)").withStyle(ChatFormatting.AQUA), false);
        for (Station.Module m : list)
            src.sendSuccess(() -> Component.literal(" #" + m.id() + " " + m.type() + " at " + m.origin().toShortString() + " facing " + m.facing().getSerializedName()
                    + (m.host() >= 0 ? " on #" + m.host() + " port " + m.port() : "")), false);
        return 1;
    }
}
