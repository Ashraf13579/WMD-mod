package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FlareStackBlockEntity extends ProcessUnitBlockEntity {

    private final FluidTank gas = new FluidTank(Balance.FLARE_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get());
    private final IFluidHandler gasPort = fillOnly(gas);
    private final GenBuffer out = new GenBuffer(Balance.FLARE_BUFFER_FE, Balance.FLARE_OUT_PER_TICK, this::setChanged);
    private int phase;

    public FlareStackBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FLARE_STACK_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean work() {
        if (gas.getFluidAmount() < 1) return false;
        int ticksPerMb = Math.max(1, 20 / Balance.FLARE_MB_PER_SEC);
        if (!out.hasRoom(Balance.FLARE_FE_PER_MB)) return false;
        if (++phase >= ticksPerMb) {
            phase = 0;
            gas.drain(1, IFluidHandler.FluidAction.EXECUTE);
            out.stored += Balance.FLARE_FE_PER_MB;
        }
        flowAcc += 1;
        return true;
    }

    @Override protected List<FluidTank> tanks() { return List.of(gas); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("lpg"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return out; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.flare_output", Balance.FLARE_FE_PER_MB * Balance.FLARE_MB_PER_SEC, Balance.FLARE_MB_PER_SEC),
                Component.translatable("gui.wmd.info.flare_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.FS_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return 0.6F; }
    @Override protected float levelFraction() { return gas.getFluidAmount() / (float) Balance.FLARE_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        double[] w = worldPoint(0.0, 12.9, 1.0);
        if (level.getGameTime() % 2 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, w[0] + (r.nextDouble() - 0.5) * 0.2, w[1], w[2] + (r.nextDouble() - 0.5) * 0.2,
                    (r.nextDouble() - 0.5) * 0.01, 0.03 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
        if (level.getGameTime() % 3 == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w[0], w[1] + 0.5, w[2], (r.nextDouble() - 0.5) * 0.02, 0.06, (r.nextDouble() - 0.5) * 0.02);
    }

    @Override protected String detail() { return "gas " + mb(gas) + ", stored " + out.stored + " FE"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 1, Direction.WEST) ? gasPort : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.EAST) ? out : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        out.stored = tag.getInt("stored");
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 0, 1, Direction.WEST, "lpg", false).power(1, 0, 1, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
