package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FurnaceBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private final FluidTank in = new FluidTank(Balance.FURNACE_TANK_MB, fs -> fs.getFluid() == WmdFluids.DESALTED_CRUDE.source().get());
    private final FluidTank out = new FluidTank(Balance.FURNACE_TANK_MB, fs -> fs.getFluid() == WmdFluids.HOT_CRUDE.source().get());

    private final IFluidHandler inPort = fillOnly(in), outPort = drainOnly(out);

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FURNACE_BE.get(), pos, state, Balance.FURNACE_FE_PER_TICK);
    }

    @Override
    protected boolean work() {
        int n = Math.min(Math.min(Balance.FURNACE_MB_PER_TICK, in.getFluidAmount()), out.getSpace());
        if (n <= 0) return false;
        flowAcc += n;
        in.drain(n, IFluidHandler.FluidAction.EXECUTE);
        out.fill(new FluidStack(WmdFluids.HOT_CRUDE.source().get(), n), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    @Override
    protected List<FluidTank> tanks() { return List.of(in, out); }

    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.DESALTED_CRUDE.source().get(), WmdFluids.HOT_CRUDE.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("desalted", "hot_crude"); }
    @Override protected int rateMbPerTick() { return Balance.FURNACE_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.FURNACE_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 1); }
    @Override protected float soundVolume() { return 0.9F; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 3 != 0) return;
        BlockPos c = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 0, 1);
        var r = level.random;
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                c.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.25, worldPosition.getY() + 10.1, c.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.25,
                (r.nextDouble() - 0.5) * 0.01, 0.07 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected java.util.List<double[]> vents() {
        return java.util.List.of(new double[] { -2.55, 0.5, 1.0 }, new double[] { 2.55, 0.5, 1.0 });
    }

    @Override
    protected String detail() { return "desalted " + mb(in) + ", hot crude " + mb(out); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 1, Direction.WEST)) return inPort;
        if (at(part, side, 2, 0, 1, Direction.EAST)) return outPort;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, -1, 0, 2, Direction.SOUTH) || at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fluid(-2, 0, 1, Direction.WEST, "desalted_crude", false).fluid(2, 0, 1, Direction.EAST, "hot_crude", true).power(-1, 0, 2, Direction.SOUTH, false).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
