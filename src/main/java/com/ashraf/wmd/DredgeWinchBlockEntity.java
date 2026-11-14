package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class DredgeWinchBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 2, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(0, 0, 0, Direction.DOWN, true).power(0, 0, 1, Direction.SOUTH, false);
    public static final int A_DROP = 0, A_AUTO = 1;
    public static final int P_IDLE = 0, P_TILT_OUT = 1, P_LOWER = 2, P_GRAB = 3, P_HAUL = 4, P_TILT_IN = 5;

    public static final int S_READY = 0, S_WORKING = 1, S_NO_WATER = 2, S_TOO_DEEP = 3, S_FULL = 4, S_EMPTY = 5, S_DRIFTED = 6;

    static final double HINGE_Y = 2.4, HINGE_Z = 2.0, SHEAVE_Y = 44.0, SHEAVE_Z = -5.0, BUCKET_Y = 26.0, JAW_DROP = 11.4, TILT_DEG = 15.7;

    static final float AFRAME_SIGN = -1.0F;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot out = new Slot(Balance.DREDGE_SLOT, true, s -> false).chute(0, 0, 0, Direction.DOWN);
    private final Slot[] slots = { out };
    private final List<FluidTank> gauges = List.of(out.gauge);
    private final IItemHandler outH = outHandler(out);
    private final OwnerData ownerData = new OwnerData();
    private int phase = P_IDLE, phaseTicks, status = S_READY, grabbed, lastHaul = -1;
    private boolean auto = true, queued, lastSignal;
    private long phaseStart;
    private double depth, total;
    private BlockPos target;
    private Vec3 landing;
    private UUID bucketId;

    public DredgeWinchBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DREDGE_WINCH_BE.get(), pos, state, Balance.DREDGE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int phase() { return phase; }
    public int dredgeStatus() { return status; }
    public boolean auto() { return auto; }
    public int lastHaul() { return lastHaul; }

    final NoduleSurvey.Hint surveyHint = new NoduleSurvey.Hint();

    public NoduleSurvey.Hint surveyHint() { return surveyHint; }
    public int stored() { return out.count(); }

    public void act(Player by, int action, int value) {
        switch (action) {
            case A_DROP -> queued = true;
            case A_AUTO -> auto = value != 0;
            default -> {}
        }
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void setPhase(int p) {
        phase = p;
        phaseTicks = 0;
        phaseStart = level != null ? level.getGameTime() : 0L;
        setChanged();
        sync();
    }

    private void setStatus(int s) { if (status != s) { status = s; setChanged(); sync(); } }

    static double[] sheaveLocal(double k) {
        double a = Math.toRadians(TILT_DEG * k), ry = SHEAVE_Y - HINGE_Y, rz = SHEAVE_Z - HINGE_Z;
        double y = ry * Math.cos(a) + rz * Math.sin(a), z = rz * Math.cos(a) - ry * Math.sin(a);
        return new double[] { 0.5, (HINGE_Y + y) / 16.0, (HINGE_Z + z) / 16.0 };
    }

    private Vec3 sheaveWorld() {
        double[] l = sheaveLocal(1.0);
        double[] w = worldPoint(l[0], l[1], l[2]);
        return new Vec3(w[0], w[1], w[2]);
    }

    static double hang() { return (SHEAVE_Y - BUCKET_Y) / 16.0; }

    @Override
    protected boolean process() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (surveyHint.refresh(sl, getOwner(), worldPosition)) sync();
        pulse();
        phaseTicks++;
        switch (phase) {
            case P_IDLE -> {
                if (!queued && !(auto && phaseTicks >= Balance.DREDGE_PAUSE_TICKS)) return false;
                if (!out.fits(Wmd.MANGANESE_NODULE.get(), Balance.DREDGE_YIELD_MAX)) { setStatus(S_FULL); queued = false; return false; }
                if (!plan(sl)) { queued = false; phaseTicks = 0; return false; }
                queued = false;
                setStatus(S_WORKING);
                setPhase(P_TILT_OUT);
                playOnce(sl, WmdSounds.T4_WINCH.get(), Balance.SND_CLICK, 1.1F);
                return true;
            }
            case P_TILT_OUT -> {
                if (phaseTicks >= Balance.DREDGE_TILT_TICKS) {
                    depth = 0.0;
                    setPhase(P_LOWER);
                    Vec3 s = sheaveWorld();
                    sl.playSound(null, s.x, s.y - 2.0, s.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 0.9F);
                }
                return true;
            }
            case P_LOWER -> {
                depth = Math.min(total, depth + Balance.DREDGE_LOWER);
                if (!moveBucket(sl, (float) Math.min(1.0, depth / 1.5))) return true;
                if (depth >= total) setPhase(P_GRAB);
                return true;
            }
            case P_GRAB -> {
                float jaw = Mth.clamp(phaseTicks / (float) Balance.DREDGE_GRAB_TICKS, 0.0F, 1.0F);
                if (!moveBucket(sl, 1.0F - jaw)) return true;
                if (phaseTicks >= Balance.DREDGE_GRAB_TICKS) {
                    grabbed = target != null ? NoduleMining.collect(sl, target, Balance.DREDGE_YIELD_MIN, Balance.DREDGE_YIELD_MAX) : 0;
                    if (grabbed == 0 && status == S_WORKING) status = S_EMPTY;
                    setPhase(P_HAUL);
                    playOnce(sl, WmdSounds.T4_WINCH.get(), Balance.SND_CLICK, 0.9F);
                }
                return true;
            }
            case P_HAUL -> {
                depth = Math.max(0.0, depth - Balance.DREDGE_HAUL);
                if (!moveBucket(sl, 0.0F)) return true;
                if (depth <= 0.0) {
                    removeBucket(sl);
                    setPhase(P_TILT_IN);
                }
                return true;
            }
            case P_TILT_IN -> {
                if (phaseTicks >= Balance.DREDGE_TILT_TICKS) {
                    if (grabbed > 0) {
                        out.add(Wmd.MANGANESE_NODULE.get(), grabbed);
                        Vec3 s = sheaveWorld();
                        sl.playSound(null, s.x, s.y - 1.0, s.z, SoundEvents.GRAVEL_FALL, SoundSource.BLOCKS, 1.0F, 0.8F);
                    }
                    lastHaul = grabbed;
                    if (status == S_WORKING) status = S_READY;
                    grabbed = 0;
                    target = null;
                    landing = null;
                    setPhase(P_IDLE);
                }
                return true;
            }
            default -> { setPhase(P_IDLE); return false; }
        }
    }

    private boolean plan(ServerLevel sl) {
        Vec3 s = sheaveWorld();
        int cx = Mth.floor(s.x), cz = Mth.floor(s.z), top = Mth.floor(s.y - hang() - JAW_DROP / 16.0);
        NoduleMining.Seabed bed = NoduleMining.seabed(sl, cx, top, cz, Balance.DREDGE_CABLE);
        if (bed == null) { setStatus(S_TOO_DEEP); return false; }
        if (!bed.wet()) { setStatus(S_NO_WATER); return false; }
        target = NoduleMining.nearest(sl, cx, bed.floorY(), cz, Balance.DREDGE_REACH);
        double floorTop = (target != null ? target.getY() : bed.floorY()) + 1.0;
        double lx = target != null ? target.getX() + 0.5 : s.x, lz = target != null ? target.getZ() + 0.5 : s.z;
        landing = new Vec3(lx, floorTop + JAW_DROP / 16.0, lz);
        total = Math.max(0.5, s.y - hang() - landing.y);
        return true;
    }

    private boolean moveBucket(ServerLevel sl, float jawOpen) {
        if (landing == null) { setPhase(P_HAUL); return false; }
        Vec3 s = sheaveWorld();
        double k = total <= 0 ? 1.0 : Mth.clamp(depth / total, 0.0, 1.0);
        double hx = s.x + (landing.x - s.x) * k, hz = s.z + (landing.z - s.z) * k, hy = s.y - hang() - depth;
        double drift = Math.hypot(s.x - landing.x, s.z - landing.z);
        if (phase != P_HAUL && drift > Balance.DREDGE_REACH + 4.0) {
            target = null;
            setStatus(S_DRIFTED);
            setPhase(P_HAUL);
            return false;
        }
        DredgeBucketEntity b = bucket(sl);
        if (b != null) b.follow(hx, hy, hz, s, (float) facing().toYRot(), jawOpen, grabbed > 0);
        return true;
    }

    private DredgeBucketEntity bucket(ServerLevel sl) {
        Entity e = bucketId != null ? sl.getEntity(bucketId) : null;
        if (e instanceof DredgeBucketEntity b && b.isAlive()) return b;
        DredgeBucketEntity b = new DredgeBucketEntity(Wmd.DREDGE_BUCKET_ENTITY.get(), sl);
        Vec3 s = sheaveWorld();
        b.setPos(s.x, s.y - hang(), s.z);
        sl.addFreshEntity(b);
        bucketId = b.getUUID();
        return b;
    }

    private void removeBucket(ServerLevel sl) {
        Entity e = bucketId != null ? sl.getEntity(bucketId) : null;
        if (e != null) e.discard();
        bucketId = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) removeBucket(sl);
        super.setRemoved();
    }

    @Override
    protected void idle() {
        super.idle();
        if (level instanceof ServerLevel sl && surveyHint.refresh(sl, getOwner(), worldPosition)) sync();
        lastSignal = signal();
    }

    private void pulse() {
        boolean sig = signal();
        if (sig && !lastSignal) queued = true;
        lastSignal = sig;
    }

    private boolean signal() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        for (BlockPos l : mb.footprint())
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()))) return true;
        return false;
    }

    public float tilt01(double tt) {
        double t = (tt - phaseStart) / Balance.DREDGE_TILT_TICKS;
        return switch (phase) {
            case P_TILT_OUT -> (float) smooth(t);
            case P_LOWER, P_GRAB, P_HAUL -> 1.0F;
            case P_TILT_IN -> (float) (1.0 - smooth(t));
            default -> 0.0F;
        };
    }

    private static double smooth(double k) { k = Mth.clamp(k, 0.0, 1.0); return k * k * (3.0 - 2.0 * k); }

    public boolean bucketOut() { return phase == P_LOWER || phase == P_GRAB || phase == P_HAUL; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        final float tilt = tilt01(tt) * (float) Math.toRadians(TILT_DEG) * AFRAME_SIGN;
        final boolean outNow = bucketOut();
        model.getBone("aframe").ifPresent(b -> b.setRotX(tilt));
        model.getBone("bucket").ifPresent(b -> { b.setHidden(outNow); b.setRotX(-tilt); });
        model.getBone("jaw_w").ifPresent(b -> b.setHidden(outNow));
        model.getBone("jaw_e").ifPresent(b -> b.setHidden(outNow));
        final float spin = phase == P_LOWER ? (float) (tt * 0.35) : phase == P_HAUL ? (float) (-tt * 0.22) : 0.0F;
        model.getBone("drum").ifPresent(b -> b.setRotX(spin));
        model.getBone("sheave").ifPresent(b -> b.setRotX(spin * 3.0F));
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return 0; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() {
        return switch (phase) {
            case P_TILT_OUT -> 0.1F * phaseTicks / Balance.DREDGE_TILT_TICKS;
            case P_LOWER -> 0.1F + 0.35F * (float) (total <= 0 ? 1.0 : depth / total);
            case P_GRAB -> 0.45F + 0.1F * phaseTicks / Balance.DREDGE_GRAB_TICKS;
            case P_HAUL -> 0.55F + 0.35F * (float) (total <= 0 ? 1.0 : 1.0 - depth / total);
            case P_TILT_IN -> 0.9F + 0.1F * phaseTicks / Balance.DREDGE_TILT_TICKS;
            default -> -1.0F;
        };
    }
    @Override protected List<String> labelKeys() { return List.of("nodules"); }
    @Override protected String[] iconIds() { return new String[] { "manganese_nodule" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.LIFT_MOTOR_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.DOWN) ? outH : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.dredge_winch"));
    }

    @Override
    protected String detail() { return out.count() + " nodules, phase " + phase + ", status " + status + (auto ? ", auto" : ""); }

    @Override
    protected void saveExtra(CompoundTag tag) {
        ownerData.write(tag);
        tag.putInt("dwPhase", phase); tag.putInt("dwTicks", phaseTicks); tag.putLong("dwStart", phaseStart); tag.putInt("dwStatus", status);
        tag.putInt("dwGrab", grabbed); tag.putInt("dwLast", lastHaul); tag.putBoolean("dwAuto", auto); tag.putBoolean("dwQueued", queued);
        tag.putDouble("dwDepth", depth); tag.putDouble("dwTotal", total);
        if (target != null) tag.putLong("dwTarget", target.asLong());
        if (landing != null) { tag.putDouble("dwLx", landing.x); tag.putDouble("dwLy", landing.y); tag.putDouble("dwLz", landing.z); }
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        surveyHint.read(tag);
        ownerData.read(tag);
        phase = tag.getInt("dwPhase"); phaseTicks = tag.getInt("dwTicks"); phaseStart = tag.getLong("dwStart"); status = tag.getInt("dwStatus");
        grabbed = tag.getInt("dwGrab"); lastHaul = tag.contains("dwLast") ? tag.getInt("dwLast") : -1; auto = !tag.contains("dwAuto") || tag.getBoolean("dwAuto");
        queued = tag.getBoolean("dwQueued"); depth = tag.getDouble("dwDepth"); total = tag.getDouble("dwTotal");
        target = tag.contains("dwTarget") ? BlockPos.of(tag.getLong("dwTarget")) : null;
        landing = tag.contains("dwLx") ? new Vec3(tag.getDouble("dwLx"), tag.getDouble("dwLy"), tag.getDouble("dwLz")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        surveyHint.write(tag);
        return tag;
    }
}
