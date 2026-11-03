package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class LeachTankBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank water = new FluidTank(Balance.LEACH_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot conc = new Slot(64, false, s -> s.is(wmd("uranium_concentrate")));
    private final Slot caustic = new Slot(64, false, s -> s.is(wmd("caustic_soda")));
    private final Slot cake = new Slot(64, true, s -> false).chute(1, 0, 0, Direction.EAST);
    private final Slot[] slots = { conc, caustic, cake };
    private final List<FluidTank> tanks = List.of(water, conc.gauge, caustic.gauge, cake.gauge);
    private final IFluidHandler waterIn = fillOnly(water);
    private final IItemHandler in = inHandler(conc, caustic), outH = outHandler(cake);
    private int progress;

    public LeachTankBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.LEACH_TANK_BE.get(), pos, state, Balance.LEACH_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (conc.count() < Balance.LEACH_CONC || caustic.count() < Balance.LEACH_CAUSTIC || water.getFluidAmount() < Balance.LEACH_WATER_MB) return fail(NO_INPUT);
            if (!cake.fits(wmd("yellowcake"), Balance.LEACH_YELLOWCAKE)) return fail(OUT_FULL);
            conc.take(Balance.LEACH_CONC);
            caustic.take(Balance.LEACH_CAUSTIC);
            water.drain(Balance.LEACH_WATER_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Math.max(1, Balance.LEACH_WATER_MB / Balance.LEACH_CYCLE_TICKS);
        if (++progress > Balance.LEACH_CYCLE_TICKS) {
            progress = 0;
            cake.add(wmd("yellowcake"), Balance.LEACH_YELLOWCAKE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "uranium_concentrate", "caustic_soda", "yellowcake" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) { return new ItemStack(wmd("yellowcake"), Balance.LEACH_YELLOWCAKE); }

    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.LEACH_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "u_conc", "caustic_soda", "yellowcake"); }
    @Override protected int rateMbPerTick() { return Math.max(1, Balance.LEACH_WATER_MB / Balance.LEACH_CYCLE_TICKS); }
    @Override protected int inputTanks() { return 3; }
    @Override public String guiTankUnit(int t) { return t < 1 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.LEACH_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.leach_tank", Balance.LEACH_CONC, Balance.LEACH_CAUSTIC, Balance.LEACH_WATER_MB, Balance.LEACH_YELLOWCAKE, Balance.LEACH_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.LT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return 0.5F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4C54_0000_0000_0000L;
        double[] glass = worldPoint(0.0, 1.35, 0.5), lamp = worldPoint(-0.29, 0.68, -0.49);
        VeilBridge.machineGlow(key, glass[0], glass[1], glass[2], 1.0F, 0.85F, 0.25F, 1.4F, 0.5F + 0.08F * (float) Math.sin(t * 0.15), t);
        VeilBridge.machineGlow(key + 1, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.45F, 0.8F, 0.5F, t);
        if (t % 10 != 0) return;
        var r = level.random;
        double[] top = worldPoint(0.0, 1.3, 0.5);
        level.addParticle(ParticleTypes.CLOUD, top[0] + (r.nextDouble() - 0.5) * 0.3, top[1], top[2] + (r.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
    }

    @Override
    protected String detail() {
        return "water " + mb(water) + ", concentrate " + conc.count() + ", caustic soda " + caustic.count() + ", yellowcake " + cake.count()
                + ", cycle " + progress * 100 / Balance.LEACH_CYCLE_TICKS + "%";
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 1, Direction.WEST) ? waterIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -1, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 1, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).item(-1, 0, 0, Direction.WEST, false).fluid(-1, 0, 1, Direction.WEST, "water", false)
            .item(1, 0, 0, Direction.EAST, true).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
