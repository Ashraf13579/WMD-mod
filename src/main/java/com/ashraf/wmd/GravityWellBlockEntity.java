package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class GravityWellBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 4, Z0 = -2, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).power(0, 0, Z1, Direction.SOUTH, false);
    public static final int S_OFF = 0, S_SPIN = 1, S_UP = 2, S_DOWN = 3, S_LOCKOUT = 4;

    public static final int ST_NO_OWNER = 0, ST_OFF = 1, ST_NO_POWER = 2, ST_CHARGING = 3, ST_SPINNING = 4, ST_UP = 5, ST_BENDING = 6, ST_DRAGGING = 7, ST_LOCKOUT = 8;

    public static final int ON_BIT = 1;

    static final double CY = 46.0, TALON_IN = 1.8, VENT_RISE = 7.0;
    static final float X_SIGN = -1.0F, Z_SIGN = 1.0F, ROT_SIGN = 1.0F;

    static final int FX_BEND = 0, FX_COLLAPSE = 1, FX_TETHER = 2;

    private final OwnerData ownerData = new OwnerData();
    private int mode = ON_BIT | (((Balance.GWG_R_DEFAULT - Balance.GWG_R_MIN) / Balance.GWG_R_STEP) << 1);

    private record Bend(long id, double fx, double fz, double tx, double tz, int step, long next) {}
    private final Map<Long, Bend> flightBends = new HashMap<>(), shellBends = new HashMap<>();
    private final Set<Long> doneFlights = new HashSet<>(), doneShells = new HashSet<>(), doneShots = new HashSet<>(), doneRockets = new HashSet<>();
    private final Set<UUID> doneBombs = new HashSet<>();
    private final Map<Integer, Integer> droneTicks = new HashMap<>();
    private final List<Vec3> tethers = new ArrayList<>();

    private int state = S_OFF, status = ST_NO_OWNER, bends, dragging;
    private long stateAt, lockUntil, bendAt = Long.MIN_VALUE / 2;
    private float dragYaw;

    private final float[] spin = new float[5];
    private double lastTT = Double.NaN;
    private int lastState = -1, clientTicks;

    public GravityWellBlockEntity(BlockPos pos, BlockState state) {
        super(GravityWellBlocks.GRAVITY_WELL_BE.get(), pos, state, Balance.GWG_IDLE_FE, Balance.GWG_BUFFER_FE, Balance.GWG_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override public int mode() { return mode; }
    @Override public int modeCount() { return 16; }
    @Override public void setMode(int m) { mode = clampMode(m); sync(); }
    @Override public void setModeClient(int m) { mode = clampMode(m); }

    private static int clampMode(int m) {
        int steps = (Balance.GWG_R_MAX - Balance.GWG_R_MIN) / Balance.GWG_R_STEP;
        return (m & ON_BIT) | (Mth.clamp((m >> 1) & 7, 0, steps) << 1);
    }

    public boolean switchedOn() { return (mode & ON_BIT) != 0; }
    public int radiusStep() { return (mode >> 1) & 7; }

    public int radius() { return Balance.GWG_R_MIN + radiusStep() * Balance.GWG_R_STEP; }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int wellState() { return state; }
    public int wellStatus() { return status; }
    public int bendCount() { return bends; }
    public int dragCount() { return dragging; }
    public int lockSecondsLeft() { return level == null ? 0 : (int) Math.max(0L, (lockUntil - level.getGameTime()) / 20L); }
    public int upkeepFe() { return radius() * radius(); }
    public int bufferPermille() { return (int) Math.min(1000L, energy().getEnergyStored() * 1000L / Balance.GWG_BUFFER_FE); }

    public Vec3 centre() {
        double[] w = worldPoint(0.0, CY / 16.0, 0.0);
        return new Vec3(w[0], w[1], w[2]);
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        boolean on = switchedOn() && getOwner() != null;
        int r = radius();
        switch (state) {
            case S_OFF -> {
                if (on && (long) energy().getEnergyStored() * 100L >= (long) Balance.GWG_BUFFER_FE * Balance.GWG_START_PCT) {
                    go(S_SPIN, now);
                    playOnce(sl, WmdSounds.FUSION_CHARGE.get(), Balance.SND_MECH, 0.6F);
                }
            }
            case S_SPIN -> {
                if (!on) { go(S_DOWN, now); break; }
                double k = Math.min(1.0, (now - stateAt) / (double) Balance.GWG_SPINUP);
                if (!useExtraEnergy((int) (r * r * k))) { collapse(sl, now, false); break; }
                if (now - stateAt >= Balance.GWG_SPINUP) go(S_UP, now);
            }
            case S_UP -> {
                if (!on) { go(S_DOWN, now); break; }
                if (!useExtraEnergy(r * r)) { collapse(sl, now, false); break; }
                field(sl, now);
            }
            case S_DOWN -> { if (now - stateAt >= Balance.GWG_SPINDOWN) go(S_OFF, now); }
            case S_LOCKOUT -> { if (now >= lockUntil) go(S_OFF, now); }
            default -> go(S_OFF, now);
        }
        updateStatus(now, false);
        return state == S_SPIN || state == S_UP || state == S_DOWN;
    }

    @Override
    protected void idle() {
        super.idle();
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (state == S_SPIN || state == S_UP) collapse(sl, now, false);
        else if (state == S_DOWN && now - stateAt >= Balance.GWG_SPINDOWN) go(S_OFF, now);
        else if (state == S_LOCKOUT && now >= lockUntil) go(S_OFF, now);
        updateStatus(now, true);
    }

    @Override
    protected void jammed(ServerLevel sl) {
        if (state == S_SPIN || state == S_UP) collapse(sl, sl.getGameTime(), true);
    }

    private void go(int s, long now) {
        state = s;
        stateAt = now;
        if (s != S_UP) { flightBends.clear(); shellBends.clear(); droneTicks.clear(); dragging = 0; }
        sync();
    }

    private void collapse(ServerLevel sl, long now, boolean emp) {
        lockUntil = now + Balance.GWG_LOCKOUT;
        go(S_LOCKOUT, now);
        Vec3 c = centre();
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.GW_COLLAPSE.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        send(sl, FX_COLLAPSE, c, List.of());
        Wmd.LOGGER.info("[gravity well] {} COLLAPSED ({}): no restart for {} s", worldPosition.toShortString(), emp ? "EMP" : "out of FE", Balance.GWG_LOCKOUT / 20);
    }

    private void updateStatus(long now, boolean starved) {
        int st;
        if (getOwner() == null) st = ST_NO_OWNER;
        else if (state == S_LOCKOUT) st = ST_LOCKOUT;
        else if (state == S_SPIN) st = ST_SPINNING;
        else if (state == S_UP) st = now - bendAt < 60 ? ST_BENDING : dragging > 0 ? ST_DRAGGING : ST_UP;
        else if (!switchedOn() || state == S_DOWN) st = ST_OFF;
        else if (starved) st = ST_NO_POWER;
        else st = ST_CHARGING;
        if (st != status) { status = st; sync(); }
    }

    private void field(ServerLevel sl, long now) {
        Vec3 c = centre();
        double R = radius(), R2 = R * R;
        if (!flights(sl, now, c, R, R2)) return;
        if (!shells(sl, now, c, R, R2)) return;
        if (!airShots(sl, now, c, R, R2)) return;
        if (!bombs(sl, now, c, R, R2)) return;
        if (!drones(sl, now, c, R2)) return;
        if (now % 2 == 0 && !craft(sl, now, c, R, R2)) return;
        if (now % 200 == 0) {
            doneFlights.retainAll(flightIds(sl));
            doneShells.retainAll(shellIds(sl));
            if (doneShots.size() > 256) doneShots.clear();
            if (doneRockets.size() > 256) doneRockets.clear();
            if (doneBombs.size() > 256) doneBombs.clear();
        }
    }

    private static Set<Long> flightIds(ServerLevel sl) {
        Set<Long> s = new HashSet<>();
        for (Flight f : FlightManager.get(sl).flights()) s.add(f.id());
        return s;
    }

    private static Set<Long> shellIds(ServerLevel sl) {
        Set<Long> s = new HashSet<>();
        for (ArtShell a : ArtilleryManager.get(sl).shells()) s.add(a.id());
        return s;
    }

    static boolean enters(Vec3 a, Vec3 b, Vec3 c, double r2) { return a.distanceToSqr(c) > r2 && b.distanceToSqr(c) <= r2; }

    static Vec3 pushOut(Vec3 c, double r, double tx, double tz, Vec3 now) {
        Vec3 h = new Vec3(tx - c.x, 0.0, tz - c.z);
        if (h.lengthSqr() < 16.0) h = new Vec3(tx - now.x, 0.0, tz - now.z);
        if (h.lengthSqr() < 1.0E-6) h = new Vec3(1.0, 0.0, 0.0);
        h = h.normalize().scale(r + Balance.GWG_MARGIN);
        return new Vec3(c.x + h.x, c.y, c.z + h.z);
    }

    static boolean landsInside(Vec3 c, double r, double tx, double tz) {
        double m = r + Balance.GWG_MARGIN;
        return (tx - c.x) * (tx - c.x) + (tz - c.z) * (tz - c.z) < m * m;
    }

    private boolean pay(ServerLevel sl, long now, int fe) {
        if (useExtraEnergy(fe)) return true;
        collapse(sl, now, false);
        return false;
    }

    private void bent(ServerLevel sl, long now, Vec3 at, String what) {
        bends++;
        bendAt = now;
        sync();
        send(sl, FX_BEND, centre(), List.of(at));
        Wmd.LOGGER.info("[gravity well] {} bent {} at {} {} {}", worldPosition.toShortString(), what, Math.round(at.x), Math.round(at.y), Math.round(at.z));
    }

    private boolean flights(ServerLevel sl, long now, Vec3 c, double R, double R2) {
        FlightManager fm = FlightManager.get(sl);
        for (Flight f : new ArrayList<>(fm.flights())) {
            if (doneFlights.contains(f.id())) continue;
            MissileBuild b = f.build();
            if (b == null || b.isInterceptor() || Satellites.toOrbit(b) || Asat.isAsat(b) || f.isDeadReckoned()) continue;
            WarheadType wt = WarheadType.of(b.warheadType());
            if (Forcefield.pierces(wt, wt.tier)) continue;
            Vec3 p = f.position(now);
            if (!enters(f.position(now - 1), p, c, R2) || new Vec3(f.ox(), f.oy(), f.oz()).distanceToSqr(c) <= R2) continue;
            doneFlights.add(f.id());
            if (!landsInside(c, R, f.tx() + 0.5, f.tz() + 0.5)) continue;
            int cost = Balance.GWG_BEND_FE * Math.max(1, wt.tier);
            if (energy().getEnergyStored() < cost + feCost()) { collapse(sl, now, false); return false; }
            Vec3 to = pushOut(c, R, f.tx() + 0.5, f.tz() + 0.5, p);
            if (f.isCruise()) {
                if (!bendCruise(sl, fm, f, to.x, to.z, now)) continue;
            } else flightBends.put(f.id(), new Bend(f.id(), f.tx() + 0.5, f.tz() + 0.5, to.x, to.z, 0, now));
            useExtraEnergy(cost);
            bent(sl, now, p, "missile #" + f.id() + " (" + wt.id + " T" + wt.tier + (f.isCruise() ? ", cruise" : "") + ")");
        }
        for (Iterator<Bend> it = flightBends.values().iterator(); it.hasNext();) {
            Bend bd = it.next();
            if (now < bd.next()) continue;
            Flight f = fm.find(bd.id());
            it.remove();
            if (f == null) continue;
            double k = (bd.step() + 1) / (double) Balance.GWG_BEND_STEPS;
            fm.releg(sl, f, (int) Math.floor(Mth.lerp(k, bd.fx(), bd.tx())), (int) Math.floor(Mth.lerp(k, bd.fz(), bd.tz())), 1.0, 0, 0);
            if (bd.step() + 1 < Balance.GWG_BEND_STEPS) flightBends.put(bd.id(), new Bend(bd.id(), bd.fx(), bd.fz(), bd.tx(), bd.tz(), bd.step() + 1, now + Balance.GWG_BEND_GAP));
            break;
        }
        return true;
    }

    static final double CRUISE_AHEAD = 64.0;

    private boolean bendCruise(ServerLevel sl, FlightManager fm, Flight f, double nx, double nz, long now) {
        CruiseRoute old = f.cruise();
        if (old == null || old.dr != null) return false;
        double s = Math.min(old.length(), old.distanceAt(now - f.start()));
        if (old.length() - s < CRUISE_AHEAD + 8.0) return false;
        double[] k = old.ground(s + CRUISE_AHEAD);
        int n = old.xs.length;
        int[] xs = new int[n + 1], zs = new int[n + 1];
        System.arraycopy(old.xs, 0, xs, 0, n - 1);
        System.arraycopy(old.zs, 0, zs, 0, n - 1);
        xs[n - 1] = Mth.floor(k[0]); zs[n - 1] = Mth.floor(k[1]);
        xs[n] = Mth.floor(nx); zs[n] = Mth.floor(nz);
        CruiseRoute r = new CruiseRoute(old.ox, old.oz, old.fx, old.fz, xs, zs, old.speed, old.alt).withAir(old.air);
        double[] g0 = old.ground(s), g1 = r.ground(s);
        if (Math.hypot(g0[0] - g1[0], g0[1] - g1[1]) > 1.5) return false;
        r = r.withAlt(r.sampleAltitude(sl));
        int dur = r.ticks();
        if (f.start() + dur <= now + 5) return false;
        Flight nf = f.withCruiseEnd(xs[n], zs[n], r, dur, CruiseRoute.groundAt(sl, xs[n], zs[n]));
        fm.flights().replaceAll(q -> q.id() == f.id() ? nf : q);
        fm.setDirty();
        fm.syncSoon();
        return true;
    }

    private boolean shells(ServerLevel sl, long now, Vec3 c, double R, double R2) {
        ArtilleryManager am = ArtilleryManager.get(sl);
        for (ArtShell s : new ArrayList<>(am.shells())) {
            if (doneShells.contains(s.id()) || now <= s.start() || now >= s.endTime()) continue;
            Vec3 p = s.position(now);
            if (!enters(s.position(now - 1), p, c, R2) || new Vec3(s.ox(), s.oy(), s.oz()).distanceToSqr(c) <= R2) continue;
            doneShells.add(s.id());
            if (!landsInside(c, R, s.tx(), s.tz())) continue;
            if (!pay(sl, now, Balance.GWG_SHELL_FE)) return false;
            Vec3 to = pushOut(c, R, s.tx(), s.tz(), p);
            shellBends.put(s.id(), new Bend(s.id(), s.tx(), s.tz(), to.x, to.z, 0, now));
            bent(sl, now, p, "shell #" + s.id());
        }
        for (Iterator<Bend> it = shellBends.values().iterator(); it.hasNext();) {
            Bend bd = it.next();
            if (now < bd.next()) continue;
            it.remove();
            double k = (bd.step() + 1) / (double) Balance.GWG_BEND_STEPS;
            double nx = Mth.lerp(k, bd.fx(), bd.tx()), nz = Mth.lerp(k, bd.fz(), bd.tz());
            if (am.gravityBend(sl, bd.id(), nx, nz, now) && bd.step() + 1 < Balance.GWG_BEND_STEPS)
                shellBends.put(bd.id(), new Bend(bd.id(), bd.fx(), bd.fz(), bd.tx(), bd.tz(), bd.step() + 1, now + Balance.GWG_BEND_GAP));
            break;
        }
        return true;
    }

    private boolean airShots(ServerLevel sl, long now, Vec3 c, double R, double R2) {
        if (!AirGate.on()) return true;
        for (AirMissiles.Shot s : AirMissiles.inFlight(sl)) {
            if (s.end != null || doneShots.contains(s.id()) || !enters(s.pos.subtract(s.vel), s.pos, c, R2)) continue;
            doneShots.add(s.id());
            if (!pay(sl, now, Balance.GWG_BEND_FE)) return false;
            Vec3 ahead = s.pos.add(s.vel.scale(20.0));
            Vec3 to = pushOut(c, R, ahead.x, ahead.z, s.pos);
            s.decoy = new Vec3(to.x, s.pos.y, to.z);
            s.decoyAt = now;
            s.lockLost = true;
            bent(sl, now, s.pos, "air missile #" + s.id());
        }
        for (AirRockets.Shot s : AirRockets.inFlight(sl)) {
            if (s.ended || doneRockets.contains(s.id) || !enters(s.pos.subtract(s.vel), s.pos, c, R2)) continue;
            doneRockets.add(s.id);
            if (!pay(sl, now, Balance.GWG_SHELL_FE)) return false;
            double sp = s.vel.length();
            Vec3 out = new Vec3(s.pos.x - c.x, 0.0, s.pos.z - c.z);
            out = out.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
            Vec3 nv = s.vel.normalize().add(out.scale(1.2));
            s.vel = nv.lengthSqr() < 1.0E-6 ? s.vel : nv.normalize().scale(sp);
            bent(sl, now, s.pos, "rocket #" + s.id);
        }
        return true;
    }

    private boolean bombs(ServerLevel sl, long now, Vec3 c, double R, double R2) {
        for (BombEntity b : BombEntity.FALLING) {
            if (b.level() != sl || b.isRemoved()) continue;
            Vec3 p = b.position();
            if (p.distanceToSqr(c) > R2 || b.gravityDrop() == null || b.gravityDrop().distanceToSqr(c) <= R2) continue;
            if (doneBombs.add(b.getUUID())) {
                if (!landsInside(c, R, p.x, p.z)) continue;
                if (!pay(sl, now, Balance.GWG_SHELL_FE)) return false;
                bent(sl, now, p, "bomb");
            }
            Vec3 out = new Vec3(p.x - c.x, 0.0, p.z - c.z);
            out = out.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
            b.gravityPush(out.scale(Balance.GWG_BOMB_PUSH));
        }
        return true;
    }

    private boolean drones(ServerLevel sl, long now, Vec3 c, double R2) {
        if (sl.getServer() == null) return true;
        Drones dr = Drones.get(sl.getServer());
        Set<Integer> seen = new HashSet<>();
        for (Drones.Drone d : dr.drones()) {
            if (!d.airborne() || d.pos().distanceToSqr(c) > R2 || spared(d.owner())) continue;
            seen.add(d.id());
            int t = droneTicks.merge(d.id(), 1, Integer::sum);
            if (t < Balance.GWG_DRONE_TICKS) continue;
            droneTicks.remove(d.id());
            if (!pay(sl, now, Balance.GWG_SHELL_FE)) return false;
            dr.shootDown(sl, d, getOwner(), getOwnerName());
            bent(sl, now, d.pos(), "drone #" + d.id());
        }
        droneTicks.keySet().retainAll(seen);
        return true;
    }

    private boolean spared(UUID who) {
        return who != null && (who.equals(getOwner()) || security().allows(who) || Access.listedFriend(this, who));
    }

    private boolean craft(ServerLevel sl, long now, Vec3 c, double R, double R2) {
        int n = 0;
        tethers.clear();
        double best = Double.MAX_VALUE;
        if (SableBridge.active()) {
            UUID own = SableBridge.shipIdAt(sl, worldPosition);
            for (SableBridge.ShipInfo si : SableBridge.shipsIntersecting(sl, new AABB(c, c).inflate(R))) {
                Vec3 sc = si.worldCenter();
                if (si.id().equals(own) || sc == null || sc.distanceToSqr(c) > R2 || spared(Crafts.owner(sl, si.id()))) continue;
                if ((AirGate.on() && AirPicture.isWreck(si.id())) || SableBridge.debrisRemnant(sl, si.id())) continue;
                int gx = Mth.floor(sc.x), gz = Mth.floor(sc.z);
                if (!sl.hasChunk(gx >> 4, gz >> 4)) continue;
                double bottom = si.worldBounds() == null ? sc.y : si.worldBounds().minY;
                if (bottom < sl.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz) + Balance.GWG_AIRBORNE) continue;
                if (!pay(sl, now, Balance.GWG_CRAFT_FE * 2)) return false;
                Vec3 v = ShipDefence.velocity(sl, si).scale(20.0);
                double dt = 0.1, bleed = v.length() > Balance.GWG_SLOW ? Balance.GWG_BLEED_FAST : Balance.GWG_BLEED_SLOW;
                Vec3 dv = new Vec3(0.0, -Balance.GWG_DRAG_G * 9.81 * dt, 0.0).subtract(v.scale(bleed * dt));
                SableBridge.shipPush(sl, si.id(), dv, Vec3.ZERO);
                n++;
                if (!Cloak.hidden(sl, si.id())) {
                    if (tethers.size() < 6) tethers.add(sc);
                    double d = sc.distanceToSqr(c);
                    if (d < best) { best = d; dragYaw = localYaw(sc.subtract(c)); }
                }
            }
        }
        if (n != dragging || n > 0 && now % 20 == 0) { dragging = n; sync(); }
        if (n > 0 && now % 10 == 0) send(sl, FX_TETHER, c, new ArrayList<>(tethers));
        return true;
    }

    private float localYaw(Vec3 d) {
        double lx, lz;
        switch (facing()) {
            case EAST -> { lx = d.z; lz = -d.x; }
            case SOUTH -> { lx = -d.x; lz = -d.z; }
            case WEST -> { lx = -d.z; lz = d.x; }
            default -> { lx = d.x; lz = d.z; }
        }
        return (float) Math.toDegrees(Math.atan2(-lz, lx));
    }

    private void send(ServerLevel sl, int kind, Vec3 c, List<Vec3> pts) {
        GravityWellPayload p = new GravityWellPayload(kind, worldPosition, c, radius(), pts.size() > 8 ? pts.subList(0, 8) : pts);
        double range = Balance.GWG_FX_RANGE + radius();
        for (ServerPlayer pl : sl.players()) if (pl.position().distanceToSqr(c) <= range * range) PacketDistributor.sendToPlayer(pl, p);
    }

    static void clientTickWell(Level level, BlockPos pos, BlockState state, GravityWellBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickClient(level);
    }

    private void tickClient(Level level) {
        long now = level.getGameTime();
        if (clientTicks++ > 40 && lastState != state && state == S_UP) playOnce(level, WmdSounds.FUSION_CHARGE.get(), Balance.SND_MECH, 1.3F);
        lastState = state;

        if (state == S_SPIN && now - stateAt >= 240 || state == S_UP)
            ClientGravityWell.field(worldPosition, centre(), radius(), state == S_UP ? 1.0F : (float) Mth.clamp((now - stateAt - 240.0) / 60.0, 0.05, 1.0), now);
        if (state == S_LOCKOUT && now - stateAt < 200 && now % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double[] w = worldPoint(VENTS[i][0] / 16.0, 32.0 / 16.0, VENTS[i][1] / 16.0);
                level.addParticle(ParticleTypes.CLOUD, w[0] + (level.random.nextDouble() - 0.5) * 0.4, w[1], w[2] + (level.random.nextDouble() - 0.5) * 0.4,
                        (level.random.nextDouble() - 0.5) * 0.02, 0.08 + level.random.nextDouble() * 0.04, (level.random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    static final double[][] VENTS = { { -28.0, -28.0 }, { 28.0, -28.0 }, { -28.0, 28.0 }, { 28.0, 28.0 } };

    private float spinShare(double t) { return (float) Mth.clamp((t - stateAt - 40.0) / 200.0, 0.0, 1.0); }

    private double speedShare(double tt) {
        double since = tt - stateAt;
        double v = switch (state) {
            case S_SPIN -> ease(spinShare(tt));
            case S_UP -> 1.0;
            case S_DOWN -> 1.0 - Mth.clamp(since / Balance.GWG_SPINDOWN, 0.0, 1.0);
            case S_LOCKOUT -> Math.max(0.0, 1.0 - since / Balance.GWG_COAST);
            default -> 0.0;
        };
        double sb = tt - bendAt;
        if (state == S_UP && sb >= 0.0 && sb < Balance.GWG_SURGE_TICKS) v += Balance.GWG_SURGE * Math.sin(Math.PI * sb / Balance.GWG_SURGE_TICKS);
        return v;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double dt = Double.isNaN(lastTT) ? 0.0 : Mth.clamp(tt - lastTT, 0.0, 5.0);
        lastTT = tt;
        double v = speedShare(tt), since = tt - stateAt, sb = tt - bendAt;
        float[] rate = { Balance.GWG_SPIN_O, -Balance.GWG_SPIN_M, Balance.GWG_SPIN_I, Balance.GWG_SPIN_CORE, -0.5F * Balance.GWG_SPIN_O };
        for (int i = 0; i < 5; i++) spin[i] = (float) ((spin[i] + rate[i] * v * dt) % 360.0);
        final float o = rad(spin[0]), m = rad(spin[1]), in = rad(spin[2]), c = rad(spin[3]), cr = rad(spin[4]);
        model.getBone("ring_o").ifPresent(b -> b.setRotY(o));
        model.getBone("ring_m").ifPresent(b -> b.setRotX(m));
        model.getBone("ring_i").ifPresent(b -> b.setRotZ(in));
        model.getBone("core").ifPresent(b -> b.setRotY(c));
        model.getBone("crown").ifPresent(b -> b.setRotY(cr));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean live = state == S_SPIN || state == S_UP, fading = state == S_DOWN && since < 40.0;

        final boolean gHalo = !dark && (live || fading);
        final boolean gO = gHalo && (state != S_SPIN || since >= 80.0), gM = gHalo && (state != S_SPIN || since >= 140.0),
                gI = gHalo && (state != S_SPIN || since >= 200.0), gCore = gHalo && (state != S_SPIN || since >= 240.0);
        model.getBone("glow").ifPresent(b -> b.setHidden(!gHalo));
        model.getBone("ro_glow").ifPresent(b -> b.setHidden(!gO));
        model.getBone("rm_glow").ifPresent(b -> b.setHidden(!gM));
        model.getBone("ri_glow").ifPresent(b -> b.setHidden(!gI));
        final float flash = (float) (state == S_UP && sb >= 0.0 && sb < Balance.GWG_SURGE_TICKS ? 1.0 + 0.3 * Math.sin(Math.PI * sb / Balance.GWG_SURGE_TICKS) : 1.0);
        model.getBone("coreglow").ifPresent(b -> { b.setHidden(!gCore); b.setScaleX(flash); b.setScaleY(flash); b.setScaleZ(flash); });

        double tin = switch (state) {
            case S_SPIN -> Mth.clamp(since / 40.0, 0.0, 1.0);
            case S_UP -> 1.0;
            case S_DOWN -> 1.0 - Mth.clamp(since / 40.0, 0.0, 1.0);
            default -> state == S_LOCKOUT ? 1.0 - Mth.clamp(since / 5.0, 0.0, 1.0) : 0.0;
        };
        final double out = TALON_IN * (1.0 - ease(tin));
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45.0 * k);
            final float px = X_SIGN * (float) (Math.cos(a) * out), pz = Z_SIGN * (float) (-Math.sin(a) * out);
            model.getBone("talon" + k).ifPresent(b -> { b.setPosX(px); b.setPosZ(pz); });
            double off = Math.abs(Mth.wrapDegrees(22.5 + 45.0 * k - dragYaw));
            final float flare = dragging > 0 && state == S_UP && off < 50.0 ? 1.6F + 0.3F * (float) Math.sin(tt * 0.6) : 1.0F;
            final boolean tg = gHalo && tin > 0.5;
            model.getBone("talonglow" + k).ifPresent(b -> { b.setHidden(!tg); b.setScaleX(flare); b.setScaleY(flare); b.setScaleZ(flare); });
        }

        double load = switch (state) {
            case S_SPIN -> 0.25 * ease(spinShare(tt));
            case S_UP -> 0.25 + (dragging > 0 ? 0.45 : 0.0) + 0.08 * Math.sin(tt * 0.105);
            case S_DOWN -> 0.25 * (1.0 - Mth.clamp(since / 100.0, 0.0, 1.0));
            case S_LOCKOUT -> since < 200.0 ? Mth.clamp(since / 10.0, 0.0, 1.0) : 1.0 - Mth.clamp((since - 200.0) / 60.0, 0.0, 1.0);
            default -> 0.0;
        };
        if (state == S_UP && sb >= 0.0 && sb < 80.0) load = Math.max(load, sb < 40.0 ? Mth.clamp(sb / 4.0, 0.0, 1.0) : 1.0 - (sb - 40.0) / 40.0);
        for (int k = 0; k < 4; k++) {
            final double lk = Mth.clamp(load * (0.92 + 0.08 * Math.sin(tt * 0.3 + k * 1.3)), 0.0, 1.0);
            final float rise = (float) (VENT_RISE * ease(lk));
            final boolean hot = !dark && lk > 0.15;
            model.getBone("vent" + k).ifPresent(b -> b.setPosY(rise));
            model.getBone("ventglow" + k).ifPresent(b -> b.setHidden(!hot));
        }

        final boolean bOn = !dark && (live && ((long) tt) % 20 < 10 || state == S_LOCKOUT && ((long) tt) % 6 < 3);
        model.getBone("beacon_l").ifPresent(b -> b.setHidden(!bOn));
        model.getBone("beacon_r").ifPresent(b -> b.setHidden(!bOn));
    }

    private static float rad(float deg) { return ROT_SIGN * (float) Math.toRadians(deg); }

    private static double ease(double t) { t = Mth.clamp(t, 0.0, 1.0); return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0; }

    @Override protected SoundEvent runSound() { return WmdSounds.GW_HUM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 2, 0); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }
    @Override protected float soundPitch() {
        double tt = level == null ? 0.0 : level.getGameTime();
        return (float) (0.75 + 0.2 * Mth.clamp(speedShare(tt), 0.0, 1.4));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float progress01() { return state == S_SPIN && level != null ? (float) Mth.clamp((level.getGameTime() - stateAt) / (double) Balance.GWG_SPINUP, 0.0, 1.0) : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gravity_well", radius(), String.format("%,d", upkeepFe()), Balance.GWG_BUFFER_FE / 1_000_000),
                Component.translatable("gui.wmd.info.gravity_well_bend", String.format("%,d", Balance.GWG_BEND_FE / 1_000), String.format("%,d", Balance.GWG_SHELL_FE / 1_000),
                        String.format("%,d", Balance.GWG_CRAFT_FE)));
    }

    @Override
    protected String detail() {
        return "state " + state + ", radius " + radius() + ", on " + switchedOn() + ", bends " + bends + ", dragging " + dragging + ", buffer " + bufferPermille() / 10 + " %";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putInt("gMode", mode);
        tag.putInt("gState", state);
        tag.putLong("gStateAt", stateAt);
        tag.putLong("gLock", lockUntil);
        tag.putLong("gBendAt", bendAt);
        tag.putInt("gBends", bends);
        tag.putInt("gDrag", dragging);
        tag.putFloat("gDragYaw", dragYaw);
        tag.putInt("gSt", status);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("gMode")) mode = clampMode(tag.getInt("gMode"));
        state = Mth.clamp(tag.getInt("gState"), S_OFF, S_LOCKOUT);
        stateAt = tag.getLong("gStateAt");
        lockUntil = tag.getLong("gLock");
        if (tag.contains("gBendAt")) bendAt = tag.getLong("gBendAt");
        bends = tag.getInt("gBends");
        dragging = tag.getInt("gDrag");
        dragYaw = tag.getFloat("gDragYaw");
        status = tag.getInt("gSt");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        put(tag);
        return tag;
    }
}
