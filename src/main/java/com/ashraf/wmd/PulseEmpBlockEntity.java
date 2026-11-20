package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class PulseEmpBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, RadarLinked, CopycatState.Host {

    public static final int X0 = -3, X1 = 3, Y0 = 0, Y1 = 4, Z0 = -3, Z1 = 3;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).power(0, 0, Z1, Direction.SOUTH, false);
    public static final int S_COLD = 0, S_CHARGE = 1, S_READY = 2, S_BUILDUP = 3, S_COOL = 4;

    public static final int ST_NO_OWNER = 0, ST_NO_POWER = 1, ST_CHARGING = 2, ST_READY = 3, ST_NO_RADAR = 4, ST_TARGET = 5, ST_FIRING = 6, ST_COOLING = 7;

    public static final int R_ANY_JET = 0, R_CONTACTS = 1, R_SALVO = 2, R_MANUAL = 3, FIRE_BIT = 4;

    static final double PIV_Y = 49.0, PIV_Z = 4.0, FOOT_Y = 23.2, FOOT_Z = -20.0, LUG_Y = 34.4, LUG_Z = -8.0, TIP_Z = -55.0, BANK_RISE = 14.0;
    static final double[][] RINGS = { { -36.0, 17.0 }, { -43.0, 21.0 }, { -50.0, 25.0 } };
    static final float EL_SIGN = 1.0F, YAW_SIGN = 1.0F, FLAP_SIGN = 1.0F;
    static final float ROD_REST = (float) ram(0.0)[1];

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int frequency = 1, mode;

    private long charge;
    private boolean sig, fireAsked;
    private final List<Vec3> lastHits = new ArrayList<>();

    private int state = S_COLD, status = ST_NO_OWNER, chargePm, contacts, inCone;
    private long stateAt, coolUntil, fireAt = Long.MIN_VALUE / 2;
    private float az, el, tAz, tEl;

    private float prevAz, prevEl;
    private boolean placedClient;
    private final float[] spin = new float[3];
    private double lastTT = Double.NaN;
    private long fxDone = Long.MIN_VALUE / 2;
    private int lastState = -1, clientTicks;

    public PulseEmpBlockEntity(BlockPos pos, BlockState state) {
        super(PulseEmpBlocks.PULSE_EMP_BE.get(), pos, state, Balance.PEMP_IDLE_FE, Balance.PEMP_BUFFER_FE, Balance.PEMP_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override public int mode() { return mode & 3; }
    @Override public int modeCount() { return 8; }
    @Override public void setMode(int m) {
        if ((m & FIRE_BIT) != 0 && state == S_READY) fireAsked = true;
        mode = m & 3;
        sync();
    }
    @Override public void setModeClient(int m) { mode = m & 3; }
    public int rule() { return mode & 3; }

    @Override public int getFrequency() { return frequency; }
    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); sync(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        net.minecraft.world.item.ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    public boolean remoteFire() {
        if (state != S_READY) return false;
        fireAsked = true;
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int empState() { return state; }
    public int empStatus() { return status; }
    public int chargePermille() { return chargePm; }
    public int contacts() { return contacts; }
    public int inCone() { return inCone; }
    public int coolSecondsLeft() { return level == null ? 0 : (int) Math.max(0L, (coolUntil - level.getGameTime()) / 20L); }

    @Override
    protected boolean work() {
        if (!AirGate.on() || !(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        boolean pulse = sl.hasNeighborSignal(worldPosition);
        if (pulse && !sig && state == S_READY) fireAsked = true;
        sig = pulse;
        switch (state) {
            case S_COLD -> { if (getOwner() != null) go(S_CHARGE, now); }
            case S_CHARGE -> {
                if (useExtraEnergy(Balance.PEMP_FE_PER_TICK)) {
                    charge += Balance.PEMP_FE_PER_TICK;
                    if (charge >= Balance.PEMP_CHARGE_FE) { charge = Balance.PEMP_CHARGE_FE; go(S_READY, now); }
                }
            }
            case S_READY -> { if (!useExtraEnergy(Balance.PEMP_HOLD_FE)) { charge = Math.max(0L, charge - Balance.PEMP_FE_PER_TICK); if (charge < Balance.PEMP_CHARGE_FE) go(S_CHARGE, now); } }
            case S_BUILDUP -> { if (now - stateAt >= Balance.PEMP_BUILDUP) pulse(sl, now); }
            case S_COOL -> { if (now >= coolUntil) { charge = 0; go(S_COLD, now); } }
            default -> state = S_COLD;
        }
        if ((now + (worldPosition.asLong() & 7L)) % Balance.PEMP_SCAN_TICKS == 0) scan(sl, now);
        if (state == S_READY && fireAsked && aligned() && getOwner() != null) {
            fireAsked = false;
            go(S_BUILDUP, now);
            playOnce(sl, WmdSounds.FUSION_COIL.get(), Balance.SND_RELEASE, 1.3F);
        }
        int pm = (int) Math.min(1000L, charge * 1000L / Balance.PEMP_CHARGE_FE);
        if (pm / 10 != chargePm / 10) { chargePm = pm; sync(); }
        move();
        return true;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level == null || level.isClientSide) return;
        if (status != ST_NO_POWER && AirGate.on() && state != S_COOL) { status = ST_NO_POWER; sync(); }
        if (state == S_COOL && level.getGameTime() >= coolUntil) { charge = 0; go(S_COLD, level.getGameTime()); }
        if (state == S_BUILDUP && level instanceof ServerLevel sl && level.getGameTime() - stateAt >= Balance.PEMP_BUILDUP) pulse(sl, level.getGameTime());
    }

    private void go(int s, long now) {
        state = s;
        stateAt = now;
        sync();
    }

    private boolean aligned() {
        return Math.abs(Mth.wrapDegrees(az - tAz)) < Balance.PEMP_AIM_TOL && Math.abs(el - tEl) < Balance.PEMP_AIM_TOL && el >= Balance.PEMP_EL_MIN - 0.5;
    }

    private record Cand(Vec3 pos, int kind) {
        int weight() { return kind == 3 ? 4 : kind == 2 ? 3 : kind == 1 ? 2 : 1; }
    }

    private void scan(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        UUID owner = getOwner();
        Vec3 me = tipWorld(el, az);
        List<Cand> cands = new ArrayList<>();
        boolean radar = owner != null && TrackNet.airRadarUp(sl, owner, frequency, now);
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        if (owner != null) {
            for (UUID id : TrackNet.aircraftTracks(sl, owner, frequency, now)) {
                if (id.equals(own) || AirPicture.isWreck(id)) continue;
                AirPicture.Aircraft a = AirPicture.get(sl, id);
                if (a != null) cands.add(new Cand(a.pos(), 2));
            }
            for (UUID id : TrackNet.shipTracks(sl, owner, frequency, now)) {
                if (id.equals(own)) continue;
                SableBridge.ShipInfo si = SableBridge.shipInfo(sl, id);
                if (si != null && si.worldCenter() != null) cands.add(new Cand(si.worldCenter(), 3));
            }
            FlightManager fm = FlightManager.get(sl);
            for (long fid : TrackNet.tracks(sl, owner, frequency, now)) {
                Flight f = fm.find(fid);
                if (f != null) cands.add(new Cand(f.position(now), 1));
            }
            if (sl.getServer() != null)
                for (Drones.Drone d : Drones.get(sl.getServer()).drones())
                    if (d.airborne() && !owner.equals(d.owner()) && d.pos().distanceTo(me) <= Balance.PEMP_RANGE) cands.add(new Cand(d.pos(), 0));
        }
        cands.removeIf(c -> c.pos().distanceTo(me) > Balance.PEMP_RANGE || AirSearchRadarBlockEntity.masked(sl, me, c.pos()));

        Cand best = null;
        int bestScore = 0, bestCount = 0, bestJets = 0, bestMissiles = 0;
        double cos = Math.cos(Math.toRadians(Balance.PEMP_HALF_ANGLE));
        for (Cand c : cands) {
            Vec3 axis = c.pos().subtract(me).normalize();
            int score = 0, n = 0, jets = 0, ms = 0;
            for (Cand o : cands) {
                Vec3 d = o.pos().subtract(me);
                if (d.lengthSqr() < 1.0E-6 || d.normalize().dot(axis) < cos) continue;
                score += o.weight(); n++;
                if (o.kind() >= 2) jets++;
                if (o.kind() == 1) ms++;
            }
            if (score > bestScore || (score == bestScore && best != null && c.pos().distanceTo(me) < best.pos().distanceTo(me))) {
                best = c; bestScore = score; bestCount = n; bestJets = jets; bestMissiles = ms;
            }
        }
        boolean armedTurret = state == S_CHARGE || state == S_READY || state == S_BUILDUP;
        if (best != null && armedTurret) {
            Vec3 d = best.pos().subtract(me);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            float brg = (float) Math.toDegrees(Math.atan2(d.x, -d.z));
            tAz = Mth.wrapDegrees(brg - frontYaw());
            tEl = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, h)), Balance.PEMP_EL_MIN, Balance.PEMP_EL_MAX);
        } else if (armedTurret) {
            tEl = (float) Balance.PEMP_EL_MIN;
        } else if (state == S_COLD || (state == S_COOL && now - stateAt > 200)) {
            tAz = 0.0F;
            tEl = 0.0F;
        }

        if (state == S_READY && best != null && !fireAsked) {
            boolean go = switch (rule()) {
                case R_ANY_JET -> bestJets > 0;
                case R_CONTACTS -> bestCount >= Balance.PEMP_AUTO_CONTACTS;
                case R_SALVO -> bestMissiles >= Balance.PEMP_AUTO_SALVO;
                default -> false;
            };
            if (go) fireAsked = true;
        }
        int st;
        if (owner == null) st = ST_NO_OWNER;
        else if (state == S_COOL) st = ST_COOLING;
        else if (state == S_BUILDUP) st = ST_FIRING;
        else if (state == S_CHARGE || state == S_COLD) st = ST_CHARGING;
        else if (!radar) st = ST_NO_RADAR;
        else st = best != null ? ST_TARGET : ST_READY;
        if (st != status || cands.size() != contacts || bestCount != inCone || Math.abs(Mth.wrapDegrees(tAz - shownTAz)) > 0.5F || Math.abs(tEl - shownTEl) > 0.5F) {
            status = st;
            contacts = cands.size();
            inCone = bestCount;
            shownTAz = tAz;
            shownTEl = tEl;
            sync();
        }
    }

    private float shownTAz, shownTEl;

    private void pulse(ServerLevel sl, long now) {
        Vec3 from = tipWorld(el, az), axis = axisWorld(el, az);
        double r = Balance.PEMP_RANGE, cos = Math.cos(Math.toRadians(Balance.PEMP_HALF_ANGLE));
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        List<Vec3> hits = new ArrayList<>();
        int crafts = 0, missiles = 0, drones = 0;
        if (AirGate.on()) {
            for (SableBridge.ShipInfo si : SableBridge.shipsIntersecting(sl, new AABB(from, from).inflate(r))) {
                if (si.id().equals(own) || si.worldCenter() == null) continue;
                if (AirPicture.isWreck(si.id()) || SableBridge.debrisRemnant(sl, si.id())) continue;
                Vec3 c = si.worldCenter();
                if (!inCone(from, axis, c, r, cos) || blocked(sl, from, c)) continue;
                if (CraftShield.shieldCraft(sl, si.id(), from, Balance.CSH_EMP_FE, "the pulse EMP")) continue;
                EmpBlackout.hit(sl, si.id(), now + Balance.PEMP_BLACKOUT);
                hits.add(c);
                crafts++;
                AABB box = si.worldBounds() == null ? new AABB(c, c).inflate(4.0) : si.worldBounds().inflate(2.0);
                for (ServerPlayer p : sl.players())
                    if (box.contains(p.position())) p.displayClientMessage(Component.translatable("message.wmd.pulse_emp.blackout", Balance.PEMP_BLACKOUT / 20)
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            for (AirMissiles.Shot s : AirMissiles.inFlight(sl)) {
                if (s.end != null || !inCone(from, axis, s.pos, r, cos) || blocked(sl, from, s.pos)) continue;
                s.decoy = s.pos.add(s.vel.x * 6.0, -60.0, s.vel.z * 6.0);
                s.decoyAt = now;
                s.lockLost = true;
                hits.add(s.pos);
                missiles++;
            }
        }
        FlightManager fm = FlightManager.get(sl);
        for (Flight f : new ArrayList<>(fm.flights())) {
            Vec3 p = f.position(now);
            if (!inCone(from, axis, p, r, cos) || blocked(sl, from, p)) continue;
            fm.kill(sl, f);
            hits.add(p);
            missiles++;
        }
        if (sl.getServer() != null) {
            Drones dr = Drones.get(sl.getServer());
            for (Drones.Drone d : dr.drones()) {
                if (!d.airborne() || !inCone(from, axis, d.pos(), r, cos) || blocked(sl, from, d.pos())) continue;
                dr.shootDown(sl, d, getOwner(), getOwnerName());
                hits.add(d.pos());
                drones++;
            }
        }
        for (ServerPlayer p : sl.players()) {
            Vec3 c = p.position().add(0.0, 1.0, 0.0);
            if (!ExoSuitItem.anyPiece(p) || !inCone(from, axis, c, r, cos) || blocked(sl, from, c)) continue;
            ExoSuit.empHit(p, now);
            hits.add(c);
        }
        for (ServerPlayer p : sl.players()) {
            Vec3 c = p.position().add(0.0, 1.0, 0.0);
            if (!inCone(from, axis, c, r, cos) || blocked(sl, from, c)) continue;
            HandTech.empDrain(p);
        }
        lastHits.clear();
        lastHits.addAll(hits);
        fireAt = now;
        coolUntil = now + Balance.PEMP_COOLDOWN;
        charge = 0;
        go(S_COOL, now);
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.EMP_PULSE.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        PulseEmpPayload pay = new PulseEmpPayload(from, axis, hits.size() > 24 ? hits.subList(0, 24) : hits);
        for (ServerPlayer p : sl.players()) if (p.position().distanceTo(from) <= Balance.PEMP_RANGE + 200.0) PacketDistributor.sendToPlayer(p, pay);
        Wmd.LOGGER.info("Pulse EMP at {} fired: {} craft blacked out, {} missiles, {} drones", worldPosition.toShortString(), crafts, missiles, drones);
    }

    private static boolean inCone(Vec3 from, Vec3 axis, Vec3 p, double r, double cos) {
        Vec3 d = p.subtract(from);
        double l = d.length();
        return l > 0.5 && l <= r && d.scale(1.0 / l).dot(axis) >= cos;
    }

    private static boolean blocked(ServerLevel sl, Vec3 from, Vec3 to) {
        return AirSearchRadarBlockEntity.masked(sl, from, to) || Forcefield.crossing(sl, from, to) != null || Forcefield.shelters(sl, to, from);
    }

    private boolean move() {
        float dA = Mth.wrapDegrees(tAz - az), dE = tEl - el;
        boolean moving = Math.abs(dA) > 0.05F || Math.abs(dE) > 0.05F;
        az = Mth.wrapDegrees(az + SamSiteBlockEntity.step(dA, Balance.PEMP_SLEW_DEG));
        el = Mth.clamp(el + SamSiteBlockEntity.step(dE, Balance.PEMP_RAISE_DEG), 0.0F, (float) Balance.PEMP_EL_MAX);
        return moving;
    }

    private float frontYaw() { return (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F; }

    static double[] ram(double e) {
        double t = Math.toRadians(e), dy = LUG_Y - PIV_Y, dz = LUG_Z - PIV_Z;
        double ly = PIV_Y + dy * Math.cos(t) - dz * Math.sin(t), lz = PIV_Z + dy * Math.sin(t) + dz * Math.cos(t);
        double vy = ly - FOOT_Y, vz = lz - FOOT_Z;
        return new double[] { Math.toDegrees(Math.atan2(vz, vy)), Math.hypot(vy, vz) };
    }

    static double[] local(double x, double y, double z, double e, double a) {
        double t = Math.toRadians(e), dy = y - PIV_Y, dz = z - PIV_Z;
        double y1 = PIV_Y + dy * Math.cos(t) - dz * Math.sin(t), z1 = PIV_Z + dy * Math.sin(t) + dz * Math.cos(t);
        double r = Math.toRadians(a);
        double x2 = x * Math.cos(r) - z1 * Math.sin(r), z2 = x * Math.sin(r) + z1 * Math.cos(r);
        return new double[] { x2 / 16.0, y1 / 16.0, z2 / 16.0 };
    }

    private Vec3 world(double x, double y, double z, double e, double a) {
        double[] l = local(x, y, z, e, a);
        double[] w = worldPoint(l[0], l[1], l[2]);
        return new Vec3(w[0], w[1], w[2]);
    }

    Vec3 tipWorld(double e, double a) { return world(0.0, PIV_Y, TIP_Z, e, a); }

    Vec3 axisWorld(double e, double a) { return tipWorld(e, a).subtract(world(0.0, PIV_Y, 0.0, e, a)).normalize(); }

    Vec3 ringWorld(int i, double e, double a) { return world(0.0, PIV_Y, RINGS[i][0], e, a); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }

    static void clientTickEmp(Level level, BlockPos pos, BlockState state, PulseEmpBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickClient(level);
    }

    private void tickClient(Level level) {
        prevAz = az;
        prevEl = el;
        if (!placedClient) { placedClient = true; prevAz = az; prevEl = el; lastState = state; fxDone = fireAt; }
        move();
        long now = level.getGameTime();
        if (clientTicks++ > 40 && lastState != state) {
            if (state == S_READY) playOnce(level, WmdSounds.FUSION_CHARGE.get(), Balance.SND_MECH, 1.4F);
            if (state == S_COOL) playOnce(level, WmdSounds.EMP_VENT.get(), Balance.SND_MECH, 1.0F);
        }
        lastState = state;

        if ((state == S_CHARGE && chargePm >= 800) || state == S_READY || state == S_BUILDUP)
            for (int i = 0; i < 3; i++) ClientPulseEmp.chargeArc(worldPosition, i, ringWorld(i, el, az), axisWorld(el, az), RINGS[i][1] / 16.0, now);
        if (fxDone != fireAt && now - fireAt >= 0 && now - fireAt < 4) fxDone = fireAt;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = prevAz + Mth.wrapDegrees(az - prevAz) * pt;
        float e = prevEl + (el - prevEl) * pt;
        double since = tt - fireAt;
        if (since >= 0.0 && since < 10.0) e -= (float) (Balance.PEMP_RECOIL * (1.0 - since / 10.0));
        final float yaw = YAW_SIGN * (float) Math.toRadians(-a), elev = EL_SIGN * (float) Math.toRadians(e);
        model.getBone("yaw").ifPresent(b -> b.setRotY(yaw));
        model.getBone("cradle").ifPresent(b -> b.setRotX(elev));
        double[] r = ram(e);
        final float th = EL_SIGN * (float) Math.toRadians(r[0]), slide = (float) r[1] - ROD_REST;
        for (String n : new String[] { "l", "r" }) {
            model.getBone("ram_" + n).ifPresent(b -> b.setRotX(th));
            model.getBone("rod_" + n).ifPresent(b -> b.setPosY(slide));
        }
        double c = chargePm / 1000.0;
        boolean charged = state == S_CHARGE || state == S_READY || state == S_BUILDUP;

        double up = charged ? Mth.clamp((tt - stateAt) / 60.0 + (state == S_CHARGE ? 0.0 : 1.0), 0.0, 1.0)
                : state == S_COOL ? 1.0 - Mth.clamp((since - 100.0) / 60.0, 0.0, 1.0) : 0.0;
        final float bank = (float) (BANK_RISE * ease(up));
        for (int i = 0; i < 4; i++) model.getBone("cap" + i).ifPresent(b -> b.setPosY(bank));

        double v = switch (state) {
            case S_CHARGE -> 1.0 + 26.0 * c;
            case S_READY -> 28.0;
            case S_BUILDUP -> 40.0;
            case S_COOL -> 34.0 * Math.max(0.0, 1.0 - since / 200.0);
            default -> 0.0;
        };
        double dt = Double.isNaN(lastTT) ? 0.0 : Mth.clamp(tt - lastTT, 0.0, 5.0);
        lastTT = tt;
        for (int i = 0; i < 3; i++) {
            spin[i] = (float) ((spin[i] + v * dt * (1.0 + 0.35 * i) * (i % 2 == 0 ? 1 : -1)) % 360.0);
            final float s = (float) Math.toRadians(spin[i]);
            model.getBone("ring" + i).ifPresent(b -> b.setRotZ(s));
        }
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean g0 = !dark && charged && (c >= 0.2 || state != S_CHARGE), g1 = !dark && charged && (c >= 0.5 || state != S_CHARGE),
                g2 = !dark && charged && (c >= 0.8 || state != S_CHARGE);
        model.getBone("ringglow0").ifPresent(b -> b.setHidden(!g0));
        model.getBone("ringglow1").ifPresent(b -> b.setHidden(!g1));
        model.getBone("ringglow2").ifPresent(b -> b.setHidden(!g2));
        final boolean body = !dark && charged, tip = !dark && (state == S_READY || state == S_BUILDUP || (state == S_CHARGE && c > 0.5));
        model.getBone("bodyglow").ifPresent(b -> b.setHidden(!body));
        model.getBone("tip").ifPresent(b -> b.setHidden(!tip));
        if (state == S_BUILDUP) {
            final float k = (float) (1.0 + 0.8 * Mth.clamp((tt - stateAt) / Balance.PEMP_BUILDUP, 0.0, 1.0));
            model.getBone("tip").ifPresent(b -> { b.setScaleX(k); b.setScaleY(k); b.setScaleZ(k); });
        } else model.getBone("tip").ifPresent(b -> { b.setScaleX(1.0F); b.setScaleY(1.0F); b.setScaleZ(1.0F); });

        double open = state != S_COOL ? 0.0 : since < 1200.0 ? Mth.clamp(since / 20.0, 0.0, 1.0) : 1.0 - Mth.clamp((since - 1200.0) / 20.0, 0.0, 1.0);
        final float flap = FLAP_SIGN * (float) Math.toRadians(-60.0 * ease(open));
        for (int i = 0; i < 3; i++) model.getBone("flap" + i).ifPresent(b -> b.setRotX(flap));
        final boolean vent = !dark && state == S_COOL && since < 1200.0;
        model.getBone("ventglow").ifPresent(b -> b.setHidden(!vent));
        final boolean ready = !dark && (state == S_READY);
        final boolean cool = !dark && state == S_COOL;
        final boolean beacon = !dark && charged;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready));
        model.getBone("lamp_a").ifPresent(b -> b.setHidden(!cool));
        final float br = (float) ((tt * 0.25) % (Math.PI * 2.0));
        model.getBone("beacon").ifPresent(b -> { b.setHidden(!beacon); b.setRotY(br); });
    }

    private static double ease(double t) { t = Mth.clamp(t, 0.0, 1.0); return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float progress01() { return state == S_CHARGE ? chargePm / 1000.0F : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.pulse_emp", (int) Balance.PEMP_RANGE, (int) Balance.PEMP_HALF_ANGLE,
                        String.format("%,d", Balance.PEMP_CHARGE_FE / 1_000_000L), String.format("%,d", Balance.PEMP_FE_PER_TICK)),
                Component.translatable("gui.wmd.info.pulse_emp_hit", Balance.PEMP_BLACKOUT / 20, Balance.PEMP_COOLDOWN / 1200));
    }

    @Override
    protected String detail() {
        return "state " + state + ", charge " + chargePm / 10 + " %, contacts " + contacts + ", in the cone " + inCone + ", rule " + rule() + ", frequency " + frequency;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.EMP_CHARGE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 2, 0); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("pFreq", frequency);
        tag.putInt("pMode", mode & 3);
        tag.putLong("pCharge", charge);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putInt("pState", state);
        tag.putLong("pStateAt", stateAt);
        tag.putLong("pCool", coolUntil);
        tag.putLong("pFire", fireAt);
        tag.putFloat("pAz", az);
        tag.putFloat("pEl", el);
        tag.putFloat("pTAz", tAz);
        tag.putFloat("pTEl", tEl);
        tag.putInt("pSt", status);
        tag.putInt("pPm", chargePm);
        tag.putInt("pCon", contacts);
        tag.putInt("pCone", inCone);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("pFreq")) frequency = Mth.clamp(tag.getInt("pFreq"), 1, 9999);
        mode = tag.getInt("pMode") & 3;
        if (tag.contains("pCharge")) charge = tag.getLong("pCharge");
        state = Mth.clamp(tag.getInt("pState"), S_COLD, S_COOL);
        stateAt = tag.getLong("pStateAt");
        coolUntil = tag.getLong("pCool");
        if (tag.contains("pFire")) fireAt = tag.getLong("pFire");
        boolean client = level != null && level.isClientSide;
        if (!client || !placedClient) { az = tag.getFloat("pAz"); el = tag.getFloat("pEl"); }
        tAz = tag.getFloat("pTAz");
        tEl = tag.getFloat("pTEl");
        status = tag.getInt("pSt");
        chargePm = tag.getInt("pPm");
        contacts = tag.getInt("pCon");
        inCone = tag.getInt("pCone");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("pFreq", frequency);
        tag.putInt("pMode", mode & 3);
        put(tag);
        return tag;
    }
}
