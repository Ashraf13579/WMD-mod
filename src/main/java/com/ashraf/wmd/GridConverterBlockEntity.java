package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class GridConverterBlockEntity extends ProcessUnitBlockEntity {

    private int inStored;
    private final GenBuffer out = new GenBuffer(Balance.CONVERTER_OUT_BUFFER_FE, Balance.CONVERTER_OUT_PER_TICK, this::setChanged);

    private final FluidTank inGauge = new FluidTank(Balance.CONVERTER_IN_BUFFER_FE), outGauge = new FluidTank(Balance.CONVERTER_OUT_BUFFER_FE);
    private final List<FluidTank> tanks = List.of(inGauge, outGauge);

    private final IEnergyStorage foreignIn = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean simulate) {
            if (Grid.pushing) return 0;
            int got = Mth.clamp(Math.min(n, Balance.CONVERTER_IN_BUFFER_FE - inStored), 0, Integer.MAX_VALUE);
            if (!simulate && got > 0) { inStored += got; setChanged(); }
            return got;
        }
        @Override public int extractEnergy(int n, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return inStored; }
        @Override public int getMaxEnergyStored() { return Balance.CONVERTER_IN_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return !Grid.pushing; }
    };

    public GridConverterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.GRID_CONVERTER_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean work() {
        reason = 0;
        int eff = Config.GRID_CONVERTER_EFFICIENCY.get(), max = Config.GRID_CONVERTER_MAX.get();
        int give = Math.min(max, Math.min((int) ((long) inStored * eff / 100), Balance.CONVERTER_OUT_BUFFER_FE - out.stored));
        boolean did = false;
        if (give > 0) {
            int cost = Math.min(inStored, (int) (((long) give * 100 + eff - 1) / eff));
            inStored -= cost;
            out.stored += give;
            flowAcc += give;
            did = true;
        } else {
            reason = inStored <= 0 ? 4 : 3;
        }
        syncGauges();
        return did;
    }

    private void syncGauges() {
        inGauge.setFluid(inStored <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, inStored));
        outGauge.setFluid(out.stored <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, out.stored));
    }

    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("fe_in", "fe_out"); }
    @Override protected int rateMbPerTick() { return Math.max(1, Config.GRID_CONVERTER_MAX.get()); }
    @Override public String guiTankUnit(int t) { return "FE"; }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.converter", Config.GRID_CONVERTER_MAX.get() * 20, Config.GRID_CONVERTER_EFFICIENCY.get()),
                Component.translatable("gui.wmd.info.converter_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GC_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return 0.4F; }

    @Override protected String detail() { return "in " + inStored + " FE, out " + out.stored + " FE"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.WEST)) return foreignIn;
        return at(part, side, 2, 0, 0, Direction.EAST) ? out : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("in", inStored);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inStored = Mth.clamp(tag.getInt("in"), 0, Balance.CONVERTER_IN_BUFFER_FE);
        out.stored = Mth.clamp(tag.getInt("stored"), 0, Balance.CONVERTER_OUT_BUFFER_FE);
        syncGauges();
    }

    private static final PortMap PORT_MAP = new PortMap(0, 2, 2).power(0, 0, 0, Direction.WEST, false).power(2, 0, 0, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
