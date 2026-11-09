package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class AlkylationUnitBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank lpg = new FluidTank(Balance.ALKY_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get());
    private final FluidTank alky = new FluidTank(Balance.ALKY_TANK_MB, fs -> fs.getFluid() == WmdFluids.ALKYLATE.source().get());
    private final Slot acid = new Slot(Balance.ALKY_ACID_SLOT, false, Alkylate::isAcid);
    private final Slot empty = new Slot(Balance.ALKY_CANISTER_SLOT, false, Alkylate::isEmptyCanister);
    private final Slot full = new Slot(Balance.ALKY_CANISTER_SLOT, true, s -> false);
    private final Slot[] slots = { acid, empty, full };
    private final List<FluidTank> tanks = List.of(lpg, alky, acid.gauge, empty.gauge, full.gauge);
    private final IFluidHandler lpgPort = fillOnly(lpg), alkyPort = drainOnly(alky);
    private final IItemHandler hatch = new IItemHandler() {
        @Override public int getSlots() { return 3; }
        @Override public ItemStack getStackInSlot(int i) { return slots[i].stack; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { if (i > 1) return s; ItemStack r = slots[i].insert(s, sim); if (!sim) setChanged(); return r; }
        @Override
        public ItemStack extractItem(int i, int n, boolean sim) {
            if (i != 2 || full.stack.isEmpty() || n <= 0) return ItemStack.EMPTY;
            int t = Math.min(n, full.stack.getCount());
            ItemStack r = full.stack.copyWithCount(t);
            if (!sim) { full.take(t); setChanged(); }
            return r;
        }
        @Override public int getSlotLimit(int i) { return slots[i].max; }
        @Override public boolean isItemValid(int i, ItemStack s) { return i < 2 && slots[i].valid.test(s); }
    };
    private double alkyFrac;
    private int acidCredit;
    private int fill;

    public AlkylationUnitBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ALKYLATION_UNIT_BE.get(), pos, state, Balance.ALKYLATION_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        boolean canned = fillCanisters();
        int n = Math.min(Balance.ALKYLATION_MB_PER_TICK, lpg.getFluidAmount());
        if (n <= 0) return canned;
        boolean needAcid = acidCredit < n;
        if (needAcid && acid.count() < 1) return canned || fail(NO_INPUT);
        double na = alkyFrac + n * Balance.ALKYLATE_YIELD;
        int add = (int) na;
        if (alky.getSpace() < Math.max(1, add)) return canned || fail(OUT_FULL);
        alkyFrac = na - add;
        if (needAcid) { acid.take(1); acidCredit += Balance.ALKY_ACID_LPG_MB; }
        acidCredit -= n;
        if (add > 0) alky.fill(new FluidStack(WmdFluids.ALKYLATE.source().get(), add), IFluidHandler.FluidAction.EXECUTE);
        lpg.drain(n, IFluidHandler.FluidAction.EXECUTE);
        flowAcc += n;
        setChanged();
        return true;
    }

    private boolean fillCanisters() {
        if (empty.count() < 1 || alky.getFluidAmount() < Balance.ALKYLATE_CANISTER_MB || !full.fits(Wmd.ALKYLATE_CANISTER.get(), 1)) { fill = 0; return false; }
        if (++fill >= Balance.ALKY_FILL_TICKS) {
            fill = 0;
            alky.drain(Balance.ALKYLATE_CANISTER_MB, IFluidHandler.FluidAction.EXECUTE);
            empty.take(1);
            full.add(Wmd.ALKYLATE_CANISTER.get(), 1);
            setChanged();
        }
        return true;
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected String[] iconIds() { return new String[] { "acid_catalyst", "fuel_canister", "alkylate_canister" }; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get(), WmdFluids.ALKYLATE.source().get(), Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("lpg", "alkylate", "acid", "canister_empty", "canister_full"); }
    @Override protected int rateMbPerTick() { return Balance.ALKYLATION_MB_PER_TICK; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }
    @Override protected float progress01() { return fill > 0 ? fill / (float) Balance.ALKY_FILL_TICKS : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ALKYLATION_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.alkylation", Balance.ALKYLATION_MB_PER_TICK * 20, String.format("%.0f", Balance.ALKYLATE_YIELD * 100.0), String.format("%,d", Balance.ALKY_ACID_LPG_MB)),
                Component.translatable("gui.wmd.info.alky_canister", String.format("%,d", Balance.ALKYLATE_CANISTER_MB)));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.HDT_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-1, 0, 2); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override
    protected float levelFraction() {
        return (lpg.getFluidAmount() + alky.getFluidAmount()) / (float) (lpg.getCapacity() + alky.getCapacity());
    }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        if (level.getGameTime() % 7 == 0) {
            double[] w = worldPoint(-26.0 / 16.0, 121.0 / 16.0, 14.0 / 16.0);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, w[0] + (r.nextDouble() - 0.5) * 0.15, w[1], w[2] + (r.nextDouble() - 0.5) * 0.15,
                    (r.nextDouble() - 0.5) * 0.01, 0.04 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
        }
        if (level.getGameTime() % 11 == 0) {
            double[] w = worldPoint(-22.0 / 16.0, 154.0 / 16.0, 46.0 / 16.0);
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.035, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    protected List<double[]> vents() {
        List<double[]> l = new ArrayList<>();
        l.add(new double[] { -2.55, 0.5, 2.0 });
        l.add(new double[] { 2.55, 0.5, 2.0 });
        return l;
    }

    @Override
    protected String detail() {
        return "LPG " + mb(lpg) + ", alkylate " + mb(alky) + ", acid " + acid.count() + " (" + Math.max(0, acidCredit) + " mB paid), canisters " + empty.count() + " empty / " + full.count() + " full";
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 2, Direction.WEST)) return lpgPort;
        if (at(part, side, 2, 0, 2, Direction.EAST)) return alkyPort;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.NORTH) ? hatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("acidCredit", acidCredit);
        tag.putDouble("alkyFrac", alkyFrac);
        tag.putInt("fill", fill);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        acidCredit = Math.max(0, tag.getInt("acidCredit"));
        alkyFrac = tag.getDouble("alkyFrac");
        fill = Math.max(0, tag.getInt("fill"));
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 5).fluid(-2, 0, 2, Direction.WEST, "lpg", false).fluid(2, 0, 2, Direction.EAST, "alkylate", true)
            .item(0, 0, 0, Direction.NORTH, false).power(0, 0, 4, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
