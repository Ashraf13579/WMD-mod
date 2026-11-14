package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SamSiteBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated, ModeMachine, RadarLinked, CopycatState.Host {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.SOUTH, false);

    public static final int ST_NO_OWNER = 0, ST_NO_RADAR = 1, ST_READY = 2, ST_TRACKING = 3, ST_FIRING = 4, ST_GUIDING = 5, ST_EMPTY = 6, ST_HOLD = 7, ST_NO_POWER = 8;

    static final double PIV_Y = 14.0, PIV_Z = 28.4, FOOT_Y = 14.0, FOOT_Z = 20.0, LUG_Y = 17.0, LUG_Z = 6.0, CAN_FRONT = -5.6, YAW_Z = 16.0;
    static final double[][] CANS = { { -4.6, 18.4 }, { 4.6, 18.4 }, { -4.6, 27.4 }, { 4.6, 27.4 } };

    static final float EL_SIGN = 1.0F, YAW_SIGN = 1.0F, HATCH_SIGN = 1.0F;
    static final float ROD_REST = (float) ram(0.0)[1];

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private final Slot[] slots = new Slot[4];
    private final List<FluidTank> tanks = new ArrayList<>();
    private int frequency = 1, mode;

    private UUID target;

    private AirMissiles.Shot lastShot;

    private static final java.util.Map<UUID, long[]> CLAIMS = Statics.server(new java.util.HashMap<>());
    private List<UUID> tracks = List.of();
    private long lastTargetSeen = Long.MIN_VALUE / 2, nextShotAt, lastSalvoEnd = Long.MIN_VALUE / 2, guideUntil = Long.MIN_VALUE / 2;
    private int salvoLeft;
    private SamLock lock;

    private float az, el, tAz, tEl;
    private int status = ST_NO_OWNER, loaded, trackCount, targetDist = -1, shotTube = -1;
    private long hatchAt = Long.MIN_VALUE / 2, shotAt = Long.MIN_VALUE / 2;
    private boolean guiding;

    private float prevAz, prevEl;
    private boolean placedClient;
    private long fxDone = Long.MIN_VALUE / 2, hatchDone = Long.MIN_VALUE / 2;
    private boolean elWasMoving;
    private int lastStatus = -1, clientTicks;

    public SamSiteBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.SAM_SITE_BE.get(), pos, state, Balance.SAM_FE, Balance.SAM_BUFFER_FE, Balance.SAM_RECEIVE_FE);
        for (int i = 0; i < 4; i++) {
            slots[i] = new Slot(1, false, SamSiteBlockEntity::isRound);
            tanks.add(slots[i].gauge);
        }
    }

    static boolean isRound(ItemStack s) { return s.getItem() instanceof AamItem a && a.kind() == AirMissiles.RADAR; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override public int mode() { return mode; }
    @Override public int modeCount() { return 4; }
    @Override public void setMode(int m) { mode = Mth.clamp(m, 0, 3); sync(); }
    @Override public void setModeClient(int m) { mode = Mth.clamp(m, 0, 3); }
    public int salvoSize() { return (mode & 1) != 0 ? 2 : 1; }
    public boolean hold() { return (mode & 2) != 0; }

    @Override public int getFrequency() { return frequency; }
    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); sync(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int samStatus() { return status; }
    public int loadedMask() { return loaded; }
    public int roundsLoaded() { return Integer.bitCount(loaded); }
    public int trackCount() { return trackCount; }
    public int targetDistance() { return targetDist; }
    public boolean guidingNow() { return guiding; }

    boolean emitting() { return level != null && level.getGameTime() < guideUntil && AirGate.on(); }

    @Override
    protected boolean process() {
        if (!AirGate.on() || !(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        common(now);
        if ((now + (worldPosition.asLong() & 7L)) % Balance.SAM_SCAN_TICKS == 0) scan(sl, now);
        fireStep(sl, now);
        return move();
    }

    @Override
    protected void idle() {
        super.idle();
        if (level == null || level.isClientSide) return;
        common(level.getGameTime());
        if (status != ST_NO_POWER && AirGate.on()) { status = ST_NO_POWER; salvoLeft = 0; sync(); }
    }

    private void common(long now) {
        int m = 0;
        for (int i = 0; i < 4; i++) if (!slots[i].stack.isEmpty()) m |= 1 << i;
        boolean g = now < guideUntil;
        if (m != loaded || g != guiding) {
            if ((m & ~loaded) != 0) hatchAt = now;
            loaded = m;
            guiding = g;
            sync();
        }
    }

    private void scan(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        UUID owner = getOwner();
        int st;
        UUID pick = null;
        Vec3 me = SableBridge.worldPos(sl, worldPosition).add(0.0, 1.5, 0.0);
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        tracks = owner == null ? List.of() : TrackNet.aircraftTracks(sl, owner, frequency, now);
        double best = Double.MAX_VALUE, pd = 0.0;
        AirPicture.Aircraft pa = null;
        for (UUID id : tracks) {
            if (id.equals(own)) continue;
            if (AirPicture.isWreck(id)) continue;
            long[] cl = CLAIMS.get(id);
            if (cl != null && cl[0] != worldPosition.asLong() && now - cl[1] <= Balance.SAM_CLAIM_TICKS) continue;
            AirPicture.Aircraft a = AirPicture.get(sl, id);
            if (a == null) continue;
            double d = a.pos().distanceTo(me);
            if (d > AirJam.lockRange(sl, id, Balance.SAM_RANGE) || d < Balance.SAM_MIN_RANGE) continue;
            double score = d + (id.equals(target) ? -Balance.SAM_RANGE : 0.0) + (shotsAt(sl, id) > 0 && !id.equals(target) ? Balance.SAM_RANGE * 0.5 : 0.0);
            if (score < best) { best = score; pick = id; pa = a; pd = d; }
        }
        if (pick != null && !pick.equals(target)) { target = pick; salvoLeft = 0; }
        if (pick == null) target = null;
        if (pick != null && !hold()) CLAIMS.put(pick, new long[] { worldPosition.asLong(), now });
        int dist = -1;
        if (pa != null) {
            lastTargetSeen = now;
            Vec3 p = pa.pos().add(pa.vel().scale(Math.min(40.0, pd / Balance.AAM_SPEED[AirMissiles.RADAR]) * 0.5));
            double dx = p.x - me.x, dy = p.y - me.y, dz = p.z - me.z, h = Math.sqrt(dx * dx + dz * dz);
            float brg = (float) Math.toDegrees(Math.atan2(dx, -dz));
            tAz = Mth.wrapDegrees(brg - frontYaw());
            tEl = (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, h)) + Balance.SAM_LOFT, Balance.SAM_EL_MIN, Balance.SAM_EL_MAX);
            dist = (int) Math.round(pd);
        } else if (now - lastTargetSeen > Balance.SAM_STOW_DELAY) {
            tAz = 0.0F;
            tEl = 0.0F;
        }
        if (owner == null) st = ST_NO_OWNER;
        else if (!TrackNet.airRadarUp(sl, owner, frequency, now)) st = ST_NO_RADAR;
        else if (guideUntil > now) st = ST_GUIDING;
        else if (salvoLeft > 0) st = ST_FIRING;
        else if (target != null) st = hold() ? ST_HOLD : loaded == 0 ? ST_EMPTY : ST_TRACKING;
        else st = hold() ? ST_HOLD : loaded == 0 ? ST_EMPTY : ST_READY;
        if (st != status || tracks.size() != trackCount || dist != targetDist || Math.abs(Mth.wrapDegrees(tAz - shownTAz)) > 0.5F || Math.abs(tEl - shownTEl) > 0.5F) {
            status = st;
            trackCount = tracks.size();
            targetDist = dist;
            shownTAz = tAz;
            shownTEl = tEl;
            sync();
        }
    }

    private float shownTAz, shownTEl;

    private int shotsAt(ServerLevel sl, UUID id) {
        if (!AirGate.on()) return 0;
        int n = 0;
        for (AirMissiles.Shot s : AirMissiles.inFlight(sl)) if (s.lock instanceof SamLock l && l.pos.equals(worldPosition) && id.equals(s.targetShip())) n++;
        return n;
    }

    private void fireStep(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        if (target == null || hold() || getOwner() == null) { salvoLeft = 0; return; }
        boolean aligned = Math.abs(Mth.wrapDegrees(az - tAz)) < Balance.SAM_AIM_TOL && Math.abs(el - tEl) < Balance.SAM_AIM_TOL && el >= Balance.SAM_EL_MIN - 0.5;
        if (!aligned) return;
        if (salvoLeft <= 0) {
            if (loaded == 0 || shotsAt(sl, target) > 0 || now - lastSalvoEnd < Balance.SAM_RELOOK) return;
            if (lastShot != null && target.equals(lastShot.targetShip()) && AirMissiles.HIT.equals(lastShot.end)) {
                AirPicture.Aircraft hit = AirPicture.get(sl, target);
                if (hit == null || hit.vel().y * 20.0 <= Balance.SAM_REFIRE_CLIMB) return;
            }
            if (!TrackNet.airRadarUp(sl, getOwner(), frequency, now)) return;
            salvoLeft = salvoSize();
            nextShotAt = now;
        }
        if (now < nextShotAt) return;
        int tube = -1;
        for (int i : new int[] { 2, 3, 0, 1 }) if (!slots[i].stack.isEmpty()) { tube = i; break; }
        if (tube < 0) { salvoLeft = 0; lastSalvoEnd = now; return; }
        if (energy().getEnergyStored() < Balance.SAM_SHOT_FE + Balance.SAM_FE) return;
        if (lock == null) lock = new SamLock(sl, worldPosition, getOwner());
        Vec3 from = muzzle(tube, el, az), dir = axis(el, az);
        AirPicture.Aircraft a = AirPicture.get(sl, target);
        final UUID tgt = target; final String nm = a == null || a.name() == null ? "aircraft" : a.name();
        String why = AirMissiles.refinedShot(AamItem.refined(slots[tube].stack), () -> AirMissiles.fire(sl, AirMissiles.RADAR, getOwner(), from, dir, dir.scale(Balance.SAM_BOOST),
                SableBridge.shipIdAt(sl, worldPosition), tgt, null, null, lock, null, nm));
        if (why != null) {
            Wmd.LOGGER.debug("SAM site {} launch refused: {}", worldPosition, why);
            salvoLeft = 0;
            lastSalvoEnd = now;
            return;
        }
        useExtraEnergy(Balance.SAM_SHOT_FE);
        for (AirMissiles.Shot s : AirMissiles.inFlight(sl))
            if (s.lock == lock && tgt.equals(s.targetShip()) && (lastShot == null || s.id() > lastShot.id() || !tgt.equals(lastShot.targetShip()))) lastShot = s;
        slots[tube].take(1);
        guideUntil = now + Balance.SAM_GUIDE_HOLD;
        RadarEmit.note(this);
        shotAt = now;
        shotTube = tube;
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.SAM_LAUNCH.get(), SoundSource.BLOCKS, Balance.SAM_LAUNCH_VOLUME, 0.95F + sl.random.nextFloat() * 0.1F);
        if (--salvoLeft <= 0) lastSalvoEnd = now;
        nextShotAt = now + Balance.SAM_SALVO_GAP;
        common(now);
        status = ST_FIRING;
        sync();
    }

    boolean guides(ServerLevel sl, UUID id, Vec3 t, long now) {
        if (!AirGate.on() || isRemoved() || id == null || t == null || getOwner() == null) return false;
        if (EmpField.jammed(sl, worldPosition) || energy().getEnergyStored() <= 0) return false;
        if (!tracks.contains(id)) return false;
        if (SableBridge.worldPos(sl, worldPosition).distanceTo(t) > AirJam.lockRange(sl, id, Balance.SAM_RANGE) * Balance.SAM_GUIDE_SPARE) return false;
        guideUntil = Math.max(guideUntil, now + Balance.SAM_GUIDE_HOLD);
        return true;
    }

    private boolean move() {
        float dA = Mth.wrapDegrees(tAz - az), dE = tEl - el;
        boolean moving = Math.abs(dA) > 0.05F || Math.abs(dE) > 0.05F;
        az = Mth.wrapDegrees(az + step(dA, Balance.SAM_SLEW_DEG));
        el = Mth.clamp(el + step(dE, Balance.SAM_RAISE_DEG), 0.0F, (float) Balance.SAM_EL_MAX);
        return moving;
    }

    static float step(float d, double max) {
        float m = (float) Math.min(max, Math.abs(d) * 0.25 + 0.15);
        return Mth.clamp(d, -m, m);
    }

    private float frontYaw() { return (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F; }

    static double[] ram(double e) {
        double t = Math.toRadians(e), dy = LUG_Y - PIV_Y, dz = LUG_Z - PIV_Z;
        double ly = PIV_Y + dy * Math.cos(t) - dz * Math.sin(t), lz = PIV_Z + dy * Math.sin(t) + dz * Math.cos(t);
        double vy = ly - FOOT_Y, vz = lz - FOOT_Z;
        return new double[] { Math.toDegrees(Math.atan2(vz, vy)), Math.hypot(vy, vz) };
    }

    private static double[] local(double x, double y, double z, double e, double a) {
        double t = Math.toRadians(e), dy = y - PIV_Y, dz = z - PIV_Z;
        double y1 = PIV_Y + dy * Math.cos(t) - dz * Math.sin(t), z1 = PIV_Z + dy * Math.sin(t) + dz * Math.cos(t);
        double r = Math.toRadians(a), zz = z1 - YAW_Z;
        double x2 = x * Math.cos(r) - zz * Math.sin(r), z2 = x * Math.sin(r) + zz * Math.cos(r) + YAW_Z;
        return new double[] { x2 / 16.0, y1 / 16.0, z2 / 16.0 };
    }

    Vec3 muzzle(int tube, double e, double a) {
        double[] l = local(CANS[tube][0], CANS[tube][1], CAN_FRONT - 2.0, e, a);
        double[] w = worldPoint(l[0], l[1], l[2]);
        return new Vec3(w[0], w[1], w[2]);
    }

    private Vec3 rear(int tube, double e, double a) {
        double[] l = local(CANS[tube][0], CANS[tube][1], 28.0, e, a);
        double[] w = worldPoint(l[0], l[1], l[2]);
        return new Vec3(w[0], w[1], w[2]);
    }

    Vec3 axis(double e, double a) {
        Vec3 f = muzzle(0, e, a), b = rear(0, e, a);
        return f.subtract(b).normalize();
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    static void clientTickSam(Level level, BlockPos pos, BlockState state, SamSiteBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickClient(level);
    }

    private void tickClient(Level level) {
        prevAz = az;
        prevEl = el;
        if (!placedClient) { placedClient = true; prevAz = az; prevEl = el; lastStatus = status; hatchDone = hatchAt; }
        move();
        long now = level.getGameTime();

        boolean elMoves = Math.abs(el - prevEl) > 0.01F;
        if (clientTicks++ > 40) {
            if (elWasMoving && !elMoves) playOnce(level, WmdSounds.SAM_LOCK.get(), Balance.SND_MECH, 1.0F);
            if (hatchDone != hatchAt && now - hatchAt >= 0 && now - hatchAt < 5) playOnce(level, WmdSounds.SAM_HATCH.get(), Balance.SND_MECH, 1.0F);
            boolean engaged = status == ST_TRACKING || status == ST_FIRING || status == ST_GUIDING;
            boolean wasEngaged = lastStatus == ST_TRACKING || lastStatus == ST_FIRING || lastStatus == ST_GUIDING;
            if (engaged && !wasEngaged) playOnce(level, WmdSounds.SAM_CHIRP.get(), Balance.SND_CLICK, 1.0F);
        }
        elWasMoving = elMoves;
        hatchDone = hatchAt;
        lastStatus = status;
        if (now - shotAt >= 0 && now - shotAt < 3 && fxDone != shotAt && shotTube >= 0) {
            fxDone = shotAt;
            Vec3 m = muzzle(shotTube, el, az), b = rear(shotTube, el, az), d = m.subtract(b).normalize();
            var r = level.random;
            for (int i = 0; i < 14; i++) {
                level.addParticle(ParticleTypes.POOF, m.x, m.y, m.z, d.x * 0.3 + (r.nextDouble() - 0.5) * 0.25, d.y * 0.3 + (r.nextDouble() - 0.5) * 0.25, d.z * 0.3 + (r.nextDouble() - 0.5) * 0.25);
                level.addParticle(ParticleTypes.FLAME, m.x, m.y, m.z, d.x * 0.5 + (r.nextDouble() - 0.5) * 0.1, d.y * 0.5 + (r.nextDouble() - 0.5) * 0.1, d.z * 0.5 + (r.nextDouble() - 0.5) * 0.1);
            }
            for (int i = 0; i < 18; i++)
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, b.x, b.y, b.z, -d.x * 0.25 + (r.nextDouble() - 0.5) * 0.2, -d.y * 0.1 + r.nextDouble() * 0.05, -d.z * 0.25 + (r.nextDouble() - 0.5) * 0.2);
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = prevAz + Mth.wrapDegrees(az - prevAz) * pt;
        float e = prevEl + (el - prevEl) * pt;
        double since = tt - shotAt;
        if (since >= 0.0 && since < 3.0) e -= (float) (1.5 * (1.0 - since / 3.0));
        final float yaw = YAW_SIGN * (float) Math.toRadians(-a), elev = EL_SIGN * (float) Math.toRadians(e);
        model.getBone("yaw").ifPresent(b -> b.setRotY(yaw));
        model.getBone("cradle").ifPresent(b -> b.setRotX(elev));
        double[] r = ram(e);
        final float th = EL_SIGN * (float) Math.toRadians(r[0]), slide = (float) r[1] - ROD_REST;
        for (String n : new String[] { "l", "r" }) {
            model.getBone("ram_" + n).ifPresent(b -> b.setRotX(th));
            model.getBone("rod_" + n).ifPresent(b -> b.setPosY(slide));
        }
        for (int i = 0; i < 4; i++) {
            final boolean has = (loaded >> i & 1) != 0;
            model.getBone("cap" + i).ifPresent(b -> b.setHidden(!has));
        }

        double h = tt - hatchAt;
        float open = h < 0 || h > 3 * Balance.SAM_HATCH_TICKS ? 0.0F : (float) (h < Balance.SAM_HATCH_TICKS ? ease(h / Balance.SAM_HATCH_TICKS)
                : h < 2 * Balance.SAM_HATCH_TICKS ? 1.0 : 1.0 - ease((h - 2 * Balance.SAM_HATCH_TICKS) / Balance.SAM_HATCH_TICKS));
        final float door = HATCH_SIGN * (float) Math.toRadians(100.0 * open);
        model.getBone("hatch_l").ifPresent(b -> b.setRotY(-door));
        model.getBone("hatch_r").ifPresent(b -> b.setRotY(door));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean ready = !dark && (status == ST_READY || status == ST_TRACKING || status == ST_FIRING || status == ST_GUIDING);
        final boolean warn = !dark && (status == ST_NO_RADAR || status == ST_EMPTY || status == ST_HOLD);
        final boolean glow = !dark && guiding;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready));
        model.getBone("lamp_a").ifPresent(b -> b.setHidden(!warn));
        model.getBone("glow").ifPresent(b -> b.setHidden(!glow));
    }

    private static double ease(double t) { t = Mth.clamp(t, 0.0, 1.0); return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected String[] iconIds() { return new String[] { "aam_radar", "aam_radar", "aam_radar", "aam_radar" }; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("sam_round", "sam_round", "sam_round", "sam_round"); }
    @Override protected int inputTanks() { return 4; }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.sam", String.format("%,d", (int) Balance.SAM_RANGE), Balance.SAM_SHOT_FE / 1000, Balance.SAM_FE * 20),
                Component.translatable("gui.wmd.info.sam_rounds"));
    }

    @Override
    protected String detail() {
        return "status " + status + ", rounds " + roundsLoaded() + "/4, tracks " + trackCount + ", frequency " + frequency + ", salvo " + salvoSize() + (hold() ? ", HOLD" : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SAM_SLEW.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("samFreq", frequency);
        tag.putInt("samMode", mode);
        tag.putFloat("samAz", az);
        tag.putFloat("samEl", el);
        tag.putFloat("samTAz", tAz);
        tag.putFloat("samTEl", tEl);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("samFreq")) frequency = Mth.clamp(tag.getInt("samFreq"), 1, 9999);
        mode = Mth.clamp(tag.getInt("samMode"), 0, 3);
        boolean client = level != null && level.isClientSide;
        if (!client || !placedClient) { az = tag.getFloat("samAz"); el = tag.getFloat("samEl"); }
        tAz = tag.getFloat("samTAz");
        tEl = tag.getFloat("samTEl");
        if (tag.contains("samSt")) {
            status = tag.getInt("samSt");
            loaded = tag.getInt("samLd");
            trackCount = tag.getInt("samTr");
            targetDist = tag.getInt("samDist");
            hatchAt = tag.getLong("samHatch");
            shotAt = tag.getLong("samShot");
            shotTube = tag.getInt("samTube");
            guiding = tag.getBoolean("samGd");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        saveExtra(tag);
        tag.putInt("samSt", status);
        tag.putInt("samLd", loaded);
        tag.putInt("samTr", trackCount);
        tag.putInt("samDist", targetDist);
        tag.putLong("samHatch", hatchAt);
        tag.putLong("samShot", shotAt);
        tag.putInt("samTube", shotTube);
        tag.putBoolean("samGd", guiding);
        return tag;
    }

    static final class SamLock implements AirMissiles.LockSource {
        final ServerLevel level;
        final BlockPos pos;
        final UUID owner;

        SamLock(ServerLevel level, BlockPos pos, UUID owner) { this.level = level; this.pos = pos.immutable(); this.owner = owner; }

        @Override
        public boolean holds(ServerLevel l, AirMissiles.Shot s, Vec3 t, long now) {
            return l == level && l.isLoaded(pos) && l.getBlockEntity(pos) instanceof SamSiteBlockEntity site && site.guides(l, s.targetShip(), t, now);
        }

        @Override public Vec3 at(ServerLevel l) { return SableBridge.worldPos(l, pos).add(0.0, 1.5, 0.0); }
        @Override public UUID owner() { return owner; }
        @Override public String label() { return "sam"; }
    }
}
