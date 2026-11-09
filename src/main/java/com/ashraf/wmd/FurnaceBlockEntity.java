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

public class FurnaceBlockEntity extends ProcessUnitBlockEntity implements CcAnimated {

    static final float STACK_TOP = 168.0F, DMAX = 66.0F, DMIN = 8.0F, SILL = 30.8F;

    static final float[] FLAME_STEP = { 0.06F, 0.4F, 0.72F };
    static final float HOT_COIL = 0.35F, HOT_EMBER = 0.03F;

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
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    private double lastTt = Double.NaN;
    private float heat;

    float heatTarget() { return isRunning() ? 0.4F + 0.6F * flowFraction() : 0.0F; }

    float heatShown() { return heat; }

    static float ease(float v, float target, float upK, float downK, float dt) {
        float k = target > v ? upK : downK;
        return v + (target - v) * (1.0F - (float) Math.pow(1.0F - k, dt));
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean run = isRunning();
        if (Double.isNaN(lastTt)) heat = heatTarget();
        float dt = Double.isNaN(lastTt) ? 0.0F : (float) Math.max(0.0, Math.min(5.0, tt - lastTt));
        lastTt = tt;
        heat = ease(heat, heatTarget(), 0.012F, 0.004F, dt);
        final float h = heat;
        int top = -1;
        for (int k = 0; k < 3; k++) if (run && h > FLAME_STEP[k]) top = k;
        boolean gutter = top > 0 && h < 0.97F && Math.floorMod((long) (tt / 3.0) * 2654435761L, 23L) == 0;
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            final boolean shown = k <= top && !(gutter && k == top);
            final float flick = 0.86F + 0.08F * (float) Math.sin(tt * 1.1 + kk * 2.3) + 0.06F * (float) Math.sin(tt * 2.9 + kk * 3.9);
            model.getBone("flame" + k).ifPresent(b -> { b.setHidden(!shown); b.setScaleY(flick); });
        }
        model.getBone("glow").ifPresent(b -> b.setHidden(h <= HOT_EMBER));
        model.getBone("glow1").ifPresent(b -> b.setHidden(h <= HOT_COIL));
        final float ang = DMIN + (DMAX - DMIN) * h + (run ? 2.0F * (float) Math.sin(tt * 0.05) : 0.0F);
        model.getBone("damper").ifPresent(b -> b.setRotX((float) Math.toRadians(ang)));
        boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);
        model.getBone("lamp").ifPresent(b -> b.setHidden(dark || !run));
        model.getBone("lampidle").ifPresent(b -> b.setHidden(dark || run || h <= 0.05F));
    }

    private double[] stackTop(double above) { return worldPoint(0.0, (STACK_TOP + above) / 16.0, 1.0); }

    @Override
    protected void runStarted(Level level) {
        var r = level.random;
        double[] w = stackTop(2.0);
        for (int i = 0; i < 6; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.4, w[1] + r.nextDouble() * 0.4, w[2] + (r.nextDouble() - 0.5) * 0.4,
                    (r.nextDouble() - 0.5) * 0.02, 0.05 + r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.02);
    }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        float h = Math.max(heat, 0.2F);
        double[] w = stackTop(0.5);
        if (level.getGameTime() % 3 == 0 && r.nextFloat() < 0.25F + 0.5F * h)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.25, w[1], w[2] + (r.nextDouble() - 0.5) * 0.25,
                    (r.nextDouble() - 0.5) * 0.01, 0.07 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
        if (r.nextFloat() < 0.35F * h)
            level.addParticle(ParticleTypes.SMOKE, w[0] + (r.nextDouble() - 0.5) * 0.5, w[1] + 0.2, w[2] + (r.nextDouble() - 0.5) * 0.5, 0.0, 0.04, 0.0);
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
