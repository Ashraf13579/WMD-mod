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

public class KrollReactorBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank ticl = new FluidTank(Balance.KROLL_TANK_MB, fs -> fs.getFluid() == WmdFluids.TITANIUM_TETRACHLORIDE.source().get());
    private final FluidTank mgcl = new FluidTank(Balance.KROLL_TANK_MB, fs -> fs.getFluid() == WmdFluids.MAGNESIUM_CHLORIDE.source().get());
    private final Slot mg = new Slot(64, false, s -> s.is(wmd("magnesium_ingot")));
    private final Slot sponge = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { mg, sponge };
    private final List<FluidTank> tanks = List.of(ticl, mgcl, mg.gauge, sponge.gauge);
    private final IFluidHandler ticlIn = fillOnly(ticl), mgclOut = drainOnly(mgcl);
    private final IItemHandler in = inHandler(mg), outH = outHandler(sponge);
    private int progress;

    public KrollReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.KROLL_REACTOR_BE.get(), pos, state, Balance.KROLL_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (ticl.getFluidAmount() < Balance.KROLL_TICL_MB || mg.count() < Balance.KROLL_MG) return fail(NO_INPUT);
            if (!sponge.fits(wmd("titanium_sponge"), Balance.KROLL_SPONGE) || mgcl.getCapacity() - mgcl.getFluidAmount() < Balance.KROLL_MGCL_MB) return fail(OUT_FULL);
            ticl.drain(Balance.KROLL_TICL_MB, IFluidHandler.FluidAction.EXECUTE);
            mg.take(Balance.KROLL_MG);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Balance.KROLL_TICL_MB / Balance.KROLL_CYCLE_TICKS;
        if (++progress > Balance.KROLL_CYCLE_TICKS) {
            progress = 0;
            sponge.add(wmd("titanium_sponge"), Balance.KROLL_SPONGE);
            mgcl.fill(new FluidStack(WmdFluids.MAGNESIUM_CHLORIDE.source().get(), Balance.KROLL_MGCL_MB), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "magnesium_ingot", "titanium_sponge" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.KROLL_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.TITANIUM_TETRACHLORIDE.source().get(), WmdFluids.MAGNESIUM_CHLORIDE.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("ticl4", "mgcl2", "magnesium", "sponge"); }
    @Override protected int rateMbPerTick() { return Math.max(1, Balance.KROLL_TICL_MB / Balance.KROLL_CYCLE_TICKS); }
    @Override public boolean guiTankOutput(int tank) { return tank == 1 || tank == 3; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }

    @Override protected float levelFraction() { return progress / (float) Balance.KROLL_CYCLE_TICKS; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.KROLL_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.kroll", Balance.KROLL_TICL_MB, Balance.KROLL_MG, Balance.KROLL_SPONGE, Balance.KROLL_MGCL_MB, Balance.KROLL_CYCLE_TICKS / 20));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.KR_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return 0.7F; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 4 != 0) return;
        var r = level.random;
        double[] w = worldPoint((r.nextDouble() - 0.5) * 1.2, 2.9, 1.0 + (r.nextDouble() - 0.5) * 1.2);
        level.addParticle(ParticleTypes.SMOKE, w[0], w[1], w[2], 0, 0.02, 0);
    }

    @Override
    protected String detail() { return "TiCl4 " + mb(ticl) + ", MgCl2 " + mb(mgcl) + ", magnesium " + mg.count() + ", sponge " + sponge.count() + ", batch " + progress * 100 / Balance.KROLL_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 1, 1, Direction.WEST)) return ticlIn;
        if (at(part, side, -1, 0, 2, Direction.WEST)) return mgclOut;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.NORTH)) return in;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "titanium_tetrachloride", false).item(-1, 0, 0, Direction.NORTH, false)
            .item(1, 0, 1, Direction.EAST, true).fluid(-1, 0, 2, Direction.WEST, "magnesium_chloride", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
