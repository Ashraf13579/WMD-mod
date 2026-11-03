package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class BigDrones {

    private BigDrones() {}

    static final int THROW = 11, CRUISE = 12, DROP = 13, LOITER = 14, RETURN = 15, CHUTE = 16, APPROACH = 17, LANDING = 18, TAXI_IN = 19, TAXI_OUT = 20, ROLL = 21;
    static final int THROW_TICKS = 12, DROP_GAP = 8, LOITER_GAP = 100;
    static final double WP_REACH = 20.0, TAXI = 0.15, ROLL_ACCEL = 0.03, CLIMB = 0.3, APPROACH_D = 80.0, GLIDE_H = 20.0, HOLD_AHEAD = 300.0, HOLD_R = 48.0;

    static final double CIRCUIT_OUT = 120.0, CIRCUIT_SIDE = 90.0, CIRCUIT_TURN = 3.0;

    static final double CHUTE_H = 8.0;

    static Vec3 local(BlockPos o, Direction f, double lx, double ly, double lz) {
        double dx, dz;
        switch (f) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return new Vec3(o.getX() + 0.5 + dx, o.getY() + ly, o.getZ() + 0.5 + dz);
    }

    static Vec3 hangarBay(BlockPos h, Direction f) { return local(h, f, 0.0, 0.125, 4.0); }
    static Vec3 runway(BlockPos h, Direction f, double along) { return local(h, f, 0.0, 0.125, -0.6 - along); }

    static Vec3 shuttle(BlockPos c, Direction f) { return local(c, f, 0.0, 1.17, 9.0); }
    static Vec3 railEnd(BlockPos c, Direction f) { return local(c, f, 0.0, 2.78, -0.1); }
    static Vec3 beside(BlockPos c, Direction f) { return local(c, f, 4.0, 0.0, 4.0); }

    static boolean shipKnown(ServerLevel level, BlockPos site) { return level.isLoaded(site) && SableBridge.isOnShip(level, site); }

    static Vec3 deckToWorld(ServerLevel level, BlockPos site, Vec3 deck) {
        Direction m = level.isLoaded(site) ? Machines.mount(level.getBlockState(site)) : Direction.UP;
        Vec3 plot = m == Direction.UP ? deck : Machines.mountPoint(site, m, deck.x - site.getX() - 0.5, deck.y - site.getY(), deck.z - site.getZ() - 0.5);
        return SableBridge.toWorld(level, plot);
    }

    static Vec3 worldToDeck(ServerLevel level, BlockPos site, Vec3 world) {
        java.util.UUID id = SableBridge.shipIdAt(level, site);
        Vec3 plot = id == null ? null : SableBridge.toShip(level, id, world);
        if (plot == null) return null;
        Direction m = Machines.mount(level.getBlockState(site));
        if (m == Direction.UP) return plot;
        double[] u = Machines.mountUnturn(m, plot.x - site.getX() - 0.5, plot.y - site.getY() - 0.5, plot.z - site.getZ() - 0.5);
        return new Vec3(site.getX() + 0.5 + u[0], site.getY() + 0.5 + u[1], site.getZ() + 0.5 + u[2]);
    }

    private static float worldYaw(ServerLevel level, BlockPos site, Vec3 deck, float yaw) {
        double r = Math.toRadians(yaw);
        Vec3 a = deckToWorld(level, site, deck), b = deckToWorld(level, site, deck.add(-Math.sin(r), 0.0, Math.cos(r)));
        double hx = b.x - a.x, hz = b.z - a.z;
        return hx * hx + hz * hz < 1.0E-6 ? yaw : (float) Math.toDegrees(Math.atan2(-hx, hz));
    }

    private static boolean toDeck(ServerLevel level, Drones.Drone d, BlockPos site) {
        Vec3 p = worldToDeck(level, site, d.pos());
        if (p == null) return false;
        d.deck = true;
        d.px = p.x; d.py = p.y; d.pz = p.z;
        Vec3 a = deckToWorld(level, site, p);
        double r = Math.toRadians(d.yaw), hx = -Math.sin(r), hz = Math.cos(r);
        Vec3 b = worldToDeck(level, site, a.add(hx, 0.0, hz));
        d.pyaw = b == null ? d.yaw : (float) Math.toDegrees(Math.atan2(-(b.x - p.x), b.z - p.z));
        return true;
    }

    static final java.util.Set<BigDroneSite> SITES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static java.util.List<BigDroneSite> sitesOf(java.util.UUID owner, net.minecraft.world.level.Level level) {
        java.util.List<BigDroneSite> l = new java.util.ArrayList<>();
        for (BigDroneSite s : SITES)
            if (owner.equals(s.getOwner()) && s instanceof net.minecraft.world.level.block.entity.BlockEntity be && !be.isRemoved() && be.getLevel() == level) l.add(s);
        l.sort(java.util.Comparator.comparingLong(s -> s.sitePos().asLong()));
        return l;
    }

    private static int flying(Drones ds, java.util.UUID owner, int kind) {
        int n = 0;
        for (Drones.Drone d : ds.drones()) if (owner.equals(d.owner) && d.kind == kind && d.airborne()) n++;
        return n;
    }

    private static Drones.Drone start(ServerLevel level, Owned site, ServerPlayer by, DroneType t, DroneMission m, ItemStack[] bombs, Component[] why) {
        MinecraftServer server = level.getServer();
        if (level != server.overworld()) { why[0] = Component.translatable("message.wmd.hive.overworld"); return null; }
        java.util.UUID owner = site.getOwner() != null ? site.getOwner() : by.getUUID();
        int cc = CommandCenter.activeBuildings(owner).size();
        if (cc == 0) { why[0] = Component.translatable("message.wmd.big_drone.no_cc"); return null; }
        Drones ds = Drones.get(server);
        int kind = t.tier;
        if (flying(ds, owner, kind) >= Balance.DRONE_PER_CC * cc) { why[0] = Component.translatable("message.wmd.big_drone.limit", Balance.DRONE_PER_CC * cc, t.id().toUpperCase()); return null; }
        if (m == null || !m.hasTarget()) { why[0] = Component.translatable("message.wmd.big_drone.no_target"); return null; }
        Drones.Drone d = new Drones.Drone();
        d.id = ds.newId();
        d.owner = owner;
        d.ownerName = site.getOwnerName() != null && !site.getOwnerName().isEmpty() ? site.getOwnerName() : by.getGameProfile().getName();
        d.kind = kind;
        d.wx = m.xs(); d.wz = m.zs(); d.tx = m.tx(); d.tz = m.tz(); d.wpI = 0;
        d.bombs = new int[bombs.length];
        for (int i = 0; i < bombs.length; i++) { BombType b = BombType.of(bombs[i]); d.bombs[i] = b == null ? -1 : b.ordinal(); }
        d.battery = t == DroneType.T3 ? Balance.DRONE_T3_BATTERY : Balance.DRONE_T4_BATTERY;
        d.health = t == DroneType.T3 ? Balance.DRONE_T3_HEALTH : Balance.DRONE_T4_HEALTH;
        return d;
    }

    static Component launchCatapult(ServerLevel level, DroneCatapultBlockEntity c, ServerPlayer by, int[] out) {
        DroneType t = c.drone();
        if (t == null || !t.big) return Component.translatable("message.wmd.catapult.empty");
        Component[] why = new Component[1];
        Drones.Drone d = start(level, c, by, t, c.mission(), c.bombStacks(), why);
        if (d == null) return why[0];
        int range = t == DroneType.T3 ? Balance.DRONE_T3_RANGE : Balance.DRONE_T4_RANGE;
        Direction f = c.getBlockState().getValue(MachineBlock.FACING);
        Vec3 sd = shuttle(c.getBlockPos(), f), s = deckToWorld(level, c.getBlockPos(), sd);
        if (Math.hypot(d.tx + 0.5 - s.x, d.tz + 0.5 - s.z) > range) return Component.translatable("message.wmd.big_drone.too_far", range);
        d.hiveX = c.getBlockPos().getX(); d.hiveY = c.getBlockPos().getY(); d.hiveZ = c.getBlockPos().getZ(); d.bay = 1;
        d.fx = f.getStepX(); d.fz = f.getStepZ();
        d.hx = d.x = s.x; d.hy = d.y = s.y; d.hz = d.z = s.z;
        d.yaw = f.toYRot();
        if (SableBridge.isOnShip(level, c.getBlockPos())) { d.ship = d.deck = true; d.px = sd.x; d.py = sd.y; d.pz = sd.z; d.pyaw = d.yaw; d.yaw = worldYaw(level, c.getBlockPos(), sd, d.pyaw); }
        d.phase = THROW;
        d.timer = 0;
        Drones.get(level.getServer()).add(d);
        out[0] = d.id;
        Vec3 cw = SableBridge.worldPos(level, c.getBlockPos());
        level.playSound(null, cw.x, cw.y, cw.z, WmdSounds.CATAPULT_THROW.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        Wmd.LOGGER.info("Big drone #{} ({}) of {} thrown from the catapult at {}", d.id, t.id(), d.ownerName, c.getBlockPos().toShortString());
        logOut(level, d);
        return null;
    }

    static Component launchRunway(ServerLevel level, DroneModuleBlockEntity h, ServerPlayer by, int[] out) {
        DroneType t = DroneType.of(h.bay(0));
        if (t != DroneType.T4) return Component.translatable("gui.wmd.big_drone.st." + BigDroneSite.S_EMPTY);
        Direction f = h.getBlockState().getValue(MachineBlock.FACING);

        if (Machines.mount(h.getBlockState()) != Direction.UP && !SableBridge.isOnShip(level, h.getBlockPos())) return Component.translatable("message.wmd.big_drone.runway_flat");
        if (!runwayOk(level, h.getBlockPos(), f)) return Component.translatable("message.wmd.big_drone.no_runway", Balance.RUNWAY_W, Balance.RUNWAY_L);
        Component[] why = new Component[1];
        Drones.Drone d = start(level, h, by, t, h.mission(), h.bombStacks(), why);
        if (d == null) return why[0];
        Vec3 bd = hangarBay(h.getBlockPos(), f), bay = deckToWorld(level, h.getBlockPos(), bd);
        if (Math.hypot(d.tx + 0.5 - bay.x, d.tz + 0.5 - bay.z) > Balance.DRONE_T4_RANGE) return Component.translatable("message.wmd.big_drone.too_far", Balance.DRONE_T4_RANGE);
        d.hiveX = h.getBlockPos().getX(); d.hiveY = h.getBlockPos().getY(); d.hiveZ = h.getBlockPos().getZ(); d.bay = 0;
        d.fx = f.getStepX(); d.fz = f.getStepZ();
        d.hx = d.x = bay.x; d.hy = d.y = bay.y; d.hz = d.z = bay.z;
        d.yaw = f.toYRot();
        if (SableBridge.isOnShip(level, h.getBlockPos())) { d.ship = d.deck = true; d.px = bd.x; d.py = bd.y; d.pz = bd.z; d.pyaw = d.yaw; d.yaw = worldYaw(level, h.getBlockPos(), bd, d.pyaw); }
        d.phase = TAXI_OUT;
        d.roll = 0.0;
        Drones.get(level.getServer()).add(d);
        out[0] = d.id;
        h.openDoor(400);
        Wmd.LOGGER.info("Big drone #{} (t4) of {} taxis out of the hangar at {}", d.id, d.ownerName, h.getBlockPos().toShortString());
        logOut(level, d);
        return null;
    }

    private static void logOut(ServerLevel level, Drones.Drone d) {
        CcBattleLog.add(level.getServer(), d.owner, CcBattleLog.DRONE, "log.wmd.drone_out", Integer.toString(d.id), "T" + d.kind, Integer.toString(d.tx), Integer.toString(d.tz));
    }

    static boolean runwayOk(ServerLevel level, BlockPos h, Direction f) {
        Direction m = Machines.mount(level.getBlockState(h));
        int half = Balance.RUNWAY_W / 2;
        for (int lz = -1; lz >= -Balance.RUNWAY_L; lz--)
            for (int lx = -half; lx < Balance.RUNWAY_W - half; lx++) {
                BlockPos p = Machines.toWorld(h, f, m, lx, -1, lz);
                if (!level.isLoaded(p) || !RunwayBlocks.isRunway(level.getBlockState(p))) return false;
            }
        return true;
    }

    static void recall(MinecraftServer server, int id) {
        Drones.Drone d = Drones.get(server).byId(id);
        if (d == null || d.kind < 3 || !d.airborne()) return;
        if (d.phase == CRUISE || d.phase == DROP || d.phase == LOITER) goHome(server, d);
    }

    static Component watch(ServerPlayer sp, int id) {
        Drones.Drone d = Drones.get(sp.server).byId(id);
        if (d == null || d.kind < 3 || !d.airborne()) return Component.translatable("message.wmd.big_drone.not_flying");
        if (d.pilot != null) return Component.translatable("message.wmd.big_drone.watched");
        if (sp.level() != sp.server.overworld()) return Component.translatable("message.wmd.hive.overworld");
        d.pilot = sp.getUUID();
        DronePilots.begin(sp);
        sendWatch(sp, d, sp.serverLevel());
        return null;
    }

    private static void sendWatch(ServerPlayer p, Drones.Drone d, ServerLevel level) {
        int g = Drones.groundAt(level, d.x, d.z);
        int full = d.kind == 3 ? Balance.DRONE_T3_BATTERY : Balance.DRONE_T4_BATTERY;
        PacketDistributor.sendToPlayer(p, new DroneFlyPayload(d.id, true, d.x, d.y, d.z, d.yaw, Mth.clamp(d.battery * 100 / full, 0, 100),
                g == Integer.MIN_VALUE ? -1 : (int) Math.round(d.y - g), (int) Math.round(Math.hypot(d.x - d.hx, d.z - d.hz)),
                -(d.kind == 3 ? Balance.DRONE_T3_RANGE : Balance.DRONE_T4_RANGE)));
    }

    static boolean step(Drones ds, ServerLevel level, Drones.Drone d, long now) {
        MinecraftServer server = level.getServer();
        double speed = d.kind == 3 ? Balance.DRONE_T3_SPEED : Balance.DRONE_T4_SPEED;
        int cruise = d.kind == 3 ? Balance.DRONE_T3_CRUISE : Balance.DRONE_T4_CRUISE;
        if (--d.battery <= 0) {
            Drones.tell(server, d.owner, Component.translatable("message.wmd.big_drone.battery", d.id).withStyle(ChatFormatting.RED));
            ds.shootDown(level, d, null, "battery");
            return false;
        }
        BlockPos site = new BlockPos(d.hiveX, d.hiveY, d.hiveZ);
        Direction f = Direction.fromYRot(Math.toDegrees(Math.atan2(-d.fx, d.fz)));

        boolean known = d.ship && shipKnown(level, site);
        if (d.ship && !known && level.isLoaded(site) && !(level.getBlockEntity(site) instanceof BigDroneSite)
                && (d.deck || d.phase == RETURN || d.phase == CHUTE)) {
            if (d.bay == 1) recover(level, d, site); else dockT4(level, d, site);
            if (d.spotId >= 0) LaserSpots.remove(d.spotId);
            return true;
        }
        if (known) { Vec3 h = deckToWorld(level, site, d.bay == 1 ? shuttle(site, f) : hangarBay(site, f)); d.hx = h.x; d.hy = h.y; d.hz = h.z; }
        boolean onDeck = d.ship && d.deck;
        if (onDeck && !known) {
            d.vx = d.vy = d.vz = 0.0;
            d.battery++;
            if (d.pilot != null) watchTick(level, d);
            return false;
        }
        double w0x = d.x, w0y = d.y, w0z = d.z;
        if (onDeck) { d.x = d.px; d.y = d.py; d.z = d.pz; d.yaw = d.pyaw; }
        boolean done = false;
        switch (d.phase) {
            case THROW -> {
                Vec3 a = shuttle(site, f), b = railEnd(site, f);
                if (!onDeck) { a = deckToWorld(level, site, a); b = deckToWorld(level, site, b); }
                double k = Math.min(1.0, ++d.timer / (double) THROW_TICKS);
                Vec3 p = a.lerp(b, k * k);
                d.vx = p.x - d.x; d.vy = p.y - d.y; d.vz = p.z - d.z;
                d.x = p.x; d.y = p.y; d.z = p.z;
                if (d.timer >= THROW_TICKS) { d.phase = CRUISE; d.timer = 0; d.deck = false; }
            }
            case TAXI_OUT -> {
                Vec3 to = runway(site, f, 2.0);
                if (moveFlat(d, to, TAXI) < 0.1) { d.phase = ROLL; d.roll = TAXI; sound(level, d, WmdSounds.DRONE_JET_TAKEOFF.get(), 8.0F); }
            }
            case ROLL -> {
                d.roll = Math.min(speed, d.roll + ROLL_ACCEL);
                d.vx = d.fx * d.roll; d.vz = d.fz * d.roll;
                d.x += d.vx; d.z += d.vz;
                if (d.roll >= speed * 0.9) { d.vy = CLIMB; d.y += CLIMB; }
                else d.vy = 0.0;
                if (onDeck) { if (d.y >= runway(site, f, 0.0).y + 12.0) { d.phase = CRUISE; d.deck = false; } }
                else {
                    int g = Drones.groundAt(level, d.x, d.z);
                    if (d.y >= (g == Integer.MIN_VALUE ? d.hy : g) + 12.0) d.phase = CRUISE;
                }
            }
            case CRUISE -> {
                double[] p = nextPoint(d);
                double left = flyLevel(level, d, p[0], p[1], speed, cruise);
                if (d.wpI >= d.wx.length && left < HOLD_AHEAD && d.timer == 0 && hasBombs(d)) {
                    FlightManager.get(level).holdForTest(level, d.tx, d.tz, HOLD_R);
                    d.timer = 1;
                }
                if (d.wpI < d.wx.length) { if (left <= WP_REACH) d.wpI++; }
                else if (d.kind == 3) {
                    if (!hasBombs(d)) { if (left <= WP_REACH) goHome(server, d); }
                    else if (left <= fallLead(level, d, speed)) { d.phase = DROP; d.timer = 0; }
                } else if (left <= Balance.T4_LOITER_R + 10.0) { d.phase = LOITER; d.timer = hasBombs(d) ? 60 : Balance.T4_LOITER_S * 20; d.angle = Math.atan2(d.z - (d.tz + 0.5), d.x - (d.tx + 0.5)); }
            }
            case DROP -> {
                flyLevel(level, d, d.x + d.vx * 10.0, d.z + d.vz * 10.0, speed, cruise);
                if (d.timer-- <= 0) {
                    if (!releaseNext(level, d)) goHome(server, d);
                    d.timer = DROP_GAP;
                }
            }
            case LOITER -> {
                d.angle += speed / Balance.T4_LOITER_R;
                double px = d.tx + 0.5 + Math.cos(d.angle) * Balance.T4_LOITER_R, pz = d.tz + 0.5 + Math.sin(d.angle) * Balance.T4_LOITER_R;
                flyLevel(level, d, px, pz, speed, cruise);
                lase(level, d, cruise);
                if (--d.timer <= 0) {
                    if (hasBombs(d)) { releaseNext(level, d); d.timer = hasBombs(d) ? LOITER_GAP : 200; }
                    else goHome(server, d);
                }
            }
            case RETURN -> {
                if (d.ship) returnToShip(level, d, site, f, known, speed);
                else if (d.bay == 1) {
                    Vec3 to = deckToWorld(level, site, beside(site, f));
                    if (flyLevel(level, d, to.x, to.z, speed, cruise) < 3.0) { d.phase = CHUTE; d.vx = d.vz = 0.0; sound(level, d, WmdSounds.CHUTE_POP.get(), 3.0F); }
                } else if (circuit(level, d, site, f, speed)) { d.phase = APPROACH; d.roll = speed; }
            }
            case CHUTE -> {
                if (onDeck) {
                    Vec3 s = shuttle(site, f);
                    double ex = s.x - d.x, ez = s.z - d.z, dist = Math.sqrt(ex * ex + ez * ez), k = dist > 0.2 ? 0.2 / dist : 1.0;
                    d.x += ex * k; d.z += ez * k;
                    d.y = Math.max(s.y, d.y - Balance.CHUTE_RATE);
                    if (d.y <= s.y + 1.0E-3 && dist <= 0.2) { recover(level, d, site); done = true; }
                    break;
                }
                int g = Drones.groundAt(level, d.x, d.z);
                double floor = g == Integer.MIN_VALUE ? d.hiveY : g;
                if (d.y > floor) { d.vy = -Balance.CHUTE_RATE; d.y = Math.max(floor, d.y - Balance.CHUTE_RATE); }
                else {
                    d.vy = 0.0;
                    if (level.isLoaded(site)) { recover(level, d, site); done = true; }
                    else d.battery++;
                }
            }
            case APPROACH -> {
                Vec3 end = runway(site, f, Balance.RUNWAY_L - 4.0);
                double dist = Math.hypot(end.x - d.x, end.z - d.z);
                double s = Math.min(d.roll, dist);

                d.vx = dist > 1.0E-6 ? (end.x - d.x) / dist * s : 0.0; d.vz = dist > 1.0E-6 ? (end.z - d.z) / dist * s : 0.0;
                d.x += d.vx; d.z += d.vz;
                d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
                double want = end.y + Math.min(GLIDE_H, dist * GLIDE_H / APPROACH_D);
                d.vy = Mth.clamp(want - d.y, -0.4, 0.2);
                d.y += d.vy;
                if (dist < 1.0) { d.phase = LANDING; d.y = end.y; d.vy = 0.0; if (level.getBlockEntity(site) instanceof DroneModuleBlockEntity h) h.openDoor(400); }
            }
            case LANDING -> {
                Vec3 stop = runway(site, f, 2.0);
                d.roll = Math.max(TAXI, d.roll - ROLL_ACCEL);
                if (moveFlat(d, stop, d.roll) < 0.1) d.phase = TAXI_IN;
            }
            case TAXI_IN -> {
                if (moveFlat(d, hangarBay(site, f), TAXI) < 0.1) {
                    if (level.isLoaded(site)) { dockT4(level, d, site); done = true; }
                    else d.battery++;
                }
            }
            default -> goHome(server, d);
        }
        if (onDeck) {
            Vec3 p = new Vec3(d.x, d.y, d.z), w = deckToWorld(level, site, p);
            d.px = p.x; d.py = p.y; d.pz = p.z; d.pyaw = d.yaw;
            d.yaw = worldYaw(level, site, p, d.yaw);
            d.vx = w.x - w0x; d.vy = w.y - w0y; d.vz = w.z - w0z;
            d.x = w.x; d.y = w.y; d.z = w.z;
        }

        if (d.kind == 4 && (d.phase == TAXI_OUT || d.phase == ROLL || d.phase == APPROACH || d.phase == LANDING || d.phase == TAXI_IN) && level.getGameTime() % 10 == 0) {
            Vec3 r0 = runway(site, f, 0.0), r1 = runway(site, f, Balance.RUNWAY_L);
            Wmd.LOGGER.info("[T4 path] #{} {} at {} {} {} yaw {} v {} {} | hangar {} facing {} | runway {} {} -> {} {}", d.id, d.phase,
                    String.format("%.1f", d.x), String.format("%.1f", d.y), String.format("%.1f", d.z), String.format("%.0f", d.yaw),
                    String.format("%.2f", d.vx), String.format("%.2f", d.vz), site.toShortString(), f,
                    String.format("%.1f", r0.x), String.format("%.1f", r0.z), String.format("%.1f", r1.x), String.format("%.1f", r1.z));
        }
        if (d.phase == CRUISE || d.phase == LOITER) {
            Vec3 home = d.ship ? new Vec3(d.hx, d.hy, d.hz) : d.bay == 1 ? beside(site, f) : runway(site, f, Balance.RUNWAY_L + APPROACH_D);
            double need = Math.hypot(home.x - d.x, home.z - d.z) / speed + (d.bay == 1 ? 600 : 900);
            if (d.battery < need) {
                Drones.tell(server, d.owner, Component.translatable("message.wmd.big_drone.home", d.id).withStyle(ChatFormatting.GRAY));
                goHome(server, d);
            }
        }
        if (d.pilot != null) watchTick(level, d);
        if (done && d.spotId >= 0) LaserSpots.remove(d.spotId);
        return done;
    }

    private static void goHome(MinecraftServer server, Drones.Drone d) {
        d.phase = RETURN;
        d.circuit = -1;
        if (d.spotId >= 0) { LaserSpots.remove(d.spotId); d.spotId = -1; }
    }

    private static void watchTick(ServerLevel level, Drones.Drone d) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.pilot);
        if (p == null || d.ctlLeave) { StrikeDrones.endPilot(level.getServer(), d); return; }
        DronePilots.follow(p, d.x, d.y, d.z);
        sendWatch(p, d, level);
    }

    private static double[] nextPoint(Drones.Drone d) {
        if (d.wpI < d.wx.length) return new double[] { d.wx[d.wpI] + 0.5, d.wz[d.wpI] + 0.5 };
        return new double[] { d.tx + 0.5, d.tz + 0.5 };
    }

    private static void sound(ServerLevel level, Drones.Drone d, net.minecraft.sounds.SoundEvent s, float volume) {
        Vec3 w = SableBridge.worldPos(level, d.x, d.y, d.z);
        level.playSound(null, w.x, w.y, w.z, s, SoundSource.HOSTILE, volume, 1.0F);
    }

    private static void returnToShip(ServerLevel level, Drones.Drone d, BlockPos site, Direction f, boolean known, double speed) {
        boolean t3 = d.bay == 1;
        if (!known) {
            if (flyTo(level, d, new Vec3(d.hx, d.hy + GLIDE_H, d.hz), speed) < 16.0) d.battery++;
            return;
        }
        Vec3 deck = t3 ? shuttle(site, f).add(0.0, CHUTE_H, 0.0) : runway(site, f, Balance.RUNWAY_L + APPROACH_D).add(0.0, GLIDE_H, 0.0);
        Vec3 to = deckToWorld(level, site, deck);
        double left = flyTo(level, d, to, speed);
        if (left < (t3 ? 2.0 : 4.0) && Math.abs(to.y - d.y) < 2.0 && toDeck(level, d, site)) {
            if (t3) { d.phase = CHUTE; d.vx = d.vz = 0.0; sound(level, d, WmdSounds.CHUTE_POP.get(), 3.0F); }
            else { d.phase = APPROACH; d.roll = speed; }
        }
    }

    private static double flyTo(ServerLevel level, Drones.Drone d, Vec3 to, double speed) {
        double dx = to.x - d.x, dz = to.z - d.z, dist = Math.sqrt(dx * dx + dz * dz);
        double s = Math.min(speed, dist);
        d.vx = dist > 1.0E-6 ? dx / dist * s : 0.0;
        d.vz = dist > 1.0E-6 ? dz / dist * s : 0.0;
        d.x += d.vx; d.z += d.vz;
        if (s > 1.0E-3) d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
        d.vy = Mth.clamp(to.y - d.y, -0.6, 0.6);
        d.y += d.vy;
        int g = Drones.groundAt(level, d.x, d.z);
        if (g != Integer.MIN_VALUE && d.y < g + 4.0) d.y = g + 4.0;
        return Math.max(0.0, dist - s);
    }

    private static double flyLevel(ServerLevel level, Drones.Drone d, double px, double pz, double speed, int cruise) {
        double dx = px - d.x, dz = pz - d.z, dist = Math.sqrt(dx * dx + dz * dz);
        double s = Math.min(speed, dist);
        d.vx = dist > 1.0E-6 ? dx / dist * s : 0.0;
        d.vz = dist > 1.0E-6 ? dz / dist * s : 0.0;
        d.x += d.vx; d.z += d.vz;
        if (s > 1.0E-3) d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
        int g = Drones.groundAt(level, d.x, d.z);
        if (g != Integer.MIN_VALUE) {
            double want = g + cruise;
            d.vy = Math.max(-0.4, Math.min(0.6, want - d.y));
            d.y += d.vy;
            if (d.y < g + 4.0) d.y = g + 4.0;
        } else d.vy = 0.0;
        return Math.max(0.0, dist - s);
    }

    private static boolean circuit(ServerLevel level, Drones.Drone d, BlockPos site, Direction f, double speed) {
        Vec3 r0 = runway(site, f, 0.0), app = runway(site, f, Balance.RUNWAY_L + APPROACH_D), fix = runway(site, f, Balance.RUNWAY_L + APPROACH_D + CIRCUIT_OUT);
        double ux = app.x - r0.x, uz = app.z - r0.z, ul = Math.hypot(ux, uz);
        ux /= ul; uz /= ul;
        double px = d.x - app.x, pz = d.z - app.z;
        double along = px * ux + pz * uz, side = px * -uz + pz * ux;
        double off = Math.abs(Mth.wrapDegrees(d.yaw - Math.toDegrees(Math.atan2(ux, -uz))));
        if (d.circuit < 0) {
            d.circuit = along >= CIRCUIT_OUT * 0.5 && Math.abs(side) <= along * 0.5 ? 2 : along >= CIRCUIT_OUT * 0.5 ? 1 : 0;
            d.circuitSide = side >= 0.0 ? 1.0 : -1.0;
        }
        double tx, tz;
        if (d.circuit == 0) {
            tx = fix.x - uz * d.circuitSide * CIRCUIT_SIDE; tz = fix.z + ux * d.circuitSide * CIRCUIT_SIDE;
            if (Math.hypot(d.x - tx, d.z - tz) < 15.0) d.circuit = 1;
        }
        if (d.circuit == 1) {
            tx = fix.x; tz = fix.z;
            if (Math.hypot(d.x - tx, d.z - tz) < 15.0) d.circuit = 2;
        }
        if (d.circuit == 2) {
            if (Math.hypot(px, pz) < 8.0 && off < 25.0) { d.circuit = -1; return true; }
            if (along < -2.0) { d.circuit = -1; return false; }
        }
        tx = d.circuit == 0 ? fix.x - uz * d.circuitSide * CIRCUIT_SIDE : d.circuit == 1 ? fix.x : app.x;
        tz = d.circuit == 0 ? fix.z + ux * d.circuitSide * CIRCUIT_SIDE : d.circuit == 1 ? fix.z : app.z;
        flyTurn(level, d, tx, tz, speed, (int) GLIDE_H);
        return false;
    }

    private static void flyTurn(ServerLevel level, Drones.Drone d, double px, double pz, double speed, int cruise) {
        double want = Math.toDegrees(Math.atan2(-(px - d.x), pz - d.z));
        d.yaw = (float) (d.yaw + Mth.clamp(Mth.wrapDegrees(want - d.yaw), -CIRCUIT_TURN, CIRCUIT_TURN));
        double r = Math.toRadians(d.yaw);
        d.vx = -Math.sin(r) * speed; d.vz = Math.cos(r) * speed;
        d.x += d.vx; d.z += d.vz;
        int g = Drones.groundAt(level, d.x, d.z);
        if (g != Integer.MIN_VALUE) {
            d.vy = Math.max(-0.4, Math.min(0.6, g + cruise - d.y));
            d.y += d.vy;
            if (d.y < g + 4.0) d.y = g + 4.0;
        } else d.vy = 0.0;
    }

    private static double moveFlat(Drones.Drone d, Vec3 to, double speed) {
        double dx = to.x - d.x, dz = to.z - d.z, dist = Math.sqrt(dx * dx + dz * dz);
        double s = Math.min(speed, dist);
        d.vx = dist > 1.0E-6 ? dx / dist * s : 0.0;
        d.vz = dist > 1.0E-6 ? dz / dist * s : 0.0;
        d.vy = 0.0;
        d.x += d.vx; d.z += d.vz; d.y = to.y;
        if (s > 1.0E-3) d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
        return Math.max(0.0, dist - s);
    }

    private static double fallLead(ServerLevel level, Drones.Drone d, double speed) {
        int g = Drones.groundAt(level, d.tx + 0.5, d.tz + 0.5);
        double h = g == Integer.MIN_VALUE ? (d.kind == 3 ? Balance.DRONE_T3_CRUISE : Balance.DRONE_T4_CRUISE) : Math.max(1.0, d.y - g);
        return speed * Math.sqrt(2.0 * h / Balance.BOMB_G) + 2.0;
    }

    private static boolean hasBombs(Drones.Drone d) {
        for (int b : d.bombs) if (b >= 0) return true;
        return false;
    }

    private static boolean releaseNext(ServerLevel level, Drones.Drone d) {
        for (int i = 0; i < d.bombs.length; i++) {
            if (d.bombs[i] < 0) continue;
            BombType t = BombType.ofOrdinal(d.bombs[i]);
            d.bombs[i] = -1;
            Vec3 at = new Vec3(d.x, d.y - 1.0, d.z), v = new Vec3(d.vx, 0.0, d.vz);
            int g = Drones.groundAt(level, d.tx + 0.5, d.tz + 0.5);
            double h = Math.max(1.0, at.y - (g == Integer.MIN_VALUE ? at.y - (d.kind == 3 ? Balance.DRONE_T3_CRUISE : Balance.DRONE_T4_CRUISE) : g));
            double fall = Math.sqrt(2.0 * h / Balance.BOMB_G);
            if (d.kind == 4) {
                double vx = (d.tx + 0.5 - at.x) / fall, vz = (d.tz + 0.5 - at.z) / fall, s = Math.hypot(vx, vz), max = 1.5 * Balance.DRONE_T4_SPEED;
                v = s > max ? new Vec3(vx / s * max, 0.0, vz / s * max) : new Vec3(vx, 0.0, vz);
            }
            Vec3 land = at.add(v.scale(fall));
            if (level.isPositionEntityTicking(BlockPos.containing(at)) && level.isPositionEntityTicking(BlockPos.containing(land.x, at.y, land.z)))
                BombEntity.release(level, t, at, v, d.owner, new int[] { d.tx, d.tz }, null, null);
            else remoteFall(level, d, t, at, v, land, (long) Math.ceil(fall));
            Drones.tell(level.getServer(), d.owner, Component.translatable("message.wmd.big_drone.bomb", d.id, Component.translatable("item.wmd." + t.id)).withStyle(ChatFormatting.GOLD));
            CcBattleLog.add(level.getServer(), d.owner, CcBattleLog.DRONE, "log.wmd.drone_bomb", Integer.toString(d.id), t.id, Integer.toString(Mth.floor(land.x)), Integer.toString(Mth.floor(land.z)));
            return true;
        }
        return false;
    }

    private static void lase(ServerLevel level, Drones.Drone d, int cruise) {
        int g = Drones.groundAt(level, d.tx + 0.5, d.tz + 0.5);
        Vec3 at = d.lased ? new Vec3(d.lx, d.ly, d.lz) : new Vec3(d.tx + 0.5, g == Integer.MIN_VALUE ? d.y - cruise : g, d.tz + 0.5);
        Vec3 from = d.pos();
        if (d.spotId < 0 || !LaserSpots.refresh(level, d.spotId, at, from)) d.spotId = LaserSpots.light(level, d.owner, at, LaserSpots.SRC_DRONE, from);
    }

    private static void recover(ServerLevel level, Drones.Drone d, BlockPos site) {
        DroneType t = d.kind == 3 ? DroneType.T3 : DroneType.T4;
        boolean ok = level.isLoaded(site) && level.getBlockEntity(site) instanceof DroneCatapultBlockEntity c && c.recover(d.id, t, d.bombs);
        if (!ok) {
            ItemEntity it = new ItemEntity(level, d.x, d.y + 0.2, d.z, t.stack());
            it.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(it);
        }
        Drones.tell(level.getServer(), d.owner, Component.translatable("message.wmd.big_drone.recovered", d.id).withStyle(ChatFormatting.AQUA));
        CcBattleLog.add(level.getServer(), d.owner, CcBattleLog.DRONE, "log.wmd.drone_home", Integer.toString(d.id), "T" + d.kind);
    }

    private static void dockT4(ServerLevel level, Drones.Drone d, BlockPos site) {
        boolean ok = level.isLoaded(site) && level.getBlockEntity(site) instanceof DroneModuleBlockEntity h && h.dockT4(d.id, d.bombs);
        if (!ok) {
            ItemEntity it = new ItemEntity(level, d.x, d.y + 0.2, d.z, DroneType.T4.stack());
            it.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(it);
        }
        Drones.tell(level.getServer(), d.owner, Component.translatable("message.wmd.big_drone.landed", d.id).withStyle(ChatFormatting.AQUA));
        CcBattleLog.add(level.getServer(), d.owner, CcBattleLog.DRONE, "log.wmd.drone_home", Integer.toString(d.id), "T" + d.kind);
    }

    private record Fall(BombType type, double x, double z, double dx, double dz, long at, java.util.UUID owner, double sx, double sz) {}
    private static final java.util.List<Fall> FALLS = new java.util.ArrayList<>();

    static void clearStatics() { FALLS.clear(); SITES.clear(); }

    private static void remoteFall(ServerLevel level, Drones.Drone d, BombType t, Vec3 at, Vec3 v, Vec3 land, long ticks) {
        double x = land.x, z = land.z, h = Math.max(1.0, 0.5 * Balance.BOMB_G * ticks * ticks);
        switch (t) {
            case LASER -> { if (d.spotId >= 0) { x = d.lased ? d.lx : d.tx + 0.5; z = d.lased ? d.lz : d.tz + 0.5; } }
            case GLIDE -> {
                if (Math.hypot(d.tx + 0.5 - at.x, d.tz + 0.5 - at.z) <= Balance.GLIDE_RANGE) {
                    double r = Balance.GLIDE_SCATTER * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2.0;
                    x = d.tx + 0.5 + Math.cos(a) * r; z = d.tz + 0.5 + Math.sin(a) * r;
                }
            }
            case IRON -> {
                double r = Math.min(Balance.IRON_SCATTER_MAX, Balance.IRON_SCATTER * h) * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2.0;
                x += Math.cos(a) * r; z += Math.sin(a) * r;
            }
            default -> {}
        }
        FlightManager.get(level).holdForTest(level, Mth.floor(x), Mth.floor(z), HOLD_R);
        DroneWarnings.bomb(level, d.owner, at.x, at.z, x, z, ticks);
        FALLS.add(new Fall(t, x, z, v.x, v.z, level.getGameTime() + Math.max(20L, ticks), d.owner, at.x, at.z));
    }

    static void tickFalls(ServerLevel level, long now) {
        if (FALLS.isEmpty()) return;
        for (java.util.Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
            Fall f = it.next();
            if (now < f.at()) continue;
            it.remove();
            int bx = Mth.floor(f.x()), bz = Mth.floor(f.z());
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            WarheadType w = f.type().blast;
            java.util.UUID prev = ShipStrike.begin(f.owner());
            try {
                WarheadFx.detonate(level, FlightManager.get(level), bx, y, bz, w.tier, w, f.dx(), f.dz());
            } finally {
                ShipStrike.end(prev);
            }
            Wmd.LOGGER.info("Bomb: {} ({}) from a drone went off at {} {} {} (fell as numbers)", f.type().id, w.id, bx, y, bz);
            if (f.owner() != null && level.getServer().getPlayerList().getPlayer(f.owner()) instanceof ServerPlayer p)
                p.displayClientMessage(Component.translatable("message.wmd.bomb.hit", Component.translatable("item.wmd." + f.type().id), bx, y, bz,
                        (int) Math.round(Math.hypot(f.x() - f.sx(), f.z() - f.sz()))), false);
        }
    }
}
