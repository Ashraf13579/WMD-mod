package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class HydrotreaterBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private final FluidTank kerosene = new FluidTank(Balance.HDT_TANK_MB, fs -> fs.getFluid() == WmdFluids.KEROSENE.source().get());
    private final FluidTank hydrogen = new FluidTank(Balance.HDT_TANK_MB, fs -> fs.getFluid() == WmdFluids.HYDROGEN.source().get());
    private final FluidTank jet = new FluidTank(Balance.HDT_TANK_MB, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get());
    private final IFluidHandler keroPort, h2Port, jetPort;
    private double jetFrac;
    private int h2Credit;

    public HydrotreaterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.HYDROTREATER_BE.get(), pos, state, Balance.HYDROTREATER_FE_PER_TICK);
        keroPort = fillOnly(kerosene);
        h2Port = fillOnly(hydrogen);
        jetPort = drainOnly(jet);
    }

    @Override
    protected boolean work() {
        int n = Math.min(Balance.HYDROTREATER_MB_PER_TICK, kerosene.getFluidAmount());
        if (n <= 0) return false;
        final int perH2 = (int) Math.round(1.0 / Balance.HDT_H2_PER_MB);
        boolean needH = h2Credit < n;
        if (needH && hydrogen.getFluidAmount() < 1) return false;
        double nj = jetFrac + n * Balance.HDT_JET_YIELD;
        int addJ = (int) nj;
        if (jet.getSpace() < Math.max(1, addJ)) return false;
        jetFrac = nj - addJ;
        if (needH) { hydrogen.drain(1, IFluidHandler.FluidAction.EXECUTE); h2Credit += perH2; }
        h2Credit -= n;
        if (addJ > 0) jet.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), addJ), IFluidHandler.FluidAction.EXECUTE);
        kerosene.drain(n, IFluidHandler.FluidAction.EXECUTE);
        flowAcc += n;
        return true;
    }

    @Override
    protected List<FluidTank> tanks() { return List.of(kerosene, hydrogen, jet); }
    @Override protected int inputTanks() { return 2; }

    @Override
    protected List<Fluid> tankFluids() {
        return List.of(WmdFluids.KEROSENE.source().get(), WmdFluids.HYDROGEN.source().get(), WmdFluids.JET_FUEL.source().get());
    }

    @Override protected List<String> labelKeys() { return List.of("kerosene", "hydrogen", "jet_fuel"); }
    @Override protected int rateMbPerTick() { return Balance.HYDROTREATER_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.HDT_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-1, 0, 2); }
    @Override protected float soundVolume() { return 0.8F; }

    @Override
    protected float levelFraction() {
        long amount = 0, cap = 0;
        for (FluidTank t : tanks()) { amount += t.getFluidAmount(); cap += t.getCapacity(); }
        return cap <= 0 ? 0.0F : amount / (float) cap;
    }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        if (level.getGameTime() % 4 == 0) {
            double[] w = worldPoint(18.0 / 16.0, 132.0 / 16.0, 31.0 / 16.0);
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.15, w[1], w[2] + (r.nextDouble() - 0.5) * 0.15,
                    (r.nextDouble() - 0.5) * 0.01, 0.05 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
        }
        if (level.getGameTime() % 10 == 0) {
            double[] w = worldPoint(-2.0 / 16.0, 112.0 / 16.0, 14.0 / 16.0);
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.04, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    protected List<double[]> vents() {
        List<double[]> l = new ArrayList<>();
        l.add(new double[] { -1.55, 1.5, 1.0 });
        l.add(new double[] { -1.55, 3.5, 1.0 });
        l.add(new double[] { 1.55, 1.5, 1.0 });
        return l;
    }

    @Override
    protected String detail() {
        return "kerosene " + mb(kerosene) + ", hydrogen " + mb(hydrogen) + ", jet fuel " + mb(jet);
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 1, 1, Direction.WEST)) return keroPort;
        if (at(part, side, -1, 3, 1, Direction.WEST)) return h2Port;
        if (at(part, side, 1, 1, 1, Direction.EAST)) return jetPort;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "kerosene", false).fluid(-1, 3, 1, Direction.WEST, "hydrogen", false).fluid(1, 1, 1, Direction.EAST, "jet_fuel", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
