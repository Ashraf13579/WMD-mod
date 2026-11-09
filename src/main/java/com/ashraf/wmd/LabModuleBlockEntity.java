package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LabModuleBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(net.minecraft.world.entity.player.Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage {
        IDLE(0), LOAD(Balance.LAB_LOAD_TICKS), TO_PRESS(Balance.LAB_TRAVEL_TICKS), PRESS(Balance.LAB_PRESS_TICKS), TO_FILLER(Balance.LAB_TRAVEL_TICKS),
        FILLER(Balance.LAB_FILL_TICKS), TO_REACTOR(Balance.LAB_TRAVEL_TICKS), REACTOR(0), TO_OVEN(Balance.LAB_TO_OVEN_TICKS), OVEN(Balance.LAB_OVEN_TICKS),
        TO_STORE(Balance.LAB_TRAVEL_TICKS), STORE(Balance.LAB_STORE_TICKS), TO_HOME(Balance.LAB_HOME_TICKS), FAIL_HOME(Balance.LAB_HOME_TICKS);
        public final int ticks;
        Stage(int ticks) { this.ticks = ticks; }
    }

    public static final int SLOTS = 10;

    public static final float SPIN = 0.2F;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.LAB_BUFFER_FE, Balance.LAB_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final LabKind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public final ItemStackHandler inputs = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Buffer store = new Buffer();
    private Stage stage = Stage.IDLE;
    private int stageLen, type, grade, storeSlot;
    private float storeFromDeg, storeToDeg;
    private long stageStart;
    private boolean reactorStarted;

    private boolean auto, autoRun;
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank heavyWater = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(
            Balance.LAB_HEAVY_WATER_TANK, fs -> fs.getFluid() == WmdFluids.HEAVY_WATER.source().get()) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    private final net.neoforged.neoforge.fluids.capability.IFluidHandler heavyWaterIn = new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int t) { return heavyWater.getFluid(); }
        @Override public int getTankCapacity(int t) { return heavyWater.getCapacity(); }
        @Override public boolean isFluidValid(int t, net.neoforged.neoforge.fluids.FluidStack s) { return heavyWater.isFluidValid(s); }
        @Override public int fill(net.neoforged.neoforge.fluids.FluidStack s, FluidAction a) { return heavyWater.fill(s, a); }
        @Override public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack s, FluidAction a) { return net.neoforged.neoforge.fluids.FluidStack.EMPTY; }
        @Override public net.neoforged.neoforge.fluids.FluidStack drain(int max, FluidAction a) { return net.neoforged.neoforge.fluids.FluidStack.EMPTY; }
    };

    public final ItemStackHandler racked = new ItemStackHandler(SLOTS) {
        @Override protected void onContentsChanged(int slot) { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    };
    private float baseDeg;
    private long refTime;

    public LabModuleBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.LAB_BE.get(), pos, state);
        this.kind = ((LabModuleBlock) state.getBlock()).kind;
    }

    public LabKind kind() { return kind; }
    public Stage stage() { return stage; }
    public int typeIndex() { return type; }
    public WarheadType type() { return WarheadType.ofOrdinal(type); }
    public int grade() { return grade; }
    public int storeSlot() { return storeSlot; }

    public double angleNow(double time) { return baseDeg + SPIN * (time - refTime); }
    public int energy() { return store.getEnergyStored(); }
    public boolean atReactor() { return stage == Stage.REACTOR; }
    public IEnergyStorage energyStore() { return store; }
    public boolean auto() { return auto; }
    public boolean autoRun() { return autoRun; }
    public int heavyWater() { return heavyWater.getFluidAmount(); }

    public int autoBlocker() {
        int b = blocker();
        if (b != 0) return b;
        return heavyWater.getFluidAmount() < Balance.LAB_AUTO_HEAVY_WATER ? 6 : 0;
    }

    public void toggleAuto() { auto = !auto; setChanged(); sync(); }

    private void startAuto() {
        LabRecipes.consume(inputs, LabRecipes.needs(type()));
        store.use(Balance.LAB_RUN_FE);
        heavyWater.drain(Balance.LAB_AUTO_HEAVY_WATER, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        grade = 0;
        autoRun = true;
        level.playSound(null, worldPosition, WmdSounds.WR_STAGE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.2F);
        enter(Stage.LOAD, Stage.LOAD.ticks);
    }

    @Override public void empDrain() { store.set(0); setChanged(); }

    public float progress(Level level) {
        if (stage == Stage.IDLE || stageLen <= 0) return 0.0F;
        return Mth.clamp((level.getGameTime() - stageStart) / (float) stageLen, 0.0F, 1.0F);
    }

    public LabAnim.Pose pose(float partialTick) {
        double t = level == null ? 0 : level.getGameTime() + partialTick - stageStart;
        BlockEntity st = LabLine.module(this, LabKind.STORAGE);
        double now = level == null ? 0 : level.getGameTime() + partialTick;
        double rest = st instanceof LabModuleBlockEntity s ? s.angleNow(now) : 0.0;
        return LabAnim.compute(stage, Math.max(0.0, t), stageLen, grade, rest, storeFromDeg, storeToDeg);
    }

    public ItemStack cradle(int i) { return racked.getStackInSlot(Mth.clamp(i, 0, SLOTS - 1)); }

    private LabModuleBlockEntity storage() { return LabLine.module(this, LabKind.STORAGE) instanceof LabModuleBlockEntity s ? s : null; }

    public void cycleType(boolean back) {
        if (stage != Stage.IDLE) return;
        int n = WarheadType.values().length;
        do type = (type + (back ? n - 1 : 1)) % n; while (WarheadType.ofOrdinal(type).bombOnly() || WarheadType.ofOrdinal(type).machineOnly());
        sync();
    }

    public int blocker() {
        if (stage != Stage.IDLE) return 1;
        if (!LabLine.complete(this)) return 2;
        LabModuleBlockEntity st = storage();
        if (st == null || freeSlot(st) < 0) return 3;
        if (store.getEnergyStored() < Balance.LAB_RUN_FE) return 4;
        if (!LabRecipes.satisfied(inputs, LabRecipes.needs(type()))) return 5;
        return 0;
    }

    private static int freeSlot(LabModuleBlockEntity st) {
        for (int i = 0; i < SLOTS; i++) if (st.racked.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    static double alignAngle(int s) { return (s - SLOTS / 2) * (360.0 / SLOTS); }

    private void pickChamber(LabModuleBlockEntity st, double now) {
        double a = st.angleNow(now);
        int best = -1; double bestDelta = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!st.racked.getStackInSlot(i).isEmpty()) continue;
            double d = alignAngle(i) - a;
            d -= 360.0 * Math.floor((d + 180.0) / 360.0);
            if (best < 0 || Math.abs(d) < Math.abs(bestDelta)) { best = i; bestDelta = d; }
        }
        storeSlot = Math.max(0, best);
        storeFromDeg = (float) a;
        storeToDeg = (float) (a + bestDelta);
    }

    public void startRun(Player player) {
        int b = blocker();
        if (b != 0) { player.displayClientMessage(Component.translatable("message.wmd.lab.cannot." + b), true); return; }
        LabRecipes.consume(inputs, LabRecipes.needs(type()));
        store.use(Balance.LAB_RUN_FE);
        grade = 0;
        autoRun = false;
        level.playSound(null, worldPosition, WmdSounds.WR_STAGE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.15F);
        enter(Stage.LOAD, Stage.LOAD.ticks);
        player.displayClientMessage(Component.translatable("message.wmd.lab.started", Component.translatable("warhead.wmd." + type().id)), true);
    }

    public void reactorDone(boolean ok, boolean refined) {
        if (stage != Stage.REACTOR) return;
        if (ok) { grade = refined ? 1 : 0; enter(Stage.TO_OVEN, Stage.TO_OVEN.ticks); }
        else { grade = 0; enter(Stage.FAIL_HOME, Stage.FAIL_HOME.ticks); }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LabModuleBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        if (be.kind == LabKind.STORAGE) Logistics.noteLab(be);
        if (be.kind != LabKind.CORE) return;
        if (be.stage == Stage.IDLE) {
            if (be.auto && level.getGameTime() % 20 == 0 && be.autoBlocker() == 0) be.startAuto();
            return;
        }
        long now = level.getGameTime();
        if (be.stageStart > now) { be.stageStart = now; be.sync(); }
        if (now % 20 == 0 && !LabLine.complete(be)) { be.abort(); return; }
        if (be.stage == Stage.REACTOR) {

            if (!be.reactorStarted && now >= be.stageStart + 26 && LabLine.module(be, LabKind.REACTOR) instanceof WarheadReactorBlockEntity r) {
                be.reactorStarted = true;
                if (!r.isRunning()) { if (be.autoRun) r.lineStartAuto(); else r.lineStart(); }
            }
            return;
        }
        if (now >= be.stageStart + be.stageLen) be.advance();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LabModuleBlockEntity be) {}

    private void enter(Stage s, int len) {
        stage = s; stageLen = len; stageStart = level.getGameTime();
        sync();
    }

    private void advance() {
        switch (stage) {
            case LOAD -> enter(Stage.TO_PRESS, Stage.TO_PRESS.ticks);
            case TO_PRESS -> enter(Stage.PRESS, Stage.PRESS.ticks);
            case PRESS -> enter(Stage.TO_FILLER, Stage.TO_FILLER.ticks);
            case TO_FILLER -> enter(Stage.FILLER, Stage.FILLER.ticks);
            case FILLER -> enter(Stage.TO_REACTOR, Stage.TO_REACTOR.ticks);
            case TO_REACTOR -> {
                reactorStarted = false;
                enter(Stage.REACTOR, 0);
                if (LabLine.module(this, LabKind.REACTOR) instanceof WarheadReactorBlockEntity r) r.lineReady(type);
            }
            case TO_OVEN -> enter(Stage.OVEN, Stage.OVEN.ticks);
            case OVEN -> enter(Stage.TO_STORE, Stage.TO_STORE.ticks);
            case TO_STORE -> {
                LabModuleBlockEntity st = storage();
                if (st != null) pickChamber(st, level.getGameTime());
                enter(Stage.STORE, Stage.STORE.ticks);
            }
            case STORE -> {
                ItemStack w = LabRecipes.warheadStack(type());
                if (grade > 0) w.set(WmdComponents.WARHEAD_GRADE.get(), 1);
                LabModuleBlockEntity st = storage();
                if (st != null) {
                    int s = st.racked.getStackInSlot(storeSlot).isEmpty() ? storeSlot : freeSlot(st);
                    if (s < 0) return;
                    st.racked.setStackInSlot(s, w);
                    st.baseDeg = s == storeSlot ? storeToDeg : (float) alignAngle(s);
                    st.refTime = level.getGameTime();
                    st.sync();
                }
                level.playSound(null, worldPosition, WmdSounds.LAB_DONE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, grade > 0 ? 1.15F : 1.0F);
                tell(Component.translatable(grade > 0 ? "message.wmd.lab.done_refined" : "message.wmd.lab.done", Component.translatable("warhead.wmd." + type().id)));
                enter(Stage.TO_HOME, Stage.TO_HOME.ticks);
            }
            case TO_HOME, FAIL_HOME -> enter(Stage.IDLE, 0);
            default -> { }
        }
    }

    private void abort() {
        tell(Component.translatable("message.wmd.lab.aborted"));
        grade = 0;
        enter(Stage.IDLE, 0);
    }

    private void tell(Component c) {
        if (level == null) return;
        Vec3 m = SableBridge.worldPos(level, worldPosition);
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(14))) p.displayClientMessage(c, true);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        if (kind == LabKind.CORE) for (int i = 0; i < inputs.getSlots(); i++) Containers.dropItemStack(level, x, y, z, inputs.getStackInSlot(i));
        if (kind == LabKind.STORAGE) for (int i = 0; i < SLOTS; i++) Containers.dropItemStack(level, x, y, z, racked.getStackInSlot(i));
    }

    private final IItemHandler hopperIn = new IItemHandler() {
        @Override public int getSlots() { return 9; }
        @Override public ItemStack getStackInSlot(int s) { return inputs.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return isItemValid(s, stack) ? inputs.insertItem(s, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int s) { return 64; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return LabRecipes.accepts(stack); }
    };

    private final IItemHandler hopperOut = new IItemHandler() {
        @Override public int getSlots() { return SLOTS; }
        @Override public ItemStack getStackInSlot(int s) { return racked.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int s) { return 1; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return false; }
    };

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return kind == LabKind.CORE && Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, 0, 0, 3, Direction.SOUTH) ? store : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return kind == LabKind.CORE ? hopperIn : null; }

    @Override
    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluidPort(BlockPos part, Direction side) {
        return kind == LabKind.CORE && Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, -2, 1, 3, Direction.SOUTH) ? heavyWaterIn : null;
    }

    public IItemHandler storageOut() { return racked; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (kind == LabKind.STORAGE) { tag.put("racked", racked.serializeNBT(registries)); tag.putFloat("base", baseDeg); tag.putLong("ref", refTime); return; }
        if (kind != LabKind.CORE) return;
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        tag.put("heavyWater", heavyWater.writeToNBT(registries, new CompoundTag()));
        writeState(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (kind == LabKind.STORAGE) {
            if (tag.contains("racked")) readRacked(registries, tag.getCompound("racked"));
            baseDeg = tag.getFloat("base"); refTime = tag.getLong("ref");
            return;
        }
        if (kind != LabKind.CORE) return;
        if (tag.contains("inputs")) inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        store.set(tag.getInt("energy"));
        if (tag.contains("heavyWater")) heavyWater.readFromNBT(registries, tag.getCompound("heavyWater"));
        readState(tag);
    }

    private void readRacked(HolderLookup.Provider registries, CompoundTag tag) {
        ItemStackHandler tmp = new ItemStackHandler(SLOTS);
        tmp.deserializeNBT(registries, tag);
        for (int i = 0; i < SLOTS; i++) racked.setStackInSlot(i, i < tmp.getSlots() ? tmp.getStackInSlot(i) : ItemStack.EMPTY);
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("stage", stage.ordinal()); tag.putInt("len", stageLen); tag.putLong("start", stageStart); tag.putInt("type", type); tag.putInt("grade", grade);
        tag.putInt("storeSlot", storeSlot); tag.putFloat("storeFrom", storeFromDeg); tag.putFloat("storeTo", storeToDeg);
        tag.putBoolean("auto", auto); tag.putBoolean("autoRun", autoRun);
    }

    private void readState(CompoundTag tag) {
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageLen = tag.getInt("len"); stageStart = tag.getLong("start"); type = tag.getInt("type"); grade = tag.getInt("grade");
        storeSlot = Mth.clamp(tag.getInt("storeSlot"), 0, SLOTS - 1); storeFromDeg = tag.getFloat("storeFrom"); storeToDeg = tag.getFloat("storeTo");
        auto = tag.getBoolean("auto"); autoRun = tag.getBoolean("autoRun");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (kind == LabKind.CORE) writeState(tag);
        if (kind == LabKind.STORAGE) { tag.put("racked", racked.serializeNBT(registries)); tag.putFloat("base", baseDeg); tag.putLong("ref", refTime); }
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (kind == LabKind.CORE) readState(tag);
        if (kind == LabKind.STORAGE) {
            if (tag.contains("racked")) readRacked(registries, tag.getCompound("racked"));
            baseDeg = tag.getFloat("base"); refTime = tag.getLong("ref");
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
