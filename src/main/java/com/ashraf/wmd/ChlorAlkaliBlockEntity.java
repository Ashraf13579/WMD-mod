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

public class ChlorAlkaliBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank brine = new FluidTank(Balance.CELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.BRINE.source().get() || fs.getFluid() == WmdFluids.HEAVY_WATER.source().get());
    private final FluidTank chlorine = new FluidTank(Balance.CELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.CHLORINE.source().get());
    private final FluidTank hydrogen = new FluidTank(Balance.CELL_TANK_MB, fs -> fs.getFluid() == WmdFluids.HYDROGEN.source().get() || fs.getFluid() == WmdFluids.DEUTERIUM.source().get());
    private final Slot caustic = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.NORTH);
    private final Slot[] slots = { caustic };
    private final List<FluidTank> tanks = List.of(brine, chlorine, hydrogen, caustic.gauge);
    private final IFluidHandler brineIn = fillOnly(brine), clOut = drainOnly(chlorine), h2Out = drainOnly(hydrogen);
    private final IItemHandler causticOut = outHandler(caustic);
    private int progress;
    private boolean heavy;

    public ChlorAlkaliBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CHLOR_ALKALI_BE.get(), pos, state, Balance.CELL_FE_PER_TICK);
    }

    private static boolean room(FluidTank t, int mb) { return t.getCapacity() - t.getFluidAmount() >= mb; }

    @Override
    protected boolean process() {
        if (progress == 0 && isHeavy(brine)) {
            if (brine.getFluidAmount() < Balance.CELL_HW_MB) return fail(NO_INPUT);
            boolean gasFree = hydrogen.isEmpty() || hydrogen.getFluid().getFluid() == WmdFluids.DEUTERIUM.source().get();
            if (!gasFree || !room(hydrogen, Balance.CELL_D2_FROM_HW)) return fail(OUT_FULL);
            brine.drain(Balance.CELL_HW_MB, IFluidHandler.FluidAction.EXECUTE);
            heavy = true;
            progress = 1;
            setChanged();
            return true;
        }
        if (progress == 0) {
            if (brine.getFluidAmount() < Balance.CELL_BRINE_MB) return fail(NO_INPUT);
            if (!hydrogen.isEmpty() && hydrogen.getFluid().getFluid() != WmdFluids.HYDROGEN.source().get()) return fail(OUT_FULL);
            heavy = false;
            if (!room(chlorine, Balance.CELL_CL_FROM_BRINE) || !room(hydrogen, Balance.CELL_H2_FROM_BRINE)) return fail(OUT_FULL);
            brine.drain(Balance.CELL_BRINE_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Balance.CELL_BRINE_MB / Balance.CELL_CYCLE_TICKS;
        if (++progress > Balance.CELL_CYCLE_TICKS && heavy) {
            progress = 0;
            hydrogen.fill(new FluidStack(WmdFluids.DEUTERIUM.source().get(), Balance.CELL_D2_FROM_HW), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            return true;
        }
        if (progress > Balance.CELL_CYCLE_TICKS) {
            progress = 0;
            chlorine.fill(new FluidStack(WmdFluids.CHLORINE.source().get(), Balance.CELL_CL_FROM_BRINE), IFluidHandler.FluidAction.EXECUTE);
            hydrogen.fill(new FluidStack(WmdFluids.HYDROGEN.source().get(), Balance.CELL_H2_FROM_BRINE), IFluidHandler.FluidAction.EXECUTE);
            if (caustic.fits(wmd("caustic_soda"), Balance.CELL_CAUSTIC)) caustic.add(wmd("caustic_soda"), Balance.CELL_CAUSTIC);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "caustic_soda" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.CELL_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    private static boolean isHeavy(FluidTank t) { return !t.isEmpty() && t.getFluid().getFluid() == WmdFluids.HEAVY_WATER.source().get(); }
    private boolean deuteriumIn() { return !hydrogen.isEmpty() && hydrogen.getFluid().getFluid() == WmdFluids.DEUTERIUM.source().get(); }

    @Override protected List<Fluid> tankFluids() {
        return List.of(isHeavy(brine) ? WmdFluids.HEAVY_WATER.source().get() : WmdFluids.BRINE.source().get(), WmdFluids.CHLORINE.source().get(),
                deuteriumIn() ? WmdFluids.DEUTERIUM.source().get() : WmdFluids.HYDROGEN.source().get(), Fluids.EMPTY);
    }
    @Override protected List<String> labelKeys() { return List.of(isHeavy(brine) ? "heavy_water" : "brine", "chlorine", deuteriumIn() ? "deuterium" : "hydrogen", "caustic_soda"); }
    @Override protected int rateMbPerTick() { return Balance.CELL_BRINE_MB / Balance.CELL_CYCLE_TICKS; }
    @Override protected int inputTanks() { return 1; }
    @Override public String guiTankUnit(int t) { return t < 3 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CELL_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.cell_brine", Balance.CELL_BRINE_MB, Balance.CELL_CL_FROM_BRINE, Balance.CELL_H2_FROM_BRINE, Balance.CELL_CAUSTIC),
                Component.translatable("gui.wmd.info.cell_hint"),
                Component.translatable("gui.wmd.info.cell_heavy", Balance.CELL_HW_MB, Balance.CELL_D2_FROM_HW));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CA_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    static final double CA_FIZZ_BASE = 0.15, CA_FIZZ_LOAD = 0.45;

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        if (r.nextDouble() >= CA_FIZZ_BASE + CA_FIZZ_LOAD * flowFraction()) return;
        int k = r.nextInt(7);
        double xPx = -17.6 + 4.0 * k + 0.6 + r.nextDouble() * 2.0, yPx = 7.0 + r.nextDouble() * 13.0;
        double[] w = worldPoint(xPx / 16.0, yPx / 16.0, -2.5 / 16.0);
        level.addParticle(ParticleTypes.BUBBLE_POP, w[0], w[1], w[2], 0, 0.04, 0);
    }

    @Override
    protected String detail() { return (isHeavy(brine) ? "heavy water " : "brine ") + mb(brine) + ", chlorine " + mb(chlorine) + ", hydrogen " + mb(hydrogen) + ", caustic soda " + caustic.count(); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.WEST)) return brineIn;
        if (at(part, side, 1, 1, 0, Direction.EAST)) return clOut;
        if (at(part, side, 1, 1, 1, Direction.SOUTH)) return h2Out;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.NORTH) ? causticOut : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putBoolean("heavy", heavy); }
    @Override protected void loadExtra(CompoundTag tag) { if (!tag.contains("progress")) return; progress = tag.getInt("progress"); heavy = tag.getBoolean("heavy"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).fluid(-1, 0, 0, Direction.WEST, "brine", false)
            .fluid(1, 1, 0, Direction.EAST, "chlorine", true).fluid(1, 1, 1, Direction.SOUTH, "hydrogen", true).item(1, 0, 0, Direction.NORTH, true).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
