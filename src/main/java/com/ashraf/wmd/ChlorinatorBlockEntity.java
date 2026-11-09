package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class ChlorinatorBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank chlorine = new FluidTank(Balance.CHLOR_TANK_MB, fs -> fs.getFluid() == WmdFluids.CHLORINE.source().get());
    private final FluidTank ticl = new FluidTank(Balance.CHLOR_TANK_MB, fs -> fs.getFluid() == WmdFluids.TITANIUM_TETRACHLORIDE.source().get());
    private final Slot conc = new Slot(64, false, s -> s.is(wmd("titanium_concentrate")));
    private final Slot coke = new Slot(64, false, s -> s.is(wmd("petroleum_coke")));
    private final Slot[] slots = { conc, coke };
    private final List<FluidTank> tanks = List.of(chlorine, ticl, conc.gauge, coke.gauge);
    private final IFluidHandler clIn = fillOnly(chlorine), ticlOut = drainOnly(ticl);
    private final IItemHandler in = inHandler(conc, coke);
    private int progress;

    public ChlorinatorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CHLORINATOR_BE.get(), pos, state, Balance.CHLOR_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (conc.count() < Balance.CHLOR_CONC || coke.count() < Balance.CHLOR_COKE || chlorine.getFluidAmount() < Balance.CHLOR_CL_MB) return fail(NO_INPUT);
            if (ticl.getCapacity() - ticl.getFluidAmount() < Balance.CHLOR_TICL_MB) return fail(OUT_FULL);
            conc.take(Balance.CHLOR_CONC);
            coke.take(Balance.CHLOR_COKE);
            chlorine.drain(Balance.CHLOR_CL_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Balance.CHLOR_CL_MB / Balance.CHLOR_CYCLE_TICKS;
        if (++progress > Balance.CHLOR_CYCLE_TICKS) {
            progress = 0;
            ticl.fill(new FluidStack(WmdFluids.TITANIUM_TETRACHLORIDE.source().get(), Balance.CHLOR_TICL_MB), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "titanium_concentrate", "petroleum_coke" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.CHLOR_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.CHLORINE.source().get(), WmdFluids.TITANIUM_TETRACHLORIDE.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("chlorine", "ticl4", "concentrate", "coke"); }
    @Override protected int rateMbPerTick() { return Balance.CHLOR_CL_MB / Balance.CHLOR_CYCLE_TICKS; }
    @Override public boolean guiTankOutput(int tank) { return tank == 1; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CHLOR_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.chlorinator", Balance.CHLOR_CONC, Balance.CHLOR_COKE, Balance.CHLOR_CL_MB, Balance.CHLOR_TICL_MB, Balance.CHLOR_CYCLE_TICKS / 20));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CHL_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 6 != 0) return;
        var r = level.random;
        double[] w = worldPoint(0.0, 3.1, 1.0);
        level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.15, w[1], w[2] + (r.nextDouble() - 0.5) * 0.15, 0, 0.03, 0);
    }

    @Override
    protected String detail() { return "chlorine " + mb(chlorine) + ", TiCl4 " + mb(ticl) + ", concentrate " + conc.count() + ", coke " + coke.count(); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 1, Direction.WEST)) return clIn;
        if (at(part, side, 1, 1, 1, Direction.EAST)) return ticlOut;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 0, Direction.NORTH) ? in : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).item(-1, 0, 0, Direction.NORTH, false).fluid(-1, 0, 1, Direction.WEST, "chlorine", false)
            .fluid(1, 1, 1, Direction.EAST, "titanium_tetrachloride", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
