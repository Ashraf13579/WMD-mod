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

public class VacuumColumnBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private static final List<WmdFluids.Entry> PRODUCTS = List.of(WmdFluids.LVGO, WmdFluids.HVGO, WmdFluids.BITUMEN);
    private static final int[] OUT_CELL_Y = { 7, 4, 0 };
    private static final int N = 3;

    private final FluidTank feed = new FluidTank(Balance.VDU_FEED_TANK_MB, fs -> fs.getFluid() == WmdFluids.FUEL_OIL.source().get());
    private final FluidTank[] out = new FluidTank[N];
    private final IFluidHandler[] outPorts = new IFluidHandler[N];
    private final IFluidHandler inPort;
    private final double[] frac = new double[N];

    public VacuumColumnBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.VACUUM_COLUMN_BE.get(), pos, state, Balance.VDU_FE_PER_TICK);
        for (int i = 0; i < N; i++) {
            final int idx = i;
            out[i] = new FluidTank(Balance.VDU_PRODUCT_TANK_MB, fs -> fs.getFluid() == PRODUCTS.get(idx).source().get());
            outPorts[i] = drainOnly(out[i]);
        }
        inPort = fillOnly(feed);
    }

    @Override
    protected boolean work() {
        int n = Math.min(Balance.VDU_MB_PER_TICK, feed.getFluidAmount());
        if (n <= 0) return false;
        int[] add = new int[N];
        double[] nf = new double[N];
        for (int i = 0; i < N; i++) {
            nf[i] = frac[i] + n * Balance.VDU_YIELD[i];
            add[i] = (int) nf[i];
            if (out[i].getSpace() < Math.max(1, add[i])) return false;
        }
        for (int i = 0; i < N; i++) {
            frac[i] = nf[i] - add[i];
            if (add[i] > 0) out[i].fill(new FluidStack(PRODUCTS.get(i).source().get(), add[i]), IFluidHandler.FluidAction.EXECUTE);
        }
        feed.drain(n, IFluidHandler.FluidAction.EXECUTE);
        flowAcc += n;
        return true;
    }

    @Override
    protected List<FluidTank> tanks() {
        List<FluidTank> l = new ArrayList<>();
        l.add(feed);
        for (FluidTank t : out) l.add(t);
        return l;
    }

    @Override
    protected List<Fluid> tankFluids() {
        List<Fluid> l = new ArrayList<>();
        l.add(WmdFluids.FUEL_OIL.source().get());
        for (WmdFluids.Entry e : PRODUCTS) l.add(e.source().get());
        return l;
    }

    @Override protected List<String> labelKeys() { return List.of("fuel_oil", "lvgo", "hvgo", "bitumen"); }
    @Override protected int rateMbPerTick() { return Balance.VDU_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.VDU_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override
    protected float levelFraction() {
        long amount = feed.getFluidAmount(), cap = feed.getCapacity();
        for (FluidTank t : out) { amount += t.getFluidAmount(); cap += t.getCapacity(); }
        return cap <= 0 ? 0.0F : amount / (float) cap;
    }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 6 != 0) return;
        double[] w = worldPoint(0.0, 10.55, 1.0);
        level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], (level.random.nextDouble() - 0.5) * 0.01, 0.045, (level.random.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected List<double[]> vents() {
        List<double[]> l = new ArrayList<>();
        l.add(new double[] { -1.55, 1.5, 1.0 });
        for (int y : OUT_CELL_Y) l.add(new double[] { 1.55, y + 0.5, 1.0 });
        l.add(new double[] { -1.5, 8.0, 1.56 });
        return l;
    }

    @Override
    protected String detail() {
        StringBuilder sb = new StringBuilder("fuel oil ").append(mb(feed));
        String[] names = { "LVGO", "HVGO", "bitumen" };
        for (int i = 0; i < N; i++) sb.append(", ").append(names[i]).append(' ').append(mb(out[i]));
        return sb.toString();
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 1, 1, Direction.WEST)) return inPort;
        for (int i = 0; i < N; i++) if (at(part, side, 1, OUT_CELL_Y[i], 1, Direction.EAST)) return outPorts[i];
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "fuel_oil", false).fluid(1, 7, 1, Direction.EAST, "lvgo", true).fluid(1, 4, 1, Direction.EAST, "hvgo", true).fluid(1, 0, 1, Direction.EAST, "bitumen", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
