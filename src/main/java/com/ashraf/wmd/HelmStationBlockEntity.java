package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class HelmStationBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, LoadoutHolder {

    private CompoundTag loadout = new CompoundTag();
    @Override public CompoundTag loadoutTag() { return loadout; }
    @Override public void setLoadoutTag(CompoundTag t) { loadout = t == null ? new CompoundTag() : t; setChanged(); }

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).power(0, 0, 1, Direction.SOUTH, false);
    static final int STOP = Balance.PROP_STEP_STOP;

    static final double SEAT_X = 0.5, SEAT_Y = 0.62, SEAT_Z = 0.22, STAND_Z = -0.95;
    static final float SEAT_OUT = 20.0F, SEAT_IN = 2.4F, YOKE_TRAVEL = 2.0F, YOKE_TURN = 60.0F, THROTTLE_DEG = 10.0F;

    static final float AXIS_TICKS = 30.0F;

    private int step = STOP;
    private float rudder, planes;
    private long changed;
    private UUID seatId;

    private int axisR, axisP, axisB;
    private long lastInput;
    private UUID ship;
    private double ballast = -2.0, ballastSent = -2.0;
    private boolean manned, sentManned;
    private int sentStep = -1;
    private float sentRudder = 9, sentPlanes = 9;

    private boolean cManned, cPowered;
    private int cStep = STOP;
    private float tRudder, tPlanes, cRudder, cPlanes, pRudder, pPlanes, seatK, pSeatK, gRud, gPln;
    private float throttleFrom, throttleTo;
    private double throttleT0 = -100, bootT0 = -100, lastTt;
    private float tReal, tPlnReal;

    public HelmStationBlockEntity(BlockPos pos, BlockState state) { super(Wmd.HELM_STATION_BE.get(), pos, state, Balance.HELM_FE); }

    Vec3 seatPoint() { return plotPoint(SEAT_X, SEAT_Y, SEAT_Z); }

    Vec3 standPoint() {
        Vec3 p = plotPoint(SEAT_X, 0.05, STAND_Z);
        return level == null ? p : SableBridge.toWorld(level, p);
    }

    private HelmSeatEntity seat(ServerLevel sl) {
        if (seatId == null) return null;
        Entity e = sl.getEntity(seatId);
        return e instanceof HelmSeatEntity s && s.isAlive() ? s : null;
    }

    void sit(ServerPlayer p) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        HelmSeatEntity s = seat(sl);
        if (s != null && s.isVehicle()) { p.displayClientMessage(Component.translatable("message.wmd.helm.taken"), true); return; }
        if (s == null) {
            s = new HelmSeatEntity(Wmd.HELM_SEAT.get(), sl);
            s.setHelm(worldPosition);
            Vec3 at = seatPoint();
            Direction ahead = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.SOUTH);
            s.moveTo(at.x, at.y, at.z, ahead.toYRot(), 0.0F);
            sl.addFreshEntity(s);
            seatId = s.getUUID();
        }
        if (!p.startRiding(s, true)) return;
        manned = true;
        lastInput = sl.getGameTime();
        sl.playSound(null, worldPosition, WmdSounds.HELM_SEAT_IN.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        if (hasPower()) sl.playSound(null, worldPosition.above(), WmdSounds.HELM_BOOT.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        p.displayClientMessage(Component.translatable("message.wmd.helm.seated"), true);
        ship = SableBridge.shipIdAt(sl, worldPosition);
        hud(sl, p);
        sync();
    }

    void left(Entity who) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        manned = false;
        axisR = axisP = axisB = 0;
        sl.playSound(null, worldPosition, WmdSounds.HELM_SEAT_OUT.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F);
        sync();
    }

    void input(ServerPlayer p, int throttle, int rudderAxis, int planesAxis, int bowAxis, boolean leave) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (leave) { p.stopRiding(); return; }
        lastInput = sl.getGameTime();
        if (pilot.on() && (throttle != 0 || rudderAxis != 0 || planesAxis != 0 || bowAxis != 0)) apLetGo(sl, SeaPilot.R_HELM);
        axisR = rudderAxis;
        axisP = planesAxis;
        if (bowAxis != axisB) { axisB = bowAxis; publish(sl); }
        if (throttle != 0 && hasPower()) {
            int n = ShipControl.clampStep(step + throttle);
            if (n != step) {
                step = n;
                changed = sl.getGameTime();
                sl.playSound(null, worldPosition, WmdSounds.HELM_STEP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.9F + 0.05F * (n - STOP));
                publish(sl);
                sync();
            } else if (throttle > 0 && ship != null) {
                ShipControl.boost(sl, ship);
                sl.playSound(null, worldPosition, WmdSounds.HELM_STEP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.4F);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.telegraph.boost"), true);
            }
        }
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        HelmSeatEntity s = seat(sl);
        boolean riding = s != null && s.isVehicle() && s.getFirstPassenger() instanceof ServerPlayer;
        if (riding != manned) { manned = riding; if (!riding) { axisR = axisP = 0; } sync(); }
        if (now - lastInput > 20) { axisR = 0; axisP = 0; axisB = 0; }
        if (apWork(sl, now, s)) return true;
        float k = 1.0F / AXIS_TICKS;
        float r = ScrewPropellerBlockEntity.approach(rudder, axisR, k), pl = ScrewPropellerBlockEntity.approach(planes, axisP, k);
        boolean moved = Math.abs(r - rudder) > 1.0E-5F || Math.abs(pl - planes) > 1.0E-5F;
        rudder = r; planes = pl;
        if (moved) changed = now;
        if (moved && now % 2 == 0) publish(sl);
        else if ((now + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) publish(sl);
        if (axisP != 0) ballast(sl, now);
        if (manned && now % Balance.HELM_HUD_TICKS == 0 && s.getFirstPassenger() instanceof ServerPlayer p) hud(sl, p);
        if (Math.abs(rudder - sentRudder) > 0.04F || Math.abs(planes - sentPlanes) > 0.04F || (moved && (rudder == 0.0F || planes == 0.0F) && (sentRudder != rudder || sentPlanes != planes))) sync();
        return manned;
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        if (pilot.on()) apLetGo(sl, SeaPilot.R_POWER);
        HelmSeatEntity s = seat(sl);
        if (s != null && s.getFirstPassenger() instanceof ServerPlayer p && sl.getGameTime() % Balance.HELM_HUD_TICKS == 0) hud(sl, p);
    }

    private void publish(ServerLevel sl) {
        ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null) ShipControl.publish(sl, ship, worldPosition, ShipControl.SRC_HELM, step, rudder, planes, 0.0F, changed);
        if (ship != null) ShipControl.publishBow(sl, ship, manned ? axisB : 0);
    }

    private void ballast(ServerLevel sl, long now) {
        if (ship == null || !DeepSeasBridge.isSub(ship)) return;
        if (ballast < -1.0) { ballast = DeepSeasBridge.ballastFraction(sl, ship); ballastSent = ballast; if (ballast < 0.0) return; }
        ballast = Mth.clamp(ballast + axisP * Balance.HELM_BALLAST_RATE / 20.0, 0.0, 1.0);
        if (now % Balance.HELM_BALLAST_TICKS == 0 && Math.abs(ballast - ballastSent) > 0.004) {
            DeepSeasBridge.setBallast(sl, ship, ballast);
            ballastSent = ballast;
        }
    }

    private void hud(ServerLevel sl, ServerPlayer p) {
        boolean power = hasPower();
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        double[] a = worldPoint(SEAT_X, 0.5, 0.0), b = worldPoint(SEAT_X, 0.5, 1.0);
        double fx = b[0] - a[0], fz = b[2] - a[2];
        float heading = (float) ((Math.toDegrees(Math.atan2(fx, -fz)) + 360.0) % 360.0);
        double speed = 0.0;
        if (info != null) {
            Vec3 v = info.linearVelocity(), f = new Vec3(fx, b[1] - a[1], fz);
            double fl = f.length();
            speed = v.length() * (fl > 1.0E-6 && v.dot(f) < -0.05 * v.length() * fl ? -1.0 : 1.0);
        }
        DeepSeasBridge.SubState sub = ship == null ? null : DeepSeasBridge.state(ship);
        float depth = sub != null ? sub.depth() : (float) NavalLayer.depthBelowSurface(sl, new Vec3(a[0], a[1], a[2]));
        int crush = sub != null ? DeepSeasBridge.crushDepth(sl, ship) : -1;
        boolean air = sub == null && depth <= 0.0F;
        if (air) {
            int gx = net.minecraft.util.Mth.floor(a[0]), gz = net.minecraft.util.Mth.floor(a[2]);
            int ground = sl.hasChunk(gx >> 4, gz >> 4) ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz) : sl.getMinBuildHeight();
            depth = (float) a[1];
            crush = Math.max(0, (int) Math.floor(a[1] - ground));
        }
        ShipControl.Answer ans = ship == null ? null : ShipControl.answerOf(sl, ship);
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        int flags = (power ? HelmHudPayload.F_POWER : 0) | (air ? HelmHudPayload.F_AIR : 0) | (sub != null ? HelmHudPayload.F_SUB : 0) | (ship != null ? HelmHudPayload.F_SHIP : 0)
                | (o != null && worldPosition.equals(o.from()) ? HelmHudPayload.F_CONTROL : 0) | (o != null && o.source() == ShipControl.SRC_TELEGRAPH ? HelmHudPayload.F_TELEGRAPH : 0);
        flags |= ship == null ? 0 : Anchors.hudFlags(sl, ship);
        double bal = sub == null ? -1.0 : ballast >= 0.0 ? ballast : DeepSeasBridge.ballastFraction(sl, ship);
        if (sub != null && ballast < -1.0) { ballast = bal; ballastSent = bal; }
        PacketDistributor.sendToPlayer(p, new HelmHudPayload(worldPosition, depth, crush, heading, (float) speed, step, ans == null ? STOP : ans.step(),
                ans == null ? 0 : ans.engines(), ans == null ? 0 : ans.running(), rudder, ans == null ? 0.0F : (float) ans.rudder(), planes,
                ans == null ? 0.0F : (float) ans.planes(), (float) (ship == null ? 0.0 : NoiseNet.loudestAboard(sl, ship)), (float) bal, flags));
        apHud(sl, p);
    }

    private void sync() {
        sentManned = manned; sentStep = step; sentRudder = rudder; sentPlanes = planes;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) ShipControl.withdraw(sl, ship, worldPosition);
        super.setRemoved();
    }

    static void clientTickHelm(Level lv, BlockPos pos, BlockState state, HelmStationBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.pRudder = be.cRudder; be.pPlanes = be.cPlanes; be.pSeatK = be.seatK;
        be.cRudder = ScrewPropellerBlockEntity.approach(be.cRudder, be.tRudder, 1.0F / AXIS_TICKS * 1.4F);
        be.cPlanes = ScrewPropellerBlockEntity.approach(be.cPlanes, be.tPlanes, 1.0F / AXIS_TICKS * 1.4F);
        be.seatK = ScrewPropellerBlockEntity.approach(be.seatK, be.cManned ? 1.0F : 0.0F, 1.0F / 10.0F);
        be.gRud += (be.tReal - be.gRud) * 0.2F;
        be.gPln += (be.tPlnReal - be.gPln) * 0.2F;
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0.0F, 1.0F); return x * x * (3.0F - 2.0F * x); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        lastTt = tt;
        float rx = ScrewPropellerBlockEntity.RX, ry = ScrewPropellerBlockEntity.RY, rz = ScrewPropellerBlockEntity.RZ;
        float s = smooth(Mth.lerp(partialTick, pSeatK, seatK));
        model.getBone("seat").ifPresent(b -> { b.setRotY((float) Math.toRadians(SEAT_OUT * (1.0F - s)) * ry); b.setPosZ(SEAT_IN * s); });
        float ru = Mth.lerp(partialTick, pRudder, cRudder), pl = Mth.lerp(partialTick, pPlanes, cPlanes);
        model.getBone("yoke_col").ifPresent(b -> b.setPosZ(YOKE_TRAVEL * pl));
        model.getBone("yoke").ifPresent(b -> b.setRotZ((float) Math.toRadians(YOKE_TURN * ru) * rz));
        float x = (float) Mth.clamp((tt - throttleT0) / 4.0, 0.0, 1.0), c1 = 1.70158F, u = x - 1.0F;
        float th = throttleFrom + (throttleTo - throttleFrom) * (1.0F + (c1 + 1.0F) * u * u * u + c1 * u * u);
        model.getBone("throttle").ifPresent(b -> b.setRotX((float) Math.toRadians(th) * rx));
        double boot = tt - bootT0;
        boolean lit = cPowered && cManned && !(boot >= 0 && boot < 12 && ((long) boot % 3 == 1));
        model.getBone("screen").ifPresent(b -> b.setHidden(!lit));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!cPowered));
        model.getBone("g_rudder").ifPresent(b -> b.setRotZ((float) Math.toRadians(gRud / Balance.PROP_RUDDER_MAX * 120.0) * rz));
        model.getBone("g_planes").ifPresent(b -> b.setRotZ((float) Math.toRadians(gPln / Balance.PROP_PLANES_MAX * 120.0) * rz));
    }

    static float throttleDeg(int step) { return -(step - STOP) * THROTTLE_DEG; }

    private void clientUpdate(boolean manned, boolean powered, int st, float ru, float pl, float real, float plReal) {
        if (st != cStep || throttleT0 < -50) {
            float x = (float) Mth.clamp((lastTt - throttleT0) / 4.0, 0.0, 1.0);
            throttleFrom = throttleT0 < -50 ? throttleDeg(st) : throttleFrom + (throttleTo - throttleFrom) * x;
            throttleTo = throttleDeg(st);
            throttleT0 = throttleT0 < -50 ? -60 : lastTt;
            cStep = st;
        }
        if (manned && !cManned && powered) bootT0 = lastTt;
        cManned = manned; cPowered = powered; tRudder = ru; tPlanes = pl; tReal = real; tPlnReal = plReal;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.HELM_STEP.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override public List<Component> guiInfo() { return List.of(Component.translatable("gui.wmd.info.use", Balance.HELM_FE * 20)); }

    @Override
    public Component status() {
        return Component.translatable("message.wmd.helm.status", Component.translatable("gui.wmd.prop.step" + step), Math.round(rudder * (float) Balance.PROP_RUDDER_MAX),
                Math.round(planes * (float) Balance.PROP_PLANES_MAX), Component.translatable(hasPower() ? "message.wmd.unit.idle" : "message.wmd.unit.no_power"));
    }

    @Override protected String detail() { return "step " + step + ", rudder " + Math.round(rudder * 100) + "%, planes " + Math.round(planes * 100) + "%" + (manned ? ", manned" : ""); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putLong("hsChg", changed);
        if (!loadout.isEmpty()) tag.put("wmdLoadout", loadout);
        if (seatId != null) tag.putUUID("hsSeat", seatId);
        tag.put("wmdSeaPilot", pilot.save());
    }

    private void put(CompoundTag tag) {
        tag.putInt("hsStep", step);
        tag.putFloat("hsRud", rudder);
        tag.putFloat("hsPln", planes);
        tag.putBoolean("hsMan", manned);
        tag.putBoolean("hsPow", hasPower());
        if (level instanceof ServerLevel sl && ship != null) {
            ShipControl.Answer a = ShipControl.answerOf(sl, ship);
            if (a != null) { tag.putFloat("hsReal", (float) a.rudder()); tag.putFloat("hsPlnReal", (float) a.planes()); }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("wmdLoadout")) loadout = tag.getCompound("wmdLoadout");
        int st = ShipControl.clampStep(tag.contains("hsStep") ? tag.getInt("hsStep") : STOP);
        float ru = Mth.clamp(tag.getFloat("hsRud"), -1.0F, 1.0F), pl = Mth.clamp(tag.getFloat("hsPln"), -1.0F, 1.0F);
        clientUpdate(tag.getBoolean("hsMan"), tag.getBoolean("hsPow"), st, ru, pl, tag.getFloat("hsReal"), tag.getFloat("hsPlnReal"));
        if (level != null && level.isClientSide) return;
        step = st; rudder = ru; planes = pl; changed = tag.getLong("hsChg");
        seatId = tag.hasUUID("hsSeat") ? tag.getUUID("hsSeat") : null;
        cRudder = pRudder = ru; cPlanes = pPlanes = pl;
        if (tag.contains("wmdSeaPilot")) pilot.load(tag.getCompound("wmdSeaPilot"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }

    final SeaPilot pilot = new SeaPilot();

    SeaPilot.Ctx apCtx(ServerLevel sl) {
        UUID id = SableBridge.shipIdAt(sl, worldPosition);
        SableBridge.ShipInfo info = id == null ? null : SableBridge.shipInfo(sl, id);
        if (info == null) return null;
        double[] a = worldPoint(SEAT_X, 0.5, 0.0), b = worldPoint(SEAT_X, 0.5, 1.0);
        double fx = b[0] - a[0], fz = b[2] - a[2];
        double heading = (Math.toDegrees(Math.atan2(fx, -fz)) + 360.0) % 360.0;
        Vec3 at = new Vec3(a[0], a[1], a[2]);
        double spd = Crafts.speed(sl, id, null);
        if (!Double.isFinite(spd) || spd > 1.0E5) spd = 0.0;
        Vec3 v = info.linearVelocity();
        double fwd = v != null && v.x * fx + v.z * fz < 0.0 ? -spd : spd;
        DeepSeasBridge.SubState sub = DeepSeasBridge.state(id);
        double depth = sub != null ? sub.depth() : NavalLayer.depthBelowSurface(sl, at);
        int crush = sub != null ? DeepSeasBridge.crushDepth(sl, id) : -1;
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        double surface = sl.hasChunk(cx >> 4, cz >> 4) ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz) : sl.getSeaLevel();
        if (depth > 0.0) surface = at.y + depth;
        double draft = Math.max(0.0, surface - info.worldBounds().minY);
        return new SeaPilot.Ctx(sl, id, worldPosition, at, heading, fwd, spd, depth, sub != null || depth > 2.0, crush, draft, surface, hasPower(), sl.getGameTime());
    }

    private boolean apWork(ServerLevel sl, long now, HelmSeatEntity s) {
        if (!pilot.on()) {
            if (now % Balance.SP_TRACK_TICKS == 0) { SeaPilot.Ctx c = apCtx(sl); if (c != null) pilot.trackTick(c.at(), now); }
            return false;
        }
        if (!AirGate.on()) { apLetGo(sl, SeaPilot.R_OFF); return false; }
        SeaPilot.Ctx c = apCtx(sl);
        SeaPilot.Out o = pilot.step(c);
        if (c != null) pilot.trackTick(c.at(), now);
        if (o == null) { if (!pilot.on()) apRelease(sl, false); return false; }
        if (!pilot.on()) { apRelease(sl, true); return false; }
        int was = step;
        step = ShipControl.clampStep(o.step());
        rudder = o.rudder();
        planes = o.planes();
        if (step != was) sl.playSound(null, worldPosition, WmdSounds.HELM_STEP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.9F + 0.05F * (step - STOP));
        ship = c.ship();
        ShipControl.publish(sl, ship, worldPosition, ShipControl.SRC_AUTOPILOT, step, rudder, planes, 0.0F, pilot.engagedAt);
        ShipControl.publishBow(sl, ship, o.bow());
        if (DeepSeasBridge.isSub(ship) && Math.abs(o.planes()) > 0.3F) apBallast(sl, now, o.planes());
        if (s != null && s.isVehicle() && now % Balance.HELM_HUD_TICKS == 0 && s.getFirstPassenger() instanceof ServerPlayer p) hud(sl, p);
        if (Math.abs(rudder - sentRudder) > 0.04F || Math.abs(planes - sentPlanes) > 0.04F || step != sentStep) sync();
        return true;
    }

    private void apBallast(ServerLevel sl, long now, float order) {
        if (ballast < -1.0) { ballast = DeepSeasBridge.ballastFraction(sl, ship); ballastSent = ballast; if (ballast < 0.0) return; }
        ballast = Mth.clamp(ballast + order * Balance.HELM_BALLAST_RATE / 20.0, 0.0, 1.0);
        if (now % Balance.HELM_BALLAST_TICKS == 0 && Math.abs(ballast - ballastSent) > 0.004) {
            DeepSeasBridge.setBallast(sl, ship, ballast);
            ballastSent = ballast;
        }
    }

    void apLetGo(ServerLevel sl, int why) {
        if (!pilot.on()) return;
        pilot.letGo(why, sl.getGameTime());
        apRelease(sl, false);
    }

    private void apRelease(ServerLevel sl, boolean stop) {
        rudder = 0.0F;
        planes = 0.0F;
        if (stop) step = STOP;
        if (pilot.reason != SeaPilot.R_TELEGRAPH && pilot.reason != SeaPilot.R_HELM) changed = sl.getGameTime();
        playOnce(sl, WmdSounds.AP_DISCONNECT.get(), Balance.SND_CLICK, 1.0F);
        publish(sl);
        sync();
    }

    String apCommand(int op, CompoundTag data, SeaPilot.Ctx c) {
        if (!(level instanceof ServerLevel sl)) return "";
        switch (op) {
            case SeaPilotPayloads.SET, SeaPilotPayloads.ENGAGE -> {
                pilot.apply(data, c == null ? null : c.at());
                if (c != null && c.sub()) SeaChart.capDepths(sl, c.at(), pilot, c.keelBelow(), pilot.margin(true), c.crush());
                setChanged();
                if (op == SeaPilotPayloads.SET) return "";
                String r = pilot.engage(c, data.getInt("m"));
                if (r != null) return r;
                playOnce(sl, WmdSounds.AP_ENGAGE.get(), Balance.SND_CLICK, 1.0F);
                return "message.wmd.sp.engaged";
            }
            case SeaPilotPayloads.OFF -> {
                if (!pilot.on()) return "";
                apLetGo(sl, SeaPilot.R_OFF);
                return "message.wmd.sp.off";
            }
            case SeaPilotPayloads.QUICK -> {
                if (pilot.on()) { apLetGo(sl, SeaPilot.R_OFF); return "message.wmd.sp.off"; }
                String r = pilot.engageHere(c, step);
                if (r != null) return r;
                playOnce(sl, WmdSounds.AP_ENGAGE.get(), Balance.SND_CLICK, 1.0F);
                return "message.wmd.sp.engaged";
            }
            default -> { return ""; }
        }
    }

    private void apHud(ServerLevel sl, ServerPlayer p) {
        long now = sl.getGameTime();
        int why = pilot.reasonShown(now);
        if (!pilot.on() && why == SeaPilot.R_NONE) return;
        float dist = -1.0F, course = pilot.holdCourse;
        int spd = pilot.holdSpeed;
        double[] a = worldPoint(SEAT_X, 0.5, 0.0);
        if (pilot.mode == SeaPilot.ROUTE && !pilot.route.isEmpty()) {
            SeaPilot.Wp w = pilot.route.get(Mth.clamp(pilot.leg, 0, pilot.route.size() - 1));
            dist = (float) Math.hypot(w.x() - a[0], w.z() - a[2]);
            course = (float) Autopilot.bearingTo(new Vec3(a[0], a[1], a[2]), w.x(), w.z());
            spd = w.speed();
        } else if (pilot.mode == SeaPilot.STATION) {
            dist = (float) Math.hypot(pilot.stX - a[0], pilot.stZ - a[2]);
            course = (float) Autopilot.bearingTo(new Vec3(a[0], a[1], a[2]), pilot.stX, pilot.stZ);
        }
        UUID id = SableBridge.shipIdAt(sl, worldPosition);
        DeepSeasBridge.SubState sub = id == null ? null : DeepSeasBridge.state(id);
        int crush = sub != null ? DeepSeasBridge.crushDepth(sl, id) : -1;
        int pct = sub != null && crush > 0 ? Math.round(100.0F * sub.depth() / crush) : -1;
        PacketDistributor.sendToPlayer(p, new SeaPilotPayloads.Hud(worldPosition, pilot.mode, pilot.leg, pilot.route.size(), dist, spd, (float) pilot.wantDepth,
                (float) pilot.clearance, pct, why, pilot.capped, course));
    }
}
