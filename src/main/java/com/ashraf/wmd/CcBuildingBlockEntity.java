package com.ashraf.wmd;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CcBuildingBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    public static final int N = CcModule.values().length;
    private static final PortMap PORT_MAP = new PortMap(CcBuildingSpec.X0, CcBuildingSpec.X1, CcBuildingSpec.Z1 + 1)
            .power(CcBuildingSpec.SOCKET_X, CcBuildingSpec.SOCKET_Y, CcBuildingSpec.SOCKET_Z, Direction.EAST, false);
    private static final int UNKNOWN = 0, ABSENT = 1, PRESENT = 2;

    private static class Buffer extends EnergyStorage {
        Buffer(int capacity) { super(capacity, Math.max(1, capacity / 20), 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final OwnerData ownerData = new OwnerData();
    private final Buffer buffer = new Buffer(Balance.CC_BUFFER_FE);
    private boolean powered, creative, satsControlled, doorOpen, registered, dying;
    private boolean lastSignal, signalRead;

    private int placedMask, onlineMask, blockedMask;

    private CcTarget designated;

    private int defcon = CcDefcon.PEACE;
    private boolean lockdownToggle, sealed, autoClosed, override;
    private long lockdownUntil, counterUntil, liveCamUntil, liveCam;
    private float anim, prevAnim;
    private boolean animInit;

    private final ItemStackHandler modules = new ItemStackHandler(N) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(slot, stack); }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide && !dying) refresh(CcModule.values()[slot], true);
        }
    };

    private final IEnergyStorage port = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) {
            int r = buffer.receiveEnergy(n, sim);
            if (r > 0 && !sim) setChanged();
            return r;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return buffer.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return buffer.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public CcBuildingBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CC_BUILDING_BE.get(), pos, state, Balance.CC_BUILDING_FE_PER_TICK);
    }

    public static boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot >= N) return false;
        CcModule k = CcModule.values()[slot];
        return k.built && CcBuildingSpec.block(k) != null && CcBuildingSpec.kindOf(stack.getItem()) == k;
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    private CompoundTag ownerTag() {
        CompoundTag t = new CompoundTag();
        ownerData.write(t);
        return t;
    }

    public boolean isPowered() { return powered; }

    public boolean isActive() { return level != null && level.isClientSide ? running : powered; }
    public boolean isCreative() { return creative; }
    public boolean satsControlled() { return satsControlled; }
    public boolean isDoorOpen() { return doorOpen; }
    public ItemStackHandler moduleSlots() { return modules; }
    public boolean installed(CcModule k) { return !modules.getStackInSlot(k.ordinal()).isEmpty(); }
    public boolean placed(CcModule k) { return (placedMask >> k.ordinal() & 1) != 0; }
    public boolean online(CcModule k) { return (onlineMask >> k.ordinal() & 1) != 0; }
    public boolean blocked(CcModule k) { return (blockedMask >> k.ordinal() & 1) != 0; }
    public int placedMask() { return placedMask; }
    public int onlineMask() { return onlineMask; }
    public int blockedMask() { return blockedMask; }

    public int moduleCount(CcModule k) { return online(k) ? 1 : 0; }

    public CcTarget designated() { return designated; }

    void designate(CcTarget t) {
        if (java.util.Objects.equals(t, designated)) return;
        designated = t;
        setChanged();
        sync();
    }

    public int defcon() { return CcDefcon.clamp(defcon); }
    public boolean lockdownToggle() { return lockdownToggle; }
    public boolean sealed() { return sealed; }

    public boolean counterArmed(long now) { return now < counterUntil; }

    public long liveCam(long now) { return now < liveCamUntil ? liveCam : 0L; }

    int setDefcon(int d) {
        int old = defcon();
        d = CcDefcon.clamp(d);
        if (d == old) return old;
        defcon = d;
        if (d > 1) counterUntil = 0L;
        setChanged();
        sync();
        return old;
    }

    void setLockdownToggle(boolean on) {
        if (on == lockdownToggle) return;
        lockdownToggle = on;
        setChanged();
        sync();
    }

    void lockdown(long until) {
        if (until > lockdownUntil) { lockdownUntil = until; setChanged(); }
        sealCheckNow();
    }

    void endLockdown() {
        lockdownUntil = 0L;
        setChanged();
        sealCheckNow();
    }

    void armCounter(long until) { counterUntil = until; setChanged(); sync(); }

    void disarmCounter() { if (counterUntil != 0L) { counterUntil = 0L; setChanged(); sync(); } }

    void setLiveCam(long id, long until) { liveCam = id; liveCamUntil = until; setChanged(); sync(); }

    void sealCheckNow() { if (level != null && !level.isClientSide) sealStep(level, level.getGameTime()); }

    private boolean incoming(long now) {
        for (CommandCenter.Warning w : CommandCenter.warnings(getOwner())) if (now <= w.impactGt() + CcDefcon.IMPACT_GRACE_TICKS && CcDefcon.threat(this, w)) return true;
        return false;
    }

    private void sealStep(Level level, long now) {
        boolean want = powered && getOwner() != null && !dying && (now < lockdownUntil || (defcon() <= 2 && incoming(now)));
        if (want == sealed) return;
        sealed = want;
        BlockPos d = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 2, 1);
        if (want) {
            override = false;
            autoClosed = doorOpen;
            if (doorOpen) setDoor(false);
            level.playSound(null, d, WmdSounds.DEFCON_1.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
        } else {
            if (autoClosed && !override && !doorOpen) setDoor(true);
            autoClosed = override = false;
            level.playSound(null, d, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        }
        setChanged();
        sync();
    }

    void doorClick(Player player) {
        if (!sealed) { toggleDoor(); return; }
        boolean own = getOwner() != null && getOwner().equals(player.getUUID());
        BlockPos l = Machines.toLocal(worldPosition, facing(), Machines.mount(getBlockState()), player.blockPosition());
        boolean inside = CcBuildingSpec.inHall(l.getX(), l.getY(), l.getZ());
        if (!own && !inside) {
            player.displayClientMessage(Component.translatable("message.wmd.cc.door_sealed").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        toggleDoor();
        override = doorOpen;
        setChanged();
    }

    public int draw() {
        int d = Balance.CC_BUILDING_FE_PER_TICK;
        for (CcModule k : CcModule.values()) if (placed(k)) d += k.fe();
        return d;
    }

    int bufferStored() { return buffer.getEnergyStored(); }
    int bufferCapacity() { return buffer.getMaxEnergyStored(); }

    public BlockPos hallCentre() { return Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), CcBuildingSpec.CENTRE_X, CcBuildingSpec.CENTRE_Y, CcBuildingSpec.CENTRE_Z); }

    void testPower(boolean fill, boolean creativeOn) {
        buffer.set(fill ? buffer.getMaxEnergyStored() : 0);
        creative = creativeOn;
        setChanged();
        sync();
    }

    public BlockPos moduleController(CcModule k) {
        BlockPos a = CcBuildingSpec.anchor(k);
        return Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), a.getX(), a.getY(), a.getZ());
    }

    public Direction moduleFacing(CcModule k) { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), CcBuildingSpec.slot(k).facing()); }

    public boolean hosts(CcModuleBlockEntity m) {
        return m != null && m.getLevel() == level && installed(m.kind()) && m.getBlockPos().equals(moduleController(m.kind()));
    }

    public CcModuleBlockEntity module(CcModule k) {
        if (level == null) return null;
        BlockPos c = moduleController(k);
        if (!level.isLoaded(c)) return null;
        return level.getBlockEntity(c) instanceof CcModuleBlockEntity m && m.kind() == k && worldPosition.equals(m.buildingPos()) && !m.isRemoved() ? m : null;
    }

    private int presence(CcModule k, boolean mayLoad) {
        BlockPos c = moduleController(k);
        if (!mayLoad && !level.isLoaded(c)) return UNKNOWN;
        BlockEntity be = level.getBlockEntity(c);
        return be instanceof CcModuleBlockEntity m && m.kind() == k && worldPosition.equals(m.buildingPos()) ? PRESENT : ABSENT;
    }

    private void refresh(CcModule k, boolean mayLoad) {
        if (level == null || level.isClientSide || dying || CcBuildingSpec.block(k) == null) return;
        int pres = presence(k, mayLoad);
        if (pres == UNKNOWN) return;
        boolean want = installed(k);
        if (want && pres == ABSENT && place(k)) pres = PRESENT;
        else if (!want && pres == PRESENT) { remove(k); pres = ABSENT; }
        CcModuleBlockEntity m = pres == PRESENT ? module(k) : null;
        if (m != null && !Objects.equals(m.getOwner(), getOwner())) m.manage(worldPosition, ownerTag());
        int bit = 1 << k.ordinal();
        int p = pres == PRESENT ? placedMask | bit : placedMask & ~bit;
        int b = want && pres != PRESENT ? blockedMask | bit : blockedMask & ~bit;
        int o = m != null && m.isOnline() ? onlineMask | bit : onlineMask & ~bit;
        if (p != placedMask || b != blockedMask || o != onlineMask) {
            placedMask = p;
            blockedMask = b;
            onlineMask = o;
            setChanged();
            sync();
        }
    }

    private boolean place(CcModule k) {
        if (!(CcBuildingSpec.block(k) instanceof MachineBlock mb)) return false;
        BlockPos c = moduleController(k);
        Direction f = moduleFacing(k);
        for (BlockPos l : mb.footprint()) {
            BlockPos p = Machines.toWorld(c, f, Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ());
            if (!level.isLoaded(p) || !level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()) return false;
            if (PlaceGuard.blocker(level, p) != null) return false;
        }
        BlockState st = mb.defaultBlockState().setValue(MachineBlock.FACING, f);
        level.setBlock(c, st, 3);
        mb.setPlacedBy(level, c, st, null, ItemStack.EMPTY);
        if (level.getBlockEntity(c) instanceof CcModuleBlockEntity m) {
            m.manage(worldPosition, ownerTag());
            return true;
        }
        return false;
    }

    private void remove(CcModule k) {
        BlockPos c = moduleController(k);
        if (presence(k, true) == PRESENT) level.removeBlock(c, false);
    }

    private boolean minedByOwner;
    void markMinedBy(net.minecraft.world.entity.player.Player p) {
        if (getOwner() == null || p.getUUID().equals(getOwner()) || security().trusted().containsKey(p.getUUID())) minedByOwner = true;
    }
    boolean minedByOwner() { return minedByOwner; }

    void dismantle() {
        if (level == null || level.isClientSide || dying) return;
        dying = true;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (CcModule k : CcModule.values()) {
            if (CcBuildingSpec.block(k) != null) remove(k);
            ItemStack s = modules.getStackInSlot(k.ordinal());
            if (!s.isEmpty()) {
                modules.setStackInSlot(k.ordinal(), ItemStack.EMPTY);
                Containers.dropItemStack(level, x, y, z, s);
            }
        }
        placedMask = onlineMask = blockedMask = 0;
    }

    Component testInstall(CcModule k, boolean in) {
        if (!k.built || CcBuildingSpec.block(k) == null) return Component.literal(k.id + ": no module item yet");
        if (in) {
            if (!installed(k)) modules.setStackInSlot(k.ordinal(), new ItemStack(CcBuildingSpec.item(k)));
            else refresh(k, true);
            return Component.literal(k.id + (placed(k) ? ": installed at " + moduleController(k).toShortString() + " facing " + moduleFacing(k)
                    : ": in the slot but BLOCKED (clear the space at " + moduleController(k).toShortString() + ")"));
        }
        if (installed(k)) modules.setStackInSlot(k.ordinal(), ItemStack.EMPTY);
        return Component.literal(k.id + ": uninstalled");
    }

    void toggleDoor() { setDoor(!doorOpen); }

    void setDoor(boolean v) {
        if (v == doorOpen || level == null) return;
        doorOpen = v;
        setChanged();
        sync();
        BlockPos d = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 2, 1);
        level.playSound(null, d, v ? WmdSounds.CC_DOOR_OPEN.get() : WmdSounds.CC_DOOR_CLOSE.get(), SoundSource.BLOCKS, 1.2F, 1.0F);

    }

    private void doorRedstone(Level level) {
        if ((level.getGameTime() + (worldPosition.asLong() & 3L)) % 4 != 0) return;
        boolean sig = false;
        Direction f = facing();
        for (int x = -1; x <= 1 && !sig; x++) for (int y = 1; y <= 3 && !sig; y++) for (int z = 0; z <= 1 && !sig; z++)
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), x, y, z))) sig = true;
        if (!signalRead) { signalRead = true; lastSignal = sig; return; }
        if (sig != lastSignal) {
            lastSignal = sig;
            if (!sealed) setDoor(sig);
        }
    }

    public float openness(float pt) { return Mth.lerp(pt, prevAnim, anim); }

    public static void serverTickBuilding(Level level, BlockPos pos, BlockState state, CcBuildingBlockEntity be) {
        if (!be.registered) {
            CommandCenter.join(be);
            be.registered = true;
        }
        be.serverStep(level, level.getGameTime());
    }

    private void serverStep(Level level, long now) {
        if (!layoutChecked && now % 20 == 11) layoutChecked = repairLayout(level);
        boolean changed = false;
        if ((now + (worldPosition.asLong() & 15L)) % 20 == 0) for (CcModule k : CcModule.values()) refresh(k, false);
        boolean jammed = level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition);
        boolean p = false;
        if (!jammed) {
            if (creative) buffer.set(buffer.getMaxEnergyStored());
            int d = draw();
            if (buffer.getEnergyStored() >= d) { buffer.use(d); p = true; }
        }
        if (p != powered) { powered = p; changed = true; }
        if (now % 20 == 3) {
            boolean s = powered && GroundStationBlockEntity.hasPoweredStation(getOwner());
            if (s != satsControlled) { satsControlled = s; changed = true; }
        }
        if (powered != running) { running = powered; changed = true; }
        doorRedstone(level);
        if ((now + (worldPosition.asLong() & 15L)) % 20 == 7) sealStep(level, now);
        if (changed) { setChanged(); sync(); }
        if (now % 40 == 0) setChanged();
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != running) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, running), 3);
    }

    private boolean layoutChecked;

    private boolean repairLayout(Level level) {
        Direction f = facing();
        for (int x : new int[] { CcBuildingSpec.X0, CcBuildingSpec.X1 }) for (int z : new int[] { CcBuildingSpec.Z0, CcBuildingSpec.Z1 })
            if (!level.isLoaded(Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), x, 0, z))) return false;
        int added = 0, removed = 0;
        for (BlockPos l : CcBuildingSpec.footprint()) {
            BlockPos p = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ());
            if (p.equals(worldPosition) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, Wmd.MACHINE_PART.get().defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof MachinePartBlockEntity part) part.setController(worldPosition);
            added++;
        }
        MachineBlock.removing = true;
        try {
            for (int x = CcBuildingSpec.HALL_X0; x <= CcBuildingSpec.HALL_X1; x++)
                for (int y = CcBuildingSpec.HALL_Y0; y <= CcBuildingSpec.HALL_Y1; y++)
                    for (int z = CcBuildingSpec.HALL_Z0; z <= CcBuildingSpec.HALL_Z1; z++) {
                        if (CcBuildingSpec.hallPart(x, y, z)) continue;
                        BlockPos p = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), x, y, z);
                        if (level.getBlockState(p).is(Wmd.MACHINE_PART.get()) && level.getBlockEntity(p) instanceof MachinePartBlockEntity part
                                && worldPosition.equals(part.getController())) {
                            level.removeBlock(p, false);
                            removed++;
                        }
                    }
        } finally {
            MachineBlock.removing = false;
        }

        Object[][] moved = { { CcModule.PLANNER, new BlockPos(0, 5, 3) }, { CcModule.LAUNCH_CONSOLE, new BlockPos(-4, 1, 14) },
                { CcModule.ARTILLERY_CONSOLE, new BlockPos(0, 1, 14) }, { CcModule.AIR_DEFENCE, new BlockPos(4, 1, 14) }, { CcModule.ALARM_BOARD, new BlockPos(-7, 1, 8) } };
        for (Object[] mv : moved) {
            CcModule k = (CcModule) mv[0];
            BlockPos l = (BlockPos) mv[1], old = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ());
            if (!old.equals(moduleController(k)) && level.getBlockEntity(old) instanceof CcModuleBlockEntity m && m.kind() == k && worldPosition.equals(m.buildingPos())) {
                level.removeBlock(old, false);
                removed++;
            }
        }
        if (added + removed > 0) Wmd.LOGGER.info("Command center at {}: layout repaired ({} part blocks added, {} old ones removed)", worldPosition, added, removed);
        return true;
    }

    public static void clientTickBuilding(Level level, BlockPos pos, BlockState state, CcBuildingBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (!be.animInit) { be.animInit = true; be.anim = be.doorOpen ? 1.0F : 0.0F; }
        be.prevAnim = be.anim;
        be.anim = Mth.clamp(be.anim + (be.doorOpen ? 1.0F : -1.0F) / Math.max(1, CcBuildingSpec.DOOR_TICKS), 0.0F, 1.0F);
    }

    private boolean unloading;

    @Override
    public void onChunkUnloaded() { unloading = true; super.onChunkUnloaded(); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CommandCenter.leave(this);
        if (!unloading && level != null && !level.isClientSide) CommandCenter.forget(getOwner());
        registered = false;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float k = openness(pt);
        float e = k * k * (3.0F - 2.0F * k);
        final float travel = CcBuildingSpec.DOOR_TRAVEL_PX * e;
        model.getBone("leaf_l").ifPresent(b -> b.setPosX(CcBuildingSpec.XS * CcBuildingSpec.LEAF_L_DIR * travel));
        model.getBone("leaf_r").ifPresent(b -> b.setPosX(-CcBuildingSpec.XS * CcBuildingSpec.LEAF_L_DIR * travel));
        final boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean red = running && defcon() <= 1, strobe = red && ((long) (tt / 10.0)) % 2 == 0;
        model.getBone("door_lamp").ifPresent(b -> b.setHidden(dark || (red ? !strobe : doorOpen)));
        model.getBone("door_lamp_g").ifPresent(b -> b.setHidden(dark || !doorOpen || red));
        model.getBone("lights").ifPresent(b -> b.setHidden(dark || !running || (red && !strobe)));
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
    }

    @Override protected boolean work() { return false; }
    @Override public boolean hasPower() { return powered; }
    @Override protected IEnergyStorage energy() { return buffer; }
    @Override public IEnergyStorage guiEnergy() { return buffer; }
    @Override public int guiFeUse() { return draw(); }
    @Override public void empDrain() { buffer.set(0); setChanged(); }
    @Override public int guiStatus() { return isActive() ? 1 : 2; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, CcBuildingSpec.SOCKET_X, CcBuildingSpec.SOCKET_Y, CcBuildingSpec.SOCKET_Z, Direction.EAST) ? port : null;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", draw() * 20), Component.translatable("gui.wmd.info.cc_building"));
    }

    @Override
    protected String detail() {
        return Integer.bitCount(placedMask) + " modules placed, " + Integer.bitCount(onlineMask) + " online" + (isActive() ? ", online" : ", NO POWER")
                + (doorOpen ? ", door open" : ", door closed");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CC_SERVER_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(CcBuildingSpec.CENTRE_X, CcBuildingSpec.CENTRE_Y + 1, CcBuildingSpec.CENTRE_Z); }
    @Override protected float soundVolume() { return 0.05F; }

    private void writeState(CompoundTag tag) { writeState(tag, true); }

    private void writeState(CompoundTag tag, boolean full) {
        if (full) ownerData.write(tag);
        else { if (ownerData.id() != null) tag.putUUID("owner", ownerData.id()); tag.putString("ownerName", ownerData.name()); }
        tag.putBoolean("powered", powered);
        tag.putBoolean("sats", satsControlled);
        tag.putBoolean("door", doorOpen);
        tag.putInt("placed", placedMask);
        tag.putInt("online", onlineMask);
        tag.putInt("blocked", blockedMask);
        if (creative) tag.putBoolean("creative", true);
        if (designated != null) tag.put("designated", designated.save());
        tag.putInt("defcon", defcon());
        tag.putBoolean("lockdownT", lockdownToggle);
        tag.putBoolean("sealed", sealed);
        tag.putBoolean("autoClosed", autoClosed);
        tag.putBoolean("override", override);
        tag.putLong("lockdownUntil", lockdownUntil);
        tag.putLong("counterUntil", counterUntil);
        tag.putLong("liveCam", liveCam);
        tag.putLong("liveCamUntil", liveCamUntil);
    }

    private void readState(CompoundTag tag) {
        ownerData.read(tag);
        powered = tag.getBoolean("powered");
        satsControlled = tag.getBoolean("sats");
        doorOpen = tag.getBoolean("door");
        placedMask = tag.getInt("placed");
        onlineMask = tag.getInt("online");
        blockedMask = tag.getInt("blocked");
        creative = tag.getBoolean("creative");
        designated = tag.contains("designated") ? CcTarget.load(tag.getCompound("designated")) : null;
        defcon = tag.contains("defcon") ? CcDefcon.clamp(tag.getInt("defcon")) : CcDefcon.PEACE;
        lockdownToggle = tag.getBoolean("lockdownT");
        sealed = tag.getBoolean("sealed");
        autoClosed = tag.getBoolean("autoClosed");
        override = tag.getBoolean("override");
        lockdownUntil = tag.getLong("lockdownUntil");
        counterUntil = tag.getLong("counterUntil");
        liveCam = tag.getLong("liveCam");
        liveCamUntil = tag.getLong("liveCamUntil");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag);
        tag.putInt("ccEnergy", buffer.getEnergyStored());
        tag.put("modules", modules.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readState(tag);
        buffer.set(tag.getInt("ccEnergy"));
        if (tag.contains("modules")) {
            CompoundTag m = tag.getCompound("modules").copy();
            m.putInt("Size", N);
            modules.deserializeNBT(registries, m);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeState(tag, false);
        return tag;
    }
}
