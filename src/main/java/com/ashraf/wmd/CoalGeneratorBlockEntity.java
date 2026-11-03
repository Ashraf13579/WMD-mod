package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CoalGeneratorBlockEntity extends ProcessUnitBlockEntity implements GuiItemSlots {

    private ItemStack fuel = ItemStack.EMPTY;
    private int burnLeft;
    private int stored;
    private final FluidTank gauge = new FluidTank(Balance.COAL_GEN_FUEL_MAX);

    private final IEnergyStorage out = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, stored), Balance.COAL_GEN_OUT_PER_TICK);
            if (t > 0 && !sim) { stored -= t; setChanged(); }
            return t;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.COAL_GEN_BUFFER_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    private final IItemHandler hopper = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int i) { return fuel; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return insertFuel(s, sim); }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int i) { return Balance.COAL_GEN_FUEL_MAX; }
        @Override public boolean isItemValid(int i, ItemStack s) { return isFuel(s); }
    };

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.COAL_GENERATOR_BE.get(), pos, state, 0);
    }

    private static boolean isFuel(ItemStack s) {
        return !s.isEmpty() && !s.hasCraftingRemainingItem() && s.getBurnTime(RecipeType.SMELTING) > 0;
    }

    private ItemStack insertFuel(ItemStack s, boolean simulate) {
        if (!isFuel(s)) return s;
        if (!fuel.isEmpty() && !ItemStack.isSameItemSameComponents(fuel, s)) return s;
        int room = Math.min(Balance.COAL_GEN_FUEL_MAX, s.getMaxStackSize()) - fuel.getCount();
        int n = Math.min(room, s.getCount());
        if (n <= 0) return s;
        if (!simulate) {
            if (fuel.isEmpty()) fuel = s.copyWithCount(n); else fuel.grow(n);
            syncGauge();
            setChanged();
        }
        return s.getCount() == n ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
    }

    public boolean tryInsertFuel(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        ItemStack rest = insertFuel(held, false);
        if (rest.getCount() == held.getCount()) return false;
        if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
        return true;
    }

    public ItemStack takeFuel() {
        ItemStack f = fuel;
        fuel = ItemStack.EMPTY;
        syncGauge();
        return f;
    }

    @Override public int slotCount() { return 1; }
    @Override public int firstSlotTank() { return 0; }
    @Override public int slotMax(int i) { return Balance.COAL_GEN_FUEL_MAX; }

    @Override
    public net.minecraft.world.Container slotContainer() {
        return new net.minecraft.world.Container() {
            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return fuel.isEmpty(); }
            @Override public ItemStack getItem(int i) { return fuel; }
            @Override public ItemStack removeItem(int i, int n) { if (fuel.isEmpty()) return ItemStack.EMPTY; ItemStack r = fuel.split(n); syncGauge(); setChanged(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = fuel; fuel = ItemStack.EMPTY; syncGauge(); return r; }
            @Override public void setItem(int i, ItemStack st) { fuel = st; syncGauge(); setChanged(); }
            @Override public int getMaxStackSize() { return 64; }
            @Override public void setChanged() { CoalGeneratorBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return isFuel(st); }
            @Override public void clearContent() { fuel = ItemStack.EMPTY; syncGauge(); }
        };
    }

    private void syncGauge() { gauge.setFluid(fuel.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, fuel.getCount())); }

    @Override
    public void empDrain() { stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (stored + Balance.COAL_GEN_FE_PER_TICK > Balance.COAL_GEN_BUFFER_FE) return false;
        if (burnLeft <= 0) {
            if (!isFuel(fuel)) return false;
            burnLeft = fuel.getBurnTime(RecipeType.SMELTING);
            fuel.shrink(1);
            if (fuel.isEmpty()) fuel = ItemStack.EMPTY;
            syncGauge();
        }
        burnLeft--;
        stored += Balance.COAL_GEN_FE_PER_TICK;
        flowAcc += Balance.COAL_GEN_FE_PER_TICK;
        return true;
    }

    @Override protected List<FluidTank> tanks() { return List.of(gauge); }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return Balance.COAL_GEN_FE_PER_TICK * 5 / 4; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(net.minecraft.world.item.Items.COAL)); }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gen_output", Balance.COAL_GEN_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.gen_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CG_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-2, 1, 0); }
    @Override protected float soundVolume() { return 0.5F; }
    @Override protected float levelFraction() { return fuel.getCount() / (float) Balance.COAL_GEN_FUEL_MAX; }

    @Override
    protected void clientEffects(Level level) {

        double[] fire = worldPoint(-1.44, 1.05, -0.4);
        VeilBridge.machineGlow(getBlockPos().asLong(), fire[0], fire[1], fire[2], 1.0F, 0.55F, 0.2F, 7.0F, 1.6F, level.getGameTime());
        if (level.getGameTime() % 3 != 0) return;
        var r = level.random;
        double[] w = worldPoint(-1.44, 7.95, 2.06);
        level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.3, w[1], w[2] + (r.nextDouble() - 0.5) * 0.3,
                (r.nextDouble() - 0.5) * 0.01, 0.05 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected List<double[]> vents() { return List.of(new double[] { 1.5, 3.55, 1.0 }); }

    @Override
    protected String detail() { return "fuel " + fuel.getCount() + " / " + Balance.COAL_GEN_FUEL_MAX + ", stored " + stored + " FE"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 2, Direction.SOUTH) ? out : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, -2, 3, 0, Direction.UP) ? hopper : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fuel", fuel.saveOptional(registries));
        tag.putInt("burnLeft", burnLeft);
        tag.putInt("stored", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = ItemStack.parseOptional(registries, tag.getCompound("fuel"));
        burnLeft = tag.getInt("burnLeft");
        stored = tag.getInt("stored");
        syncGauge();
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).item(-2, 3, 0, Direction.UP, false).power(2, 0, 2, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
