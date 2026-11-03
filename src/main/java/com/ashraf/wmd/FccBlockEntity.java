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

public class FccBlockEntity extends ProcessUnitBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return FLUID_UNIT.contains(t); }

    private static final List<WmdFluids.Entry> PRODUCTS = List.of(WmdFluids.GASOLINE, WmdFluids.LPG);
    private static final int N = 2;

    private final FluidTank lvgo = new FluidTank(Balance.FCC_FEED_TANK_MB, fs -> fs.getFluid() == WmdFluids.LVGO.source().get());
    private final FluidTank hvgo = new FluidTank(Balance.FCC_FEED_TANK_MB, fs -> fs.getFluid() == WmdFluids.HVGO.source().get());
    private final FluidTank[] out = new FluidTank[N];
    private final IFluidHandler[] outPorts = new IFluidHandler[N];
    private final IFluidHandler lvgoPort, hvgoPort;
    private final double[] frac = new double[N];

    public FccBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FCC_BE.get(), pos, state, Balance.FCC_FE_PER_TICK);
        for (int i = 0; i < N; i++) {
            final int idx = i;
            out[i] = new FluidTank(Balance.FCC_PRODUCT_TANK_MB, fs -> fs.getFluid() == PRODUCTS.get(idx).source().get());
            outPorts[i] = drainOnly(out[i]);
        }
        lvgoPort = fillOnly(lvgo);
        hvgoPort = fillOnly(hvgo);
    }

    @Override
    protected boolean work() {
        int n = Math.min(Balance.FCC_MB_PER_TICK, lvgo.getFluidAmount() + hvgo.getFluidAmount());
        if (n <= 0) return false;
        int fromL = Math.min(lvgo.getFluidAmount(), (n + 1) / 2);
        int fromH = Math.min(hvgo.getFluidAmount(), n - fromL);
        fromL = Math.min(lvgo.getFluidAmount(), n - fromH);
        int t = fromL + fromH;
        if (t <= 0) return false;
        int[] add = new int[N];
        double[] nf = new double[N];
        for (int i = 0; i < N; i++) {
            nf[i] = frac[i] + t * Balance.FCC_YIELD[i];
            add[i] = (int) nf[i];
            if (out[i].getSpace() < Math.max(1, add[i])) return false;
        }
        for (int i = 0; i < N; i++) {
            frac[i] = nf[i] - add[i];
            if (add[i] > 0) out[i].fill(new FluidStack(PRODUCTS.get(i).source().get(), add[i]), IFluidHandler.FluidAction.EXECUTE);
        }
        if (fromL > 0) lvgo.drain(fromL, IFluidHandler.FluidAction.EXECUTE);
        if (fromH > 0) hvgo.drain(fromH, IFluidHandler.FluidAction.EXECUTE);
        flowAcc += t;
        return true;
    }

    @Override protected int inputTanks() { return 2; }

    @Override
    protected List<FluidTank> tanks() {
        List<FluidTank> l = new ArrayList<>();
        l.add(lvgo);
        l.add(hvgo);
        for (FluidTank t : out) l.add(t);
        return l;
    }

    @Override
    protected List<Fluid> tankFluids() {
        List<Fluid> l = new ArrayList<>();
        l.add(WmdFluids.LVGO.source().get());
        l.add(WmdFluids.HVGO.source().get());
        for (WmdFluids.Entry e : PRODUCTS) l.add(e.source().get());
        return l;
    }

    @Override protected List<String> labelKeys() { return List.of("lvgo", "hvgo", "gasoline", "lpg"); }
    @Override protected int rateMbPerTick() { return Balance.FCC_MB_PER_TICK; }

    @Override protected SoundEvent runSound() { return WmdSounds.FCC_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-2, 0, 0); }
    @Override protected float soundVolume() { return 0.9F; }

    @Override
    protected float levelFraction() {
        long amount = lvgo.getFluidAmount() + hvgo.getFluidAmount(), cap = lvgo.getCapacity() + hvgo.getCapacity();
        for (FluidTank t : out) { amount += t.getFluidAmount(); cap += t.getCapacity(); }
        return cap <= 0 ? 0.0F : amount / (float) cap;
    }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        if (level.getGameTime() % 3 == 0) {
            double[] w = worldPoint(14.0 / 16.0, 158.0 / 16.0, 18.0 / 16.0);
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.2, w[1], w[2] + (r.nextDouble() - 0.5) * 0.2,
                    (r.nextDouble() - 0.5) * 0.01, 0.07 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
        }
        if (level.getGameTime() % 8 == 0) {
            double[] w = worldPoint(32.0 / 16.0, 118.0 / 16.0, 28.0 / 16.0);
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.045, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    protected List<double[]> vents() {
        List<double[]> l = new ArrayList<>();
        for (int y : new int[] { 1, 3 }) { l.add(new double[] { -2.55, y + 0.5, 1.0 }); l.add(new double[] { 2.55, y + 0.5, 1.0 }); }
        return l;
    }

    @Override
    protected String detail() {
        StringBuilder sb = new StringBuilder("LVGO ").append(mb(lvgo)).append(", HVGO ").append(mb(hvgo));
        String[] names = { "gasoline", "LPG" };
        for (int i = 0; i < N; i++) sb.append(", ").append(names[i]).append(' ').append(mb(out[i]));
        return sb.toString();
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 1, 1, Direction.WEST)) return lvgoPort;
        if (at(part, side, -2, 3, 1, Direction.WEST)) return hvgoPort;
        if (at(part, side, 2, 1, 1, Direction.EAST)) return outPorts[0];
        if (at(part, side, 2, 3, 1, Direction.EAST)) return outPorts[1];
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fluid(-2, 1, 1, Direction.WEST, "lvgo", false).fluid(-2, 3, 1, Direction.WEST, "hvgo", false).fluid(2, 1, 1, Direction.EAST, "gasoline", true).fluid(2, 3, 1, Direction.EAST, "lpg", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
