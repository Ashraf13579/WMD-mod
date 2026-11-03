package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StationLaser {

    private StationLaser() {}

    public static final int IDLE = 0, CHARGING = 1, ARMED = 2, LOCK = 3, FIRE = 4, COOL = 5;
    public static final int CMD_CHARGE = 0, CMD_STAND_DOWN = 1, CMD_AUTO = 2, CMD_FIRE_SAT = 3, CMD_FIRE_MODULE = 4, CMD_ASAT_PICK = 5, CMD_BOARD_PICK = 6;
    public static final int CMD_FIRE_TUG = 7;
    public static final int CMD_MATCH = 8, CMD_RAM = 9;
    public static final int T_SAT = 0, T_MODULE = 1, T_FLIGHT = 2, T_ROD = 3, T_TUG = 4, T_ROCK = 5;
    private static final double TIP = (593.0 - 248.0) / 16.0;

    private static final class St {
        int state; long t0; float roll, pitch;
        int kind; long target; Vec3 dir = new Vec3(0, 1, 0);
        boolean close;
    }

    private static final Map<Integer, St> ST = new HashMap<>();

    static void clearStatics() { ST.clear(); }

    static boolean busy(int slot) { St s = ST.get(slot); return s != null && s.state != IDLE; }

    public record Target(int kind, long id, String label, int health) {}

    static Station.Module module(MinecraftServer server, int slot) {
        for (Station.Module m : Station.modules(server, slot)) if (Station.LASER.equals(m.type()) && m.built()) return m;
        return null;
    }

    static boolean auto(MinecraftServer server, int slot) { return Station.Data.get(server).laserAuto.getOrDefault(slot, 0) == 1; }

    static int[] status(MinecraftServer server, int slot, long now) {
        Station.Module m = module(server, slot);
        if (m == null) return new int[] { -1, 0 };
        if (Station.wrecked(server, m)) return new int[] { -2, 0 };
        St s = ST.get(slot);
        if (s == null) return new int[] { IDLE, 0 };
        int len = switch (s.state) { case CHARGING -> Balance.LASER_CHARGE_TICKS; case LOCK -> Balance.LASER_LOCK_TICKS; case FIRE -> Balance.LASER_FIRE_TICKS; case COOL -> Balance.LASER_COOL_TICKS; default -> 1; };
        return new int[] { s.state, (int) Math.max(0, Math.min(100, 100 * (now - s.t0) / Math.max(1, len))) };
    }

    static Vec3 meshPoint(Station.Module m, double px, double py, double pz) {
        Vec3 l = new Vec3(px / 16.0 - 0.5, py / 16.0, pz / 16.0);
        BlockPos o = m.origin();
        return new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5).add(toWorld(m.facing(), l));
    }

    static Vec3 toWorld(Direction f, Vec3 l) {
        return switch (f) { case SOUTH -> new Vec3(-l.z, l.y, l.x); case WEST -> new Vec3(-l.x, l.y, -l.z); case NORTH -> new Vec3(l.z, l.y, -l.x); default -> l; };
    }

    static Vec3 toLocal(Direction f, Vec3 w) {
        return switch (f) { case SOUTH -> new Vec3(w.z, w.y, -w.x); case WEST -> new Vec3(-w.x, w.y, -w.z); case NORTH -> new Vec3(-w.z, w.y, w.x); default -> w; };
    }

    static Vec3 trunnion(Station.Module m) { return meshPoint(m, 248.0, 40.0, 0.0); }

    static float[] aim(Station.Module m, Vec3 dir) {
        Vec3 l = toLocal(m.facing(), dir.normalize());
        if (l.x < -0.05) return null;
        float pitch = (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, l.x))));
        float roll = pitch < 0.5F ? 0.0F : (float) Math.toDegrees(Math.atan2(l.z, l.y));
        return new float[] { roll, pitch };
    }

    static Component command(ServerPlayer p, int slot, int cmd, long arg) {
        MinecraftServer server = p.server;
        if (!p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) return Component.translatable("message.wmd.laser.not_owner");
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Data d = Station.Data.get(server);
        if (cmd == CMD_AUTO) { d.laserAuto.put(slot, auto(server, slot) ? 0 : 1); d.setDirty(); return null; }
        if (cmd == CMD_BOARD_PICK) return Boarding.setPick(p, slot, (int) arg);
        if (cmd == CMD_MATCH || cmd == CMD_RAM) return Rendezvous.approach(p, slot, cmd == CMD_RAM);
        if (cmd == CMD_ASAT_PICK) {
            if (arg < 0) { d.asatPick.remove(slot); d.setDirty(); return Component.translatable("message.wmd.laser.asat_cleared"); }
            Station.Module t = Station.byId(server, (int) arg);
            if (t == null || Station.slotOf(server, t) == slot || !Rendezvous.sees(server, slot, Station.slotOf(server, t))) return Component.translatable("message.wmd.laser.bad_target");
            d.asatPick.put(slot, t.id());
            d.setDirty();
            return Component.translatable("message.wmd.laser.asat_picked", name(server, t), Component.translatable("station.wmd.module." + t.type()));
        }
        Station.Module m = module(server, slot);
        if (m == null || orbit == null) return Component.translatable("message.wmd.laser.none");
        if (Station.wrecked(server, m)) return Component.translatable("message.wmd.laser.wrecked");
        St s = ST.computeIfAbsent(slot, k -> new St());
        long now = orbit.getGameTime();
        switch (cmd) {
            case CMD_CHARGE -> {
                if (s.state != IDLE) return Component.translatable("message.wmd.laser.busy");
                long buf = d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2);
                boolean close = Rendezvous.anyClose(server, slot);
                long cost = close ? Balance.LASER_CLOSE_SHOT_FE : Balance.LASER_SHOT_FE;
                if (buf < cost) return Component.translatable("message.wmd.laser.no_power", String.format("%,d", cost / 1000));
                d.energy.put(slot, buf - cost);
                d.setDirty();
                s.close = close;
                set(orbit, slot, m, s, CHARGING, close ? now - (Balance.LASER_CHARGE_TICKS - Balance.LASER_CLOSE_CHARGE_TICKS) : now);
                if (close) p.displayClientMessage(Component.translatable("message.wmd.laser.close_mode"), true);
                orbit.playSound(null, Station.middle(m), WmdSounds.LZ_CHARGE.get(), SoundSource.BLOCKS, 3.0F, 0.9F);
                return null;
            }
            case CMD_STAND_DOWN -> {
                if (s.state != CHARGING && s.state != ARMED) return Component.translatable("message.wmd.laser.not_charged");
                set(orbit, slot, m, s, COOL, now - (Balance.LASER_COOL_TICKS - 200));
                return Component.translatable("message.wmd.laser.stood_down");
            }
            case CMD_FIRE_SAT, CMD_FIRE_MODULE, CMD_FIRE_TUG -> {
                if (s.state != ARMED) return Component.translatable("message.wmd.laser.charge_first");
                Station.Module tm = cmd == CMD_FIRE_MODULE ? Station.byId(server, (int) arg) : null;
                boolean near = tm != null && Rendezvous.close(server, slot, Station.slotOf(server, tm));
                if (!near && !SensorArray.working(server, slot)) return Component.translatable("message.wmd.laser.blind");
                if (s.close && !near) return Component.translatable("message.wmd.laser.close_only");
                Vec3 dir = cmd == CMD_FIRE_SAT ? satDir(server, slot, m, (int) arg) : cmd == CMD_FIRE_TUG ? tugDir(server, slot, m, (int) arg, now) : moduleDir(server, slot, m, (int) arg);
                if (dir == null) return Component.translatable("message.wmd.laser.bad_target");
                int kind = cmd == CMD_FIRE_SAT ? T_SAT : cmd == CMD_FIRE_TUG ? T_TUG : T_MODULE;
                return lock(orbit, slot, m, s, kind, arg, dir, now) ? null : Component.translatable("message.wmd.laser.arc");
            }
            default -> { return null; }
        }
    }

    private static boolean lock(ServerLevel orbit, int slot, Station.Module m, St s, int kind, long target, Vec3 dir, long now) {
        float[] a = aim(m, dir);
        if (a == null) return false;
        s.kind = kind; s.target = target; s.dir = dir.normalize(); s.roll = a[0]; s.pitch = a[1];
        set(orbit, slot, m, s, LOCK, s.close ? now - (Balance.LASER_LOCK_TICKS - Balance.LASER_CLOSE_LOCK_TICKS) : now);
        orbit.playSound(null, Station.middle(m), WmdSounds.LZ_SLEW.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        return true;
    }

    private static void set(ServerLevel orbit, int slot, Station.Module m, St s, int state, long t0) {
        s.state = state; s.t0 = t0;
        boolean aimed = state == LOCK || state == FIRE;
        float roll = aimed ? s.roll : 0.0F, pitch = aimed ? s.pitch : 0.0F;
        if (orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setLaser(state, t0, roll, pitch);
    }

    static String name(MinecraftServer server, Station.Module t) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(Station.slotOf(server, t));
        return o == null ? "?" : CcIntel.name(server, o);
    }

    static List<Target> targets(MinecraftServer server, int slot) {
        List<Target> out = new ArrayList<>();
        boolean works = SensorArray.working(server, slot);
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        Orbit.Ground g = Orbit.ground(server, slot);
        long ms = System.currentTimeMillis();
        if (works) for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (!s.owner().equals(owner) && OrbitMath.isOverhead(s, g.x(), g.z(), ms)) out.add(new Target(T_SAT, s.id(), OrbitData.typeName(s.type()) + " #" + s.id() + " (" + s.ownerName() + ")", 100));
        for (Map.Entry<Integer, List<Station.Module>> e : Station.Data.get(server).slots.entrySet()) {
            if (e.getKey() == slot || (!works && !Rendezvous.close(server, slot, e.getKey()))) continue;
            for (Station.Module t : e.getValue()) {
                if (!t.built() || Station.wrecked(server, t)) continue;
                int hp = Math.round(100.0F * Station.health(server, t) / Math.max(1, Station.maxHealth(t.type())));
                out.add(new Target(T_MODULE, t.id(), name(server, t) + ": " + t.type(), hp));
            }
        }
        for (int s2 : AsteroidRedirect.pushing(server, slot))
            out.add(new Target(T_TUG, s2, AsteroidRedirect.ownerName(server, s2) + ": tug + asteroid", AsteroidRedirect.tugHealth(server, s2)));
        return out;
    }

    private static Vec3 tugDir(MinecraftServer server, int slot, Station.Module m, int target, long now) {
        if (target == slot) return null;
        Vec3 rock = AsteroidRedirect.pushingRock(server, target, now);
        return rock == null ? null : Rendezvous.inFrame(server, slot, target, rock).subtract(trunnion(m));
    }

    private static Vec3 satDir(MinecraftServer server, int slot, Station.Module m, int id) {
        OrbitMath.Sat s = OrbitData.get(server).byId(id);
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        Orbit.Ground g = Orbit.ground(server, slot);
        if (s == null || s.owner().equals(owner) || !OrbitMath.isOverhead(s, g.x(), g.z(), System.currentTimeMillis())) return null;
        double a = (id * 2.399) % (Math.PI * 2);
        return toWorld(m.facing(), new Vec3(0.35, 1.0, Math.sin(a) * 0.3));
    }

    private static Vec3 moduleDir(MinecraftServer server, int slot, Station.Module m, int id) {
        Station.Module t = Station.byId(server, id);
        if (t == null || !t.built() || Station.slotOf(server, t) == slot || Station.wrecked(server, t)) return null;
        return Rendezvous.inFrame(server, slot, Station.slotOf(server, t), Vec3.atCenterOf(Station.middle(t))).subtract(trunnion(m));
    }

    private static Vec3 groundDir(MinecraftServer server, int slot, double x, double z) {
        Orbit.Ground g = Orbit.ground(server, slot);
        double s = 40.0 / Balance.PLANET_ALT;
        return new Vec3((x - g.x()) * s, -40.0, (z - g.z()) * s);
    }

    private static Flight autoTarget(MinecraftServer server, int slot, UUID owner) {
        Orbit.Ground g = Orbit.ground(server, slot);
        ServerLevel ground = server.getLevel(g.dim());
        if (ground == null) return null;
        FlightManager fm = FlightManager.get(ground);
        long now = ground.getGameTime();
        String ownerName = null;
        ServerPlayer op = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (op != null) ownerName = op.getGameProfile().getName();
        for (Flight f : fm.flights()) {
            Integer t = fm.asatTargets.get(f.id());
            if (t != null && t < 0) {
                Station.Module tm = Station.byId(server, -t - 1);
                if (tm != null && Station.slotOf(server, tm) == slot) return f;
            }
        }
        for (Flight f : fm.flights()) {
            if (f.build() == null || f.build().isInterceptor() || now - f.start() > Balance.LASER_BOOST_TICKS) continue;
            if (owner != null && f.isOwnedBy(owner, ownerName)) continue;
            if (Math.abs(f.ox() - g.x()) > Balance.SENSOR_GROUND_R || Math.abs(f.oz() - g.z()) > Balance.SENSOR_GROUND_R) continue;
            return f;
        }
        return null;
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (ST.isEmpty() || !(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;
        MinecraftServer server = orbit.getServer();
        long now = orbit.getGameTime();
        for (Map.Entry<Integer, St> e : new ArrayList<>(ST.entrySet())) {
            int slot = e.getKey();
            St s = e.getValue();
            Station.Module m = module(server, slot);
            if (m == null || Station.wrecked(server, m)) { abort(orbit, slot, m, s); continue; }
            if ((s.state == CHARGING || s.state == ARMED) && !StationLife.powered(slot)) { abort(orbit, slot, m, s); continue; }
            switch (s.state) {
                case CHARGING -> {
                    long el = now - s.t0;
                    BlockPos mid = Station.middle(m);
                    if (el > 0 && el % (Balance.LASER_CHARGE_TICKS / 12) == 0) orbit.playSound(null, mid, WmdSounds.LZ_DRUM.get(), SoundSource.BLOCKS, 2.5F, 0.9F + el * 0.4F / Balance.LASER_CHARGE_TICKS);
                    if (el > 0 && el % 100 == 0) orbit.playSound(null, mid, WmdSounds.LZ_CHARGE.get(), SoundSource.BLOCKS, 3.0F, 0.9F + el * 0.5F / Balance.LASER_CHARGE_TICKS);
                    if (now >= s.t0 + Balance.LASER_CHARGE_TICKS) set(orbit, slot, m, s, ARMED, now);
                }
                case ARMED -> {
                    if ((now - s.t0) % 60 == 0) orbit.playSound(null, Station.middle(m), WmdSounds.LZ_ARMED_LOOP.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
                    if (now % 10 == 0 && !s.close && auto(server, slot) && SensorArray.working(server, slot)) {
                        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
                        int[] rock = AsteroidDrop.fallingOver(server, slot);
                        if (rock != null) lock(orbit, slot, m, s, T_ROCK, rock[0], groundDir(server, slot, rock[1] + 0.5, rock[2] + 0.5), now);
                        int[] rod = rock != null ? null : RodOfDeath.fallingOver(server, slot);
                        if (rod != null) lock(orbit, slot, m, s, T_ROD, rod[0], groundDir(server, slot, rod[1] + 0.5, rod[2] + 0.5), now);
                        Flight f = rod != null || rock != null ? null : autoTarget(server, slot, owner);
                        if (f != null) {
                            Orbit.Ground g = Orbit.ground(server, slot);
                            ServerLevel ground = server.getLevel(g.dim());
                            Vec3 at = ground == null ? new Vec3(f.ox(), 0, f.oz()) : f.position(ground.getGameTime());
                            lock(orbit, slot, m, s, T_FLIGHT, f.id(), groundDir(server, slot, at.x, at.z), now);
                        }
                    }
                }
                case LOCK -> { if (now >= s.t0 + Balance.LASER_LOCK_TICKS) set(orbit, slot, m, s, FIRE, now); }
                case FIRE -> {
                    if (now == s.t0 + Balance.LASER_HIT_AT - 14) {
                        BlockPos mz = BlockPos.containing(trunnion(m).add(s.dir.scale(TIP)));
                        orbit.playSound(null, mz, WmdSounds.LZ_FIRE.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
                        orbit.playSound(null, mz, WmdSounds.LZ_FIRE_FAR.get(), SoundSource.BLOCKS, 24.0F, 1.0F);
                    }
                    if (now == s.t0 + Balance.LASER_HIT_AT) hit(orbit, slot, m, s);
                    if (now >= s.t0 + Balance.LASER_FIRE_TICKS) {
                        set(orbit, slot, m, s, COOL, s.close ? now - (Balance.LASER_COOL_TICKS - Balance.LASER_CLOSE_COOL_TICKS) : now);
                        orbit.playSound(null, Station.middle(m), WmdSounds.LZ_COOL.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                    }
                }
                case COOL -> { if (now >= s.t0 + Balance.LASER_COOL_TICKS) { set(orbit, slot, m, s, IDLE, now); ST.remove(slot); } }
                default -> ST.remove(slot);
            }
        }
    }

    private static void abort(ServerLevel orbit, int slot, Station.Module m, St s) {
        ST.remove(slot);
        if (m == null) return;
        if (s.state == CHARGING || s.state == ARMED) {
            BlockPos c = Station.middle(m);
            orbit.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5, c.getY() + 3.5, c.getZ() + 0.5, 120, 3.0, 2.0, 3.0, 0.4);
            orbit.playSound(null, c, WmdSounds.LZ_ABORT.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
            for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == slot)
                p.sendSystemMessage(Component.translatable("message.wmd.laser.abort").withStyle(ChatFormatting.RED));
        }
        if (orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setLaser(IDLE, orbit.getGameTime(), 0.0F, 0.0F);
    }

    private static void hit(ServerLevel orbit, int slot, Station.Module m, St s) {
        MinecraftServer server = orbit.getServer();
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        ServerPlayer op = owner == null ? null : server.getPlayerList().getPlayer(owner);
        String ownerName = op != null ? op.getGameProfile().getName() : owner == null ? "?" : CcIntel.name(server, owner);
        Vec3 from = trunnion(m).add(s.dir.scale(TIP));
        Vec3 to = from.add(s.dir.scale(400.0));
        Vec3[] there = null;
        boolean landed = false;
        switch (s.kind) {
            case T_SAT -> {
                OrbitMath.Sat sat = OrbitData.get(server).byId((int) s.target);
                if (sat != null) { Asat.strike(server, sat, true, ownerName, op); landed = true; }
            }
            case T_MODULE -> {
                Station.Module t = Station.byId(server, (int) s.target);
                if (t != null && t.built()) {
                    int ts = Station.slotOf(server, t);
                    Vec3 real = Vec3.atCenterOf(Station.middle(t));
                    to = Rendezvous.inFrame(server, slot, ts, real);
                    if (!to.equals(real)) there = new Vec3[] { Rendezvous.inFrame(server, ts, slot, from), real };
                    int after = StationLife.damage(orbit, ts, t, s.close ? Balance.LASER_CLOSE_DMG : Balance.LASER_DMG);
                    VacuumBurst.send(orbit, real, 4.0);
                    if (there != null) VacuumBurst.send(orbit, to, 4.0);
                    orbit.playSound(null, BlockPos.containing(real), WmdSounds.LZ_HIT.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                    if (there != null) orbit.playSound(null, BlockPos.containing(to), WmdSounds.LZ_HIT.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                    UUID tOwner = Orbit.OrbitSlots.get(server).ownerOf(ts);
                    Component typ = Component.translatable("station.wmd.module." + t.type());
                    if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.hit_module", name(server, t), typ, Math.max(0, after)).withStyle(ChatFormatting.GREEN));
                    for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == ts)
                        p.sendSystemMessage(Component.translatable("message.wmd.laser.hit_by", ownerName, typ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.HIT, "log.wmd.laser.hit_module", name(server, t), "#station.wmd.module." + t.type());
                    if (tOwner != null) CcBattleLog.add(server, tOwner, CcBattleLog.ALARM, "log.wmd.laser.hit_by", ownerName, "#station.wmd.module." + t.type());
                    landed = true;
                }
            }
            case T_FLIGHT -> {
                Orbit.Ground g = Orbit.ground(server, slot);
                ServerLevel ground = server.getLevel(g.dim());
                Flight f = ground == null ? null : FlightManager.get(ground).find(s.target);
                if (f != null) {
                    Vec3 at = f.position(ground.getGameTime());
                    to = from.add(s.dir.normalize().scale(60.0));
                    FlightManager.get(ground).kill(ground, f);
                    PacketDistributor.sendToPlayersNear(ground, null, at.x, at.y, at.z, 1_024.0, new LaserPayloads.Ground(at.x, at.y, at.z, ground.getGameTime()));
                    if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.hit_missile", (int) at.x, (int) at.z).withStyle(ChatFormatting.GREEN));
                    if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.INTERCEPT, "log.wmd.laser.hit_missile", Integer.toString((int) at.x), Integer.toString((int) at.z));
                    landed = true;
                }
            }
            case T_ROCK -> {
                int[] rock = AsteroidDrop.fallingOver(server, slot);
                if (rock != null && rock[0] == (int) s.target && AsteroidDrop.split(server, rock[0], ownerName)) {
                    to = from.add(s.dir.normalize().scale(60.0));
                    if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.hit_rock", rock[1], rock[2]).withStyle(ChatFormatting.GREEN));
                    if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.INTERCEPT, "log.wmd.laser.hit_rock", Integer.toString(rock[1]), Integer.toString(rock[2]));
                    landed = true;
                }
            }
            case T_TUG -> {
                Vec3 rock = AsteroidRedirect.pushingRock(server, (int) s.target, orbit.getGameTime());
                if (rock != null && AsteroidRedirect.hitTug(server, (int) s.target, ownerName)) {
                    Vec3 d = rock.subtract(from);
                    to = d.length() > 380.0 ? from.add(d.normalize().scale(380.0)) : rock;
                    if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.hit_tug", AsteroidRedirect.ownerName(server, (int) s.target)).withStyle(ChatFormatting.GREEN));
                    if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.HIT, "log.wmd.laser.hit_tug", AsteroidRedirect.ownerName(server, (int) s.target));
                    landed = true;
                }
            }
            case T_ROD -> {
                int[] rod = RodOfDeath.fallingOver(server, slot);
                if (rod != null && rod[0] == (int) s.target && RodOfDeath.burn(server, rod[0], ownerName)) {
                    to = from.add(s.dir.normalize().scale(60.0));
                    if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.hit_rod", rod[1], rod[2]).withStyle(ChatFormatting.GREEN));
                    if (owner != null) CcBattleLog.add(server, owner, CcBattleLog.INTERCEPT, "log.wmd.laser.hit_rod", Integer.toString(rod[1]), Integer.toString(rod[2]));
                    landed = true;
                }
            }
            default -> {}
        }
        if (!landed && op != null) op.sendSystemMessage(Component.translatable("message.wmd.laser.missed").withStyle(ChatFormatting.YELLOW));
        LaserPayloads.Beam beam = new LaserPayloads.Beam(from, to, orbit.getGameTime(), landed);
        for (ServerPlayer p : orbit.players())
            if (p.position().distanceTo(from) < 2_048.0 || p.position().distanceTo(to) < 2_048.0) PacketDistributor.sendToPlayer(p, beam);
        if (there != null) {
            LaserPayloads.Beam in = new LaserPayloads.Beam(there[0], there[1], orbit.getGameTime(), true);
            for (ServerPlayer p : orbit.players()) if (Rendezvous.frameOf(p.getX()) == Rendezvous.frameOf(there[1].x)) PacketDistributor.sendToPlayer(p, in);
        }
    }

    static void forget(int slot) { ST.remove(slot); }
}
