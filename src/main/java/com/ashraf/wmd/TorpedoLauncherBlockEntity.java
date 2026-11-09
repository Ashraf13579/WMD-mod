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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class TorpedoLauncherBlockEntity extends ItemProcessBlockEntity implements Owned {

    public static final int S_READY = 0, S_EMPTY = 1, S_CHARGING = 2, S_RELOADING = 3, S_NO_POWER = 4, S_BLOCKED = 5, S_TRAINING = 6, S_FIRED = 99;

    public static final int A_FIRE = 0, A_DEPTH = 1, A_TRAIN = 2, A_FREQ = 3, A_GYRO = 4;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final OwnerData ownerData = new OwnerData();
    protected final Slot[] tslots;
    private final List<FluidTank> gauges;
    protected int depth;
    protected int charge;
    protected int reloadLeft;
    protected long lastFire = -1000L;

    protected int lastSlot = -1, firingSlot = -1;
    private int status = S_EMPTY;
    private boolean lastSignal;

    protected int frequency = 1, gyro;

    protected int[] shownKinds;

    protected TorpedoLauncherBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fe, int slotCount, int depth0) {
        super(type, pos, state, fe);
        tslots = new Slot[slotCount];
        for (int i = 0; i < slotCount; i++) tslots[i] = new Slot(1, false, s -> s.getItem() instanceof TorpedoItem);
        List<FluidTank> g = new ArrayList<>();
        for (Slot s : tslots) g.add(s.gauge);
        gauges = g;
        depth = depth0;
        shownKinds = new int[slotCount];
        java.util.Arrays.fill(shownKinds, -1);
    }

    protected abstract int chargeTicks();

    protected abstract double[][] muzzle();

    protected double exitSpeed() { return 0.6; }

    protected void onFired(ServerLevel sl, Vec3 wp, Vec3 wd) {}

    protected int nextSlot() { return firstLoaded(); }

    protected boolean onBearing() { return true; }

    protected boolean extraWork() { return false; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int depth() { return depth; }
    public int frequency() { return frequency; }
    public int gyro() { return gyro; }

    public void setFrequency(int f) {
        f = Math.max(1, Math.min(9999, f));
        if (f == frequency) return;
        if (level instanceof ServerLevel sl) FireControlNet.forget(sl, getOwner(), frequency, worldPosition.asLong());
        frequency = f;
        setChanged();
        sync();
    }

    public void setGyro(int g) {
        g = (int) Math.max(-Balance.GYRO_MAX, Math.min(Balance.GYRO_MAX, g));
        if (g == gyro) return;
        gyro = g;
        setChanged();
        sync();
    }

    public Vec3[] worldMuzzle() {
        double[][] m = muzzle();
        Vec3 p = localPoint(m[0][0], m[0][1], m[0][2]), d = localDir(m[1][0], m[1][1], m[1][2]).normalize();
        Vec3 wp = level == null ? p : SableBridge.worldPos(level, p.x, p.y, p.z), wq = level == null ? p.add(d) : SableBridge.worldPos(level, p.x + d.x, p.y + d.y, p.z + d.z);
        Vec3 wd = wq.subtract(wp);
        if (wd.lengthSqr() < 1e-6) wd = d;
        Vec3 h = new Vec3(wd.x, 0.0, wd.z);
        return new Vec3[] { wp, h.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : h.normalize() };
    }

    public Vec3 worldFront() { return worldMuzzle()[1]; }

    public TorpedoKind nextKind() { int i = firstLoaded(); return i < 0 ? null : TorpedoKind.of(tslots[i].stack); }

    public int statusNow() { return why(); }

    public void setDepth(int d) {
        d = Math.max(0, Math.min((int) Balance.TORP_MAX_DEPTH, d));
        if (d == depth) return;
        depth = d;
        setChanged();
        sync();
    }

    private Vec3 basis(int x, int y, int z) {
        BlockPos b = Machines.toWorld(BlockPos.ZERO, facing(), Machines.mount(getBlockState()), x, y, z);
        return new Vec3(b.getX(), b.getY(), b.getZ());
    }

    protected Vec3 localPoint(double lx, double ly, double lz) {
        Vec3 c = Vec3.atCenterOf(worldPosition);
        return c.add(basis(1, 0, 0).scale(lx - 0.5)).add(basis(0, 1, 0).scale(ly - 0.5)).add(basis(0, 0, 1).scale(lz - 0.5));
    }

    protected Vec3 localDir(double dx, double dy, double dz) {
        return basis(1, 0, 0).scale(dx).add(basis(0, 1, 0).scale(dy)).add(basis(0, 0, 1).scale(dz));
    }

    private int firstLoaded() {
        int att = -1;
        for (int i = 0; i < tslots.length; i++) {
            if (tslots[i].stack.isEmpty()) continue;
            if (TorpedoKind.of(tslots[i].stack) != TorpedoKind.ATT) return i;
            if (att < 0) att = i;
        }
        return att;
    }

    public boolean hasAtt() {
        for (Slot s : tslots) if (!s.stack.isEmpty() && TorpedoKind.of(s.stack) == TorpedoKind.ATT) return true;
        return false;
    }

    public int fireAtt(TorpedoEntity threat) {
        if (!(level instanceof ServerLevel sl)) return S_EMPTY;
        int i = -1;
        for (int j = 0; j < tslots.length; j++) if (!tslots[j].stack.isEmpty() && TorpedoKind.of(tslots[j].stack) == TorpedoKind.ATT) { i = j; break; }
        if (i < 0) return S_EMPTY;
        if (charge < chargeTicks()) return hasPower() ? S_CHARGING : S_NO_POWER;
        if (blocked()) return S_BLOCKED;
        firingSlot = i;
        double[][] m = muzzle();
        firingSlot = -1;
        Vec3 p = localPoint(m[0][0], m[0][1], m[0][2]), d = localDir(m[1][0], m[1][1], m[1][2]).normalize();
        Vec3 wp = SableBridge.worldPos(sl, p.x, p.y, p.z), wq = SableBridge.worldPos(sl, p.x + d.x, p.y + d.y, p.z + d.z);
        Vec3 wd = wq.subtract(wp);
        if (wd.lengthSqr() < 1e-6) wd = d;
        TorpedoEntity te = TorpedoEntity.launch(sl, TorpedoKind.ATT, wp.add(wd.normalize().scale(0.3)), wd.normalize(), exitSpeed(), depth, getOwner(), SableBridge.shipIdAt(sl, worldPosition), null);
        AntiTorpedo.track(te, threat, wd.normalize(), exitSpeed());
        onFired(sl, wp, wd.normalize());
        tslots[i].take(1);
        lastSlot = i;
        charge = 0;
        lastFire = sl.getGameTime();
        status = why();
        setChanged();
        sync();
        return S_FIRED;
    }

    public BlockPos muzzleFront() {
        double[][] m = muzzle();
        Vec3 p = localPoint(m[0][0], m[0][1], m[0][2]), d = localDir(m[1][0], m[1][1], m[1][2]).normalize();
        return BlockPos.containing(p.add(d.scale(0.6)));
    }

    private boolean blocked() {
        BlockPos front = muzzleFront();
        return level != null && !level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }

    private int why() {
        if (firstLoaded() < 0) return reloadLeft > 0 ? S_RELOADING : S_EMPTY;
        if (charge < chargeTicks()) return hasPower() ? S_CHARGING : S_NO_POWER;
        if (!onBearing()) return S_TRAINING;
        if (blocked()) return S_BLOCKED;
        return S_READY;
    }

    public int fire(Player by) { return fire(by, 0); }

    public int fire(Player by, int extraGyro) {
        if (!(level instanceof ServerLevel sl)) return S_EMPTY;
        int w = why();
        if (w != S_READY) return w;
        int i = firstLoaded();
        TorpedoKind k = TorpedoKind.of(tslots[i].stack);
        if (k == null) return S_EMPTY;
        double[][] m = muzzle();
        Vec3 p = localPoint(m[0][0], m[0][1], m[0][2]), d = localDir(m[1][0], m[1][1], m[1][2]).normalize();
        Vec3 wp = SableBridge.worldPos(sl, p.x, p.y, p.z), wq = SableBridge.worldPos(sl, p.x + d.x, p.y + d.y, p.z + d.z);
        Vec3 wd = wq.subtract(wp);
        if (wd.lengthSqr() < 1e-6) wd = d;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        UUID shooter = by != null ? by.getUUID() : getOwner();
        int g = gyro + extraGyro;
        Vec3 h = new Vec3(wd.x, 0.0, wd.z);
        Vec3 course = g == 0 || h.lengthSqr() < 1e-6 ? null : h.normalize().yRot((float) Math.toRadians(-g));
        TorpedoEntity te = TorpedoEntity.launch(sl, k, wp.add(wd.normalize().scale(0.3)), wd.normalize(), exitSpeed(), depth, shooter, ship, course);
        te.wire(frequency);
        if (k == TorpedoKind.ATT) AntiTorpedo.track(te, null, wd.normalize(), exitSpeed());
        onFired(sl, wp, wd.normalize());
        tslots[i].take(1);
        lastSlot = i;
        charge = 0;
        lastFire = sl.getGameTime();
        status = why();
        setChanged();
        sync();
        return S_FIRED;
    }

    private TorpedoRackBlockEntity rackNear() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return null;
        Direction f = facing(), mo = Machines.mount(getBlockState());
        java.util.Set<BlockPos> mine = new java.util.HashSet<>();
        for (BlockPos l : mb.footprint()) mine.add(Machines.toWorld(worldPosition, f, mo, l.getX(), l.getY(), l.getZ()));
        for (BlockPos c : mine) for (Direction d : Direction.values()) {
            BlockPos n = c.relative(d);
            if (mine.contains(n)) continue;
            BlockPos ctrl = n;
            if (level.getBlockEntity(n) instanceof MachinePartBlockEntity part) { ctrl = part.validController(level); if (ctrl == null) continue; }
            if (level.getBlockEntity(ctrl) instanceof TorpedoRackBlockEntity r && r.hasTorpedo() && r.hasPower()) return r;
        }
        return null;
    }

    @Override
    protected boolean process() {
        boolean busy = false, changed = false;
        if (charge < chargeTicks()) { charge++; busy = true; if (charge == chargeTicks()) changed = true; }
        int empty = -1;
        for (int i = 0; i < tslots.length; i++) if (tslots[i].stack.isEmpty()) { empty = i; break; }
        if (reloadLeft > 0) {
            busy = true;
            if (--reloadLeft == 0) {
                TorpedoRackBlockEntity r = rackNear();
                if (empty >= 0 && r != null) {
                    ItemStack t = r.take();
                    if (!t.isEmpty()) { tslots[empty].insert(t, false); level.playSound(null, worldPosition, WmdSounds.TP_RELOAD.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.0F); }
                }
                changed = true;
            }
        } else if (empty >= 0 && level != null && level.getGameTime() % 20 == 3) {
            TorpedoRackBlockEntity r = rackNear();
            if (r != null) { reloadLeft = Balance.TUBE_RELOAD_TICKS; r.feeding(); busy = true; changed = true; }
        }
        if (extraWork()) busy = true;
        if (level instanceof ServerLevel sl && sl.getGameTime() % 20 == 7) FireControlNet.beat(sl, getOwner(), frequency, worldPosition.asLong());
        if (level instanceof ServerLevel sl && sl.getGameTime() % Balance.ATT_SCAN_TICKS == 1 && AntiTorpedo.autoFire(sl, this)) changed = true;

        boolean sig = signal();
        if (sig && !lastSignal) { int r = fire(null); if (r != S_FIRED) changed = true; }
        lastSignal = sig;
        if (changed || (level != null && level.getGameTime() % 20 == 0)) {
            int old = status;
            status = why();
            if (changed || old != status) { setChanged(); sync(); }
        }
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level instanceof ServerLevel sl && sl.getGameTime() % 20 == 7) FireControlNet.beat(sl, getOwner(), frequency, worldPosition.asLong());
        lastSignal = signal();
        if (level != null && level.getGameTime() % 20 == 0) { int old = status; status = why(); if (old != status) { setChanged(); sync(); } }
    }

    private boolean signal() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        for (BlockPos l : mb.footprint())
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()))) return true;
        return false;
    }

    public int fireStatus() { return status; }
    public int charge01x100() { return chargeTicks() <= 0 ? 100 : Math.min(100, charge * 100 / chargeTicks()); }
    public long lastFire() { return lastFire; }
    public int lastSlot() { return lastSlot; }
    public int[] kinds() { return shownKinds; }
    public int loadedCount() { int n = 0; for (int k : shownKinds) if (k >= 0) n++; return n; }

    @Override protected Slot[] slots() { return tslots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < tslots.length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return tslots.length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return -1.0F; }

    @Override
    protected List<String> labelKeys() { List<String> l = new ArrayList<>(); for (int i = 0; i < tslots.length; i++) l.add("torpedo"); return l; }

    @Override
    protected String[] iconIds() { String[] s = new String[tslots.length]; java.util.Arrays.fill(s, "torpedo_t1"); return s; }

    @Override
    public int guiStatus() {
        if (isRunning()) return 1;
        return guiEnergy() != null && !hasPower() ? 2 : 0;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.DECK_TRAIN_LOOP.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    protected String detail() {
        return loadedCount() + " torpedo(es), charge " + charge01x100() + "%, depth " + (depth == 0 ? "launch" : depth) + ", status " + status;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("tpDepth", depth);
        tag.putInt("tpCharge", charge);
        tag.putInt("tpReload", reloadLeft);
        tag.putLong("tpLast", lastFire);
        tag.putInt("tpLastSlot", lastSlot);
        tag.putInt("tpStatus", status);
        tag.putInt("tpFreq", frequency);
        tag.putInt("tpGyro", gyro);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        depth = tag.getInt("tpDepth");
        charge = tag.getInt("tpCharge");
        reloadLeft = tag.getInt("tpReload");
        lastFire = tag.contains("tpLast") ? tag.getLong("tpLast") : -1000L;
        lastSlot = tag.contains("tpLastSlot") ? tag.getInt("tpLastSlot") : -1;
        status = tag.getInt("tpStatus");
        frequency = tag.contains("tpFreq") ? Math.max(1, tag.getInt("tpFreq")) : 1;
        gyro = tag.getInt("tpGyro");
        if (tag.contains("tpKinds")) {
            int[] k = tag.getIntArray("tpKinds");
            for (int i = 0; i < shownKinds.length; i++) shownKinds[i] = i < k.length ? k[i] : -1;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        int[] k = new int[tslots.length];
        for (int i = 0; i < k.length; i++) { TorpedoKind t = TorpedoKind.of(tslots[i].stack); k[i] = t == null ? -1 : t.ordinal(); }
        tag.putIntArray("tpKinds", k);
        ownerData.write(tag);
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            int[] k = new int[tslots.length];
            for (int i = 0; i < k.length; i++) { TorpedoKind t = TorpedoKind.of(tslots[i].stack); k[i] = t == null ? -1 : t.ordinal(); }
            if (!java.util.Arrays.equals(k, shownKinds)) { shownKinds = k; sync(); }
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) FireControlNet.forget(sl, getOwner(), frequency, worldPosition.asLong());
        super.setRemoved();
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.torpedo_launcher", tslots.length));
    }
}
