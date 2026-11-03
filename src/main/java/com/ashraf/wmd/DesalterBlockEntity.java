package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DesalterBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private final FluidTank crude = new FluidTank(Balance.DESALTER_TANK_MB, fs -> fs.getFluid() == WmdFluids.CRUDE_OIL.source().get());
    private final FluidTank desalted = new FluidTank(Balance.DESALTER_TANK_MB, fs -> fs.getFluid() == WmdFluids.DESALTED_CRUDE.source().get());
    private final FluidTank brine = new FluidTank(Balance.DESALTER_BRINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.BRINE.source().get());
    private double dFrac, bFrac;

    private final IFluidHandler inPort = fillOnly(crude), outPort = drainOnly(desalted), brinePort = drainOnly(brine);

    public DesalterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DESALTER_BE.get(), pos, state, Balance.DESALTER_FE_PER_TICK);
    }

    @Override
    protected boolean work() {

        if (brine.getSpace() < 1) return false;
        int n = Math.min(Math.min(Balance.DESALTER_MB_PER_TICK, crude.getFluidAmount()), desalted.getSpace());
        if (n <= 0) return false;
        dFrac += n * Balance.DESALTER_YIELD;
        bFrac += n * (1.0 - Balance.DESALTER_YIELD);
        int d = (int) dFrac, b = (int) bFrac;
        dFrac -= d;
        bFrac -= b;
        flowAcc += n;
        crude.drain(n, IFluidHandler.FluidAction.EXECUTE);
        if (d > 0) desalted.fill(new FluidStack(WmdFluids.DESALTED_CRUDE.source().get(), d), IFluidHandler.FluidAction.EXECUTE);
        if (b > 0) brine.fill(new FluidStack(WmdFluids.BRINE.source().get(), b), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    @Override
    protected List<FluidTank> tanks() { return List.of(crude, desalted, brine); }

    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.CRUDE_OIL.source().get(), WmdFluids.DESALTED_CRUDE.source().get(), WmdFluids.BRINE.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("crude", "desalted", "brine"); }
    @Override protected int rateMbPerTick() { return Balance.DESALTER_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.DESALTER_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float levelFraction() { return (crude.getFluidAmount() + desalted.getFluidAmount()) / (float) (crude.getCapacity() + desalted.getCapacity()); }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 6 != 0) return;
        BlockPos c = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 0, 1);
        level.addParticle(ParticleTypes.CLOUD, c.getX() + 0.5, worldPosition.getY() + 4.0, c.getZ() + 0.5,
                (level.random.nextDouble() - 0.5) * 0.01, 0.04, (level.random.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected java.util.List<double[]> vents() {
        return java.util.List.of(new double[] { -2.55, 0.5, 1.0 }, new double[] { 2.55, 0.5, 1.0 }, new double[] { 0.0, 0.5, 2.55 });
    }

    @Override
    protected String detail() { return "crude " + mb(crude) + ", desalted " + mb(desalted) + ", brine " + mb(brine); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 1, Direction.WEST)) return inPort;
        if (at(part, side, 2, 0, 1, Direction.EAST)) return outPort;
        if (at(part, side, 0, 0, 2, Direction.SOUTH)) return brinePort;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, -1, 0, 2, Direction.SOUTH) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fluid(-2, 0, 1, Direction.WEST, "crude_oil", false).fluid(2, 0, 1, Direction.EAST, "desalted_crude", true).fluid(0, 0, 2, Direction.SOUTH, "brine", true).power(-1, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
