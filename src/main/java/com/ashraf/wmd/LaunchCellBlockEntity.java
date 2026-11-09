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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LaunchCellBlockEntity extends ItemProcessBlockEntity implements Owned {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 6, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);
    public static final int A_LAUNCH = 0, A_FREQ = 1, A_TX = 2, A_TZ = 3, A_CLEAR = 4;

    public static final int R_NONE = -1, R_ASROC = 0, R_SLBM = 1, R_CRUISE = 2;

    public static final int SRC_NONE = 0, SRC_OWN = 1, SRC_CONSOLE = 2, SRC_NET = 3;
    public static final int S_READY = 0, S_EMPTY = 1, S_NO_TARGET = 2, S_TOO_CLOSE = 3, S_TOO_FAR = 4, S_TOO_DEEP = 5, S_NO_POWER = 6, S_LAUNCHING = 7,
            S_NO_CAPSULE = 8, S_REFUSED = 9;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot[] slots = new Slot[2];
    private final List<FluidTank> gauges = new ArrayList<>();
    private final OwnerData ownerData = new OwnerData();
    private int frequency = 1, openLeft, status = S_EMPTY, dist;

    private boolean strikeSet;
    private int strikeX, strikeZ, pendingX;

    private int aimSrc = SRC_NONE, csX, csZ;

    private int shownRound = R_NONE, shownRange, shownCaps;
    private long lastLaunch = -1000L, opening = -2000L;

    private long abortAt = -3000L;
    private boolean torn;
    private boolean lastSignal;
    private UUID launcher;

    private boolean shownLoaded;

    public LaunchCellBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.LAUNCH_CELL_BE.get(), pos, state, Balance.LC_FE);
        slots[0] = new Slot(1, false, LaunchCellBlockEntity::isRound);
        slots[1] = new Slot(Balance.LC_CAPSULES, false, s -> s.is(Wmd.CRUISE_CAPSULE.get()));
        gauges.add(slots[0].gauge);
        gauges.add(slots[1].gauge);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int frequency() { return frequency; }

    public static boolean isRound(ItemStack s) { return s.is(Wmd.ASROC.get()) || s.is(Wmd.SLBM.get()) || s.is(Wmd.CRUISE_MISSILE.get()); }

    public static int roundOf(ItemStack s) { return s.is(Wmd.ASROC.get()) ? R_ASROC : s.is(Wmd.SLBM.get()) ? R_SLBM : s.is(Wmd.CRUISE_MISSILE.get()) ? R_CRUISE : R_NONE; }

    public int roundKind() { return level != null && level.isClientSide ? shownRound : roundOf(slots[0].stack); }
    public boolean strikeSet() { return strikeSet; }
    public int strikeX() { return strikeX; }
    public int strikeZ() { return strikeZ; }
    public int capsules() { return slots[1].stack.getCount(); }
    public int shownRange() { return shownRange; }
    public int shownCapsules() { return shownCaps; }

    private int roundRange() { MissileBuild b = roundBuild(); return b != null ? MissileStats.range(b) : roundOf(slots[0].stack) == R_ASROC ? (int) Balance.ASROC_RANGE : 0; }
    public int cellStatus() { return status; }
    public int targetDistance() { return dist; }
    public long lastLaunch() { return lastLaunch; }
    public boolean loadedShown() { return shownLoaded; }

    public long openingTick() { return opening; }
    public long abortTick() { return abortAt; }
    public boolean tornShown() { return torn; }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void act(Player by, int action, int value) {
        if (action == A_FREQ) { if (level instanceof ServerLevel sl) FireControlNet.forgetCell(sl, getOwner(), frequency, worldPosition.asLong()); frequency = Math.max(1, Math.min(9999, value)); setChanged(); sync(); return; }
        if (action == A_TX) { pendingX = value; return; }
        if (action == A_TZ) { strikeX = pendingX; strikeZ = value; strikeSet = true; setChanged(); sync(); return; }
        if (action == A_CLEAR) { strikeSet = false; setChanged(); sync(); return; }
        if (action == A_LAUNCH) {
            int s = check();
            if (s != S_READY) { if (by != null) by.displayClientMessage(Component.translatable("gui.wmd.lc.st." + s), true); return; }
            launcher = by == null ? null : by.getUUID();
            openLeft = Balance.LC_OPEN_TICKS;
            opening = level == null ? 0L : level.getGameTime();
            if (level != null) level.playSound(null, worldPosition, WmdSounds.LC_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
            playAtTop(WmdSounds.CATAPULT_LATCH.get(), Balance.SND_MECH, 1.15F);
            setChanged();
            sync();
        }
    }

    private Vec3 hatch() {
        Direction f = facing();
        net.minecraft.core.Direction m = Machines.mount(getBlockState());
        Vec3 a = SableBridge.worldPos(level, Machines.toWorld(worldPosition, f, m, 0, Y1, 0)), b = SableBridge.worldPos(level, Machines.toWorld(worldPosition, f, m, 1, Y1, 1));
        return a.add(b).scale(0.5).add(0.0, 0.6, 0.0);
    }

    private MissileBuild roundBuild() {
        int r = roundOf(slots[0].stack);
        return r == R_SLBM ? SlbmItem.buildOf(slots[0].stack) : r == R_CRUISE ? CruiseMissileItem.buildOf(slots[0].stack) : null;
    }

    private Vec3 aim(ServerLevel sl, int round) {
        if (round != R_ASROC && strikeSet) { aimSrc = SRC_OWN; return new Vec3(strikeX + 0.5, CruiseRoute.groundAt(sl, strikeX, strikeZ), strikeZ + 0.5); }
        FireControlNet.Strike cs = round != R_ASROC ? FireControlNet.strikeOf(sl, getOwner(), frequency, worldPosition) : null;
        if (cs != null) { aimSrc = SRC_CONSOLE; csX = cs.x(); csZ = cs.z(); return new Vec3(cs.x() + 0.5, CruiseRoute.groundAt(sl, cs.x(), cs.z()), cs.z() + 0.5); }
        Vec3 a = FireControlNet.aimOf(sl, getOwner(), frequency);
        aimSrc = a == null ? SRC_NONE : SRC_NET;
        return a;
    }

    public int recheck() {
        int old = status, oldSrc = aimSrc, oldDist = dist;
        status = openLeft > 0 ? S_LAUNCHING : check();
        if (old != status || oldSrc != aimSrc || oldDist != dist) { setChanged(); sync(); }
        return status;
    }

    public int aimSource() { return aimSrc; }
    public int consoleStrikeX() { return csX; }
    public int consoleStrikeZ() { return csZ; }

    public int rangeNow() { return roundRange(); }
    public int minRangeNow() { int r = roundOf(slots[0].stack); return (int) (r == R_SLBM ? Balance.SLBM_MIN_RANGE : r == R_CRUISE ? Balance.CRUISE_MIN_RANGE : Balance.ASROC_MIN_RANGE); }

    private int check() {
        if (!(level instanceof ServerLevel sl)) return S_EMPTY;
        if (openLeft > 0) return S_LAUNCHING;
        if (slots[0].stack.isEmpty()) return S_EMPTY;
        if (!hasPower()) return S_NO_POWER;
        int round = roundOf(slots[0].stack);
        MissileBuild b = roundBuild();
        if (round == R_CRUISE && slots[1].stack.isEmpty()) return S_NO_CAPSULE;
        if (b != null && Config.Server.refuse(b) != null) return S_REFUSED;
        Vec3 t = aim(sl, round);
        if (t == null) { dist = 0; return S_NO_TARGET; }
        Vec3 h = hatch();
        dist = (int) Math.round(Math.hypot(t.x - h.x, t.z - h.z));
        double min = round == R_SLBM ? Balance.SLBM_MIN_RANGE : round == R_CRUISE ? Balance.CRUISE_MIN_RANGE : Balance.ASROC_MIN_RANGE;
        double max = b != null ? MissileStats.range(b) : Balance.ASROC_RANGE;
        if (dist < min) return S_TOO_CLOSE;
        if (dist > max) return S_TOO_FAR;
        if (NavalLayer.depthBelowSurface(sl, h) > Balance.LC_MAX_DEPTH) return S_TOO_DEEP;
        return S_READY;
    }

    @Override
    protected boolean process() {
        boolean busy = false;
        if (openLeft > 0) {
            busy = true;
            if (--openLeft == 0) fire();
        }
        beats();
        boolean sig = signal();
        if (sig && !lastSignal && openLeft == 0) act(null, A_LAUNCH, 0);
        lastSignal = sig;
        if (level != null && level.getGameTime() % 20 == 0) recheck();
        if (level instanceof ServerLevel sl && sl.getGameTime() % 20 == 7) FireControlNet.beatCell(sl, getOwner(), frequency, worldPosition.asLong());
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        lastSignal = signal();
        if (level != null && level.getGameTime() % 20 == 0) { int old = status; status = S_NO_POWER; if (old != status) { setChanged(); sync(); } }
        if (level instanceof ServerLevel sl && sl.getGameTime() % 20 == 7) FireControlNet.beatCell(sl, getOwner(), frequency, worldPosition.asLong());
    }

    public boolean launchFromConsole(Player by) {
        int before = openLeft;
        act(by, A_LAUNCH, 0);
        return openLeft > 0 && before == 0;
    }

    public int loadedCount() { return slots[0].stack.isEmpty() ? 0 : 1; }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) FireControlNet.forgetCell(sl, getOwner(), frequency, worldPosition.asLong());
        super.setRemoved();
    }

    private void fire() {
        if (!(level instanceof ServerLevel sl)) return;
        if (slots[0].stack.isEmpty()) { abort(sl); return; }
        int round = roundOf(slots[0].stack);
        if (round == R_CRUISE && slots[1].stack.isEmpty()) { abort(sl); return; }
        Vec3 t = aim(sl, round);
        if (t == null) { abort(sl); return; }
        MissileBuild b = roundBuild();
        UUID by = launcher != null ? launcher : getOwner();
        net.minecraft.server.level.ServerPlayer who = launcher == null ? null : sl.getServer().getPlayerList().getPlayer(launcher);
        String byName = who != null ? who.getGameProfile().getName() : getOwnerName().isEmpty() ? "?" : getOwnerName();
        Vec3 h = hatch();
        slots[0].take(1);
        if (round == R_SLBM) SlbmEntity.launch(sl, h, b, Mth.floor(t.x), (int) Math.round(t.y), Mth.floor(t.z), by, byName);
        else if (round == R_CRUISE) { slots[1].take(1); CapsuleEntity.launch(sl, h, b, Mth.floor(t.x), Mth.floor(t.z), by, byName); }
        else AsrocEntity.launch(sl, h, t, NavalLayer.depthBelowSurface(sl, t), by);
        lastLaunch = sl.getGameTime();
        torn = true;
        plume(sl, h);
        setChanged();
        sync();
    }

    private void abort(ServerLevel sl) {
        abortAt = sl.getGameTime();
        playAtTop(WmdSounds.LC_HATCH.get(), Balance.SND_MECH, 0.85F);
        setChanged();
        sync();
    }

    private static void plume(ServerLevel sl, Vec3 h) {
        boolean wet = sl.getFluidState(BlockPos.containing(h)).is(net.minecraft.tags.FluidTags.WATER);
        if (wet) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, h.x, h.y + 0.5, h.z, 50, 0.45, 1.2, 0.45, 0.25);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, h.x, h.y + 0.3, h.z, 40, 0.5, 0.6, 0.5, 0.15);
        } else {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, h.x, h.y + 0.6, h.z, 36, 0.45, 0.9, 0.45, 0.08);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, h.x, h.y + 0.2, h.z, 14, 0.5, 0.2, 0.5, 0.06);
        }
    }

    private void beats() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        boolean aborted = abortAt > lastLaunch;
        long s = now - (aborted ? abortAt : lastLaunch);
        if (s < 0 || s > LaunchCellAnim.LATCH_AT + 2) return;
        if (!aborted) {
            if (s == 1) { Vec3 v = vent(); sl.playSound(null, v.x, v.y, v.z, WmdSounds.NR_STEAM.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.8F); }
            if (s >= 1 && s <= 15 && s % 2 == 1) {
                Vec3 v = vent();
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, v.x, v.y, v.z, s == 1 ? 10 : 3, 0.08, 0.05, 0.08, s == 1 ? 0.08 : 0.03);
            }
            if (s == LaunchCellAnim.CLOSE_AT) playAtTop(WmdSounds.LC_HATCH.get(), Balance.SND_MECH, 0.85F);
        }
        if (s == (aborted ? LaunchCellAnim.LATCH_AT - LaunchCellAnim.HOLD : LaunchCellAnim.LATCH_AT)) playAtTop(WmdSounds.CATAPULT_LATCH.get(), Balance.SND_MECH, 1.0F);
    }

    private Vec3 vent() { double[] w = worldPoint(-5.0 / 16.0, 27.0 / 16.0, 19.0 / 16.0); return new Vec3(w[0], w[1], w[2]); }

    private void playAtTop(SoundEvent e, float volume, float pitch) {
        if (level instanceof ServerLevel) { Vec3 h = hatch(); level.playSound(null, h.x, h.y, h.z, e, SoundSource.BLOCKS, volume, pitch); }
    }

    private boolean signal() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        for (BlockPos l : mb.footprint())
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()))) return true;
        return false;
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected int inputTanks() { return 2; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return openLeft > 0 ? 1.0F - openLeft / (float) Balance.LC_OPEN_TICKS : -1.0F; }
    @Override protected List<String> labelKeys() { return List.of("lc_round", "cruise_capsule"); }
    @Override protected String[] iconIds() { return new String[] { "slbm", "cruise_capsule" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.LC_HATCH.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 0); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.launch_cell"));
    }

    @Override
    protected String detail() {
        String[] n = { "1 anti-sub rocket", "1 SLBM", "1 cruise missile" };
        int r = roundOf(slots[0].stack);
        return (r < 0 ? "empty" : n[r]) + ", " + capsules() + " capsule(s), frequency " + frequency + (strikeSet ? ", strike target " + strikeX + " " + strikeZ : "") + ", status " + status;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("lcFreq", frequency); tag.putLong("lcLast", lastLaunch); tag.putLong("lcOpening", opening); tag.putInt("lcStatus", status); tag.putInt("lcDist", dist);
        tag.putBoolean("lcStrike", strikeSet); tag.putInt("lcSx", strikeX); tag.putInt("lcSz", strikeZ);
        tag.putInt("lcSrc", aimSrc); tag.putInt("lcCsx", csX); tag.putInt("lcCsz", csZ);
        tag.putLong("lcAbort", abortAt); tag.putBoolean("lcTorn", torn);
        ownerData.write(tag);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("lcFreq")) frequency = Math.max(1, tag.getInt("lcFreq"));
        lastLaunch = tag.contains("lcLast") ? tag.getLong("lcLast") : -1000L;
        opening = tag.contains("lcOpening") ? tag.getLong("lcOpening") : -2000L;
        status = tag.getInt("lcStatus");
        dist = tag.getInt("lcDist");
        strikeSet = tag.getBoolean("lcStrike"); strikeX = tag.getInt("lcSx"); strikeZ = tag.getInt("lcSz");
        aimSrc = tag.getInt("lcSrc"); csX = tag.getInt("lcCsx"); csZ = tag.getInt("lcCsz");
        abortAt = tag.contains("lcAbort") ? tag.getLong("lcAbort") : -3000L;
        torn = tag.getBoolean("lcTorn");
        if (tag.contains("lcRound")) shownRound = tag.getInt("lcRound");
        if (tag.contains("lcRange")) shownRange = tag.getInt("lcRange");
        if (tag.contains("lcCaps")) shownCaps = tag.getInt("lcCaps");
        ownerData.read(tag);
        if (tag.contains("lcLoaded")) shownLoaded = tag.getBoolean("lcLoaded");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        tag.putBoolean("lcLoaded", !slots[0].stack.isEmpty());
        tag.putInt("lcRound", roundOf(slots[0].stack));
        tag.putInt("lcRange", roundRange());
        tag.putInt("lcCaps", capsules());
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (torn && level != null && !level.isClientSide && !slots[0].stack.isEmpty()) { torn = false; sync(); }
        if (level != null && !level.isClientSide && (shownLoaded == slots[0].stack.isEmpty() || shownRound != roundOf(slots[0].stack) || shownCaps != capsules() || shownRange != roundRange())) {
            shownLoaded = !slots[0].stack.isEmpty(); shownRound = roundOf(slots[0].stack); shownCaps = capsules(); shownRange = roundRange(); sync();
        }
    }
}
