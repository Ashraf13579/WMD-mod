package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FeedPumpBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private final FluidTank tank = new FluidTank(Balance.FEED_PUMP_TANK_MB, fs -> fs.getFluid() == WmdFluids.CRUDE_OIL.source().get());
    private int budget, moved;

    private final IFluidHandler suction = fillOnly(tank);

    private final IFluidHandler discharge = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
        @Override public int fill(FluidStack s, FluidAction a) { return 0; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) {
            if (s.isEmpty() || !tank.getFluid().is(s.getFluid())) return FluidStack.EMPTY;
            return drain(s.getAmount(), a);
        }
        @Override public FluidStack drain(int max, FluidAction a) {
            int n = Math.min(max, budget);
            if (n <= 0) return FluidStack.EMPTY;
            FluidStack out = tank.drain(n, a);
            if (a.execute()) { budget -= out.getAmount(); moved += out.getAmount(); }
            return out;
        }
    };

    public FeedPumpBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FEED_PUMP_BE.get(), pos, state, Balance.FEED_PUMP_FE_PER_TICK);
    }

    @Override
    protected boolean work() {
        boolean did = moved > 0;
        flowAcc += moved;
        moved = 0;
        budget = Balance.FEED_PUMP_MB_PER_TICK;
        return did;
    }

    @Override
    protected void idle() { budget = 0; moved = 0; }

    @Override
    protected List<FluidTank> tanks() { return List.of(tank); }

    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.CRUDE_OIL.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("crude"); }
    @Override protected int rateMbPerTick() { return Balance.FEED_PUMP_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.PUMP_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected List<double[]> vents() { return List.of(new double[] { -1.55, 1.5, 0.0 }, new double[] { -1.0, 1.5, 0.55 }); }

    @Override
    protected String detail() { return "crude " + mb(tank); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 1, 0, Direction.WEST)) return suction;
        if (at(part, side, -1, 1, 0, Direction.SOUTH)) return discharge;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 1, 0, 0, Direction.EAST) || at(part, side, 1, 1, 0, Direction.EAST) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 1).fluid(-1, 1, 0, Direction.WEST, "crude_oil", false).fluid(-1, 1, 0, Direction.SOUTH, "crude_oil", true).power(1, 0, 0, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
