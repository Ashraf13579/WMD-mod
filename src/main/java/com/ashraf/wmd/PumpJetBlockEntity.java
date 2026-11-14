package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class PumpJetBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, ShipEngine, DriveEngine, CopycatHull.Owner {

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).power(0, 0, 0, Direction.NORTH, false);

    static final double AXIS_Y = 1.5, NOZZLE_Z = 35.0 / 16.0, EXIT_Z = 2.45, EXIT_R = 0.95, INLET_Z = 1.45;
    static final int VANES = 9, FLAPS = 4;

    static final float[] SY = { -1, 1, 1, -1 }, SP = { -1, -1, 1, 1 };
    static final int STOP = Balance.PROP_STEP_STOP;
    private static final double ASTERN_MAX = -Balance.PROP_STEP_FRAC[0];

    private int step = STOP, source = -1, stopTicks, redstone;
    private double throttle;
    private float rudderOrd, planesOrd, rpm, rudder, planes, vanes;
    private boolean engaged, wet;
    private final CopycatHull hull = new CopycatHull();

    private UUID ship;
    private double speed;
    private volatile double[] force;
    private int sentStep = -99;
    private float sentRpm = -9, sentRudder = 999, sentPlanes = 999, sentVanes = 999;
    private boolean sentWet, sentEngaged;

    private int cStep = STOP, cSourceShown = -1;
    private boolean cWet;
    private float tRpm, tRudder, tPlanes, tVanes, cRpm, cRudder, cPlanes, cVanes, pRudder, pPlanes, pVanes;
    private double rotorAngle, pRotor;
    private long nextServo;
    private float servoFrom;

    public PumpJetBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.PUMP_JET_BE.get(), pos, state, Balance.PJ_FE, (Balance.PJ_FE + Balance.PJ_FE_LOAD) * 400, (Balance.PJ_FE + Balance.PJ_FE_LOAD) * 10);
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        surfaces();
        boolean centred = step == STOP && Math.abs(rudderOrd) < 0.01F && Math.abs(planesOrd) < 0.01F;
        stopTicks = centred ? stopTicks + 1 : 0;
        engaged = !(stopTicks > Balance.PJ_DECLUTCH && Math.abs(rpm) < 0.01F && Math.abs(rudder) < 0.5F && Math.abs(planes) < 0.5F);
        if (!engaged) {
            rpm = toward(rpm, 0.0F);
            vanes = ScrewPropellerBlockEntity.approach(vanes, 0.0F, (float) Balance.PJ_VANE_RATE * 0.05F);
            after(sl);
            return false;
        }
        boolean load = Math.abs(throttle) < 1.0E-3 || useExtraEnergy((int) Math.round(Balance.PJ_FE_LOAD * Math.abs(throttle)));
        float target = load ? targetRpm(throttle) : 0.0F;
        rpm = toward(rpm, target);
        vanes = ScrewPropellerBlockEntity.approach(vanes, vaneFor(target), (float) Balance.PJ_VANE_RATE * 0.05F);
        after(sl);
        return true;
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        orders(sl);
        engaged = false;
        rpm = toward(rpm, 0.0F);
        after(sl);
    }

    private void surfaces() {
        float dt = 0.05F;
        rudder = ScrewPropellerBlockEntity.approach(rudder, (float) (rudderOrd * Balance.PROP_RUDDER_MAX), (float) Balance.PROP_SURFACE_RATE * dt);
        planes = ScrewPropellerBlockEntity.approach(planes, (float) (planesOrd * Balance.PROP_PLANES_MAX), (float) Balance.PROP_SURFACE_RATE * dt);
    }

    static float toward(float cur, float target) {
        boolean same = cur == 0.0F || target == 0.0F || Math.signum(target) == Math.signum(cur);
        float down = 1.0F / Balance.PJ_SPOOL_DOWN;
        if (!same) {
            float n = cur - Math.signum(cur) * down;
            return Math.signum(n) != Math.signum(cur) ? 0.0F : n;
        }
        float rate = Math.abs(target) > Math.abs(cur) ? 1.0F / Balance.PJ_SPOOL_UP : down;
        return ScrewPropellerBlockEntity.approach(cur, target, rate);
    }

    static float targetRpm(double thr) { return (float) (thr >= 0.0 ? thr : thr / ASTERN_MAX); }

    static double share(float rpm) { return rpm >= 0.0F ? rpm : rpm * Balance.PJ_ASTERN_SHARE; }

    static float vaneFor(float target) { return target > 0.01F ? (float) Balance.PJ_VANE_AHEAD : target < -0.01F ? (float) Balance.PJ_VANE_ASTERN : 0.0F; }

    static float[] nozzle(float rudderDeg, float planesDeg) {
        double yaw = rudderDeg / Balance.PROP_RUDDER_MAX * Balance.PJ_VEC_MAX, pitch = -planesDeg / Balance.PROP_PLANES_MAX * Balance.PJ_VEC_MAX;
        double m = Math.hypot(yaw, pitch);
        if (m > Balance.PJ_VEC_MAX) { yaw *= Balance.PJ_VEC_MAX / m; pitch *= Balance.PJ_VEC_MAX / m; }
        return new float[] { (float) yaw, (float) pitch };
    }

    static float flap(int k, float rudderDeg, float planesDeg) {
        return (float) Mth.clamp(rudderDeg * SY[k] + planesDeg * SP[k], -Balance.PJ_FLAP_MAX, Balance.PJ_FLAP_MAX);
    }

    private void after(ServerLevel sl) {
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) beat(sl);
        updateForce();
        syncMaybe(now);
    }

    private void orders(ServerLevel sl) {
        if (sl.getGameTime() % 10 == (worldPosition.asLong() & 7)) redstone = redstoneIn(sl);
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        rudderOrd = o == null ? 0.0F : o.rudder();
        planesOrd = o == null ? 0.0F : o.planes();
        if (redstone > 0) { throttle = redstone / 15.0; step = ShipControl.stepOf(throttle); source = 0; }
        else if (o != null) { throttle = o.throttle(); step = o.step(); source = o.source(); }
        else { throttle = 0.0; step = STOP; source = -1; }
    }

    private int redstoneIn(ServerLevel sl) {
        int best = 0;
        Direction m = Machines.mount(getBlockState());
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++) best = Math.max(best, sl.getBestNeighborSignal(Machines.toWorld(worldPosition, facing(), m, x, y, 0)));
        return best;
    }

    private void beat(ServerLevel sl) {
        ship = SableBridge.shipIdAt(sl, worldPosition);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        speed = info == null ? 0.0 : info.speed();
        double[] d = worldPoint(0.0, AXIS_Y, EXIT_Z);
        Vec3 exit = new Vec3(d[0], d[1], d[2]);
        wet = sl.getFluidState(BlockPos.containing(exit)).is(FluidTags.WATER);
        double sh = wet ? share(rpm) : 0.0;
        if (ship != null) ShipControl.answer(sl, ship, worldPosition, ShipControl.stepOf(sh), wet && Math.abs(rpm) > 0.3F && engaged, sh, rudder, planes);
        if (wet && Math.abs(rpm) > 0.02F) {
            double db = Balance.PJ_DB_IDLE * Math.min(1.0, Math.abs(rpm) * 2.0) + Balance.PJ_DB_LOAD * Math.abs(sh);
            if (ship != null) db = Math.max(1.0, db - Anechoic.noiseCut(sl, ship));
            NoiseNet.emit(sl, this, exit, db, NoiseNet.Kind.ENGINE, null, ship);
        } else NoiseNet.silence(sl, this);
    }

    double thrustNow() { return Balance.PJ_THRUST * share(rpm); }

    private void updateForce() {
        if (ship == null || !wet) { force = null; return; }
        double thrust = thrustNow();
        double v2 = speed * speed;
        float[] nz = nozzle(rudder, planes);
        double side = (Balance.PROP_RUDDER_WASH * thrust + Balance.PROP_RUDDER_SPEED * v2) * Math.sin(Math.toRadians(rudder))
                + Balance.PJ_VEC_SIDE * Math.abs(thrust) * Math.sin(Math.toRadians(nz[0]));
        double vert = (Balance.PROP_PLANES_WASH * thrust + Balance.PROP_PLANES_SPEED * v2) * Math.sin(Math.toRadians(planes))
                + Balance.PJ_VEC_SIDE * Math.abs(thrust) * Math.sin(Math.toRadians(-nz[1]));
        if (Math.abs(thrust) < 1.0E-4 && Math.abs(side) < 1.0E-4 && Math.abs(vert) < 1.0E-4) { force = null; return; }
        Direction m = Machines.mount(getBlockState());
        Vec3 fwd = unit(Machines.dirToWorld(facing(), m, Direction.NORTH)), port = unit(Machines.dirToWorld(facing(), m, Direction.WEST)), up = unit(Machines.dirToWorld(facing(), m, Direction.UP));

        Vec3 f = fwd.scale(thrust).add(port.scale(side)).add(up.scale(vert));
        Vec3 p = plotPoint(0.0, AXIS_Y, NOZZLE_Z);
        force = new double[] { p.x, p.y, p.z, f.x, f.y, f.z };
    }

    private static Vec3 unit(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    @Override public double[] engineForce() { return force; }

    private void syncMaybe(long now) {
        boolean big = step != sentStep || wet != sentWet || engaged != sentEngaged || (Math.abs(rpm) >= 1.0F) != (Math.abs(sentRpm) >= 1.0F)
                || (rpm == 0.0F) != (sentRpm == 0.0F) || Math.signum(rpm) != Math.signum(sentRpm);
        boolean small = Math.abs(rpm - sentRpm) > 0.08F || Math.abs(rudder - sentRudder) > 2.0F || Math.abs(planes - sentPlanes) > 2.0F || Math.abs(vanes - sentVanes) > 2.0F;
        if (!big && !(small && now % 4 == 0)) return;
        sentStep = step; sentWet = wet; sentEngaged = engaged; sentRpm = rpm; sentRudder = rudder; sentPlanes = planes; sentVanes = vanes;
        syncNow();
    }

    private void syncNow() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public CopycatHull hull() { return hull; }
    @Override public void hullChanged() { syncNow(); }

    public BlockState copyShown() { return hull.shown(); }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        net.minecraft.world.item.ItemStack s = hull.take();
        if (!s.isEmpty()) l.add(s);
        return l;
    }

    static void clientTickJet(Level lv, BlockPos pos, BlockState state, PumpJetBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.easeTick();
    }

    private void easeTick() {
        pRudder = cRudder; pPlanes = cPlanes; pVanes = cVanes; pRotor = rotorAngle;
        float k = 1.0F / 20.0F;
        cRudder = ScrewPropellerBlockEntity.approach(cRudder, tRudder, (float) Balance.PROP_SURFACE_RATE * k * 1.3F);
        cPlanes = ScrewPropellerBlockEntity.approach(cPlanes, tPlanes, (float) Balance.PROP_SURFACE_RATE * k * 1.3F);
        cVanes = ScrewPropellerBlockEntity.approach(cVanes, tVanes, (float) Balance.PJ_VANE_RATE * k * 1.3F);
        cRpm = toward(cRpm, tRpm);
        if (Math.abs(cRpm - tRpm) > 0.15F) cRpm += (tRpm - cRpm) * 0.1F;

        float a = Math.abs(cRpm), e = a * a * (3.0F - 2.0F * a);
        rotorAngle += Math.signum(cRpm) * Math.PI * 2.0 * 1.5 / 20.0 * e;
        if (Math.abs(rotorAngle) > Math.PI * 2000.0) { double s = Math.signum(rotorAngle) * Math.PI * 2000.0; rotorAngle -= s; pRotor -= s; }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float rx = ScrewPropellerBlockEntity.RX, ry = ScrewPropellerBlockEntity.RY, rz = ScrewPropellerBlockEntity.RZ;
        float rot = (float) Mth.lerp(partialTick, pRotor, rotorAngle) * rz;
        model.getBone("rotor").ifPresent(b -> b.setRotZ(rot));
        model.getBone("shaft").ifPresent(b -> b.setRotZ(rot));
        float va = (float) Math.toRadians(Mth.lerp(partialTick, pVanes, cVanes)) * ry;
        for (int k = 0; k < VANES; k++) model.getBone("stator" + k).ifPresent(b -> b.setRotY(va));
        float ru = Mth.lerp(partialTick, pRudder, cRudder), pl = Mth.lerp(partialTick, pPlanes, cPlanes);
        for (int k = 0; k < FLAPS; k++) {
            float f = (float) Math.toRadians(flap(k, ru, pl)) * ry;
            model.getBone("flap" + k).ifPresent(b -> b.setRotY(f));
        }
        float[] nz = nozzle(ru, pl);
        model.getBone("nozzle").ifPresent(b -> { b.setRotX((float) Math.toRadians(nz[1]) * rx); b.setRotY((float) Math.toRadians(nz[0]) * ry); });

        boolean blink = Math.abs(cRpm) < Math.abs(tRpm) - 0.05F && ((long) tt / 10) % 2 == 0;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!isRunning() || blink));
    }

    @Override
    protected void clientEffects(Level lv) {
        long now = lv.getGameTime();
        if (Math.abs(tVanes - servoFrom) > 3.0F && now >= nextServo) { servoFrom = tVanes; nextServo = now + 20; playOnce(lv, WmdSounds.PUMP_JET_SERVO.get(), Balance.SND_CLICK, 1.0F); }
        if (!cWet || Math.abs(cRpm) < 0.05F) return;
        var r = lv.random;
        double sh = share(cRpm);
        float[] nz = nozzle(cRudder, cPlanes);

        double[] c = worldPoint(0.0, AXIS_Y, EXIT_Z);
        double yaw = Math.toRadians(nz[0]), pitch = Math.toRadians(nz[1]);
        double[] a = worldPoint(-Math.sin(yaw), AXIS_Y + Math.sin(pitch), EXIT_Z + Math.cos(yaw) * Math.cos(pitch));
        Vec3 aft = new Vec3(a[0] - c[0], a[1] - c[1], a[2] - c[2]);
        int n = (int) Math.round(Math.abs(sh) * 3.0 + (r.nextFloat() < Math.abs(sh) * 3.0 % 1.0 ? 1 : 0));
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0, rad = Math.sqrt(r.nextDouble()) * EXIT_R * 0.6;
            if (sh >= 0.0) {
                double[] q = worldPoint(Math.cos(ang) * rad, AXIS_Y + Math.sin(ang) * rad, EXIT_Z + 0.1);
                double sp = 0.35 + 0.45 * sh;
                lv.addParticle(ParticleTypes.BUBBLE, q[0], q[1], q[2], aft.x * sp, aft.y * sp + 0.01, aft.z * sp);
            } else {
                double[] q = worldPoint(Math.cos(ang) * EXIT_R * 1.3, AXIS_Y + Math.sin(ang) * EXIT_R * 1.3, EXIT_Z + 0.6);
                lv.addParticle(ParticleTypes.BUBBLE, q[0], q[1], q[2], -aft.x * 0.2, -aft.y * 0.2, -aft.z * 0.2);
            }
        }
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.PUMP_JET_START.get(), Balance.SND_CLICK, 1.0F); }
    @Override protected void runStopped(Level lv) { playOnce(lv, WmdSounds.PUMP_JET_STOP.get(), Balance.SND_CLICK, 1.0F); }
    @Override protected SoundEvent runSound() { return WmdSounds.PUMP_JET_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL * (0.4F + 0.6F * Math.min(1.0F, Math.abs(cRpm) + 0.2F)); }
    @Override protected float soundPitch() { return 0.7F + 0.45F * Math.abs(cRpm); }
    @Override protected boolean soundAlive() { return isRunning() || Math.abs(cRpm) > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        int maxFe = (Balance.PJ_FE + Balance.PJ_FE_LOAD) * 20;
        return List.of(Component.translatable("gui.wmd.info.screw_use", Balance.PJ_FE * 20, maxFe),
                Component.translatable("gui.wmd.screw.order", Component.translatable("gui.wmd.prop.step" + cStep), ScrewPropellerBlockEntity.sourceName(cSourceShown)),
                Component.translatable("gui.wmd.pump_jet.state", Math.round(cRpm * 100.0F), Math.round(cVanes), Math.round(cRudder), Math.round(cPlanes)),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : cWet ? "gui.wmd.pump_jet.wet" : "gui.wmd.screw.dry"));
    }

    @Override protected String detail() {
        return "step " + step + ", rotor " + Math.round(rpm * 100) + "%, vanes " + Math.round(vanes) + ", rudder " + Math.round(rudder) + ", planes " + Math.round(planes)
                + (wet ? "" : " (dry)") + (engaged ? "" : " (declutched)");
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.NORTH) ? energy() : null; }

    @Override public boolean driveFlange(BlockPos part, Direction side) { return at(part, side, 0, 1, 0, Direction.NORTH); }
    @Override public int driveIn(int fe, boolean simulate) { return fe <= 0 ? 0 : energy().receiveEnergy(fe, simulate); }
    @Override public int driveDemand() { return engaged ? (int) Math.round(Balance.PJ_FE + Balance.PJ_FE_LOAD * Math.abs(throttle)) : 0; }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) NoiseNet.silence(sl, this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        hull.save(tag, registries);
        tag.putInt("pjStop", stopTicks);
        tag.putDouble("pjThr", throttle);
    }

    private void put(CompoundTag tag) {
        tag.putInt("pjStep", step);
        tag.putInt("pjSrc", source);
        tag.putFloat("pjRpm", rpm);
        tag.putFloat("pjRud", rudder);
        tag.putFloat("pjPln", planes);
        tag.putFloat("pjVan", vanes);
        tag.putBoolean("pjWet", wet);
        tag.putBoolean("pjEng", engaged);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = ShipControl.clampStep(tag.contains("pjStep") ? tag.getInt("pjStep") : STOP);
        float rp = Mth.clamp(tag.getFloat("pjRpm"), -1.0F, 1.0F);
        float ru = Mth.clamp(tag.getFloat("pjRud"), (float) -Balance.PROP_RUDDER_MAX, (float) Balance.PROP_RUDDER_MAX);
        float pl = Mth.clamp(tag.getFloat("pjPln"), (float) -Balance.PROP_PLANES_MAX, (float) Balance.PROP_PLANES_MAX);
        float va = Mth.clamp(tag.getFloat("pjVan"), (float) Balance.PJ_VANE_ASTERN, (float) Balance.PJ_VANE_AHEAD);
        boolean client = level != null && level.isClientSide;
        hull.load(tag, registries, client);
        if (client) {
            cStep = st; tRpm = rp; tRudder = ru; tPlanes = pl; tVanes = va; cWet = tag.getBoolean("pjWet"); cSourceShown = tag.getInt("pjSrc");
            return;
        }
        step = st; rpm = rp; rudder = ru; planes = pl; vanes = va; wet = tag.getBoolean("pjWet"); engaged = tag.getBoolean("pjEng"); source = tag.getInt("pjSrc");
        stopTicks = tag.getInt("pjStop");
        throttle = Mth.clamp(tag.getDouble("pjThr"), -ASTERN_MAX, 1.0);

        cStep = st; cWet = wet; cSourceShown = source; tRpm = cRpm = rp; tRudder = cRudder = pRudder = ru; tPlanes = cPlanes = pPlanes = pl; tVanes = cVanes = pVanes = va;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        hull.putSync(tag);
        return tag;
    }
}
