package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class JetCockpitBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatHull.Owner, LoadoutHolder {

    private CompoundTag loadout = new CompoundTag();

    final Autopilot ap = new Autopilot();

    public static final int B_TAKE = 1 << 20;

    public static final int B_REVERSE = 1 << 22;
    @Override public CompoundTag loadoutTag() { return loadout; }
    @Override public void setLoadoutTag(CompoundTag t) { loadout = t == null ? new CompoundTag() : t; setChanged(); }

    public static final int AX_PITCH = 0, AX_ROLL = 1, AX_YAW = 2, AX_THROTTLE = 3;
    public static final int TINT_SMOKE = 0, TINT_CLEAR = 1, TINT_GOLD = 2;

    public static final int B_FIRE = 1, B_FLARES = 2, B_RADAR = 4, B_LOCK = 8, B_EJECT = 16, B_LEAVE = 32, B_JAM = 64, B_CHECK = 128,
            B_GEAR = 256, B_DESIG = 512, B_AGMMODE = 1024, B_HOVER = 2048, B_CHUTE = 4096, B_HOOK = 8192, B_HOOK_REL = 16384, B_REFUEL = 32768, B_JETTISON = 65536, B_STORES = 131072, B_RAMP = 262144, B_RAMP_DROP = 524288, B_TAILHOOK = 1048576, B_SLEW = 2097152;

    static final double SEAT_X = 0.0, SEAT_Y = 6.2 / 16.0, SEAT_Z0 = 5.4 / 16.0, SEAT_DZ = 2.0;
    static final float STICK_DEG = 12.0F, THROTTLE_PX = 6.0F, AB_PX = 7.2F, CANOPY_DEG = 34.0F;

    static final double FDK_SEAT_X = 1.0, FDK_SEAT_Y = 9.4 / 16.0, FDK_SEAT_Z = 7.6 / 16.0;
    static final float YOKE_DEG = 10.0F, WHEEL_DEG = 60.0F, LEVER_DEG = 22.0F, PEDAL_DEG = 12.0F, WIPER_DEG = 80.0F;

    private final CopycatHull hull = new CopycatHull();

    private final UUID[] seatIds = new UUID[2];
    private int tint, station;
    private boolean ejected, radarOn = true;
    private float throttle;
    private boolean ab;
    private long changed;

    private float pitch, roll, yaw;
    private int axP, axR, axY;
    private long lastInput;

    private boolean brakeHeld, reverseHeld;
    private boolean gun;

    private int rktTurn;

    static final Object GUN_ENTRY = "gun", RKT_ENTRY = "rkt";

    private List<Object> weaponList(ServerLevel sl) {
        List<Object> out = new ArrayList<>();
        if (ship == null) return out;
        if (GunPodBlockEntity.roundsAboard(sl, ship) >= 0) out.add(GUN_ENTRY);
        List<AirStations.Station> st = AirStations.of(sl, ship);
        for (AirStations.Station s : st) if (s instanceof RocketPodBlockEntity) { out.add(RKT_ENTRY); break; }
        for (AirStations.Station s : st) if (!(s instanceof RocketPodBlockEntity)) out.add(s);
        return out.size() > Balance.JET_WEAPON_ENTRIES ? new ArrayList<>(out.subList(0, Balance.JET_WEAPON_ENTRIES)) : out;
    }

    private List<RocketPodBlockEntity> rocketPods(ServerLevel sl) {
        List<RocketPodBlockEntity> out = new ArrayList<>();
        if (ship != null) for (AirStations.Station s : AirStations.of(sl, ship)) if (s instanceof RocketPodBlockEntity r) out.add(r);
        return out;
    }

    private List<RocketPodBlockEntity> rockets(ServerLevel sl) {
        List<RocketPodBlockEntity> out = new ArrayList<>();
        if (ship != null) for (AirStations.Station s : AirStations.of(sl, ship)) if (s instanceof RocketPodBlockEntity r && r.loaded() >= 0) out.add(r);
        return out;
    }
    private UUID ship, lockId;

    private boolean sea;
    private boolean avionics, sentAvionics;
    private final boolean[] manned = new boolean[2];
    private int[] sentSignal = { -1, -1, -1, -1 };
    private float sentPitch = 9, sentRoll = 9, sentThr = -1;

    private float cPitch, cRoll, cYaw, pPitch, pRoll, cThr, pThr, tPitch, tRoll, tThr, canopyK, pCanopyK, hudK, pHudK;
    private boolean cAb, cAvionics, cPilot, cEjected, cLock, cRadar, cWarn;

    private float tYaw, pYaw, slide0, slide1, pSlide0, pSlide1;
    private boolean cCopilot, cAp;
    private float sentYaw = 9;
    private long bellAt = Long.MIN_VALUE / 2;

    private float[] nav, cNav, cTip;
    private boolean navOff;
    private int p7Refuel, p7Hook, b5Ramp;
    private Vec3 vPrev;
    private int cTint;
    private double sweepT;

    public JetCockpitBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state, 0); }

    boolean tandem() { return getBlockState().getBlock() instanceof JetCockpitBlock b && b.tandem; }

    boolean deck() { return getBlockState().getBlock() instanceof JetCockpitBlock b && b.deck; }
    int seats() { return tandem() || deck() ? 2 : 1; }

    boolean avionicsOn() { return avionics; }
    boolean ejected() { return ejected; }

    Vec3 seatPoint(int i) {
        if (deck()) return plotPoint(i == 0 ? FDK_SEAT_X : -FDK_SEAT_X, FDK_SEAT_Y, FDK_SEAT_Z);
        double z = SEAT_Z0 + (tandem() && i == 0 ? SEAT_DZ : 0.0);
        return plotPoint(SEAT_X, SEAT_Y, z);
    }

    Vec3 standPoint(int i) {
        if (deck()) {

            Vec3 b = plotPoint(0.0, 0.05, -1.0);
            Vec3 bw = level == null ? b : SableBridge.toWorld(level, b);
            if (level != null && bw != null && level.noCollision(net.minecraft.world.entity.EntityType.PLAYER.getDimensions().makeBoundingBox(bw))) return bw;
            Vec3 a = plotPoint(0.0, 2.2 / 16.0, -0.15);
            return level == null ? a : SableBridge.toWorld(level, a);
        }
        Vec3 p = plotPoint(SEAT_X + 1.1, 1.05, SEAT_Z0 + (tandem() && i == 0 ? SEAT_DZ : 0.0));
        return level == null ? p : SableBridge.toWorld(level, p);
    }

    private CockpitSeatEntity seat(ServerLevel sl, int i) {
        if (seatIds[i] == null) return null;
        Entity e = sl.getEntity(seatIds[i]);
        return e instanceof CockpitSeatEntity s && s.isAlive() ? s : null;
    }

    private ServerPlayer occupant(ServerLevel sl, int i) {
        CockpitSeatEntity s = seat(sl, i);
        return s != null && s.getFirstPassenger() instanceof ServerPlayer p ? p : null;
    }

    void sit(ServerPlayer p) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        int free = -1;
        for (int i = 0; i < seats(); i++) if (occupant(sl, i) == null) { free = i; break; }
        if (free < 0) { p.displayClientMessage(Component.translatable("message.wmd.cockpit.full"), true); return; }
        CockpitSeatEntity s = seat(sl, free);
        if (s == null) {
            s = new CockpitSeatEntity(AirBlocks.COCKPIT_SEAT.get(), sl);
            s.setCockpit(worldPosition, free);
            Vec3 at = seatPoint(free);
            Direction ahead = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.SOUTH);
            s.moveTo(at.x, at.y, at.z, ahead.toYRot(), 0.0F);
            sl.addFreshEntity(s);
            seatIds[free] = s.getUUID();
        }
        if (!p.startRiding(s, true)) return;
        manned[free] = true;
        lastInput = sl.getGameTime();
        if (!deck()) sl.playSound(null, worldPosition, WmdSounds.CANOPY_CLOSE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        else sl.playSound(null, worldPosition, WmdSounds.FDK_SEAT_SLIDE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        sl.playSound(null, worldPosition, WmdSounds.COCKPIT_HARNESS.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        p.displayClientMessage(Component.translatable(free == 0 ? "message.wmd.cockpit.pilot" : "message.wmd.cockpit.wso"), true);
        ship = SableBridge.shipIdAt(sl, worldPosition);
        hud(sl);
        sync();
    }

    void left(Entity who, int i) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (i >= 0 && i < 2) manned[i] = false;
        if (i == 0) { axP = axR = axY = 0; }
        if (!ejected && !deck()) sl.playSound(null, worldPosition, WmdSounds.CANOPY_OPEN.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        if (deck()) sl.playSound(null, worldPosition, WmdSounds.FDK_SEAT_SLIDE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F);
        sync();
    }

    void input(ServerPlayer p, int i, float pi, float ro, float ya, float thr, boolean afterburner, int buttons, int stationSel, boolean gunHeld, boolean brake, Vec3 aimFrom, Vec3 aimDir) {
        if (!(getLevel() instanceof ServerLevel sl) || !AirGate.on()) return;
        long now = sl.getGameTime();
        if ((buttons & B_EJECT) != 0) { if (deck()) bell(sl, now); else eject(sl); return; }
        if ((buttons & B_LEAVE) != 0) {
            SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
            if (info != null && info.speed() > Balance.COCKPIT_LEAVE_SPEED && !ap.flying() && !deck()) {
            p.displayClientMessage(Component.translatable("message.wmd.cockpit.no_leave"), true); return; }
            p.stopRiding();
            return;
        }
        if ((buttons & B_CHECK) != 0 && ship != null) FlightCheck.sendTo(p, ship);
        if ((buttons & B_STORES) != 0 && ship != null) FiringGroups.sendStores(p, this, ship, loadout, station);

        boolean weapons = seats() == 1 || i == 1 || (i == 0 && occupant(sl, 1) == null);
        if (weapons && ship != null) GunTurretBlockEntity.cockpitInput(sl, ship, worldPosition, p, gunHeld);
        if (i == 0 && (buttons & B_TAILHOOK) != 0 && ship != null && AirGate.on()) {
            p.displayClientMessage(Component.translatable(TailhookBlockEntity.toggleAll(sl, ship)), true);
            click(sl, 0.7F);
        }
        if (i == 0 && (buttons & B_GEAR) != 0 && ship != null && AirGate.on()) {
            String k = LandingGearBlockEntity.toggleAll(sl, ship);
            p.displayClientMessage(Component.translatable(k), true);
            click(sl, 0.8F);
        }
        if (i == 0) brakeHeld = brake;
        if (i == 0) reverseHeld = (buttons & B_REVERSE) != 0;
        if (i == 0 && (buttons & B_HOVER) != 0 && ship != null && AirGate.on()) {
            p.displayClientMessage(Component.translatable(ShipModes.toggleHover(ship) ? "message.wmd.lift.hover_on" : "message.wmd.lift.hover_off"), true);
            click(sl, 1.0F);
        }
        if (i == 0 && (buttons & B_HOOK) != 0 && ship != null && AirGate.on()) {
            p.displayClientMessage(Component.translatable(ShipModes.toggleHook(ship) ? "message.wmd.hook.lowering" : "message.wmd.hook.raising"), true);
            click(sl, 0.8F);
        }
        if ((buttons & (B_HOOK_REL | B_JETTISON | B_RAMP | B_RAMP_DROP)) != 0) ap.ownDrop(now);
        if (i == 0 && (buttons & B_HOOK_REL) != 0 && ship != null && AirGate.on()) {
            ShipModes.requestRelease(ship, now);
            p.displayClientMessage(Component.translatable("message.wmd.hook.released"), true);
            click(sl, 0.6F);
        }
        if ((buttons & (B_RAMP | B_RAMP_DROP)) != 0 && ship != null && AirGate.on()) {
            CargoRampBlockEntity.pilot(sl, ship, p, (buttons & B_RAMP_DROP) != 0);
            click(sl, 0.9F);
        }
        if (i == 0 && (buttons & B_REFUEL) != 0 && ship != null && AirGate.on()) {
            p.displayClientMessage(Component.translatable(ShipModes.toggleRefuel(ship) ? "message.wmd.refuel.on" : "message.wmd.refuel.off"), true);
            click(sl, 1.1F);
        }
        if (i == 0 && (buttons & B_JETTISON) != 0 && ship != null && AirGate.on()) {
            int n = FuelTankBlockEntity.jettisonAll(sl, ship);
            p.displayClientMessage(Component.translatable(n > 0 ? "message.wmd.drop_tank.jettisoned" : "message.wmd.drop_tank.none", n), true);
            click(sl, 0.7F);
        }
        if (i == 0 && (buttons & B_CHUTE) != 0 && ship != null && AirGate.on()) {
            ShipModes.requestChute(ship, now);
            p.displayClientMessage(Component.translatable("message.wmd.chute.asked"), true);
            click(sl, 0.9F);
        }
        if (weapons && (buttons & B_DESIG) != 0 && ship != null && AirGate.on()) {
            Vec3 eye = aimFrom != null && aimFrom.distanceTo(p.getEyePosition()) <= Balance.TPOD_AIM_REACH ? aimFrom : p.getEyePosition();
            Vec3 look = eye != aimFrom || aimDir == null ? p.getLookAngle() : aimDir;
            p.displayClientMessage(Component.translatable(TargetingPodBlockEntity.toggle(sl, ship, eye, look)), true);
        }
        if (weapons && (buttons & B_SLEW) != 0 && (buttons & B_DESIG) == 0 && ship != null && AirGate.on() && aimFrom != null && aimDir != null
                && p.getEyePosition().distanceTo(aimFrom) <= Balance.TPOD_AIM_REACH)
            TargetingPodBlockEntity.slew(sl, ship, aimFrom, aimDir);
        if (weapons && (buttons & B_AGMMODE) != 0 && ship != null && AirGate.on()) {
            boolean g = AirStations.toggleAgmGps(ship);
            p.displayClientMessage(Component.translatable(g ? "message.wmd.agm.gps" : "message.wmd.agm.laser"), true);
            click(sl, 1.2F);
        }
        if (i == 0 && ap.flying() && (pi != 0.0F || ro != 0.0F || ya != 0.0F || (buttons & B_TAKE) != 0)) {
            ap.letGo(Autopilot.R_STICK, now); click(sl, 0.7F); sync();
        }
        if (i == 0) {
            lastInput = now;
            axP = (int) Math.signum(pi); axR = (int) Math.signum(ro); axY = (int) Math.signum(ya);
            gun = gunHeld;
            float t = ap.flying() ? throttle : Mth.clamp(thr, 0.0F, 1.0F);
            boolean a = afterburner && t >= 0.999F;
            if (Math.abs(t - throttle) > 1.0E-4F || a != ab) {
                if (a && !ab) sl.playSound(null, worldPosition, WmdSounds.COCKPIT_SWITCH.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.7F);
                throttle = t; ab = a; changed = now;
            }
        }
        if (!weapons) return;
        if (stationSel >= 1 && stationSel <= Balance.JET_WEAPON_ENTRIES && stationSel - 1 != station) { station = stationSel - 1; click(sl, 1.1F); }
        if ((buttons & B_RADAR) != 0) {
            NoseRadarBlockEntity nr = noseRadar(sl);
            if (nr == null) p.displayClientMessage(Component.translatable("message.wmd.cockpit.no_radar"), true);
            else if (!nr.switchedOn()) { sea = false; nr.setOn(true); click(sl, 1.3F); p.displayClientMessage(Component.translatable("message.wmd.cockpit.radar_air"), true); }
            else if (!sea) { sea = true; lockId = null; nr.track(null); click(sl, 1.5F); p.displayClientMessage(Component.translatable("message.wmd.cockpit.radar_sea"), true); }
            else { sea = false; nr.setOn(false); lockId = null; nr.track(null); click(sl, 0.9F); p.displayClientMessage(Component.translatable("message.wmd.cockpit.radar_off"), true); }
            sync();
        }
        if ((buttons & B_LOCK) != 0) { nextLock(sl); click(sl, 1.5F); }
        if ((buttons & B_FIRE) != 0) fireStation(sl, p);
        if ((buttons & B_FLARES) != 0) flares(sl, p);
        if ((buttons & B_JAM) != 0) jammer(sl, p);
    }

    private void fireStation(ServerLevel sl, ServerPlayer p) {
        if (!AirGate.on()) return;
        if (!avionics) { p.displayClientMessage(Component.translatable("message.wmd.cockpit.avionics_needed"), true); return; }
        String grp = station < LoadoutPlan.GROUPS ? FiringGroups.fire(sl, this, ship, loadout, station, lockId, lockId == null ? null : new CockpitLock(this)) : null;
        if (grp != null) { p.displayClientMessage(Component.translatable(grp, station + 1), true); return; }
        List<Object> wl = weaponList(sl);
        if (wl.isEmpty()) { p.displayClientMessage(Component.translatable("message.wmd.cockpit.no_stations"), true); return; }
        if (station >= wl.size()) { p.displayClientMessage(Component.translatable("message.wmd.cockpit.station_none", station + 1, wl.size()), true); return; }
        Object e = wl.get(station);
        if (e == GUN_ENTRY) {
            if (GunPodBlockEntity.triggerAll(sl, ship, Balance.COCKPIT_GUN_HOLD) <= 0) p.displayClientMessage(Component.translatable("message.wmd.cockpit.gun_empty"), true);
            return;
        }
        AirStations.Station s;
        if (e == RKT_ENTRY) {
            List<RocketPodBlockEntity> rk = rockets(sl);
            if (rk.isEmpty()) { p.displayClientMessage(Component.translatable("message.wmd.cockpit.rkt_empty"), true); return; }
            s = rk.get(Math.floorMod(rktTurn++, rk.size()));
        } else s = (AirStations.Station) e;
        AirMissiles.LockSource lock = lockId == null ? null : new CockpitLock(this);
        String why = s.fire(sl, lockId, lock);
        p.displayClientMessage(why == null ? Component.translatable("message.wmd.cockpit.station_fired", station + 1)
                : Component.translatable("message.wmd.cockpit.station_refused", station + 1, why), true);
    }

    private void jammer(ServerLevel sl, ServerPlayer p) {
        if (!AirGate.on()) return;
        int r = JammerPodBlockEntity.toggleAll(sl, ship);
        p.displayClientMessage(Component.translatable(r < 0 ? "message.wmd.cockpit.no_jammer" : r == 1 ? "message.wmd.cockpit.jam_on" : "message.wmd.cockpit.jam_off"), true);
        if (r >= 0) click(sl, r == 1 ? 1.3F : 0.9F);
    }

    private void flares(ServerLevel sl, ServerPlayer p) {
        if (!AirGate.on()) return;
        int f = CountermeasureDispenserBlockEntity.salvoOn(sl, ship, AirMissiles.FLARE), c = CountermeasureDispenserBlockEntity.salvoOn(sl, ship, AirMissiles.CHAFF);
        if (f == AirMissiles.EMPTY && c == AirMissiles.EMPTY) p.displayClientMessage(Component.translatable("message.wmd.cockpit.no_dispenser"), true);
        else { int[] l = CountermeasureDispenserBlockEntity.loadOn(sl, ship); p.displayClientMessage(Component.translatable("message.wmd.cockpit.countermeasures", l[0], l[1]), true); }
    }

    private static final class CockpitLock implements AirMissiles.LockSource {
        private final JetCockpitBlockEntity c;
        CockpitLock(JetCockpitBlockEntity c) { this.c = c; }
        @Override public boolean holds(ServerLevel level, AirMissiles.Shot s, Vec3 targetPos, long now) {
            NoseRadarBlockEntity nr = c.isRemoved() ? null : c.noseRadar(level);
            return nr != null && nr.on() && c.avionics && c.lockId != null && c.lockId.equals(s.targetShip())
                    && offAxis(nr.at(), nr.boresight(), targetPos) <= Balance.COCKPIT_LOCK_KEEP;
        }
        @Override public Vec3 at(ServerLevel level) { if (c.isRemoved()) return null; NoseRadarBlockEntity nr = c.noseRadar(level); return nr == null ? c.eye() : nr.at(); }
        @Override public UUID owner() { return c.getOwnerId(); }
        @Override public String label() { return "nose"; }
    }

    private void click(ServerLevel sl, float pitchF) { sl.playSound(null, worldPosition, WmdSounds.COCKPIT_SWITCH.get(), SoundSource.BLOCKS, Balance.SND_CLICK, pitchF); }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) return false;
        long now = sl.getGameTime();
        boolean any = false;
        for (int i = 0; i < seats(); i++) {
            boolean m = occupant(sl, i) != null;
            if (m != manned[i]) { manned[i] = m; if (i == 0 && !m) axP = axR = axY = 0; sync(); }
            any |= m;
        }
        if (now - lastInput > 20) { axP = axR = axY = 0; gun = false; brakeHeld = false; reverseHeld = false; }

        if (reverseHeld && ship != null && AirGate.on() && now % 4 == 0) {
            TowBar.pilotTook(sl, ship, "message.wmd.tow.pilot");
            if (throttle < 0.05F && !ab && LandingGearBlockEntity.onWheels(sl, ship)) LandingGearBlockEntity.reverse(sl, ship, 6, axesPlot()[0]);
        }
        if (ship != null) FiringGroups.tick(sl, this, ship, lockId, lockId == null ? null : new CockpitLock(this));
        if (brakeHeld && ship != null && AirGate.on() && now % 4 == 0) LandingGearBlockEntity.brakes(sl, ship, 6);

        if (gun && ship != null && AirGate.on() && now % Balance.COCKPIT_GUN_RETRIGGER == 0) GunPodBlockEntity.triggerAll(sl, ship, Balance.COCKPIT_GUN_HOLD);
        float k = 1.0F / Balance.COCKPIT_AXIS_TICKS;
        float apP = axP, apR = axR, apY = axY;
        Autopilot.Out apo = apStep(sl, now, any);
        if (apo != null) {
            apP = apo.pitch(); apR = apo.roll(); apY = apo.yaw();
            float th = Mth.clamp(apo.throttle(), 0.0F, 1.0F);
            if (Math.abs(th - throttle) > 1.0E-4F || ab) { throttle = th; ab = false; }
            changed = now;
            if (apo.brake() && ship != null && now % 4 == 0) LandingGearBlockEntity.brakes(sl, ship, 6);
        }
        float np = ScrewPropellerBlockEntity.approach(pitch, apP, k), nr = ScrewPropellerBlockEntity.approach(roll, apR, k),
                ny = ScrewPropellerBlockEntity.approach(yaw, apY, k);
        if (Math.abs(np - pitch) + Math.abs(nr - roll) + Math.abs(ny - yaw) > 1.0E-5F) changed = now;
        pitch = np; roll = nr; yaw = ny;
        ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null) AirCrash.tick(sl, ship);
        if (ship != null && (manned[0] || ap.flying() || now % Balance.PROP_BEAT == 0)) publish(sl);
        if (ship != null && AirGate.on()) {
            if ((now + (worldPosition.asLong() & 63)) % Balance.NAV_SCAN == 0 || (nav == null && now % 20 == 0)) {
                float[] tp = AirLights.tips(sl, ship, worldPosition, facing());
                if (tp != null && !java.util.Arrays.equals(tp, nav)) { nav = tp; sync(); }
                boolean off = NavLightBlockEntity.aboard(sl, ship);
                if (off != navOff) { navOff = off; sync(); }
            }
        }

        boolean av = (any || ap.on()) && !ejected && ship != null && ShipPower.draw(sl, ship, Balance.COCKPIT_FE)
                && (!deck() || ShipPower.draw(sl, ship, Balance.FDK_FE - Balance.COCKPIT_FE));
        if (av != avionics) { avionics = av; if (!av) lockId = null; }
        if (now % Balance.COCKPIT_RADAR_TICKS == 0) radar(sl, now);
        if (any && now % Balance.COCKPIT_HUD_TICKS == 0) hud(sl);
        if (now % Balance.COCKPIT_HUD_TICKS == 0 && ship != null && AirGate.on()) apHud(sl, now);
        signals(sl);
        if (avionics != sentAvionics || Math.abs(pitch - sentPitch) > 0.05F || Math.abs(roll - sentRoll) > 0.05F || Math.abs(throttle - sentThr) > 0.02F
                || (pitch == 0.0F && sentPitch != 0.0F) || (roll == 0.0F && sentRoll != 0.0F) || (deck() && Math.abs(yaw - sentYaw) > 0.05F)) sync();
        return false;
    }

    String devStatus(ServerLevel sl) {
        if (ship == null) return null;
        long now = sl.getGameTime();
        Autopilot.Ctx c = apCtx(sl, now);
        if (c == null) return null;
        String[] modes = { "OFF", "HOLD", "ROUTE", "ORBIT", "HOME", "PARKED" };
        ShipControl.Answer a = ShipControl.answerOf(sl, ship);
        int gear = LandingGearBlockEntity.stateAboard(sl, ship);
        return String.format(java.util.Locale.ROOT, "AP %s%s | y %.0f spd %.0f pit %.0f rol %.0f hdg %.0f vy %.1f | ord p %.2f r %.2f y %.2f thr %.2f | eng %d/%d gear %d pow %s",
                modes[Math.max(0, Math.min(5, ap.mode))], ap.mode == Autopilot.OFF ? " (reason " + ap.reason + ")" : "",
                c.at().y, c.vel().length(), c.pitch(), c.roll(), c.heading(), c.vel().y, pitch, roll, yaw, throttle,
                a == null ? 0 : a.running(), a == null ? 0 : a.engines(), gear, avionics ? "on" : "OFF");
    }

    boolean devLaunch(ServerLevel sl, double cx, double cz, float alt, float r, float spd) {
        ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship == null || !AirGate.on()) return false;
        double[] a = worldPoint(SEAT_X, 0.5, 0.0), b = worldPoint(SEAT_X, 0.5, 1.0);
        Vec3 f = new Vec3(b[0] - a[0], 0.0, b[2] - a[2]);
        if (f.lengthSqr() < 1.0E-6) return false;
        SableBridge.shipPush(sl, ship, f.normalize().scale(spd), Vec3.ZERO);
        avionics = true;
        ap.devOrbit(cx, cz, alt, r, spd);
        sync();
        return true;
    }

    private int refuelState(ServerLevel sl) {
        long now = sl.getGameTime();
        int r = 0;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, ship)) {
            if (!sl.isLoaded(q)) continue;
            BlockEntity be = sl.getBlockEntity(q);
            if (be instanceof RefuelProbeBlockEntity rp) { int s = rp.hudState(now); if (s > 0) r = Math.max(r, s); }
            else if (be instanceof DroguePodBlockEntity dp) { int s = dp.hudState(now); if (s > 0) r = Math.max(r, s + 2); }
        }
        return r;
    }

    private int hookState(ServerLevel sl) {
        int h = 0;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, ship))
            if (sl.isLoaded(q) && sl.getBlockEntity(q) instanceof CargoHookBlockEntity hk) h = Math.max(h, hk.hudState());
        return h;
    }

    static final float YAW_SENSE = 1.0F;

    private void publish(ServerLevel sl) {
        if (ship == null) return;

        ShipControl.publishJet(sl, ship, worldPosition, ShipControl.stepOf(throttle), YAW_SENSE * yaw, pitch, roll, throttle, ab, changed, ap.flying() ? ShipControl.SRC_AUTOPILOT : ShipControl.SRC_COCKPIT);
    }

    private void signals(ServerLevel sl) {
        boolean diff = false;
        for (int a = 0; a < 4; a++) {
            int s = signal(a);
            if (s != sentSignal[a]) { sentSignal[a] = s; diff = true; }
        }
        if (diff) sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    int signal(int axis) {
        if (axis == AX_THROTTLE) return ab ? 15 : Math.round(throttle * 14.0F);
        float v = axis == AX_PITCH ? pitch : axis == AX_ROLL ? roll : yaw;
        return Mth.clamp(Math.round((v + 1.0F) * 7.5F), 0, 15);
    }

    private Autopilot.Ctx apCtx(ServerLevel sl, long now) {
        if (!AirGate.on()) return null;
        double[] a = worldPoint(SEAT_X, 0.5, 0.0), b = worldPoint(SEAT_X, 0.5, 1.0), u0 = worldPoint(SEAT_X, 0.0, 0.0), u1 = worldPoint(SEAT_X, 1.0, 0.0),
                r0 = worldPoint(1.0, 0.5, 0.0), r1 = worldPoint(0.0, 0.5, 0.0);
        Vec3 f = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize(), up = new Vec3(u1[0] - u0[0], u1[1] - u0[1], u1[2] - u0[2]).normalize(),
                right = new Vec3(r1[0] - r0[0], r1[1] - r0[1], r1[2] - r0[2]).normalize();
        double heading = (Math.toDegrees(Math.atan2(f.x, -f.z)) + 360.0) % 360.0;
        double pitchDeg = Math.toDegrees(Math.asin(Mth.clamp(f.y, -1.0, 1.0)));
        double rollDeg = Math.toDegrees(Math.atan2(-right.y, up.y));
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 v = info == null ? Vec3.ZERO : info.linearVelocity();
        return new Autopilot.Ctx(sl, ship, eye(), v, heading, pitchDeg, rollDeg, right, avionics, manned[0], now, worldPosition);
    }

    private Autopilot.Out apStep(ServerLevel sl, long now, boolean any) {
        if (!AirGate.on() || ship == null) return null;
        boolean was = ap.flying();
        Autopilot.Out o = ap.step(apCtx(sl, now));
        if (was != ap.flying()) { sync(); if (!ap.flying()) sl.playSound(null, worldPosition, WmdSounds.COCKPIT_SWITCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 0.6F); }
        return o;
    }

    private void apHud(ServerLevel sl, long now) {
        if (!AirGate.on() || (!ap.on() && ap.reasonShown(now) == Autopilot.R_NONE && ap.home == null)) return;
        Autopilot.Ctx c = apCtx(sl, now);
        if (c == null) return;
        ApHudPayload h = ap.hud(c);
        for (int i = 0; i < seats(); i++) { ServerPlayer p = occupant(sl, i); if (p != null) PacketDistributor.sendToPlayer(p, h); }
        if (!ap.on() && ap.reasonShown(now) == Autopilot.R_NONE) return;
        for (ServerPlayer p : sl.players())
            if (p.getVehicle() instanceof GunnerSeatEntity && ship.equals(SableBridge.shipUnder(p.getVehicle()))) PacketDistributor.sendToPlayer(p, h);
    }

    void apAsk(ServerPlayer sp, int op, int arg, CompoundTag data) {
        if (!(getLevel() instanceof ServerLevel sl) || !AirGate.on()) return;
        long now = sl.getGameTime();
        ship = SableBridge.shipIdAt(sl, worldPosition);
        Autopilot.Ctx c = apCtx(sl, now);
        if (c == null || ship == null) { sp.displayClientMessage(Component.translatable("message.wmd.ap.no_craft"), true); return; }
        switch (op) {
            case ApPayloads.SET -> { if (data != null) ap.apply(data); setChanged(); }
            case ApPayloads.ENGAGE -> {
                if (data != null && !data.isEmpty()) ap.apply(data);
                String why = ap.engage(c, Mth.clamp(arg, Autopilot.HOLD, Autopilot.HOME));
                if (why != null) Wmd.LOGGER.info("[ap] {} engage mode {} refused: {}", ship.toString().substring(0, 8), arg, why);
                sp.displayClientMessage(Component.translatable(why == null ? "message.wmd.ap.engaged" : why), true);
                click(sl, why == null ? 1.2F : 0.6F);
                sync();
            }
            case ApPayloads.QUICK -> {
                if (ap.on()) { ap.letGo(Autopilot.R_OFF, now); sp.displayClientMessage(Component.translatable("message.wmd.ap.off"), true); click(sl, 0.7F); }
                else { String why = ap.engageHere(c); sp.displayClientMessage(Component.translatable(why == null ? "message.wmd.ap.hold_here" : why), true); click(sl, why == null ? 1.2F : 0.6F); }
                sync();
            }
            case ApPayloads.OFF -> { ap.letGo(Autopilot.R_OFF, now); click(sl, 0.7F); sync(); }
            case ApPayloads.HERE -> { ap.holdHdg = (float) c.heading(); ap.holdAlt = (float) c.at().y; ap.holdSpd = (float) c.vel().length(); setChanged(); }

            case ApPayloads.HOME_MAP, ApPayloads.HOME_HERE -> {
                String why = op == ApPayloads.HOME_HERE ? ap.markHere(c)
                        : data == null ? "message.wmd.ap.home_bad" : ap.markMap(sl, data.getDouble("x"), data.getDouble("z"), data.getDouble("hdg"), data.getDouble("len"));
                sp.displayClientMessage(Component.translatable(why == null ? "message.wmd.ap.home_set" : why), true);
                click(sl, why == null ? 1.2F : 0.6F);
                setChanged();
            }
            case ApPayloads.HOME_CLEAR -> { ap.clearHome(); sp.displayClientMessage(Component.translatable("message.wmd.ap.home_cleared"), true); click(sl, 0.7F); setChanged(); }
            default -> { }
        }
        if (op != ApPayloads.QUICK) PacketDistributor.sendToPlayer(sp, new ApPayloads.State(worldPosition, ap.screenTag(c)));
    }

    Vec3[] axesPlot() {
        Vec3 a = plotPoint(SEAT_X, 0.5, 0.0), b = plotPoint(SEAT_X, 0.5, 1.0), r0 = plotPoint(1.0, 0.5, 0.0), r1 = plotPoint(0.0, 0.5, 0.0);
        return new Vec3[] { b.subtract(a).normalize(), r1.subtract(r0).normalize() };
    }

    private Vec3 nose() { double[] a = worldPoint(SEAT_X, 1.0, 0.0), b = worldPoint(SEAT_X, 1.0, 1.0); return new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize(); }
    private Vec3 eye() { double[] a = worldPoint(SEAT_X, 1.4, SEAT_Z0); return new Vec3(a[0], a[1], a[2]); }

    private static double offAxis(Vec3 from, Vec3 nose, Vec3 to) {
        Vec3 d = to.subtract(from);
        double l = d.length();
        return l < 1.0E-6 ? 0.0 : Math.toDegrees(Math.acos(Mth.clamp(d.dot(nose) / l, -1.0, 1.0)));
    }

    NoseRadarBlockEntity noseRadar(ServerLevel sl) { return ship == null || !AirGate.on() ? null : NoseRadars.of(sl, ship); }

    private void radar(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        NoseRadarBlockEntity nr = noseRadar(sl);
        boolean on = avionics && nr != null && nr.on();
        if (on != radarOn) { radarOn = on; sync(); }
        if (!on) { if (lockId != null) { lockId = null; if (nr != null) nr.track(null); } return; }
        if (lockId == null) return;
        if (sea) {
            Vec3 sp = AntiShipMissiles.seaKeep(sl, nr, lockId, ship, getOwnerId());
            if (sp != null) nr.track(sp); else { lockId = null; nr.track(null); }
            return;
        }
        Vec3 at = nr.at();
        AirPicture.Aircraft t = AirPicture.get(sl, lockId);
        boolean keep = t != null && offAxis(at, nr.boresight(), t.pos()) <= Math.min(Balance.COCKPIT_LOCK_KEEP, nr.lockGimbal()) && t.pos().distanceTo(at) <= AirJam.lockRange(sl, lockId, nr.lockRange());
        if (keep) { Rwr.lock(sl, lockId, at, getOwnerId(), "nose", worldPosition.asLong(), now); nr.track(t.pos()); }
        else { lockId = null; nr.track(null); }
    }

    private void nextLock(ServerLevel sl) {
        if (!AirGate.on() || !avionics) return;
        NoseRadarBlockEntity nr = noseRadar(sl);
        if (nr == null || !nr.on()) { lockId = null; return; }
        Vec3 at = nr.at(), n = nr.boresight();
        double cone = Math.min(Balance.COCKPIT_LOCK_CONE, nr.lockGimbal());
        if (sea) {
            List<UUID> ships = AntiShipMissiles.seaContacts(sl, nr, ship, getOwnerId(), cone);
            if (ships.isEmpty()) { lockId = null; nr.track(null); return; }
            int k = 0;
            for (int i = 0; i < ships.size(); i++) if (ships.get(i).equals(lockId)) { k = (i + 1) % ships.size(); break; }
            lockId = ships.get(k);
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, lockId);
            nr.track(si == null ? null : si.worldCenter());
            return;
        }
        List<AirPicture.Seen> in = new ArrayList<>();
        for (AirPicture.Seen s : nr.seenInCone())
            if (offAxis(at, n, s.a().pos()) <= cone && s.a().pos().distanceTo(at) <= AirJam.lockRange(sl, s.a().id(), nr.lockRange())) in.add(s);
        in.sort((x, y) -> Double.compare(x.dist(), y.dist()));
        if (in.isEmpty()) { lockId = null; nr.track(null); return; }
        int idx = 0;
        for (int i = 0; i < in.size(); i++) if (in.get(i).a().id().equals(lockId)) { idx = (i + 1) % in.size(); break; }
        lockId = in.get(idx).a().id();
        nr.track(in.get(idx).a().pos());
    }

    private UUID getOwnerId() { return this instanceof Owned o ? o.getOwner() : null; }

    private void hud(ServerLevel sl) {
        double[] a = worldPoint(SEAT_X, 0.5, 0.0), b = worldPoint(SEAT_X, 0.5, 1.0), u0 = worldPoint(SEAT_X, 0.0, 0.0), u1 = worldPoint(SEAT_X, 1.0, 0.0),
                r0 = worldPoint(1.0, 0.5, 0.0), r1 = worldPoint(0.0, 0.5, 0.0);
        Vec3 f = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize(), up = new Vec3(u1[0] - u0[0], u1[1] - u0[1], u1[2] - u0[2]).normalize(),
                right = new Vec3(r1[0] - r0[0], r1[1] - r0[1], r1[2] - r0[2]).normalize();
        float heading = (float) ((Math.toDegrees(Math.atan2(f.x, -f.z)) + 360.0) % 360.0);
        float pitchDeg = (float) Math.toDegrees(Math.asin(Mth.clamp(f.y, -1.0, 1.0)));
        float rollDeg = (float) Math.toDegrees(Math.atan2(-right.y, up.y));
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 v = info == null ? Vec3.ZERO : info.linearVelocity();
        Vec3 at = eye();
        int ground = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        float agl = (float) (at.y - ground);
        ShipControl.Answer ans = ship == null ? null : ShipControl.answerOf(sl, ship);
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        Vec3 lock = null;
        int stnKind = -2, stnCount = 0;
        Vec3 aim = null, lead = null, lockVel = null;
        int pip = 0;
        int[] cm = { 0, 0 };
        int gunRounds = -1;
        int gear = -1;
        long pod = CockpitHudPayload.NONE;
        Vec3 spot = null;
        boolean podMem = false;
        double[] bcn = null;
        if (AirGate.on() && ship != null) {
            gear = LandingGearBlockEntity.stateAboard(sl, ship);
            BlockPos pp = TargetingPodBlockEntity.podAboard(sl, ship);
            if (pp != null) { pod = pp.asLong(); spot = TargetingPodBlockEntity.spotAboard(sl, ship); podMem = spot != null && TargetingPodBlockEntity.memAboard(sl, ship); }
            bcn = DeckLightBlockEntity.beaconFor(sl, at);
        }
        if (AirGate.on() && ship != null && sl.getGameTime() % 10 == 0) { p7Refuel = refuelState(sl); p7Hook = hookState(sl); b5Ramp = CargoRampBlockEntity.hudAboard(sl, ship); }
        int sys = 0;
        float fuel = Float.NaN;
        if (AirGate.on() && ship != null) {
            int[] e = AirEngineBlockEntity.systemsAboard(sl, ship);
            sys = CockpitHudPayload.packSys(DragChuteBlockEntity.stateAboard(sl, ship), ShipModes.hover(ship), e[0], e[1], e[2], e[3], e[4])
                    | CockpitHudPayload.packP7(p7Refuel, p7Hook) | CockpitHudPayload.packRamp(b5Ramp);
            int[] fu = PowerCoreBlockEntity.fuelOf(sl, ship);
            if (fu[1] > 0) fuel = fu[0] / (float) fu[1];
        }
        if (AirGate.on()) {
            if (lockId != null && sea) { SableBridge.ShipInfo si = SableBridge.shipInfo(sl, lockId); if (si != null) lock = si.worldCenter(); else lockId = null; }
            else if (lockId != null) { AirPicture.Aircraft t = AirPicture.get(sl, lockId); if (t != null) { lock = t.pos(); lockVel = t.vel(); } else lockId = null; }
            List<Object> wl = ship == null ? List.of() : weaponList(sl);
            stnCount = wl.size();
            Object sel = station < wl.size() ? wl.get(station) : null;
            if (ship != null) gunRounds = GunPodBlockEntity.roundsAboard(sl, ship);
            if (sel == GUN_ENTRY) stnKind = gunRounds > 0 ? AirStores.GUN : -1;
            else if (sel == RKT_ENTRY) stnKind = rocketPods(sl).isEmpty() ? -1 : AirStores.ROCKET;
            else if (sel instanceof AirStations.Station s1) stnKind = s1.loaded();
            boolean grpSel = station < LoadoutPlan.GROUPS && FiringGroups.configured(loadout, station);
            int gk = grpSel ? FiringGroups.hudKind(sl, ship, loadout, station) : -2; if (gk != -2) stnKind = gk;
            if (ship != null) cm = CountermeasureDispenserBlockEntity.loadOn(sl, ship);
            if (avionics && AirStores.isBomb(stnKind)) {
                Vec3 from = sel instanceof AirStations.Station s2 ? SableBridge.worldPos(sl, s2.stationPos()) : at;
                aim = AirStores.bombImpact(sl, stnKind, from == null ? at : from.add(0.0, -0.6, 0.0), v.scale(1.0 / 20.0), getOwnerId(), ship);
            }
            if (avionics && stnKind == AirStores.ROCKET && ship != null) {
                List<RocketPodBlockEntity> rk = sel == RKT_ENTRY ? rockets(sl) : List.of();
                if (sel == RKT_ENTRY && rk.isEmpty()) rk = rocketPods(sl);
                AirStations.Station rs = grpSel ? FiringGroups.firstLoaded(sl, ship, loadout, station) : !rk.isEmpty() ? rk.get(Math.floorMod(rktTurn, rk.size()))
                        : sel instanceof AirStations.Station s3 ? s3 : null;
                if (rs instanceof RocketPodBlockEntity rp) {
                    Vec3[] air = lock != null && lockVel != null ? rp.airPipper(sl, lock, lockVel) : null;
                    if (air != null) { aim = air[0]; lead = air[1]; pip |= CockpitHudPayload.F_PIP_AIR; }
                    else aim = rp.pipper(sl);
                }
            }
            if (avionics && stnKind == AirStores.GUN && ship != null) {
                Object[] gp = GunPodBlockEntity.pipper(sl, ship, lock);
                if (gp != null) {
                    aim = (Vec3) gp[0];
                    int k = (Integer) gp[1];
                    if (k == 1) pip |= CockpitHudPayload.F_PIP_HIT;
                    if (k >= 2) pip |= CockpitHudPayload.F_PIP_AIR;
                }
            }
        }
        int flags = (avionics ? CockpitHudPayload.F_POWER : 0) | (ship != null ? CockpitHudPayload.F_SHIP : 0) | (radarOn ? CockpitHudPayload.F_RADAR : 0)
                | (ab ? CockpitHudPayload.F_AB : 0) | (ejected ? CockpitHudPayload.F_EJECTED : 0) | (tandem() || deck() ? CockpitHudPayload.F_TANDEM : 0)
                | (o != null && worldPosition.equals(o.from()) ? CockpitHudPayload.F_CONTROL : 0) | (noseRadar(sl) == null ? CockpitHudPayload.F_NORADAR : 0)
                | (AirGate.on() && ship != null && AirJam.on(sl, ship) ? CockpitHudPayload.F_JAM : 0)
                | (AirGate.on() && lockId != null && AirJam.on(sl, lockId) ? CockpitHudPayload.F_LOCKJAM : 0)
                | (AirStations.agmGps(ship) ? CockpitHudPayload.F_AGMGPS : 0)
                | (sea && radarOn ? CockpitHudPayload.F_SEA : 0)
                | pip
                | (podMem ? CockpitHudPayload.F_PODMEM : 0)
                | (avionics && stnKind == AirMissiles.HEAT && AirStations.heatPick(sl, at, f, ship) != null ? CockpitHudPayload.F_GROWL : 0);
        for (int i = 0; i < seats(); i++) {
            ServerPlayer p = occupant(sl, i);
            if (p == null) continue;
            PacketDistributor.sendToPlayer(p, new CockpitHudPayload(worldPosition, i, flags, heading, pitchDeg, rollDeg, (float) v.horizontalDistance(), (float) v.y,
                    (float) at.y, agl, throttle, ans == null ? 0 : ans.engines(), ans == null ? 0 : ans.running(), station,
                    lock == null ? Float.NaN : (float) lock.x, lock == null ? 0.0F : (float) lock.y, lock == null ? 0.0F : (float) lock.z,
                    lock == null ? 0.0F : (float) lock.distanceTo(at), stnKind, stnCount, cm[0], cm[1], gunRounds, gear, pod,
                    spot == null ? Float.NaN : (float) spot.x, spot == null ? 0.0F : (float) spot.y, spot == null ? 0.0F : (float) spot.z,
                    bcn == null ? Float.NaN : (float) bcn[0], bcn == null ? 0.0F : (float) bcn[1], bcn == null ? 0.0F : (float) bcn[2], sys, fuel,
                    aim == null ? Float.NaN : (float) aim.x, aim == null ? 0.0F : (float) aim.y, aim == null ? 0.0F : (float) aim.z,
                    lead == null ? Float.NaN : (float) lead.x, lead == null ? 0.0F : (float) lead.y, lead == null ? 0.0F : (float) lead.z));
        }
    }

    private void bell(ServerLevel sl, long now) {
        if (!AirGate.on() || ship == null || now - bellAt < Balance.FDK_BELL_TICKS) return;
        bellAt = now;
        sl.playSound(null, worldPosition, WmdSounds.BAILOUT_BELL.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        for (ServerPlayer p : sl.players())
            if (ship.equals(SableBridge.shipUnder(p)) || (p.getVehicle() != null && ship.equals(SableBridge.shipUnder(p.getVehicle()))))
                p.displayClientMessage(Component.translatable("message.wmd.fdk.bail_out").withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    void eject(ServerLevel sl) {
        if (ejected) return;
        ejected = true;
        lockId = null;
        Vec3 up = upVec(), vel = shipVel(sl);
        Vec3 canopyAt = plotPoint(SEAT_X, 1.2, SEAT_Z0 + (tandem() ? 1.0 : 0.6));
        EjectedCanopyEntity c = new EjectedCanopyEntity(AirBlocks.EJECTED_CANOPY.get(), sl);
        Vec3 cw = SableBridge.toWorld(sl, canopyAt);
        c.setup(tandem(), tint, cw, vel.add(up.scale(Balance.EJECT_CANOPY_KICK)).add(nose().scale(-0.25)), Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.SOUTH).toYRot());
        sl.addFreshEntity(c);
        for (int i = 0; i < seats(); i++) {
            ServerPlayer p = occupant(sl, i);
            if (p == null) continue;
            Vec3 at = SableBridge.toWorld(sl, seatPoint(i)).add(up.scale(0.3));
            p.stopRiding();
            EjectionSeatEntity s = new EjectionSeatEntity(AirBlocks.EJECTION_SEAT.get(), sl);
            s.setup(tandem(), tandem() && i == 1, at, vel.add(up.scale(Balance.EJECT_KICK)), p.getYRot(), up);
            sl.addFreshEntity(s);
            p.startRiding(s, true);
        }
        sl.playSound(null, worldPosition, WmdSounds.EJECT_BLAST.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        sl.playSound(null, worldPosition, WmdSounds.SEAT_ROCKET.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        sl.sendParticles(ParticleTypes.EXPLOSION, cw.x, cw.y, cw.z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, cw.x, cw.y, cw.z, 24, 0.5, 0.3, 0.8, 0.05);
        sync();
    }

    void refit(ServerPlayer p) {
        if (!(getLevel() instanceof ServerLevel sl) || !ejected) return;
        ejected = false;
        sl.playSound(null, worldPosition, WmdSounds.CANOPY_CLOSE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        p.displayClientMessage(Component.translatable("message.wmd.cockpit.refit"), true);
        sync();
    }

    private Vec3 upVec() { double[] a = worldPoint(SEAT_X, 0.0, 0.0), b = worldPoint(SEAT_X, 1.0, 0.0); return new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize(); }

    private Vec3 shipVel(ServerLevel sl) {
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        return info == null ? Vec3.ZERO : info.linearVelocity().scale(1.0 / 20.0);
    }

    int cycleTint() { tint = (tint + 1) % 3; sync(); return tint; }
    public int tintShown() { return cTint; }
    public boolean ejectedShown() { return cEjected; }

    @Override public CopycatHull hull() { return hull; }
    @Override public void hullChanged() { sync(); }
    public BlockState copyShown() { return hull.shown(); }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        net.minecraft.world.item.ItemStack s = hull.take();
        if (!s.isEmpty()) l.add(s);
        return l;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) ShipControl.withdraw(sl, ship, worldPosition);
        super.setRemoved();
    }

    private void sync() {
        sentAvionics = avionics; sentPitch = pitch; sentRoll = roll; sentThr = throttle; sentYaw = yaw;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static void clientTickCockpit(Level lv, BlockPos pos, BlockState state, JetCockpitBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.pPitch = be.cPitch; be.pRoll = be.cRoll; be.pThr = be.cThr; be.pCanopyK = be.canopyK; be.pHudK = be.hudK;
        float k = 1.0F / 3.0F;
        be.cPitch += (be.tPitch - be.cPitch) * k;
        be.cRoll += (be.tRoll - be.cRoll) * k;
        be.cThr += (be.tThr - be.cThr) * 0.25F;
        be.pYaw = be.cYaw; be.cYaw += (be.tYaw - be.cYaw) * k;
        be.pSlide0 = be.slide0; be.pSlide1 = be.slide1;
        be.slide0 = ScrewPropellerBlockEntity.approach(be.slide0, be.cPilot ? 1.0F : 0.0F, 1.0F / 10.0F);
        be.slide1 = ScrewPropellerBlockEntity.approach(be.slide1, be.cCopilot ? 1.0F : 0.0F, 1.0F / 10.0F);
        be.canopyK = ScrewPropellerBlockEntity.approach(be.canopyK, be.cPilot ? 0.0F : 1.0F, 1.0F / 24.0F);
        be.hudK = ScrewPropellerBlockEntity.approach(be.hudK, be.cAvionics ? 1.0F : 0.0F, 1.0F / 20.0F);
        if (be.cRadar && be.cAvionics) be.sweepT += 1.0;

        if (be.cTip != null) {
            float[] t = be.cTip;
            WingTrails.feed(lv, pos, be.tipPlot(t[0], t[1], t[2]), be.tipPlot(t[3], t[4], t[5]));
        }
    }

    private double[] tipPlot(float lx, float ly, float lz) {
        Vec3 p = plotPoint(lx, ly, lz);
        return new double[] { p.x, p.y, p.z };
    }

    boolean hudLitShown() { return hudK > 0.5F; }

    private static float ease(float x) { x = Mth.clamp(x, 0.0F, 1.0F); return x * x * (3.0F - 2.0F * x); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float rx = ScrewPropellerBlockEntity.RX, rz = ScrewPropellerBlockEntity.RZ;
        float pi = Mth.lerp(partialTick, pPitch, cPitch), ro = Mth.lerp(partialTick, pRoll, cRoll);
        model.getBone("stick").ifPresent(b -> { b.setRotX((float) Math.toRadians(STICK_DEG * pi) * rx); b.setRotZ((float) Math.toRadians(STICK_DEG * ro) * rz); });
        float th = Mth.lerp(partialTick, pThr, cThr);
        float slide = cAb ? AB_PX : THROTTLE_PX * Mth.clamp(th, 0.0F, 1.0F);
        model.getBone("throttle").ifPresent(b -> b.setPosZ(slide));
        float c = ease(Mth.lerp(partialTick, pCanopyK, canopyK));
        model.getBone("canopy").ifPresent(b -> { b.setHidden(cEjected); b.setRotX((float) Math.toRadians(-CANOPY_DEG * c) * rx); });
        model.getBone("glass").ifPresent(b -> b.setHidden(cEjected));
        model.getBone("seat").ifPresent(b -> b.setHidden(cEjected));
        model.getBone("seat2").ifPresent(b -> b.setHidden(cEjected));
        boolean lit = Mth.lerp(partialTick, pHudK, hudK) > 0.5F || (hudK > 0.05F && ((long) (tt * 3)) % 2 == 0);
        for (String s : new String[] { "mfd_l", "mfd_r", "hud", "wso" }) model.getBone(s).ifPresent(b -> b.setHidden(!lit));
        if (deck()) {
            float ya = Mth.lerp(partialTick, pYaw, cYaw);
            for (String sd : new String[] { "l", "r" }) {
                model.getBone("yoke_" + sd).ifPresent(b -> b.setRotX((float) Math.toRadians(YOKE_DEG * pi) * rx));
                model.getBone("wheel_" + sd).ifPresent(b -> b.setRotZ((float) Math.toRadians(-WHEEL_DEG * ro) * rz));
                model.getBone("ped_" + sd + "1").ifPresent(b -> b.setRotX((float) Math.toRadians(PEDAL_DEG * ya) * rx));
                model.getBone("ped_" + sd + "2").ifPresent(b -> b.setRotX((float) Math.toRadians(-PEDAL_DEG * ya) * rx));
            }
            float th2 = Mth.clamp(Mth.lerp(partialTick, pThr, cThr), 0.0F, 1.0F);
            for (int k2 = 0; k2 < 4; k2++) model.getBone("lever" + k2).ifPresent(b -> b.setRotX((float) Math.toRadians(LEVER_DEG * (1.0F - 2.0F * th2)) * rx));
            float s0 = ease(Mth.lerp(partialTick, pSlide0, slide0)), s1 = ease(Mth.lerp(partialTick, pSlide1, slide1));
            model.getBone("seat").ifPresent(b -> b.setPosZ(-Balance.FDK_SEAT_BACK * (1.0F - s0)));
            model.getBone("seat2").ifPresent(b -> b.setPosZ(-Balance.FDK_SEAT_BACK * (1.0F - s1)));
            float wipe = getLevel() != null && getLevel().isRaining() ? (float) (0.5 - 0.5 * Math.cos(tt * 3.4)) : 0.0F;
            model.getBone("wiper_l").ifPresent(b -> b.setRotZ((float) Math.toRadians(WIPER_DEG * wipe) * rz));
            model.getBone("wiper_r").ifPresent(b -> b.setRotZ((float) Math.toRadians(-WIPER_DEG * wipe) * rz));
            model.getBone("ap_screen").ifPresent(b -> b.setHidden(!lit || !cAp));
            model.getBone("overhead").ifPresent(b -> b.setHidden(!lit));
        }
        model.getBone("sweep").ifPresent(b -> b.setRotZ((float) Math.toRadians(30.0 * Math.sin((sweepT + partialTick) * 0.12)) * rz));
        model.getBone("lockbox").ifPresent(b -> b.setHidden(!lit || !cLock || ((long) (tt * 4)) % 2 == 0));
        model.getBone("caution").ifPresent(b -> b.setHidden(!lit || !cWarn || ((long) (tt * 6)) % 2 == 0));
        float arrow = ClientCockpitBridge.threatBearing(worldPosition);
        model.getBone("rwr_arrow").ifPresent(b -> { b.setHidden(!lit || Float.isNaN(arrow)); if (!Float.isNaN(arrow)) b.setRotZ((float) Math.toRadians(-arrow) * rz); });
        if (lit && ClientPodPage.pageOn(this)) {
            model.getBone("mfd_r").ifPresent(b -> b.setHidden(true));
            model.getBone("rwr_arrow").ifPresent(b -> b.setHidden(true));
        }
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    private static final PortMap PORT_MAP = new PortMap(0, 1, 2);
    @Override protected SoundEvent runSound() { return WmdSounds.COCKPIT_SWITCH.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override public List<Component> guiInfo() { return List.of(); }

    @Override
    public Component status() {
        return Component.translatable("message.wmd.cockpit.status", Math.round(throttle * 100.0F), ab ? " AB" : "", Component.translatable(avionics ? "message.wmd.cockpit.avionics_on" : "message.wmd.cockpit.avionics_off"),
                Component.translatable("gui.wmd.cockpit.tint" + tint), Component.translatable(ejected ? "message.wmd.cockpit.ejected" : "message.wmd.cockpit.armed"));
    }

    @Override protected String detail() { return "thr " + Math.round(throttle * 100) + "%" + (ab ? " AB" : "") + ", pitch " + Math.round(pitch * 100) + "%, roll " + Math.round(roll * 100) + "%" + (ejected ? ", ejected" : ""); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        hull.save(tag, registries);
        if (!loadout.isEmpty()) tag.put("wmdLoadout", loadout);
        tag.put("wmdAp", ap.save()); tag.putInt("wmdApMode", ap.mode);
        tag.putLong("jcChg", changed);
        tag.putInt("jcStn", station);
        for (int i = 0; i < 2; i++) if (seatIds[i] != null) tag.putUUID("jcSeat" + i, seatIds[i]);
    }

    private void put(CompoundTag tag) {
        tag.putFloat("jcPit", pitch);
        tag.putFloat("jcRol", roll);
        tag.putFloat("jcThr", throttle);
        tag.putBoolean("jcAb", ab);
        tag.putInt("jcTint", tint);
        tag.putBoolean("jcEj", ejected);
        tag.putBoolean("jcRad", radarOn);
        tag.putBoolean("jcAv", avionics);
        tag.putBoolean("jcP0", manned[0]);
        tag.putBoolean("jcLock", lockId != null);
        tag.putFloat("jcYaw", yaw); tag.putBoolean("jcP1", manned[1]); tag.putBoolean("jcAp", ap.on());
        tag.putBoolean("jcWarn", lockId != null);
        if (nav != null && !navOff) { int[] n = new int[9]; for (int i = 0; i < 9; i++) n[i] = Float.floatToIntBits(nav[i]); tag.putIntArray("jcNav", n); }
        if (nav != null) { int[] n = new int[6]; for (int i = 0; i < 6; i++) n[i] = Float.floatToIntBits(nav[i]); tag.putIntArray("jcTip", n); }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        hull.load(tag, registries, client);
        if (tag.contains("wmdLoadout")) loadout = tag.getCompound("wmdLoadout");
        if (tag.contains("wmdAp")) { ap.load(tag.getCompound("wmdAp")); int m = Mth.clamp(tag.getInt("wmdApMode"), Autopilot.OFF, Autopilot.PARKED); ap.mode = m == Autopilot.PARKED ? Autopilot.OFF : m; ap.phase = Autopilot.PH_RETURN; }
        tPitch = tag.getFloat("jcPit"); tRoll = tag.getFloat("jcRol"); tThr = tag.getFloat("jcThr"); cAb = tag.getBoolean("jcAb");
        cTint = Mth.clamp(tag.getInt("jcTint"), 0, 2); cEjected = tag.getBoolean("jcEj"); cRadar = tag.getBoolean("jcRad");
        cAvionics = tag.getBoolean("jcAv"); cPilot = tag.getBoolean("jcP0"); tYaw = tag.getFloat("jcYaw"); cCopilot = tag.getBoolean("jcP1"); cAp = tag.getBoolean("jcAp"); cLock = tag.getBoolean("jcLock"); cWarn = tag.getBoolean("jcWarn");
        int[] nv = tag.getIntArray("jcNav");
        cNav = null;
        if (nv.length == 9) { cNav = new float[9]; for (int i = 0; i < 9; i++) cNav[i] = Float.intBitsToFloat(nv[i]); }
        int[] tv = tag.getIntArray("jcTip");
        cTip = null;
        if (tv.length == 6) { cTip = new float[6]; for (int i = 0; i < 6; i++) cTip[i] = Float.intBitsToFloat(tv[i]); }
        if (client) return;
        pitch = Mth.clamp(tPitch, -1.0F, 1.0F); roll = Mth.clamp(tRoll, -1.0F, 1.0F); throttle = Mth.clamp(tThr, 0.0F, 1.0F); ab = cAb;
        tint = cTint; ejected = cEjected; radarOn = !tag.contains("jcRad") || cRadar;
        changed = tag.getLong("jcChg"); station = Mth.clamp(tag.getInt("jcStn"), 0, Balance.JET_WEAPON_ENTRIES - 1);
        for (int i = 0; i < 2; i++) seatIds[i] = tag.hasUUID("jcSeat" + i) ? tag.getUUID("jcSeat" + i) : null;
        cPitch = pPitch = tPitch; cRoll = pRoll = tRoll; cThr = pThr = tThr;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        hull.putSync(tag);
        return tag;
    }
}
