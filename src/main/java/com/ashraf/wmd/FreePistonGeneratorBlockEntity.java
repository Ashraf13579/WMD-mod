package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FreePistonGeneratorBlockEntity extends ProcessUnitBlockEntity {

    static final float CYCLE = 0.5F;

    static final float STROKE = 5.0F;

    private final FluidTank fuel = new FluidTank(Balance.FP_TANK_MB, fs -> fs.getFluid() == WmdFluids.REFORMATE.source().get());
    private final IFluidHandler fuelIn = fillOnly(fuel);
    private final GenBuffer out = new GenBuffer(Balance.FP_BUFFER_FE, Balance.FP_OUT_PER_TICK, this::setChanged);

    private int credit;
    private int lastStroke = Integer.MIN_VALUE;

    private static final double[] EXHAUST = { 0.22, 3.0, 0.0 }, WIN_A = { 0.0, 0.94, -1.25 }, WIN_B = { 0.0, 0.94, 1.25 };

    public FreePistonGeneratorBlockEntity(BlockPos pos, BlockState state) { super(Wmd.FREE_PISTON_GENERATOR_BE.get(), pos, state, 0); }

    @Override
    public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (!out.hasRoom(Balance.FP_FE_PER_TICK)) { reason = 3; return false; }
        if (credit < Balance.FP_FE_PER_TICK) {
            if (fuel.getFluidAmount() < 1) { reason = 4; return false; }
            credit += Balance.FP_FE_PER_MB;
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }
        reason = 0;
        credit -= Balance.FP_FE_PER_TICK;
        out.stored += Balance.FP_FE_PER_TICK;
        flowAcc += Balance.FP_FE_PER_TICK;
        return true;
    }

    public float cycle(float pt) { return spinAngle(pt) * CYCLE; }

    static float offset(float t) { return STROKE * 0.5F * (1.0F - Mth.cos(t)); }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.REFORMATE.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return Balance.FP_FE_PER_TICK; }
    @Override protected float levelFraction() { return fuel.getFluidAmount() / (float) Balance.FP_TANK_MB; }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gen_output", String.format("%,d", Balance.FP_FE_PER_TICK * 20)),
                Component.translatable("gui.wmd.info.free_piston_generator", String.format("%,d", Balance.FP_FE_PER_MB)));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float soundPitch() { return 0.85F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();
        var r = level.random;
        double[] w = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        if (t % 4 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.05, (r.nextDouble() - 0.5) * 0.01);
        int stroke = Mth.floor(cycle(0.0F) / Mth.PI);
        if (stroke == lastStroke) return;
        boolean first = lastStroke == Integer.MIN_VALUE;
        lastStroke = stroke;
        if (first) return;
        for (int k = 0; k < 2; k++)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, w[0], w[1] + 0.1, w[2],
                    (r.nextDouble() - 0.5) * 0.02, 0.06 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.02);
        double[] win = (stroke & 1) == 0 ? worldPoint(WIN_A[0], WIN_A[1], WIN_A[2]) : worldPoint(WIN_B[0], WIN_B[1], WIN_B[2]);
        VeilBridge.machineGlow(getBlockPos().asLong() ^ ((stroke & 1) == 0 ? 0x4650_0000_0000_0001L : 0x4650_0000_0000_0002L), win[0], win[1], win[2],
                1.0F, 0.6F, 0.2F, 2.2F, 1.0F, t);
    }

    @Override
    protected String detail() { return "reformate " + mb(fuel) + ", stored " + out.stored + " FE"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 0, Direction.EAST) ? out : null; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -2, 0, 0, Direction.WEST) ? fuelIn : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("credit", credit);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        credit = tag.getInt("credit");
        if (tag.contains("stored")) out.stored = Mth.clamp(tag.getInt("stored"), 0, Balance.FP_BUFFER_FE);
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fromZ(-1).fluid(-2, 0, 0, Direction.WEST, "fuel", false).power(2, 0, 0, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
