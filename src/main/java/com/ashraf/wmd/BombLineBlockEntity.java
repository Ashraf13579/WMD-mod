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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BombLineBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable {
    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public enum Stage { IDLE, LOAD, TO_PRESS, PRESS, TO_FILLING, FILLING, TO_FUZE, FUZE, TO_STORE, STORE, TO_HOME }

    public static final int PALLETS = Balance.BOMB_PALLETS;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.BOMB_BUFFER_FE, Balance.BOMB_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final BombLineKind kind;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public final ItemStackHandler inputs = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final Buffer store = new Buffer();
    private Stage stage = Stage.IDLE;
    private int stageLen, type, storeSlot;
    private long stageStart;
    private boolean auto;

    public final ItemStackHandler racked = new ItemStackHandler(PALLETS) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { BombType t = BombType.of(s); return t != null && !t.heavy; }
    };
    public final ItemStackHandler parked = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { changedAndSync(); }
        @Override public boolean isItemValid(int slot, ItemStack s) { BombType t = BombType.of(s); return t != null && t.heavy; }
        @Override public int getSlotLimit(int slot) { return 1; }
    };

    private final FluidTank naphtha = new FluidTank(Balance.BOMB_NAPHTHA_TANK, fs -> fs.getFluid() == WmdFluids.NAPHTHA.source().get()) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private final IFluidHandler naphthaIn = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return naphtha.getFluid(); }
        @Override public int getTankCapacity(int t) { return naphtha.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return naphtha.isFluidValid(s); }
        @Override public int fill(FluidStack s, FluidAction a) { return naphtha.fill(s, a); }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
    };

    public BombLineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.BOMB_LINE_BE.get(), pos, state);
        this.kind = ((BombLineBlock) state.getBlock()).kind;
    }

    private void changedAndSync() { setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public BombLineKind kind() { return kind; }
    public Stage stage() { return stage; }
    public int typeIndex() { return type; }
    public BombType type() { return BombType.ofOrdinal(type); }
    public int storeSlot() { return storeSlot; }
    public int energy() { return store.getEnergyStored(); }
    public boolean auto() { return auto; }

    public int naphthaMb() { return naphtha.getFluidAmount(); }

    @Override public void empDrain() { store.set(0); setChanged(); }

    public float progress(Level level) {
        if (stage == Stage.IDLE || stageLen <= 0) return 0.0F;
        return Mth.clamp((level.getGameTime() - stageStart) / (float) stageLen, 0.0F, 1.0F);
    }

    public float runProgress(Level level) {
        if (stage == Stage.IDLE) return 0.0F;
        int done = 0, all = 0;
        for (Stage s : Stage.values()) {
            if (s == Stage.IDLE) continue;
            int l = lenOf(s);
            all += l;
            if (s.ordinal() < stage.ordinal()) done += l;
        }
        return all <= 0 ? 0.0F : Mth.clamp((done + progress(level) * stageLen) / all, 0.0F, 1.0F);
    }

    public BombLineAnim.Pose pose(float partialTick) {
        double t = level == null ? 0 : level.getGameTime() + partialTick - stageStart;
        return BombLineAnim.compute(stage, Math.max(0.0, t), stageLen, type(), storeSlot);
    }

    public ItemStack pallet(int i) { return racked.getStackInSlot(Mth.clamp(i, 0, PALLETS - 1)); }
    public ItemStack cradleBomb() { return parked.getStackInSlot(0); }

    BombLineBlockEntity storage() { return BombLine.module(this, BombLineKind.STORAGE); }
    BombLineBlockEntity cradle() { return BombLine.module(this, BombLineKind.CRADLE); }
    BombLineBlockEntity filling() { return BombLine.module(this, BombLineKind.FILLING); }

    public int lenOf(Stage s) {
        BombType t = type();
        int storeT = t.heavy ? Balance.BOMB_CRADLE_TICKS : Balance.BOMB_STORE_TICKS, toStore = t.heavy ? Balance.BOMB_TRAVEL_TICKS * 3 / 2 : Balance.BOMB_TRAVEL_TICKS;
        int fixed = Balance.BOMB_LOAD_TICKS + 3 * Balance.BOMB_TRAVEL_TICKS + toStore + storeT + Balance.BOMB_HOME_TICKS;
        int stations = Math.max(60, BombLineRecipes.runTicks(t) - fixed);
        return switch (s) {
            case IDLE -> 0;
            case LOAD -> Balance.BOMB_LOAD_TICKS;
            case TO_PRESS, TO_FILLING, TO_FUZE -> Balance.BOMB_TRAVEL_TICKS;
            case PRESS -> (int) (stations * Balance.BOMB_STATION_SHARE[0]);
            case FILLING -> (int) (stations * Balance.BOMB_STATION_SHARE[1]);
            case FUZE -> (int) (stations * Balance.BOMB_STATION_SHARE[2]);
            case TO_STORE -> toStore;
            case STORE -> storeT;
            case TO_HOME -> Balance.BOMB_HOME_TICKS;
        };
    }

    public void cycleType(boolean back) {
        if (stage != Stage.IDLE) return;
        type = Math.floorMod(type + (back ? -1 : 1), BombType.values().length);
        sync();
    }

    public void toggleAuto() { auto = !auto; setChanged(); sync(); }

    public int blocker() {
        if (stage != Stage.IDLE) return 1;
        BombType t = type();
        if (!BombLine.complete(this, t)) return 2;
        if (t.heavy ? cradle() == null || !cradle().cradleBomb().isEmpty() : freePallet(storage(), t) < 0) return 3;
        if (store.getEnergyStored() < BombLineRecipes.runFe(t)) return 4;
        if (!BombLineRecipes.satisfied(inputs, BombLineRecipes.needs(t))) return 5;
        int nap = BombLineRecipes.naphtha(t);
        if (nap > 0 && (filling() == null || filling().naphtha.getFluidAmount() < nap)) return 6;
        return 0;
    }

    static int freePallet(BombLineBlockEntity st, BombType t) {
        if (st == null) return -1;
        for (int i = 0; i < PALLETS; i++) {
            ItemStack s = st.racked.getStackInSlot(i);
            if (BombType.of(s) == t && s.getCount() < s.getMaxStackSize()) return i;
        }
        for (int i = 0; i < PALLETS; i++) if (st.racked.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    public void startRun(Player player) {
        int b = blocker();
        if (b != 0) { if (player != null) player.displayClientMessage(Component.translatable("message.wmd.bomb_line.cannot." + b), true); return; }
        BombType t = type();
        BombLineRecipes.consume(inputs, BombLineRecipes.needs(t));
        int nap = BombLineRecipes.naphtha(t);
        if (nap > 0 && filling() != null) { filling().naphtha.drain(nap, IFluidHandler.FluidAction.EXECUTE); filling().setChanged(); }
        store.use(BombLineRecipes.runFe(t));
        level.playSound(null, worldPosition, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        enter(Stage.LOAD);
        if (player != null) player.displayClientMessage(Component.translatable("message.wmd.bomb_line.started", Component.translatable("item.wmd." + t.id)), true);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BombLineBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        if (be.kind != BombLineKind.CORE) return;
        if (be.stage == Stage.IDLE) {
            if (be.auto && level.getGameTime() % 20 == 0 && be.blocker() == 0) be.startRun(null);
            return;
        }
        long now = level.getGameTime();
        if (be.stageStart > now) { be.stageStart = now; be.sync(); }
        if (now % 20 == 0 && !BombLine.complete(be, be.type())) { be.abort(); return; }
        if (now >= be.stageStart + be.stageLen) be.advance();
    }

    private void enter(Stage s) {
        stage = s; stageLen = lenOf(s); stageStart = level.getGameTime();
        sync();
    }

    private void advance() {
        BombType t = type();
        switch (stage) {
            case TO_STORE -> {
                storeSlot = t.heavy ? 0 : Math.max(0, freePallet(storage(), t));
                enter(Stage.STORE);
            }
            case STORE -> {
                BombLineBlockEntity dest = t.heavy ? cradle() : storage();
                if (dest != null) {
                    ItemStack made = BombLineRecipes.stack(t);
                    if (t.heavy) {
                        if (!dest.parked.getStackInSlot(0).isEmpty()) return;
                        dest.parked.setStackInSlot(0, made);
                    } else {
                        int s = storeSlot;
                        ItemStack at = dest.racked.getStackInSlot(s);
                        if (!(at.isEmpty() || BombType.of(at) == t && at.getCount() < at.getMaxStackSize())) s = freePallet(dest, t);
                        if (s < 0) return;
                        dest.racked.insertItem(s, made, false);
                    }
                }
                level.playSound(null, worldPosition, WmdSounds.LAB_REACTOR_DONE.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                tell(Component.translatable("message.wmd.bomb_line.done", Component.translatable("item.wmd." + t.id)));
                enter(Stage.TO_HOME);
            }
            case TO_HOME -> enter(Stage.IDLE);
            default -> enter(Stage.values()[stage.ordinal() + 1]);
        }
    }

    private void abort() {
        tell(Component.translatable("message.wmd.bomb_line.aborted"));
        enter(Stage.IDLE);
    }

    private void tell(Component c) {
        if (level == null) return;
        Vec3 m = SableBridge.worldPos(level, worldPosition);
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(16))) p.displayClientMessage(c, true);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        if (kind == BombLineKind.CORE) for (int i = 0; i < inputs.getSlots(); i++) Containers.dropItemStack(level, x, y, z, inputs.getStackInSlot(i));
        if (kind == BombLineKind.STORAGE) for (int i = 0; i < PALLETS; i++) Containers.dropItemStack(level, x, y, z, racked.getStackInSlot(i));
        if (kind == BombLineKind.CRADLE) Containers.dropItemStack(level, x, y, z, parked.getStackInSlot(0));
    }

    private final IItemHandler hopperIn = new IItemHandler() {
        @Override public int getSlots() { return 9; }
        @Override public ItemStack getStackInSlot(int s) { return inputs.getStackInSlot(s); }
        @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return isItemValid(s, stack) ? inputs.insertItem(s, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int s) { return 64; }
        @Override public boolean isItemValid(int s, ItemStack stack) { return BombLineRecipes.accepts(stack); }
    };

    private static IItemHandler takeOnly(ItemStackHandler h) {
        return new IItemHandler() {
            @Override public int getSlots() { return h.getSlots(); }
            @Override public ItemStack getStackInSlot(int s) { return h.getStackInSlot(s); }
            @Override public ItemStack insertItem(int s, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int s, int amount, boolean simulate) { return h.extractItem(s, amount, simulate); }
            @Override public int getSlotLimit(int s) { return h.getSlotLimit(s); }
            @Override public boolean isItemValid(int s, ItemStack stack) { return false; }
        };
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return kind == BombLineKind.CORE && Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, 0, 0, 3, Direction.SOUTH) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        return switch (kind) {
            case CORE -> hopperIn;
            case STORAGE -> takeOnly(racked);
            case CRADLE -> takeOnly(parked);
            default -> null;
        };
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        return kind == BombLineKind.FILLING && Machines.atPart(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), part, side, -1, 2, 3, Direction.SOUTH) ? naphthaIn : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        if (kind == BombLineKind.STORAGE) tag.put("racked", racked.serializeNBT(registries));
        if (kind == BombLineKind.CRADLE) tag.put("parked", parked.serializeNBT(registries));
        if (kind == BombLineKind.FILLING) tag.put("naphtha", naphtha.writeToNBT(registries, new CompoundTag()));
        if (kind != BombLineKind.CORE) return;
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        writeState(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (kind == BombLineKind.STORAGE && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
        if (kind == BombLineKind.CRADLE && tag.contains("parked")) parked.deserializeNBT(registries, tag.getCompound("parked"));
        if (kind == BombLineKind.FILLING && tag.contains("naphtha")) naphtha.readFromNBT(registries, tag.getCompound("naphtha"));
        if (kind != BombLineKind.CORE) return;
        if (tag.contains("inputs")) inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        store.set(tag.getInt("energy"));
        readState(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putInt("stage", stage.ordinal()); tag.putInt("len", stageLen); tag.putLong("start", stageStart); tag.putInt("type", type);
        tag.putInt("storeSlot", storeSlot); tag.putBoolean("auto", auto);
    }

    private void readState(CompoundTag tag) {
        int s = tag.getInt("stage");
        stage = s >= 0 && s < Stage.values().length ? Stage.values()[s] : Stage.IDLE;
        stageLen = tag.getInt("len"); stageStart = tag.getLong("start"); type = Math.floorMod(tag.getInt("type"), BombType.values().length);
        storeSlot = Mth.clamp(tag.getInt("storeSlot"), 0, PALLETS - 1); auto = tag.getBoolean("auto");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (kind == BombLineKind.CORE) writeState(tag);
        if (kind == BombLineKind.STORAGE) tag.put("racked", racked.serializeNBT(registries));
        if (kind == BombLineKind.CRADLE) tag.put("parked", parked.serializeNBT(registries));
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (kind == BombLineKind.CORE) readState(tag);
        if (kind == BombLineKind.STORAGE && tag.contains("racked")) racked.deserializeNBT(registries, tag.getCompound("racked"));
        if (kind == BombLineKind.CRADLE && tag.contains("parked")) parked.deserializeNBT(registries, tag.getCompound("parked"));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
