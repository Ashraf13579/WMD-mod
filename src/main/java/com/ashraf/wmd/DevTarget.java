package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevTarget {

    private DevTarget() {}

    static final UUID ENEMY = UUID.nameUUIDFromBytes("wmd:dev-target-enemy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static final String ENEMY_NAME = "Dev enemy";

    static int enemy(net.minecraft.commands.CommandSourceStack src) {
        net.minecraft.server.level.ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        ServerLevel sl = src.getLevel();
        net.minecraft.world.phys.HitResult hr = p.pick(10.0, 0.0F, false);
        if (!(hr instanceof net.minecraft.world.phys.BlockHitResult bh) || hr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("Look at a WMD machine (radar, SAM site, ...) within 10 blocks.")); return 0;
        }
        BlockPos pos = bh.getBlockPos();
        net.minecraft.world.level.block.entity.BlockEntity be = sl.getBlockEntity(pos);
        if (be instanceof MachinePartBlockEntity part && part.getController() != null) { pos = part.getController(); be = sl.getBlockEntity(pos); }
        if (!(be instanceof Owned o)) { src.sendFailure(Component.literal("That block has no owner (not a WMD machine).")); return 0; }
        boolean toEnemy = !ENEMY.equals(o.getOwner());
        net.minecraft.nbt.CompoundTag t = be.saveWithoutMetadata(sl.registryAccess());
        t.putUUID("owner", toEnemy ? ENEMY : p.getUUID());
        t.putString("ownerName", toEnemy ? ENEMY_NAME : p.getGameProfile().getName());
        be.loadWithComponents(t, sl.registryAccess());
        be.setChanged();
        sl.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
        final String what = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
        final BlockPos fp = pos;
        src.sendSuccess(() -> Component.literal(what + " at " + fp.toShortString() + (toEnemy ? " now belongs to \"" + ENEMY_NAME + "\" (hostile to you: your ARMs home on it, it engages you)."
                : " is yours again.")), false);
        return 1;
    }

    static int makeEnemy(ServerLevel sl, List<BlockPos> blocks) {
        int n = 0;
        for (BlockPos q : blocks) {
            net.minecraft.world.level.block.entity.BlockEntity be = sl.getBlockEntity(q);
            if (!(be instanceof Owned)) continue;
            net.minecraft.nbt.CompoundTag t = be.saveWithoutMetadata(sl.registryAccess());
            t.putUUID("owner", ENEMY);
            t.putString("ownerName", ENEMY_NAME);
            be.loadWithComponents(t, sl.registryAccess());
            be.setChanged();
            if (ENEMY.equals(((Owned) be).getOwner())) n++;
        }
        return n;
    }

    static final int TARGET_PREP = 10, TARGET_CORE_TRIES = 10;

    private static final class Job {
        final ServerLevel sl; final UUID player; final UUID ship; long at; final double cx, cz; final float alt, r, spd;
        int tries;
        Job(ServerLevel sl, UUID player, UUID ship, long at, double cx, double cz, float alt, float r, float spd) {
            this.sl = sl; this.player = player; this.ship = ship; this.at = at; this.cx = cx; this.cz = cz; this.alt = alt; this.r = r; this.spd = spd;
        }
    }

    private static final List<Job> JOBS = Statics.server(new ArrayList<>());

    private static final List<Object[]> FLIGHTS = Statics.server(new ArrayList<>());
    static final int TARGET_LOG_TICKS = 2400;

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;

        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("enemy")
                .executes(c -> enemy(c.getSource())))));
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("target")
                .executes(c -> spawn(c.getSource(), 60, 120, 60))
                .then(Commands.argument("above", IntegerArgumentType.integer(20, 900)).executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "above"), 120, 60))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(40, 2000)).executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "above"), IntegerArgumentType.getInteger(c, "radius"), 60))
                                .then(Commands.argument("speed", IntegerArgumentType.integer(30, 90)).executes(c -> spawn(c.getSource(), IntegerArgumentType.getInteger(c, "above"),
                                        IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "speed")))))))));
    }

    private static int spawn(CommandSourceStack src, int above, int radius, int speed) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (!AirGate.on()) { src.sendFailure(Component.literal("1.2 air battles are off this session.")); return 0; }
        if (!SableBridge.active()) { src.sendFailure(Component.literal("Sable is not running: no craft can be assembled.")); return 0; }
        CompoundTag root = DevAirfield.savedJet();
        if (root == null) { src.sendFailure(Component.literal("No test jet saved yet (/wmd dev airfield savejet).")); return 0; }
        ServerLevel sl = p.serverLevel();
        int[] size = DevAirfield.savedSize(root);
        double cx = p.getX(), cz = p.getZ();
        float alt = (float) (p.getY() + above);

        double placeY = Math.min(alt, sl.getMaxBuildHeight() - size[1] - 4);
        BlockPos a = BlockPos.containing(cx + radius - size[0] / 2.0, placeY, cz - size[2] / 2.0);
        int placed = DevAirfield.placeSaved(sl, root, a, p);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(a.offset(-2, -2, -2), a.offset(size[0] + 2, size[1] + 2, size[2] + 2)))
            if (!sl.getBlockState(q).isAir()) blocks.add(q.immutable());
        if (blocks.isEmpty()) { src.sendFailure(Component.literal("Nothing was placed.")); return 0; }
        int enemy = makeEnemy(sl, blocks);
        UUID ship = SableBridge.assembleShip(sl, blocks.get(0), blocks, "WMD target jet");
        if (ship == null) { src.sendFailure(Component.literal("Sable did not assemble the jet (" + placed + " blocks placed at " + a.toShortString() + ").")); return 0; }
        JOBS.add(new Job(sl, p.getUUID(), ship, sl.getGameTime() + TARGET_PREP, cx, cz, alt, radius, speed));
        src.sendSuccess(() -> Component.literal("(Hostile: " + enemy + " machines aboard belong to \"" + ENEMY_NAME + "\", so radars and SAM sites engage it.)"), false);
        if (placeY < alt) src.sendSuccess(() -> Component.literal(String.format("(Placed at y %.0f, under the build limit; it climbs to y %.0f on its autopilot.)", placeY, alt)), false);
        src.sendSuccess(() -> Component.literal(String.format("Target jet assembled (%d blocks) at y %.0f, %d blocks east. It launches in a moment and orbits you (radius %d, %d b/s) on its autopilot.",
                blocks.size(), placeY, radius, radius, speed)), false);
        return 1;
    }

    @SubscribeEvent
    static void telemetry(ServerTickEvent.Post event) {
        if (FLIGHTS.isEmpty()) return;
        for (Iterator<Object[]> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Object[] f = it.next();
            ServerLevel sl = (ServerLevel) f[0];
            long now = sl.getGameTime();
            if (now > (long) f[3]) { it.remove(); continue; }
            if (now % 20 != 0) continue;
            BlockPos cp = (BlockPos) f[2];
            if (!sl.isLoaded(cp) || !(sl.getBlockEntity(cp) instanceof JetCockpitBlockEntity jc)) { Wmd.LOGGER.info("[dev target] the cockpit is gone (crashed / unloaded)"); it.remove(); continue; }
            String line = jc.devStatus(sl);
            if (line == null) { it.remove(); continue; }
            Wmd.LOGGER.info("[dev target] t {} {}", now, line);
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer((UUID) f[1]);
            if (p != null) p.displayClientMessage(Component.literal(line), true);
        }
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            long now = j.sl.getGameTime();
            if (now < j.at) continue;

            if (PowerCoreBlockEntity.chargeOf(j.sl, j.ship)[1] <= 0 && j.tries++ < TARGET_CORE_TRIES) { j.at = now + TARGET_PREP; continue; }
            it.remove();
            ServerPlayer p = j.sl.getServer().getPlayerList().getPlayer(j.player);
            JetCockpitBlockEntity jc = null;
            for (BlockPos q : SableBridge.blockEntitiesOn(j.sl, j.ship)) { BlockEntity be = j.sl.getBlockEntity(q); if (be instanceof JetCockpitBlockEntity c) { jc = c; break; } }
            if (jc == null) { if (p != null) p.sendSystemMessage(Component.literal("Target jet: no jet cockpit aboard (save a jet with a cockpit).")); continue; }
            PowerCoreBlockEntity.moveFuel(j.sl, j.ship, 10_000_000, false);
            for (int i = 0; i < 4_000; i++) if (PowerCoreBlockEntity.chargeFrom(j.sl, j.ship, Integer.MAX_VALUE / 2) <= 0) break;
            LandingGearBlockEntity.setAll(j.sl, j.ship, false);
            int[] chg = PowerCoreBlockEntity.chargeOf(j.sl, j.ship);
            if (chg[1] <= 0 && p != null) p.sendSystemMessage(Component.literal("Target jet: no power core registered on the craft (the autopilot will lose power)."));
            boolean ok = jc.devLaunch(j.sl, j.cx, j.cz, j.alt, j.r, j.spd);
            Wmd.LOGGER.info("[dev target] launch: core charge {} / {} FE, fuel {} mB, waited {} x {} ticks", chg[0], chg[1], PowerCoreBlockEntity.fuelOf(j.sl, j.ship)[0], j.tries, TARGET_PREP);
            if (ok) FLIGHTS.add(new Object[] { j.sl, j.player, jc.getBlockPos(), now + TARGET_LOG_TICKS });
            if (p != null) p.sendSystemMessage(Component.literal(ok ? "Target jet launched: autopilot ORBIT, crewless." : "Target jet: the launch push failed (the craft is not ready)."));
        }
    }
}
