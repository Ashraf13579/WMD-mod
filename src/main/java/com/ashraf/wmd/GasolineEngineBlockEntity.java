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

public class GasolineEngineBlockEntity extends ProcessUnitBlockEntity {

    private final FluidTank fuel = new FluidTank(Balance.GE_TANK_MB, fs -> isFuel(fs.getFluid()));
    private final IFluidHandler fuelIn = fillOnly(fuel);
    private final GenBuffer out = new GenBuffer(Balance.GE_BUFFER_FE, Balance.GE_OUT_PER_TICK, this::setChanged);

    private int credit;

    private static final double[] EXHAUST = { 1.0, 3.0, 0.19 }, TURBINE = { 1.0, 2.375, 0.19 };

    public GasolineEngineBlockEntity(BlockPos pos, BlockState state) { super(Wmd.GASOLINE_ENGINE_BE.get(), pos, state, 0); }

    static boolean isFuel(Fluid f) { return f == WmdFluids.GASOLINE.source().get() || f == WmdFluids.REFORMATE.source().get(); }

    static int fePerMb(Fluid f) { return f == WmdFluids.REFORMATE.source().get() ? Balance.GE_FE_PER_MB_REFORMATE : Balance.GE_FE_PER_MB_GASOLINE; }

    @Override
    public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (!out.hasRoom(Balance.GE_FE_PER_TICK)) { reason = 3; return false; }
        if (credit < Balance.GE_FE_PER_TICK) {
            if (fuel.getFluidAmount() < 1) { reason = 4; return false; }
            credit += fePerMb(fuel.getFluid().getFluid());
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }
        reason = 0;
        credit -= Balance.GE_FE_PER_TICK;
        out.stored += Balance.GE_FE_PER_TICK;
        flowAcc += Balance.GE_FE_PER_TICK;
        return true;
    }

    private boolean refShown;

    public boolean onReformate() {
        if (level != null && level.isClientSide) return refShown;
        return !fuel.isEmpty() && fuel.getFluid().getFluid() == WmdFluids.REFORMATE.source().get();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("ref", onReformate());
        return tag;
    }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.GASOLINE.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return Balance.GE_FE_PER_TICK; }
    @Override protected float levelFraction() { return fuel.getFluidAmount() / (float) Balance.GE_TANK_MB; }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.gen_output", String.format("%,d", Balance.GE_FE_PER_TICK * 20)),
                Component.translatable("gui.wmd.info.gasoline_engine", String.format("%,d", Balance.GE_FE_PER_MB_GASOLINE), String.format("%,d", Balance.GE_FE_PER_MB_REFORMATE)));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float soundPitch() { return 0.6F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();
        var r = level.random;
        boolean hot = onReformate();
        double[] tb = worldPoint(TURBINE[0], TURBINE[1], TURBINE[2]), w = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        VeilBridge.machineGlow(getBlockPos().asLong() ^ 0x4745_0000_0000_0000L, tb[0], tb[1], tb[2], 1.0F, hot ? 0.55F : 0.42F, 0.15F, hot ? 2.6F : 2.0F,
                0.9F + 0.15F * (float) Math.sin(t * 0.5), t);
        if (t % 3 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.15, w[1], w[2] + (r.nextDouble() - 0.5) * 0.15,
                    (r.nextDouble() - 0.5) * 0.01, 0.06 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
        if (t % 11 == 0)
            level.addParticle(ParticleTypes.LARGE_SMOKE, w[0], w[1] + 0.1, w[2], (r.nextDouble() - 0.5) * 0.01, 0.04, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected String detail() { return "fuel " + mb(fuel) + ", stored " + out.stored + " FE"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 0, Direction.EAST) ? out : null; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 0, Direction.WEST) ? fuelIn : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("credit", credit);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ref")) refShown = tag.getBoolean("ref");
        credit = tag.getInt("credit");
        if (tag.contains("stored")) out.stored = Mth.clamp(tag.getInt("stored"), 0, Balance.GE_BUFFER_FE);
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 2, 3).fromZ(-1).fluid(-1, 0, 0, Direction.WEST, "fuel", false).power(2, 0, 0, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
