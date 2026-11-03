package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public abstract class ItemProcessBlockEntity extends ProcessUnitBlockEntity implements GuiItemSlots {

    protected static final class Slot {
        final int max;
        final Predicate<ItemStack> valid;
        final boolean output;
        ItemStack stack = ItemStack.EMPTY;
        final FluidTank gauge;
        int cx, cy, cz;
        Direction cside;

        Slot(int max, boolean output, Predicate<ItemStack> valid) { this.max = max; this.output = output; this.valid = valid; this.gauge = new FluidTank(max); }

        Slot chute(int x, int y, int z, Direction side) { cx = x; cy = y; cz = z; cside = side; return this; }

        void sync() { gauge.setFluid(stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, stack.getCount())); }

        int count() { return stack.getCount(); }

        ItemStack insert(ItemStack s, boolean sim) {
            if (output || s.isEmpty() || !valid.test(s)) return s;
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, s)) return s;
            int n = Math.min(Math.min(max, s.getMaxStackSize()) - stack.getCount(), s.getCount());
            if (n <= 0) return s;
            if (!sim) { if (stack.isEmpty()) stack = s.copyWithCount(n); else stack.grow(n); sync(); }
            return n == s.getCount() ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
        }

        boolean fits(Item item, int n) { return (stack.isEmpty() || stack.is(item)) && stack.getCount() + n <= Math.min(max, item.getDefaultMaxStackSize()); }

        void add(Item item, int n) { if (stack.isEmpty()) stack = new ItemStack(item, n); else stack.grow(n); sync(); }

        void take(int n) { stack.shrink(n); if (stack.isEmpty()) stack = ItemStack.EMPTY; sync(); }
    }

    protected ItemProcessBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick) { super(type, pos, state, fePerTick); }

    protected static Item wmd(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)); }
    static net.minecraft.world.level.block.Block wmdBlock(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)); }

    protected abstract Slot[] slots();

    protected abstract String[] iconIds();

    @Override
    public List<ItemStack> guiSlotIcons() {

        String[] ids = iconIds();
        int off = tanks().size() - ids.length;
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < off; i++) l.add(ItemStack.EMPTY);
        for (String id : ids) l.add(new ItemStack(wmd(id)));
        return l;
    }

    @Override public boolean guiSlotOutput(int i) { int k = i - (tanks().size() - slots().length); return k >= 0 && k < slots().length && slots()[k].output; }

    public net.minecraft.world.Container slotContainer() {
        return new net.minecraft.world.Container() {
            @Override public int getContainerSize() { return slots().length; }
            @Override public boolean isEmpty() { for (Slot s : slots()) if (!s.stack.isEmpty()) return false; return true; }
            @Override public ItemStack getItem(int i) { return slots()[i].stack; }
            @Override public ItemStack removeItem(int i, int n) { Slot s = slots()[i]; if (s.stack.isEmpty()) return ItemStack.EMPTY; ItemStack r = s.stack.split(n); s.sync(); setChanged(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { Slot s = slots()[i]; ItemStack r = s.stack; s.stack = ItemStack.EMPTY; s.sync(); return r; }
            @Override public void setItem(int i, ItemStack st) { Slot s = slots()[i]; s.stack = st; s.sync(); setChanged(); }
            @Override public int getMaxStackSize() { return 64; }
            @Override public void setChanged() { ItemProcessBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { Slot s = slots()[i]; return !s.output && s.valid.test(st); }
            @Override public void clearContent() { for (Slot s : slots()) { s.stack = ItemStack.EMPTY; s.sync(); } }
        };
    }

    public int slotMax(int i) { return slots()[i].max; }

    @Override protected int gaugeTanks() { return slots().length; }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) {
        if (t == UpgradeItem.Type.AUTO_EJECT) { for (Slot s : slots()) if (s.output) return true; return false; }
        return ITEM_MACHINE.contains(t);
    }

    private java.util.List<BlockPos> cells;

    private java.util.List<BlockPos> cells() {
        if (cells == null) {
            cells = new java.util.ArrayList<>();
            if (getBlockState().getBlock() instanceof MachineBlock mb) for (BlockPos l : mb.footprint()) cells.add(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()));
        }
        return cells;
    }

    private void autoEject(Level level, Slot s) {
        java.util.Set<BlockPos> mine = new java.util.HashSet<>(cells());
        for (BlockPos c : mine) for (Direction d : Direction.values()) {
            if (s.stack.isEmpty()) return;
            BlockPos n = c.relative(d);
            if (mine.contains(n)) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, n, d.getOpposite());
            if (h == null) continue;
            ItemStack rest = ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, s.stack.copy(), false));
            if (rest.getCount() != s.stack.getCount()) { s.stack = rest.isEmpty() ? ItemStack.EMPTY : rest; s.sync(); setChanged(); }
        }
    }
    public int slotCount() { return slots().length; }
    public int firstSlotTank() { return tanks().size() - slots().length; }

    protected abstract boolean process();

    @Override
    protected final boolean work() { ejectTick(); noteLogistics(); reason = 0; return process(); }

    protected boolean logisticsSource() { return false; }

    private void noteLogistics() { if (logisticsSource() && level != null && !level.isClientSide) Logistics.noteSource(this); }

    protected static final int OUT_FULL = 3, NO_INPUT = 4, NO_ELECTRODE = 5;

    protected final boolean fail(int why) { reason = why; return false; }

    @Override
    protected void idle() { ejectTick(); noteLogistics(); }

    protected IItemHandler inHandler(Slot... ss) {
        return new IItemHandler() {
            @Override public int getSlots() { return ss.length; }
            @Override public ItemStack getStackInSlot(int i) { return ss[i].stack; }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { ItemStack r = ss[i].insert(s, sim); if (!sim) setChanged(); return r; }
            @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int i) { return ss[i].max; }
            @Override public boolean isItemValid(int i, ItemStack s) { return ss[i].valid.test(s); }
        };
    }

    protected IItemHandler outHandler(Slot... ss) {
        return new IItemHandler() {
            @Override public int getSlots() { return ss.length; }
            @Override public ItemStack getStackInSlot(int i) { return ss[i].stack; }
            @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
            @Override
            public ItemStack extractItem(int i, int n, boolean sim) {
                Slot s = ss[i];
                if (s.stack.isEmpty() || n <= 0) return ItemStack.EMPTY;
                int t = Math.min(n, s.stack.getCount());
                ItemStack r = s.stack.copyWithCount(t);
                if (!sim) { s.take(t); setChanged(); }
                return r;
            }
            @Override public int getSlotLimit(int i) { return ss[i].max; }
            @Override public boolean isItemValid(int i, ItemStack s) { return false; }
        };
    }

    public ItemStack takeOutput(Predicate<ItemStack> want, boolean simulate) {
        for (Slot s : slots()) {
            if (!s.output || s.stack.isEmpty() || !want.test(s.stack)) continue;
            ItemStack one = s.stack.copyWithCount(1);
            if (!simulate) { s.take(1); setChanged(); }
            return one;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack returnOutput(ItemStack s) {
        ItemStack rest = s.copy();
        for (Slot sl : slots()) {
            if (!sl.output || rest.isEmpty() || !(sl.stack.isEmpty() || ItemStack.isSameItemSameComponents(sl.stack, rest))) continue;
            int n = Math.min(Math.min(sl.max, rest.getMaxStackSize()) - sl.stack.getCount(), rest.getCount());
            if (n <= 0) continue;
            if (sl.stack.isEmpty()) sl.stack = rest.copyWithCount(n); else sl.stack.grow(n);
            sl.sync();
            rest.shrink(n);
            setChanged();
        }
        return rest.isEmpty() ? ItemStack.EMPTY : rest;
    }

    public boolean tryInsert(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        for (Slot s : slots()) {
            if (s.output) continue;
            ItemStack rest = s.insert(held, false);
            if (rest.getCount() != held.getCount()) {
                if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
                setChanged();
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        for (Slot s : slots()) { if (!s.stack.isEmpty()) l.add(s.stack); s.stack = ItemStack.EMPTY; s.sync(); }
        return l;
    }

    private void ejectTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide || level.getGameTime() % 8 != 0) return;
        for (Slot s : slots()) {
            if (!s.output || s.stack.isEmpty()) continue;
            if (s.cside == null) { if (upgradeLevel(UpgradeItem.Type.AUTO_EJECT) > 0) autoEject(level, s); continue; }
            Direction f = facing();
            Direction wd = Machines.dirToWorld(f, Machines.mount(getBlockState()), s.cside);
            BlockPos target = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), s.cx, s.cy, s.cz).relative(wd);
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, target, wd.getOpposite());
            if (h != null) {
                ItemStack rest = ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(h, s.stack.copy(), false));
                if (rest.getCount() != s.stack.getCount()) { s.stack = rest.isEmpty() ? ItemStack.EMPTY : rest; s.sync(); setChanged(); }
            }
            if (!s.stack.isEmpty() && upgradeLevel(UpgradeItem.Type.AUTO_EJECT) > 0) autoEject(level, s);
        }
    }

    protected void saveExtra(CompoundTag tag) {}
    protected void loadExtra(CompoundTag tag) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Slot[] ss = slots();
        for (int i = 0; i < ss.length; i++) tag.put("slot" + i, ss[i].stack.saveOptional(registries));
        saveExtra(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Slot[] ss = slots();
        for (int i = 0; i < ss.length; i++) { ss[i].stack = ItemStack.parseOptional(registries, tag.getCompound("slot" + i)); ss[i].sync(); }
        loadExtra(tag);
    }
}
