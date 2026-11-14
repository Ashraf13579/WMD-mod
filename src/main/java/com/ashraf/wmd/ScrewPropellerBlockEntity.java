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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ScrewPropellerBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, ShipEngine, DriveEngine {

    private static final PortMap PORT_MAP = new PortMap(0, 0, 2).power(0, 0, 0, Direction.UP, false);

    static final double DISC_Y = 0.5, DISC_Z = 2.725, TIP_R = 1.48;

    static final int BLADES = 7;
    static final int STOP = Balance.PROP_STEP_STOP, FLANK = ShipControl.STEPS - 1;
    private static final double ASTERN_MAX = -Balance.PROP_STEP_FRAC[0];

    static final float RX = 1.0F, RY = 1.0F, RZ = 1.0F;

    private int step = STOP, source = -1, stopTicks, redstone;
    private double throttle;
    private float rudderOrd, planesOrd, spool, pitch, rudder, planes;
    private boolean engaged, wet;

    private BlockState copy;

    private net.minecraft.world.item.ItemStack copyItem = net.minecraft.world.item.ItemStack.EMPTY;
    private boolean xTail;

    private boolean noTail;

    private UUID ship;
    private double speed;
    private volatile double[] force;
    private int sentStep = -99;
    private float sentPitch = 999, sentRudder = 999, sentPlanes = 999, sentSpool = -1;
    private boolean sentWet, sentEngaged, sentCav;

    private int cStep = STOP;
    private boolean cWet, cCav, cEngaged;
    private float tPitch, tRudder, tPlanes, tSpool, cPitch, cRudder, cPlanes, cSpool;
    private float pPitch, pRudder, pPlanes;
    private double hubAngle, pHub;
    private long nextCav, nextServo;
    private float servoFrom;

    public ScrewPropellerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.SCREW_PROPELLER_BE.get(), pos, state, Balance.SCREW_FE, (Balance.SCREW_FE + Balance.SCREW_FE_LOAD) * 400, (Balance.SCREW_FE + Balance.SCREW_FE_LOAD) * 10);
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        boolean centred = step == STOP && Math.abs(rudderOrd) < 0.01F && Math.abs(planesOrd) < 0.01F;
        stopTicks = centred ? stopTicks + 1 : 0;
        float dt = 0.05F;
        rudder = approach(rudder, (float) (rudderOrd * Balance.PROP_RUDDER_MAX), (float) Balance.PROP_SURFACE_RATE * dt);
        planes = approach(planes, (float) (planesOrd * Balance.PROP_PLANES_MAX), (float) Balance.PROP_SURFACE_RATE * dt);
        engaged = !(stopTicks > Balance.SCREW_DECLUTCH && Math.abs(pitch) < 0.5F && Math.abs(rudder) < 0.5F && Math.abs(planes) < 0.5F);
        if (!engaged) {
            spool = Math.max(0.0F, spool - 1.0F / Balance.SCREW_SPOOL_DOWN);
            pitch = approach(pitch, 0.0F, (float) Balance.SCREW_PITCH_RATE * dt);
            after(sl);
            return false;
        }
        boolean load = Math.abs(throttle) < 1.0E-3 || useExtraEnergy((int) Math.round(Balance.SCREW_FE_LOAD * Math.abs(throttle)));
        pitch = approach(pitch, load ? targetPitch(throttle) : 0.0F, (float) Balance.SCREW_PITCH_RATE * dt);
        spool = Math.min(1.0F, spool + 1.0F / Balance.SCREW_SPOOL_UP);
        after(sl);
        return true;
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        orders(sl);
        engaged = false;
        spool = Math.max(0.0F, spool - 1.0F / Balance.SCREW_SPOOL_DOWN);
        after(sl);
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
        for (int z = 0; z <= 1; z++) best = Math.max(best, sl.getBestNeighborSignal(Machines.toWorld(worldPosition, facing(), m, 0, 0, z)));
        return best;
    }

    private void beat(ServerLevel sl) {
        UUID now = SableBridge.shipIdAt(sl, worldPosition);
        ship = now;
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        speed = info == null ? 0.0 : info.speed();
        double[] d = worldPoint(0.0, DISC_Y, DISC_Z);
        Vec3 disc = new Vec3(d[0], d[1], d[2]);
        wet = sl.getFluidState(BlockPos.containing(disc)).is(FluidTags.WATER);
        double share = wet ? thrustNow() / Balance.SCREW_THRUST : 0.0;
        if (ship != null) ShipControl.answer(sl, ship, worldPosition, ShipControl.stepOf(share), wet && spool > 0.5F && engaged, share, rudder, planes);
        if (wet && spool > 0.05F) {
            double db = Balance.SCREW_DB_IDLE * Math.min(1.0, spool * 2.0) + Balance.SCREW_DB_LOAD * Math.abs(share) + (cavitating() ? Balance.SCREW_DB_CAV : 0.0);
            if (ship != null) db = Math.max(1.0, db - Anechoic.noiseCut(sl, ship));
            NoiseNet.emit(sl, this, disc, db, NoiseNet.Kind.ENGINE, null, ship);
        } else NoiseNet.silence(sl, this);
    }

    private boolean cavitating() { return step == FLANK && spool > 0.9F && wet && pitch > Balance.SCREW_PITCH_MAX * 0.9; }

    static float targetPitch(double share) {
        return (float) (share >= 0.0 ? share * Balance.SCREW_PITCH_MAX : share / ASTERN_MAX * -Balance.SCREW_PITCH_MIN);
    }

    static double pitchShare(double pitchDeg) {
        return pitchDeg >= 0.0 ? pitchDeg / Balance.SCREW_PITCH_MAX : pitchDeg / -Balance.SCREW_PITCH_MIN * ASTERN_MAX;
    }

    double thrustNow() { return Balance.SCREW_THRUST * pitchShare(pitch) * spool; }

    private void updateForce() {
        if (ship == null || !wet) { force = null; return; }
        double thrust = thrustNow();
        double v2 = speed * speed;
        double side = noTail ? 0.0 : (Balance.PROP_RUDDER_WASH * thrust + Balance.PROP_RUDDER_SPEED * v2) * Math.sin(Math.toRadians(rudder));
        double vert = noTail ? 0.0 : (Balance.PROP_PLANES_WASH * thrust + Balance.PROP_PLANES_SPEED * v2) * Math.sin(Math.toRadians(planes));
        if (Math.abs(thrust) < 1.0E-4 && Math.abs(side) < 1.0E-4 && Math.abs(vert) < 1.0E-4) { force = null; return; }
        Direction m = Machines.mount(getBlockState());
        Vec3 fwd = unit(Machines.dirToWorld(facing(), m, Direction.NORTH)), port = unit(Machines.dirToWorld(facing(), m, Direction.WEST)), up = unit(Machines.dirToWorld(facing(), m, Direction.UP));

        Vec3 f = fwd.scale(thrust).add(port.scale(side)).add(up.scale(vert));
        Vec3 p = plotPoint(0.0, DISC_Y, DISC_Z);
        force = new double[] { p.x, p.y, p.z, f.x, f.y, f.z };
    }

    private static Vec3 unit(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    @Override public double[] engineForce() { return force; }

    private void syncMaybe(long now) {
        boolean cav = cavitating();
        boolean big = step != sentStep || wet != sentWet || engaged != sentEngaged || cav != sentCav || (spool >= 1.0F) != (sentSpool >= 1.0F) || (spool <= 0.0F) != (sentSpool <= 0.0F);
        boolean small = Math.abs(pitch - sentPitch) > 2.0F || Math.abs(rudder - sentRudder) > 2.0F || Math.abs(planes - sentPlanes) > 2.0F || Math.abs(spool - sentSpool) > 0.1F;
        if (!big && !(small && now % 4 == 0)) return;
        sentStep = step; sentWet = wet; sentEngaged = engaged; sentCav = cav; sentPitch = pitch; sentRudder = rudder; sentPlanes = planes; sentSpool = spool;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static float approach(float cur, float tgt, float max) {
        float d = tgt - cur;
        return Math.abs(d) <= max ? tgt : cur + Math.signum(d) * max;
    }

    static void clientTickScrew(Level lv, BlockPos pos, BlockState state, ScrewPropellerBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
        be.easeTick();
    }

    private void easeTick() {
        pPitch = cPitch; pRudder = cRudder; pPlanes = cPlanes; pHub = hubAngle;
        float k = 1.0F / 20.0F;
        cPitch = approach(cPitch, tPitch, (float) Balance.SCREW_PITCH_RATE * k * 1.3F);
        cRudder = approach(cRudder, tRudder, (float) Balance.PROP_SURFACE_RATE * k * 1.3F);
        cPlanes = approach(cPlanes, tPlanes, (float) Balance.PROP_SURFACE_RATE * k * 1.3F);
        float up = 1.0F / Balance.SCREW_SPOOL_UP, down = 1.0F / Balance.SCREW_SPOOL_DOWN;
        cSpool = cSpool < tSpool ? Math.min(tSpool, cSpool + up * 1.3F) : Math.max(tSpool, cSpool - down * 1.3F);

        hubAngle += Math.PI * 2.0 * 1.2 / 20.0 * easeShaft(cSpool);
        if (hubAngle > Math.PI * 2000.0) { hubAngle -= Math.PI * 2000.0; pHub -= Math.PI * 2000.0; }
    }

    private static float easeShaft(float s) { return s * s * (3.0F - 2.0F * s); }

    static float[] bladeEuler(int k, float deg) {
        double a = Math.toRadians(360.0 * k / BLADES), ax = Math.sin(a), ay = Math.cos(a), t = Math.toRadians(deg);
        double c = Math.cos(t), sn = Math.sin(t), C = 1.0 - c;
        double m00 = c + ax * ax * C, m10 = ay * ax * C, m20 = -ay * sn, m21 = ax * sn, m22 = c;
        return new float[] { (float) Math.atan2(m21, m22), (float) -Math.asin(Mth.clamp(m20, -1.0, 1.0)), (float) Math.atan2(m10, m00) };
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float hub = (float) Mth.lerp(partialTick, pHub, hubAngle) * RZ;
        model.getBone("hub").ifPresent(b -> b.setRotZ(hub));
        model.getBone("shaft").ifPresent(b -> b.setRotZ(hub));
        float pd = Mth.lerp(partialTick, pPitch, cPitch);
        for (int k = 0; k < BLADES; k++) {
            float[] e = bladeEuler(k, pd);
            model.getBone("blade" + k).ifPresent(b -> { b.setRotX(e[0] * RX); b.setRotY(e[1] * RY); b.setRotZ(e[2] * RZ); });
        }

        float ru = (float) Math.toRadians(Mth.lerp(partialTick, pRudder, cRudder)), pl = (float) Math.toRadians(Mth.lerp(partialTick, pPlanes, cPlanes));
        float vert = ru, horiz = pl;
        if (cXTail) { float h = 0.70710677F; vert = h * (ru + pl); horiz = h * (pl - ru); }
        final float fv = vert, fh = horiz;
        model.getBone("tail").ifPresent(b -> { b.setRotZ(cXTail ? (float) Math.toRadians(45.0) * RZ : 0.0F); b.setHidden(cNoTail); });
        model.getBone("fin_up").ifPresent(b -> b.setRotY(fv * RY));
        model.getBone("fin_dn").ifPresent(b -> b.setRotY(fv * RY));
        model.getBone("fin_l").ifPresent(b -> b.setRotX(fh * RX));
        model.getBone("fin_r").ifPresent(b -> b.setRotX(fh * RX));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!isRunning()));
    }

    private BlockState cCopy;
    private boolean cXTail, cNoTail;

    public BlockState copyShown() { return cCopy; }

    static boolean copyable(BlockState st, Level lv, BlockPos at) {
        return st != null && !st.isAir() && !st.hasBlockEntity() && st.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
                && st.isCollisionShapeFullBlock(lv, at) && !(st.getBlock() instanceof MachineBlock);
    }

    net.minecraft.world.item.ItemStack setCopy(BlockState st, net.minecraft.world.item.ItemStack used) {
        if (st == null ? copy == null : st.equals(copy)) return null;
        net.minecraft.world.item.ItemStack back = copyItem;
        copy = st;
        copyItem = st == null || used.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : used.copyWithCount(1);
        syncNow();
        return back;
    }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        if (!copyItem.isEmpty()) { l.add(copyItem); copyItem = net.minecraft.world.item.ItemStack.EMPTY; }
        return l;
    }

    void toggleTail() {
        if (noTail) { noTail = false; xTail = false; }
        else if (xTail) noTail = true;
        else xTail = true;
        syncNow();
    }
    boolean xTail() { return xTail; }

    int tailMode() { return noTail ? 2 : xTail ? 1 : 0; }

    private void syncNow() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void clientEffects(Level lv) {
        if (!cWet || cSpool < 0.05F) return;
        var r = lv.random;
        double[] c = worldPoint(0.0, DISC_Y, DISC_Z), a = worldPoint(0.0, DISC_Y, DISC_Z + 1.0);
        Vec3 disc = new Vec3(c[0], c[1], c[2]), aft = new Vec3(a[0] - c[0], a[1] - c[1], a[2] - c[2]);
        double share = pitchShare(cPitch) * cSpool;

        int n = (int) Math.round(Math.abs(share) * 4.0 + (r.nextFloat() < Math.abs(share) * 4.0 % 1.0 ? 1 : 0));
        double sgn = share >= 0.0 ? 1.0 : -1.0;
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0, rad = Math.sqrt(r.nextDouble()) * TIP_R;
            double[] q = worldPoint(Math.cos(ang) * rad, DISC_Y + Math.sin(ang) * rad, DISC_Z + 0.25 * sgn);
            double sp = 0.25 + 0.35 * Math.abs(share);
            lv.addParticle(ParticleTypes.BUBBLE, q[0], q[1], q[2], aft.x * sp * sgn, aft.y * sp * sgn + 0.02, aft.z * sp * sgn);
        }

        if (cCav) {
            for (int k = 0; k < BLADES; k++) {
                double ang = hubAngle + k * Math.PI * 2.0 / BLADES + Math.PI * 0.5;
                double[] q = worldPoint(Math.cos(ang) * TIP_R, DISC_Y + Math.sin(ang) * TIP_R, DISC_Z + 0.15);
                lv.addParticle(ParticleTypes.BUBBLE, q[0], q[1], q[2], aft.x * 0.15, aft.y * 0.15 + 0.01, aft.z * 0.15);
                if (r.nextInt(4) == 0) lv.addParticle(ParticleTypes.BUBBLE_POP, q[0], q[1], q[2], 0.0, 0.0, 0.0);
            }
            long now = lv.getGameTime();
            if (now >= nextCav) { nextCav = now + 36 + r.nextInt(16); playOnce(lv, WmdSounds.SCREW_CAVITATE.get(), Balance.SND_MECH, 0.9F + r.nextFloat() * 0.2F); }
        }

        if (Math.abs(share) > 0.2 && lv.getGameTime() % 3 == 0) {
            int top = NavalLayer.waterTop(lv, Mth.floor(disc.x), Mth.floor(disc.z));
            if (top != NavalLayer.NO_WATER && top + 1.0 - disc.y < 2.5) {
                double fx = disc.x + aft.x * 1.5 * sgn, fz = disc.z + aft.z * 1.5 * sgn;
                for (int i = 0; i < 2; i++) lv.addParticle(ParticleTypes.SPLASH, fx + (r.nextDouble() - 0.5) * 1.2, top + 1.02, fz + (r.nextDouble() - 0.5) * 1.2, 0.0, 0.06, 0.0);
                lv.addParticle(ParticleTypes.BUBBLE_POP, fx, top + 1.0, fz, 0.0, 0.0, 0.0);
            }
        }

        long now = lv.getGameTime();
        if (Math.abs(tPitch - servoFrom) > 3.0F && now >= nextServo) { servoFrom = tPitch; nextServo = now + 20; playOnce(lv, WmdSounds.SCREW_SERVO.get(), Balance.SND_CLICK, 1.0F); }
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.SCREW_START.get(), Balance.SND_MECH, 1.0F); }
    @Override protected void runStopped(Level lv) { playOnce(lv, WmdSounds.SCREW_STOP.get(), Balance.SND_MECH, 1.0F); }
    @Override protected SoundEvent runSound() { return WmdSounds.SCREW_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (0.35F + 0.65F * Math.max(cSpool, tSpool > 0 ? 0.3F : 0.0F)); }
    @Override protected float soundPitch() { return 0.75F + 0.2F * cSpool + 0.25F * (float) Math.abs(pitchShare(cPitch)); }
    @Override protected boolean soundAlive() { return isRunning() || cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        int maxFe = (Balance.SCREW_FE + Balance.SCREW_FE_LOAD) * 20;
        return List.of(Component.translatable("gui.wmd.info.screw_use", Balance.SCREW_FE * 20, maxFe),
                Component.translatable("gui.wmd.screw.order", Component.translatable("gui.wmd.prop.step" + cStep), sourceName(cSourceShown)),
                Component.translatable("gui.wmd.screw.state", Math.round(cPitch), Math.round(cSpool * 100.0F), Math.round(cRudder), Math.round(cPlanes)),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : cWet ? (cCav ? "gui.wmd.screw.cavitating" : "gui.wmd.screw.wet") : "gui.wmd.screw.dry"));
    }

    private int cSourceShown = -1;

    static Component sourceName(int src) {
        return Component.translatable(src == 0 ? "gui.wmd.prop.src_redstone" : src == ShipControl.SRC_TELEGRAPH ? "gui.wmd.prop.src_telegraph"
                : src == ShipControl.SRC_HELM ? "gui.wmd.prop.src_helm" : src == ShipControl.SRC_COCKPIT ? "gui.wmd.prop.src_cockpit" : "gui.wmd.prop.src_none");
    }

    @Override protected String detail() {
        return "step " + step + ", pitch " + Math.round(pitch) + ", shaft " + Math.round(spool * 100) + "%, rudder " + Math.round(rudder) + ", planes " + Math.round(planes)
                + (wet ? "" : " (dry)") + (engaged ? "" : " (declutched)");
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.UP) ? energy() : null; }

    public boolean driveFlange(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.NORTH); }

    public int driveIn(int fe, boolean simulate) { return fe <= 0 ? 0 : energy().receiveEnergy(fe, simulate); }

    public int driveDemand() { return engaged ? (int) Math.round(Balance.SCREW_FE + Balance.SCREW_FE_LOAD * Math.abs(throttle)) : 0; }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) NoiseNet.silence(sl, this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        if (!copyItem.isEmpty()) tag.put("spCopyItem", copyItem.save(registries));
        tag.putInt("spStop", stopTicks);
        tag.putDouble("spThr", throttle);
    }

    private void put(CompoundTag tag) {
        tag.putInt("spStep", step);
        tag.putInt("spSrc", source);
        tag.putFloat("spPitch", pitch);
        tag.putFloat("spRud", rudder);
        tag.putFloat("spPln", planes);
        tag.putFloat("spSpool", spool);
        tag.putBoolean("spWet", wet);
        tag.putBoolean("spEng", engaged);
        tag.putBoolean("spCav", cavitating());
        tag.putBoolean("spX", xTail);
        tag.putBoolean("spNoTail", noTail);
        if (copy != null) tag.put("spCopy", net.minecraft.nbt.NbtUtils.writeBlockState(copy));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = ShipControl.clampStep(tag.contains("spStep") ? tag.getInt("spStep") : STOP);
        float pi = Mth.clamp(tag.getFloat("spPitch"), (float) Balance.SCREW_PITCH_MIN, (float) Balance.SCREW_PITCH_MAX);
        float ru = Mth.clamp(tag.getFloat("spRud"), (float) -Balance.PROP_RUDDER_MAX, (float) Balance.PROP_RUDDER_MAX);
        float pl = Mth.clamp(tag.getFloat("spPln"), (float) -Balance.PROP_PLANES_MAX, (float) Balance.PROP_PLANES_MAX);
        float sp = Mth.clamp(tag.getFloat("spSpool"), 0.0F, 1.0F);
        BlockState cp = tag.contains("spCopy") ? net.minecraft.nbt.NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("spCopy")) : null;
        if (cp != null && cp.isAir()) cp = null;
        cCopy = cp; cXTail = tag.getBoolean("spX"); cNoTail = tag.getBoolean("spNoTail");
        if (level != null && level.isClientSide) {
            cStep = st; tPitch = pi; tRudder = ru; tPlanes = pl; tSpool = sp; cWet = tag.getBoolean("spWet"); cCav = tag.getBoolean("spCav");
            cEngaged = tag.getBoolean("spEng"); cSourceShown = tag.getInt("spSrc");
            return;
        }
        step = st; pitch = pi; rudder = ru; planes = pl; spool = sp; wet = tag.getBoolean("spWet"); engaged = tag.getBoolean("spEng"); source = tag.getInt("spSrc");
        cWet = wet; cCav = tag.getBoolean("spCav"); cEngaged = engaged; cSourceShown = source;
        stopTicks = tag.getInt("spStop");
        copy = cp; xTail = cXTail; noTail = cNoTail;
        copyItem = tag.contains("spCopyItem") ? net.minecraft.world.item.ItemStack.parseOptional(registries, tag.getCompound("spCopyItem")) : net.minecraft.world.item.ItemStack.EMPTY;
        throttle = Mth.clamp(tag.getDouble("spThr"), -ASTERN_MAX, 1.0);

        cStep = st; tPitch = cPitch = pPitch = pi; tRudder = cRudder = pRudder = ru; tPlanes = cPlanes = pPlanes = pl; tSpool = cSpool = sp;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
