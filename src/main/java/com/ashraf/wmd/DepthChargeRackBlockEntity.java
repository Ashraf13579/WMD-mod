package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DepthChargeRackBlockEntity extends ItemProcessBlockEntity implements Owned {

    public static final int X0 = 0, X1 = 0, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.EAST, false);
    public static final int A_DROP = 0, A_PATTERN = 1, A_DEPTH = 2;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot[] slots = new Slot[3];
    private final List<FluidTank> gauges = new ArrayList<>();
    private int depth = (int) Balance.DC_DEPTH_DEFAULT, releaseLeft, queued;
    private long lastDrop = -1000L, releaseStart = -1000L;

    static final double DROP_FWD = 0.9, DROP_UP = 0.04, DROP_SPEED = 0.2, DROP_SLOPE = 3.6 / 13.0;
    static final int SHUT_TICKS = 6;
    private java.util.UUID dropper;
    private boolean lastSignal;
    private final OwnerData ownerData = new OwnerData();

    private int shown;

    public DepthChargeRackBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DEPTH_CHARGE_RACK_BE.get(), pos, state, Balance.DC_RACK_FE);
        for (int i = 0; i < slots.length; i++) { slots[i] = new Slot(1, false, s -> s.is(Wmd.DEPTH_CHARGE.get())); gauges.add(slots[i].gauge); }
    }

    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int depth() { return depth; }
    public long lastDrop() { return lastDrop; }

    public long releaseStart() { return releaseStart; }

    public int shownCount() { return shown; }
    public int count() { int n = 0; for (Slot s : slots) if (!s.stack.isEmpty()) n++; return n; }

    public void act(Player by, int action, int value) {
        switch (action) {
            case A_DROP -> { if (count() > 0) { queued = Math.max(queued, 1); dropper = by == null ? null : by.getUUID(); } }
            case A_PATTERN -> { queued = count(); dropper = by == null ? null : by.getUUID(); }
            case A_DEPTH -> depth = (int) Math.max(Balance.DC_DEPTH_MIN, Math.min(Balance.DC_DEPTH_MAX, value));
            default -> {}
        }
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void release() {
        if (!(level instanceof ServerLevel sl)) return;
        int first = -1;
        for (int i = 0; i < slots.length; i++) if (!slots[i].stack.isEmpty()) { first = i; break; }
        if (first < 0) { queued = 0; return; }
        slots[first].take(1);
        for (int i = first; i + 1 < slots.length; i++) { slots[i].stack = slots[i + 1].stack; slots[i + 1].stack = ItemStack.EMPTY; slots[i].sync(); slots[i + 1].sync(); }
        Direction f = facing();
        net.minecraft.core.Direction mount = Machines.mount(getBlockState());
        BlockPos gate = Machines.toWorld(worldPosition, f, mount, 0, 0, 0), out = Machines.toWorld(worldPosition, f, mount, 0, 0, -1);
        Vec3 a = SableBridge.worldPos(sl, gate), b = SableBridge.worldPos(sl, out);
        Vec3 dir = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : dir.normalize();
        Vec3 at = a.add(dir.scale(DROP_FWD)).add(0.0, DROP_UP, 0.0);
        java.util.UUID ship = SableBridge.active() ? SableBridge.shipIdAt(sl, worldPosition) : null;
        DepthChargeEntity.drop(sl, at, dir.scale(DROP_SPEED).add(0.0, -DROP_SPEED * DROP_SLOPE, 0.0), depth, dropper != null ? dropper : getOwner(), ship, worldPosition);
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.DC_RELEASE.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.95F + sl.random.nextFloat() * 0.1F);
        lastDrop = sl.getGameTime();
        queued = Math.max(0, queued - 1);
        setChanged();
        sync();
    }

    @Override
    protected boolean process() {
        boolean busy = false;
        if (releaseLeft > 0) {
            busy = true;
            if (--releaseLeft == 0) release();
        } else if (queued > 0 && count() > 0 && level != null && level.getGameTime() - lastDrop >= Balance.DC_PATTERN_TICKS) {
            releaseLeft = Balance.DC_RELEASE_TICKS;
            releaseStart = level.getGameTime();
            beat(WmdSounds.CATAPULT_LATCH.get(), Balance.SND_MECH, 1.15F);
            sync();
            busy = true;
        } else if (queued > 0 && count() == 0) queued = 0;
        pulse();
        shutBeat();
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        lastSignal = signal();
        shutBeat();
    }

    private void shutBeat() {
        if (level != null && level.getGameTime() - lastDrop == SHUT_TICKS) beat(WmdSounds.LC_HATCH.get(), Balance.SND_MECH, 1.2F);
    }

    private void beat(SoundEvent e, float vol, float pitch) {
        if (!(level instanceof ServerLevel sl)) return;
        Vec3 p = SableBridge.worldPos(sl, worldPosition);
        sl.playSound(null, p.x, p.y, p.z, e, SoundSource.BLOCKS, vol, pitch + (sl.random.nextFloat() - 0.5F) * 0.1F);
    }

    private void pulse() {
        boolean sig = signal();
        if (sig && !lastSignal && count() > 0) { queued = Math.max(queued, 1); dropper = null; }
        lastSignal = sig;
    }

    private boolean signal() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        for (BlockPos l : mb.footprint())
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()))) return true;
        return false;
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots.length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return slots.length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return releaseLeft > 0 ? 1.0F - releaseLeft / (float) Balance.DC_RELEASE_TICKS : -1.0F; }
    @Override protected List<String> labelKeys() { return List.of("depth_charge", "depth_charge", "depth_charge"); }
    @Override protected String[] iconIds() { return new String[] { "depth_charge", "depth_charge", "depth_charge" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.DC_RELEASE.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.EAST) ? energy() : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.depth_charge_rack"));
    }

    @Override
    protected String detail() { return count() + " of 3 depth charges, fuse " + depth + " deep" + (queued > 0 ? ", releasing" : ""); }

    @Override
    protected void saveExtra(CompoundTag tag) { tag.putInt("dcDepth", depth); tag.putLong("dcLast", lastDrop); tag.putInt("dcQueued", queued); ownerData.write(tag); }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("dcDepth")) depth = tag.getInt("dcDepth");
        ownerData.read(tag);
        lastDrop = tag.contains("dcLast") ? tag.getLong("dcLast") : -1000L;
        queued = tag.getInt("dcQueued");
        if (tag.contains("dcShown")) shown = tag.getInt("dcShown");
        if (tag.contains("dcRelStart")) releaseStart = tag.getLong("dcRelStart");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        tag.putInt("dcShown", count());
        tag.putLong("dcRelStart", releaseStart);
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && count() != shown) { shown = count(); sync(); }
    }
}
