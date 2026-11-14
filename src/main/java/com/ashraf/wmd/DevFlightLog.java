package com.ashraf.wmd;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevFlightLog {

    private DevFlightLog() {}

    private static final double SABLE_DIRLESS = 0.068882026;

    private static final class Rec {
        final UUID ship;
        final BlockPos cockpit;
        final int every;
        final BufferedWriter out;
        final File file;
        Vec3 lastV;
        long lastT;
        final List<double[]> air = new ArrayList<>();
        int rows;
        Rec(UUID ship, BlockPos cockpit, int every, BufferedWriter out, File file) { this.ship = ship; this.cockpit = cockpit; this.every = every; this.out = out; this.file = file; }
    }

    private static final Map<UUID, Rec> RECS = Statics.server(new HashMap<>());

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("flightlog")
                .then(Commands.literal("start").executes(c -> start(c.getSource(), 10))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(2, 100)).executes(c -> start(c.getSource(), IntegerArgumentType.getInteger(c, "ticks")))))
                .then(Commands.literal("stop").executes(c -> stop(c.getSource()))))));
    }

    private static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    private static int start(CommandSourceStack src, int every) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (!AirGate.on()) { src.sendFailure(Component.literal("1.2 air battles are off this session.")); return 0; }
        if (!(p.getVehicle() instanceof CockpitSeatEntity seat) || seat.cockpit() == null) { src.sendFailure(Component.literal("Sit in a jet cockpit first.")); return 0; }
        ServerLevel sl = p.serverLevel();
        UUID ship = SableBridge.shipIdAt(sl, seat.cockpit());
        if (ship == null) { src.sendFailure(Component.literal("The cockpit is not on an assembled craft.")); return 0; }
        stopQuiet(p.getUUID());
        try {
            File dir = FMLPaths.CONFIGDIR.get().resolve("wmd_flightlog").toFile();
            dir.mkdirs();
            File f = new File(dir, "flight_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".csv");
            BufferedWriter w = new BufferedWriter(new FileWriter(f));
            w.write("tick,t_s,x,y,z,speed,vx,vy,vz,pitch,roll,aoa,pressure,gx,gy,gz,udrag,mass,lift_blocks,thrust,thrust_x,thrust_y,thrust_z,aero_x,aero_y,aero_z,"
                    + "acc_meas_x,acc_meas_y,acc_meas_z,acc_pred_x,acc_pred_y,acc_pred_z,wheels\n");
            RECS.put(p.getUUID(), new Rec(ship, seat.cockpit(), every, w, f));
            say(src, "Flight recorder ON (every " + every + " ticks) -> " + f.getName() + ". Fly: take-off, climb, level, stall. /wmd dev flightlog stop");
            return 1;
        } catch (Exception ex) { src.sendFailure(Component.literal("Could not open the log: " + ex.getMessage())); return 0; }
    }

    private static void stopQuiet(UUID player) {
        Rec r = RECS.remove(player);
        if (r != null) try { r.out.close(); } catch (Exception ignored) {}
    }

    private static int stop(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        Rec r = RECS.remove(p.getUUID());
        if (r == null) { src.sendFailure(Component.literal("The recorder is not running.")); return 0; }
        try { r.out.close(); } catch (Exception ignored) {}
        say(src, String.format("Flight recorder OFF: %d samples, %d airborne -> %s", r.rows, r.air.size(), r.file.getAbsolutePath()));
        if (!r.air.isEmpty()) {
            List<Double> vr = new ArrayList<>(), ar = new ArrayList<>();
            double pr = 0, gy = 0;
            for (double[] a : r.air) {
                if (Math.abs(a[1]) > 0.5) vr.add(a[0] / a[1]);
                if (Math.abs(a[3]) > 0.5) ar.add(a[2] / a[3]);
                pr += a[4]; gy += a[5];
            }
            say(src, String.format("CALIBRATION (measured / predicted, median): LIFT (vertical) %s, THRUST-DRAG (along path) %s. Sable pressure avg %.3f, gravity y avg %.3f.",
                    med(vr), med(ar), pr / r.air.size(), gy / r.air.size()));
            say(src, "1.00 = the math matches. Keep the CSV for the full fit.");
        }
        return 1;
    }

    private static String med(List<Double> l) {
        if (l.isEmpty()) return "n/a";
        l.sort(Double::compare);
        return String.format("%.2f (%d samples)", l.get(l.size() / 2), l.size());
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (RECS.isEmpty()) return;
        for (var it = RECS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            Rec r = e.getValue();
            if (p == null || !(p.getVehicle() instanceof CockpitSeatEntity)) { try { r.out.close(); } catch (Exception ignored) {} it.remove(); continue; }
            ServerLevel sl = p.serverLevel();
            long now = sl.getGameTime();
            if (now % r.every != 0) continue;
            try { sample(sl, p, r, now); } catch (Exception ex) { Wmd.LOGGER.warn("flight recorder sample failed", ex); }
        }
    }

    private static Vec3 dirWorld(ServerLevel sl, Vec3 at, Vec3 d) {
        double len = d.length();
        if (len < 1.0E-9) return Vec3.ZERO;
        Vec3 a = SableBridge.toWorld(sl, at), b = SableBridge.toWorld(sl, at.add(d.scale(1.0 / len)));
        return a == null || b == null ? d : b.subtract(a).normalize().scale(len);
    }

    private static void sample(ServerLevel sl, ServerPlayer p, Rec r, long now) throws Exception {
        SableBridge.ShipInfo si = SableBridge.shipInfoFresh(sl, r.ship);
        if (si == null || !(sl.getBlockEntity(r.cockpit) instanceof JetCockpitBlockEntity jc)) return;
        Vec3 v = si.linearVelocity();
        double m = Math.max(1.0E-6, si.mass());
        double[] a0 = jc.worldPoint(0.0, 0.5, 0.0), a1 = jc.worldPoint(0.0, 0.5, 1.0), u0 = jc.worldPoint(0.0, 0.0, 0.0), u1 = jc.worldPoint(0.0, 1.0, 0.0),
                r0 = jc.worldPoint(1.0, 0.5, 0.0), r1 = jc.worldPoint(0.0, 0.5, 0.0);
        Vec3 f = new Vec3(a1[0] - a0[0], a1[1] - a0[1], a1[2] - a0[2]).normalize(), up = new Vec3(u1[0] - u0[0], u1[1] - u0[1], u1[2] - u0[2]).normalize(),
                right = new Vec3(r1[0] - r0[0], r1[1] - r0[1], r1[2] - r0[2]).normalize();
        double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, f.y)))), roll = Math.toDegrees(Math.atan2(-right.y, up.y));
        double sp = v.length();
        double aoa = sp < 1.0E-3 ? 0.0 : Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -v.normalize().dot(up)))));
        double[] ph = SableBridge.physicsAt(sl, si.worldCenter());
        double pr = ph == null ? AirEngineBlockEntity.airPressure(sl, si.worldCenter().y) : ph[0];
        Vec3 g = ph == null ? new Vec3(0.0, -Balance.FC_GRAVITY, 0.0) : new Vec3(ph[1], ph[2], ph[3]);
        double ud = ph == null ? 0.0 : ph[4];

        Vec3 thrust = Vec3.ZERO, aero = Vec3.ZERO;
        int lift = 0;
        for (BlockPos bp : SableBridge.blockEntitiesOn(sl, r.ship)) {
            BlockEntity be = sl.getBlockEntity(bp);
            if (be instanceof ShipEngine eng) {
                double[] ef = eng.engineForce();
                if (ef != null && ef.length >= 6) thrust = thrust.add(dirWorld(sl, new Vec3(ef[0], ef[1], ef[2]), new Vec3(ef[3], ef[4], ef[5])));
            }
            if (be instanceof WingSectionBlock.BE || be instanceof ControlSurfaceBlock.BE) {
                Direction nd = WingParts.normal(be.getBlockState());
                Vec3 n = dirWorld(sl, Vec3.atCenterOf(bp), new Vec3(nd.getStepX(), nd.getStepY(), nd.getStepZ())).normalize();
                Vec3 plate = n.scale(n.dot(v) * Balance.WING_PLATE_SCALAR), dl = v.scale(SABLE_DIRLESS);
                aero = aero.subtract(plate.add(dl).scale(pr));
                lift++;
            }
        }
        Vec3 pred = aero.add(thrust).scale(1.0 / m).add(g);
        Vec3 meas = Vec3.ZERO;
        double dt = r.lastV == null ? 0.0 : (now - r.lastT) / 20.0;
        if (r.lastV != null && dt > 0.0) meas = v.subtract(r.lastV).scale(1.0 / dt);
        boolean wheels = LandingGearBlockEntity.stateAboard(sl, r.ship) >= 10;
        Vec3 w = si.worldCenter();
        r.out.write(String.format(java.util.Locale.ROOT, "%d,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f,%.5f,%.2f,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d%n",
                now, r.rows * r.every / 20.0, w.x, w.y, w.z, sp, v.x, v.y, v.z, pitch, roll, aoa, pr, g.x, g.y, g.z, ud, m, lift, thrust.length(), thrust.x, thrust.y, thrust.z,
                aero.x, aero.y, aero.z, meas.x, meas.y, meas.z, pred.x, pred.y, pred.z, wheels ? 1 : 0));
        r.out.flush();
        r.rows++;
        if (r.lastV != null && !wheels && sp > 10.0) {
            Vec3 vh = v.normalize();
            r.air.add(new double[] { meas.y - g.y, pred.y - g.y, meas.dot(vh) - g.dot(vh), pred.dot(vh) - g.dot(vh), pr, g.y });
        }
        r.lastV = v;
        r.lastT = now;
        p.displayClientMessage(Component.literal(String.format("REC  %.0f b/s  y %.0f  pitch %.1f  AoA %.1f  P %.3f  lift pred %.0f  vert acc meas %.1f / pred %.1f%s",
                sp, w.y, pitch, aoa, pr, aero.y, meas.y, pred.y, wheels ? "  (wheels)" : "")), true);
    }
}
