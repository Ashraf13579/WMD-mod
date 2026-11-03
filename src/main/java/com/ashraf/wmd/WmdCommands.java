package com.ashraf.wmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

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
public final class WmdCommands {

    private WmdCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("missile")
                        .then(Commands.argument("airframe", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("motor", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("1", "2", "3", "divert", "hypersonic"), b))
                                        .then(Commands.argument("guidance", IntegerArgumentType.integer(1, 3))
                                                .then(Commands.argument("warhead", StringArgumentType.word())
                                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(WarheadType.values()).map(w -> w.id), b))
                                                        .executes(c -> giveMissile(c.getSource(), IntegerArgumentType.getInteger(c, "airframe"), StringArgumentType.getString(c, "motor"),
                                                                IntegerArgumentType.getInteger(c, "guidance"), StringArgumentType.getString(c, "warhead"), ""))
                                                        .then(Commands.argument("seeker", StringArgumentType.word())
                                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of(MissileBuild.SEEK_LASER, MissileBuild.SEEK_HEAT, MissileBuild.SEEK_ARM, MissileBuild.SEEK_TV), b))
                                                                .executes(c -> giveMissile(c.getSource(), IntegerArgumentType.getInteger(c, "airframe"), StringArgumentType.getString(c, "motor"),
                                                                        IntegerArgumentType.getInteger(c, "guidance"), StringArgumentType.getString(c, "warhead"), StringArgumentType.getString(c, "seeker")))))))))
                .then(Commands.literal("collapse").then(Commands.literal("why").executes(c -> collapseWhy(c.getSource()))))
                .then(Commands.literal("dev").then(Commands.literal("load").executes(c -> load(c.getSource()))))
                .then(Commands.literal("detonate")
                        .then(Commands.literal("gallery").executes(c -> gallery(c.getSource())))
                        .then(Commands.literal("here")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                        .executes(c -> detonateHere(c.getSource(), WarheadType.HE.id, IntegerArgumentType.getInteger(c, "seconds")))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(WarheadType.values()).map(w -> w.id), b))
                                                .executes(c -> detonateHere(c.getSource(), StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "seconds"))))))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(WarheadType.values()).map(w -> w.id), b))
                                .executes(c -> detonate(c.getSource(), StringArgumentType.getString(c, "type"), false))
                                .then(Commands.literal("refined").executes(c -> detonate(c.getSource(), StringArgumentType.getString(c, "type"), true)))))
                .then(Commands.literal("railgun")
                        .then(Commands.literal("skip")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
                                        .executes(c -> railgunSkip(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))
                        .then(Commands.literal("cutscene")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> railgunCutscene(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))))))
                .then(Commands.literal("pad")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
                                .executes(c -> pad(c.getSource(), IntegerArgumentType.getInteger(c, "tier"), true))
                                .then(Commands.literal("unformed").executes(c -> pad(c.getSource(), IntegerArgumentType.getInteger(c, "tier"), false)))))
                .then(Commands.literal("enemy")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(200, 10_000))
                                        .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "tier"),
                                                IntegerArgumentType.getInteger(c, "distance"), -1))
                                        .then(Commands.argument("bearing", IntegerArgumentType.integer(0, 359))
                                                .executes(c -> enemy(c.getSource(), IntegerArgumentType.getInteger(c, "tier"),
                                                        IntegerArgumentType.getInteger(c, "distance"), IntegerArgumentType.getInteger(c, "bearing"))))))));
    }

    private static int pad(CommandSourceStack source, int tier, boolean form) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        ServerLevel level = player.serverLevel();
        net.minecraft.core.Direction ahead = player.getDirection();
        net.minecraft.core.BlockPos terminal = player.blockPosition().relative(ahead, 3);
        net.minecraft.core.Direction facing = ahead.getOpposite();
        int n = PadStructure.size(tier);
        for (int d = 0; d < n; d++) {
            for (int l = 0; l < n; l++) {
                if (d == 0 && l == n / 2) continue;
                net.minecraft.core.BlockPos p = PadStructure.cell(terminal, facing, tier, l, d);
                level.setBlock(p, Wmd.PAD_PLATE.get().defaultBlockState(), 3);
                for (int y = 1; y <= 2; y++) if (!level.getBlockState(p.above(y)).isAir()) level.setBlock(p.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        net.minecraft.world.level.block.Block tb = switch (tier) { case 1 -> Wmd.LAUNCH_TERMINAL_1.get(); case 2 -> Wmd.LAUNCH_TERMINAL_2.get(); case 3 -> Wmd.LAUNCH_TERMINAL_3.get(); default -> Wmd.LAUNCH_TERMINAL_4.get(); };
        level.setBlock(terminal, tb.defaultBlockState().setValue(LaunchTerminalBlock.FACING, facing), 3);
        if (level.getBlockEntity(terminal) instanceof Owned o) o.claim(player);
        if (form) {
            PadStructure.form(level, terminal, facing, tier);
            level.setBlock(terminal, level.getBlockState(terminal).setValue(LaunchTerminalBlock.FORMED, true), 3);
        }
        source.sendSuccess(() -> Component.literal(String.format("Tier %d pad (%d x %d) built%s: terminal at %d %d %d, the pad lies behind it.", tier, n, n, form ? " and formed" : " (not formed: use the wrench on the terminal)", terminal.getX(), terminal.getY(), terminal.getZ())), false);
        return 1;
    }

    private static int giveMissile(CommandSourceStack source, int airframe, String motor, int guidance, String warhead, String seeker) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        boolean divert = motor.equals("divert"), hyper = motor.equals("hypersonic");
        int m;
        if (divert || hyper) m = 3;
        else try { m = Integer.parseInt(motor); } catch (NumberFormatException e) { m = 0; }
        if (m < 1 || m > 3) { source.sendFailure(Component.literal("Motor must be 1, 2, 3, divert or hypersonic.")); return 0; }
        WarheadType t = null;
        for (WarheadType w : WarheadType.values()) if (w.id.equals(warhead)) t = w;
        if (t == WarheadType.INTERCEPTOR) { source.sendFailure(Component.literal("Interceptors come from the assembly bay, not from this command.")); return 0; }
        if (t == null) { source.sendFailure(Component.literal("Unknown warhead: " + warhead)); return 0; }
        if (!seeker.isEmpty() && !seeker.equals(MissileBuild.SEEK_LASER) && !seeker.equals(MissileBuild.SEEK_HEAT) && !seeker.equals(MissileBuild.SEEK_ARM)
                && !seeker.equals(MissileBuild.SEEK_TV)) {
            source.sendFailure(Component.literal("Seeker must be laser, heat, arm or tv.")); return 0;
        }
        MissileBuild build = new MissileBuild(airframe, m, guidance, t.tier, t.id, false, divert).withSeeker(seeker).withHypersonic(hyper);
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(Missiles.itemFor(airframe));
        stack.set(WmdComponents.MISSILE_BUILD.get(), build);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        final boolean rule = m > airframe || guidance > airframe || t.tier > airframe;
        source.sendSuccess(() -> Component.literal(String.format("Missile: airframe %d, motor %s, guidance %d, %s warhead" + (seeker.isEmpty() ? "" : ", " + seeker + " seeker") + ". Weight %.1f, range %,d blocks, speed x%.1f, health %d, jet fuel %,d mB%s",
                airframe, hyper ? "hypersonic" : divert ? "divert" : Integer.toString(build.motor()), guidance, build.warheadType(), MissileStats.weight(build), MissileStats.range(build), MissileStats.speed(build),
                MissileStats.health(build), MissileStats.fuel(build), rule ? " (a part is above the airframe tier: the bay would not build this)" : "")), false);
        return 1;
    }

    private static int detonate(CommandSourceStack source, String id, boolean refined) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        WarheadType t = null;
        for (WarheadType w : WarheadType.values()) if (w.id.equals(id)) t = w;
        if (t == null) { source.sendFailure(Component.literal("Unknown warhead: " + id)); return 0; }
        if (t == WarheadType.INTERCEPTOR) { source.sendFailure(Component.literal("The interceptor warhead has no blast.")); return 0; }
        ServerLevel level = player.serverLevel();
        final int tier = t.tier;
        Vec3 look = player.getLookAngle();
        double hx = look.x, hz = look.z, len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0E-4) { hx = 0; hz = 1; len = 1; }
        hx /= len; hz /= len;
        double ahead = tier >= 4 ? 450.0 : 150.0;
        int tx = (int) Math.floor(player.getX() + hx * ahead), tz = (int) Math.floor(player.getZ() + hz * ahead);
        level.getChunk(tx >> 4, tz >> 4);
        int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
        fire(level, t, tx, gy, tz, hx, hz, refined);
        final WarheadType tt = t;
        source.sendSuccess(() -> Component.literal(String.format("%s (tier %d) detonated at %d %d %d, %d blocks ahead.", tt.id, tier, tx, gy, tz, (int) ahead)), true);
        return 1;
    }

    private static int collapseWhy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        net.minecraft.world.phys.HitResult hit = player.pick(64.0, 1.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bh) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block (within 64 blocks).")); return 0;
        }
        String r = Collapse.why(player.serverLevel(), bh.getBlockPos());
        source.sendSuccess(() -> Component.literal(r), true);
        return 1;
    }

    private static int load(CommandSourceStack source) {
        int l = LoadGuard.level();
        String what = l == LoadGuard.OVERLOADED ? "no new collapse pieces, falling blocks / crumble / smoke zones / wreckage a quarter, no thrown debris, no airship fractures, shockwave players only"
                : l == LoadGuard.BUSY ? "collapse pieces, falling blocks, crumble, thrown debris, smoke zones and wreckage halved" : "full effects";
        source.sendSuccess(() -> Component.literal(String.format("Load guard: %s (avg tick %.1f ms; busy from %d, overloaded from %d%s): %s", LoadGuard.name(l), LoadGuard.mspt(),
                Config.Server.loadGuardBusyMs(), Config.Server.loadGuardOverloadMs(), Config.Server.loadGuard() ? "" : ", OFF in the config", what)), false);
        return 1;
    }

    private static void fire(ServerLevel level, WarheadType t, int tx, int gy, int tz, double hx, double hz, boolean refined) {
        FlightManager mgr = FlightManager.get(level);

        mgr.holdForTest(level, tx, tz, WarheadFx.holdRadius(t.tier, t));
        WarheadFx.detonate(level, mgr, tx, gy, tz, t.tier, t, hx, hz, refined);
        BaySabotage.check(level, mgr, tx, gy, tz, t);
        CookOff.check(level, mgr, tx, gy, tz, t);
    }

    private record Timer(ServerLevel level, WarheadType t, int x, int y, int z, double hx, double hz, long at, java.util.UUID owner, String note) {}

    private static final java.util.List<Timer> TIMERS = Statics.server(new java.util.ArrayList<>());

    private static int gallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        ServerLevel level = player.serverLevel();
        java.util.List<WarheadType> list = new java.util.ArrayList<>();
        for (WarheadType w : WarheadType.values())
            if (w != WarheadType.INTERCEPTOR && w != WarheadType.ASAT && w != WarheadType.CUBESAT) list.add(w);
        list.sort(java.util.Comparator.comparingDouble(WmdCommands::galleryRadius).thenComparingInt(w -> w.tier));
        Vec3 look = player.getLookAngle();
        double hx = look.x, hz = look.z, len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0E-4) { hx = 0; hz = 1; len = 1; }
        hx /= len; hz /= len;
        double d = 90.0, prev = 0.0;
        long at = level.getGameTime() + 100;
        for (int i = 0; i < list.size(); i++) {
            WarheadType w = list.get(i);
            double r = galleryRadius(w);
            d += prev + r + 30.0;
            prev = r;
            int x = (int) Math.floor(player.getX() + hx * d), z = (int) Math.floor(player.getZ() + hz * d);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            String note = String.format("[gallery %d/%d] %s (tier %d), %d blocks out", i + 1, list.size(), w.id, w.tier, (int) d);
            TIMERS.add(new Timer(level, w, x, y, z, hx, hz, at, player.getUUID(), note));
            at += (r >= 30.0 ? GALLERY_GAP_BIG : GALLERY_GAP) * 20L;
        }
        final int n = list.size(), secs = (int) ((at - level.getGameTime()) / 20);
        source.sendSuccess(() -> Component.literal(String.format("Gallery: %d warheads in a row in front of you, small to big, the first in 5 s, the last in about %d s. Stand still.", n, secs)), true);
        return 1;
    }

    private static final int GALLERY_GAP = 10, GALLERY_GAP_BIG = 20;

    private static double galleryRadius(WarheadType w) {
        return Math.max(4.0, Missiles.craterRadius(w.tier) * w.crater);
    }

    private static int detonateHere(CommandSourceStack source, String id, int seconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        WarheadType t = null;
        for (WarheadType w : WarheadType.values()) if (w.id.equals(id)) t = w;
        if (t == null) { source.sendFailure(Component.literal("Unknown warhead: " + id)); return 0; }
        if (t == WarheadType.INTERCEPTOR) { source.sendFailure(Component.literal("The interceptor warhead has no blast.")); return 0; }
        ServerLevel level = player.serverLevel();
        net.minecraft.core.BlockPos.MutableBlockPos p = player.blockPosition().mutable();
        while (p.getY() > level.getMinBuildHeight() && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) p.move(net.minecraft.core.Direction.DOWN);
        Vec3 look = player.getLookAngle();
        double hx = look.x, hz = look.z, len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0E-4) { hx = 0; hz = 1; len = 1; }
        TIMERS.add(new Timer(level, t, p.getX(), p.getY(), p.getZ(), hx / len, hz / len, level.getGameTime() + seconds * 20L, player.getUUID(), null));
        final WarheadType tt = t;
        final int x = p.getX(), y = p.getY(), z = p.getZ();
        source.sendSuccess(() -> Component.literal(String.format("%s (tier %d) set at %d %d %d: it goes off in %d s.", tt.id, tt.tier, x, y, z, seconds)), true);
        return 1;
    }

    @SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (TIMERS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        for (java.util.Iterator<Timer> it = TIMERS.iterator(); it.hasNext(); ) {
            Timer k = it.next();
            if (k.level != sl) continue;
            long left = k.at - now;
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(k.owner);
            if (left <= 0) {
                it.remove();
                fire(sl, k.t, k.x, k.y, k.z, k.hx, k.hz, false);
                if (owner != null) owner.displayClientMessage(Component.literal(k.t.id + " went off at " + k.x + " " + k.y + " " + k.z), true);
                if (owner != null && k.note != null) owner.sendSystemMessage(Component.literal(k.note));
                continue;
            }
            if (left % 20 == 0) {
                if (owner != null) owner.displayClientMessage(Component.literal(k.t.id + " in " + left / 20 + " s"), true);
                sl.playSound(null, k.x + 0.5, k.y + 0.5, k.z + 0.5, WmdSounds.C4_BEEP.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, left <= 60 ? 1.5F : 1.0F);
            }
            if (left % 4 == 0) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, k.x + 0.5, k.y + 1.0, k.z + 0.5, 2, 0.05, 0.6, 0.05, 0.0);
        }
    }

    private static int railgunSkip(CommandSourceStack source, int seconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        RailgunBlockEntity best = RailgunBlockEntity.nearest(player.serverLevel(), player.position(), 96.0);
        if (best == null) { source.sendFailure(Component.literal("No railgun within 96 blocks.")); return 0; }
        if (!best.testSkip(seconds)) { source.sendFailure(Component.literal("That railgun is not charging: set a target and FIRE first (it must be in its charge).")); return 0; }
        source.sendSuccess(() -> Component.literal("Railgun: " + seconds + " s to the shot."), true);
        return 1;
    }

    private static int railgunCutscene(CommandSourceStack source, int x, int z) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Run this as a player.")); return 0; }
        RailgunStrikes.replay(player, x, z);
        source.sendSuccess(() -> Component.literal("Railgun cutscene at " + x + " " + z + ": loading the target area first, it starts in a few seconds."), false);
        return 1;
    }

    private static int enemy(CommandSourceStack source, int tier, int distance, int bearing) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        double b = Math.toRadians(bearing < 0 ? level.random.nextInt(360) : bearing);
        double dx = Math.sin(b), dz = -Math.cos(b);
        Vec3 pos = player.position();
        Vec3 from = new Vec3(pos.x + dx * distance, pos.y + Flight.LAUNCH_HEIGHT, pos.z + dz * distance);
        int tx = (int) Math.floor(pos.x + dx * 250.0), tz = (int) Math.floor(pos.z + dz * 250.0);
        FlightManager.get(level).launch(level, tier, from, tx, (int) Math.floor(pos.y), tz, "Enemy", java.util.UUID.nameUUIDFromBytes("wmd-test-enemy".getBytes()));
        source.sendSuccess(() -> Component.literal(String.format("Enemy tier %d missile launched %d blocks away; it lands at %d, %d (250 blocks short of you).", tier, distance, tx, tz)), true);
        return 1;
    }
}
