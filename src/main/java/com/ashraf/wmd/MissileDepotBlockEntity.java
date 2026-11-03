package com.ashraf.wmd;

import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MissileDepotBlockEntity extends BlockEntity implements Owned, MachinePorts, EmpVulnerable {

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.DEPOT_BUFFER, Balance.DEPOT_RECEIVE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final OwnerData ownerData = new OwnerData();
    private final Buffer store = new Buffer();
    private final ItemStackHandler cells = new ItemStackHandler(Balance.DEPOT_CELLS) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return storable(s); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); sync(); }
    };

    private final IItemHandler port = new IItemHandler() {
        @Override public int getSlots() { return cells.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return cells.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack s) { return storable(s); }

        @Override
        public ItemStack insertItem(int slot, ItemStack s, boolean sim) {
            if (store.getEnergyStored() < Balance.DEPOT_FE_PER_MOVE || jammedNow()) return s;
            ItemStack rest = cells.insertItem(slot, s, sim);
            if (!sim && rest.getCount() < s.getCount()) crane(slot);
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean sim) {
            if (store.getEnergyStored() < Balance.DEPOT_FE_PER_MOVE || jammedNow()) return ItemStack.EMPTY;
            ItemStack got = cells.extractItem(slot, amount, sim);
            if (!sim && !got.isEmpty()) crane(slot);
            return got;
        }
    };

    private boolean jammedNow() { return level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition); }

    @Override public void empDrain() { store.set(0); setChanged(); }

    private long craneAt = -10_000L;
    private int craneCell;

    private ItemStack craneItem = ItemStack.EMPTY;
    private boolean craneIn;

    void announce(ItemStack s, boolean in) {
        if (level == null || s.isEmpty() || store.getEnergyStored() < Balance.DEPOT_FE_PER_MOVE) return;
        long now = level.getGameTime();
        if (now - craneAt < Balance.DEPOT_CRANE_TICKS) return;
        int cell = -1;
        for (int i = 0; i < cells.getSlots() && cell < 0; i++) {
            ItemStack c = cells.getStackInSlot(i);
            if (in ? c.isEmpty() : ItemStack.isSameItemSameComponents(c, s)) cell = i;
        }
        craneAt = now;
        craneCell = Math.max(0, cell);
        craneItem = s.copyWithCount(1);
        craneIn = in;
        sync();
    }

    static final int CRANE_TAKE = 75, CRANE_GIVE = 45;

    public MissileDepotBlockEntity(BlockPos pos, BlockState state) { super(Wmd.MISSILE_DEPOT_BE.get(), pos, state); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    static boolean storable(ItemStack s) { return s.getItem() instanceof LaunchMissileItem || s.getItem() instanceof StageItem; }

    public IItemHandler cells() { return cells; }

    IItemHandler port() { return port; }

    int freeCells() {
        int n = 0;
        for (int i = 0; i < cells.getSlots(); i++) if (cells.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    public int energy() { return store.getEnergyStored(); }

    ItemStack takeOne(Predicate<ItemStack> want, boolean sim) {
        for (int i = 0; i < cells.getSlots(); i++) if (!cells.getStackInSlot(i).isEmpty() && want.test(cells.getStackInSlot(i))) return port.extractItem(i, 1, sim);
        return ItemStack.EMPTY;
    }

    ItemStack putBack(ItemStack s) {
        ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(port, s, false);
        return rest.isEmpty() ? rest : net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(cells, rest, false);
    }

    private void crane(int cell) {
        store.use(Balance.DEPOT_FE_PER_MOVE);
        craneCell = cell;
        if (level != null) {
            if (level.getGameTime() - craneAt >= Balance.DEPOT_CRANE_TICKS) { craneAt = level.getGameTime(); craneItem = ItemStack.EMPTY; }
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        setChanged();
        sync();
    }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, f, Machines.mount(getBlockState()), part);
        return l.getX() == 4 && l.getY() == 1 && l.getZ() == 12 && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.EAST) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, f, Machines.mount(getBlockState()), part);
        boolean door = l.getZ() == 0 && l.getX() >= -2 && l.getX() <= 1 && l.getY() >= 1 && l.getY() <= 4;
        return door && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.NORTH) ? port : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MissileDepotBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        Logistics.noteDepot(be);
        if (level.getGameTime() % 100 == 0) be.setChanged();
    }

    void dropContents() {
        for (int i = 0; i < cells.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, cells.getStackInSlot(i));
            cells.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public float craneOut(float pt) {
        if (level == null) return 0.0F;
        float t = (level.getGameTime() - craneAt + pt) / Balance.DEPOT_CRANE_TICKS;
        if (t <= 0.0F || t >= 1.0F) return 0.0F;
        return t < 0.25F ? WmdUi.easeOut(t / 0.25F) : t > 0.75F ? WmdUi.easeOut((1.0F - t) / 0.25F) : 1.0F;
    }

    public float craneT(float pt) {
        if (level == null) return -1.0F;
        float t = (level.getGameTime() - craneAt + pt) / Balance.DEPOT_CRANE_TICKS;
        return t <= 0.0F || t >= 1.0F ? -1.0F : t;
    }

    public ItemStack craneItem() { return craneItem; }
    public boolean craneIn() { return craneIn; }

    public int openCell(float pt) { return craneOut(pt) > 0.05F ? craneCell : -1; }

    public int filled() {
        int n = 0;
        for (int i = 0; i < cells.getSlots(); i++) if (!cells.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("energy", store.getEnergyStored());
        tag.put("cells", cells.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("energy")) store.set(tag.getInt("energy"));
        if (tag.contains("cells")) cells.deserializeNBT(registries, tag.getCompound("cells"));
        if (tag.contains("craneAt")) { craneAt = tag.getLong("craneAt"); craneCell = tag.getInt("craneCell"); }
        craneItem = tag.contains("craneItem") ? ItemStack.parseOptional(registries, tag.getCompound("craneItem")) : ItemStack.EMPTY;
        craneIn = tag.getBoolean("craneIn");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("cells", cells.serializeNBT(registries));
        tag.putLong("craneAt", craneAt);
        tag.putInt("craneCell", craneCell);
        if (!craneItem.isEmpty()) tag.put("craneItem", craneItem.save(registries));
        tag.putBoolean("craneIn", craneIn);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
