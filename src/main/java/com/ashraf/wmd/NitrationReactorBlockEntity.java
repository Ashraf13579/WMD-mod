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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class NitrationReactorBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank naphtha = new FluidTank(Balance.NITRO_TANK_MB, fs -> fs.getFluid() == WmdFluids.NAPHTHA.source().get());
    private final Slot dust = new Slot(64, false, s -> s.is(wmd("nitrate_dust")));
    private final Slot charge = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { dust, charge };
    private final List<FluidTank> tanks = List.of(naphtha, dust.gauge, charge.gauge);
    private final IFluidHandler naphthaPort = fillOnly(naphtha);
    private final IItemHandler in = inHandler(dust), outH = outHandler(charge);
    private int progress;

    public NitrationReactorBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.NITRATION_REACTOR_BE.get(), pos, state, Balance.NITRO_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (dust.count() < Balance.NITRO_DUST_PER_CYCLE || naphtha.getFluidAmount() < Balance.NITRO_NAPHTHA_MB) return fail(NO_INPUT);
            if (!charge.fits(wmd("he_charge"), Balance.NITRO_HE_PER_CYCLE)) return fail(OUT_FULL);
            dust.take(Balance.NITRO_DUST_PER_CYCLE);
            naphtha.drain(Balance.NITRO_NAPHTHA_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 3;
        if (++progress > Balance.NITRO_CYCLE_TICKS) {
            progress = 0;
            charge.add(wmd("he_charge"), Balance.NITRO_HE_PER_CYCLE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "nitrate_dust", "he_charge" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.NITRO_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.NAPHTHA.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("naphtha", "nitrate", "he_charge"); }
    @Override protected int rateMbPerTick() { return 3; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.NITRO_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.nitration", Balance.NITRO_DUST_PER_CYCLE, Balance.NITRO_NAPHTHA_MB, Balance.NITRO_HE_PER_CYCLE, Balance.NITRO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.NR_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 1); }
    @Override protected float soundVolume() { return 0.6F; }
    @Override protected float levelFraction() { return naphtha.getFluidAmount() / (float) Balance.NITRO_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 12 != 0) return;
        var r = level.random;
        double[] w = worldPoint(0.0, 6.1, 1.0);
        level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.01, 0.03, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected String detail() { return "naphtha " + mb(naphtha) + ", dust " + dust.count() + ", HE charge " + charge.count() + ", cycle " + progress * 100 / Balance.NITRO_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 1, 1, Direction.WEST) ? naphthaPort : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 1, 4, 1, Direction.UP)) return in;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "naphtha", false).item(1, 4, 1, Direction.UP, false).item(1, 0, 1, Direction.EAST, true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
