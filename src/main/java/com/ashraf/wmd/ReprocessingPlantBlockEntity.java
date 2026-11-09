package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class ReprocessingPlantBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank water = new FluidTank(Balance.REPRO_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot rods = new Slot(16, false, s -> s.is(wmd("spent_fuel_rod")));
    private final Slot caustic = new Slot(64, false, s -> s.is(wmd("caustic_soda")));
    private final Slot plutonium = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot depleted = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot irradiated = new Slot(16, false, s -> s.is(wmd("irradiated_breeder_rod")));
    private final FluidTank tritium = new FluidTank(Balance.FUSION_FUEL_TANK_MB, fs -> fs.getFluid() == WmdFluids.TRITIUM.source().get());
    private final Slot[] slots = { rods, caustic, plutonium, depleted, irradiated };
    private final List<FluidTank> tanks = List.of(water, tritium, rods.gauge, caustic.gauge, plutonium.gauge, depleted.gauge, irradiated.gauge);
    private final IFluidHandler waterIn = fillOnly(water), tritiumOut = drainOnly(tritium);
    private final IItemHandler in = inHandler(rods, caustic, irradiated), outH = outHandler(plutonium, depleted);
    private int progress;
    private boolean extracting;

    public static final int ANIM_OFF = 0, ANIM_READY = 1, ANIM_FAULT = 3;
    private int animSent = -1;
    public int animState;
    public float animProg;
    public boolean animBreeder;
    private long flashUntil;

    public ReprocessingPlantBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.REPROCESSING_PLANT_BE.get(), pos, state, Balance.REPRO_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        int before = progress;
        boolean did = processCycle();
        animSync(did || reason != OUT_FULL ? ANIM_READY : ANIM_FAULT, before == 0 && progress > 0);
        return did;
    }

    @Override
    protected void idle() {
        super.idle();
        animSync(ANIM_OFF, false);
    }

    private void animSync(int st, boolean cycleStart) {
        if (level == null || level.isClientSide || (st == animSent && !cycleStart)) return;
        animSent = st;
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    private boolean processCycle() {
        if (progress == 0 && irradiated.count() > 0) {
            if (tritium.getCapacity() - tritium.getFluidAmount() < Balance.BREEDER_TRITIUM_MB) return fail(OUT_FULL);
            irradiated.take(1);
            extracting = true;
            progress = 1;
            setChanged();
            return true;
        }
        if (progress == 0) {
            extracting = false;
            if (rods.count() < Balance.REPRO_SPENT || caustic.count() < Balance.REPRO_CAUSTIC || water.getFluidAmount() < Balance.REPRO_WATER_MB) return fail(NO_INPUT);
            if (!plutonium.fits(wmd("plutonium"), Balance.REPRO_PLUTONIUM) || !depleted.fits(wmd("depleted_uranium"), Balance.REPRO_DEPLETED)) return fail(OUT_FULL);
            rods.take(Balance.REPRO_SPENT);
            caustic.take(Balance.REPRO_CAUSTIC);
            water.drain(Balance.REPRO_WATER_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Math.max(1, Balance.REPRO_WATER_MB / Balance.REPRO_CYCLE_TICKS);
        if (++progress > Balance.REPRO_CYCLE_TICKS && extracting) {
            progress = 0;
            tritium.fill(new net.neoforged.neoforge.fluids.FluidStack(WmdFluids.TRITIUM.source().get(), Balance.BREEDER_TRITIUM_MB), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            return true;
        }
        if (progress > Balance.REPRO_CYCLE_TICKS) {
            progress = 0;
            plutonium.add(wmd("plutonium"), Balance.REPRO_PLUTONIUM);
            depleted.add(wmd("depleted_uranium"), Balance.REPRO_DEPLETED);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "spent_fuel_rod", "caustic_soda", "plutonium", "depleted_uranium", "irradiated_breeder_rod" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) { return irradiated.count() > 0 ? ItemStack.EMPTY : new ItemStack(wmd("plutonium"), Balance.REPRO_PLUTONIUM); }

    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.REPRO_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, WmdFluids.TRITIUM.source().get(), Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "tritium", "spent_fuel", "caustic_soda", "plutonium", "depleted_uranium", "irradiated_rods"); }
    @Override protected int rateMbPerTick() { return Math.max(1, Balance.REPRO_WATER_MB / Balance.REPRO_CYCLE_TICKS); }
    @Override public boolean guiTankOutput(int t) { return t == 1 || t == 4 || t == 5; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.REPRO_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.reprocessing_plant", Balance.REPRO_SPENT, Balance.REPRO_CAUSTIC, Balance.REPRO_WATER_MB, Balance.REPRO_PLUTONIUM,
                        Balance.REPRO_DEPLETED, Balance.REPRO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.repro_tritium", Balance.BREEDER_TRITIUM_MB, Balance.REPRO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("rpst", Math.max(0, animSent));
        tag.putInt("rpp", progress);
        tag.putBoolean("rpx", extracting);
        return tag;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.RP_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();

        float before = animProg / Balance.REPRO_CYCLE_TICKS;
        animProg = Math.min(animProg + 1.0F, Balance.REPRO_CYCLE_TICKS);
        float after = animProg / Balance.REPRO_CYCLE_TICKS;
        if (crossed(before, after, HotCellAnim.GRIP_L) || crossed(before, after, HotCellAnim.GRIP_R) || crossed(before, after, HotCellAnim.REL_L))
            playOnce(level, WmdSounds.BAY_GRAB.get(), Balance.SND_CLICK, 1.25F);
        if (crossed(before, after, HotCellAnim.BEAT)) {
            playOnce(level, WmdSounds.NR_RODS.get(), Balance.SND_MECH, 0.9F);
            flashUntil = t + 12;
            double[] n = worldPoint(1.63, 2.15, 1.47), s = worldPoint(-1.44, 3.05, 1.34);
            for (int k = 0; k < 6; k++) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, n[0] + (level.random.nextDouble() - 0.5) * 0.2, n[1], n[2] + (level.random.nextDouble() - 0.5) * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.02, 0.05 + level.random.nextDouble() * 0.03, (level.random.nextDouble() - 0.5) * 0.02);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, s[0], s[1], s[2], 0, 0.06, 0);
            }
        }
        long key = getBlockPos().asLong() ^ 0x5250_0000_0000_0000L;
        double[] c = worldPoint(-0.375, 1.31, -0.6), lamp = worldPoint(-0.11, 0.51, -0.55);
        boolean flash = t < flashUntil;
        VeilBridge.machineGlow(key, c[0], c[1], c[2], 1.0F, 0.55F, 0.2F, flash ? 3.0F : 2.2F, (flash ? 1.3F : 0.8F) + 0.1F * (float) Math.sin(t * 0.12), t);
        VeilBridge.machineGlow(key + 1, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.45F, 0.8F, 0.5F, t);
        if (t % 12 == 0) { double[] s = worldPoint(-1.44, 3.05, 1.34); level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, s[0], s[1], s[2], 0, 0.04, 0); }
    }

    @Override
    protected String detail() {
        return "water " + mb(water) + ", spent rods " + rods.count() + ", caustic soda " + caustic.count() + ", plutonium " + plutonium.count() + ", depleted " + depleted.count() + ", irradiated rods " + irradiated.count() + ", tritium " + mb(tritium) + (extracting ? " (extracting)" : "")
                + ", cycle " + progress * 100 / Balance.REPRO_CYCLE_TICKS + "%";
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 2, Direction.WEST)) return waterIn;
        if (at(part, side, 2, 0, 2, Direction.EAST)) return tritiumOut;
        return null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 1, Direction.WEST)) return in;
        if (at(part, side, 2, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    private static boolean crossed(float a, float b, float at) { return a < at && b >= at; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putBoolean("extracting", extracting); }
    @Override protected void loadExtra(CompoundTag tag) {
        if (tag.contains("rpst")) {
            animState = tag.getInt("rpst");
            animProg = tag.getInt("rpp");
            animBreeder = tag.getBoolean("rpx");
            return;
        }
        progress = tag.getInt("progress");
        extracting = tag.getBoolean("extracting");

        if (!tritium.isEmpty() && tritium.getFluid().getFluid() != WmdFluids.TRITIUM.source().get()) tritium.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
    }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).item(-2, 0, 1, Direction.WEST, false).fluid(-2, 0, 2, Direction.WEST, "water", false)
            .item(2, 0, 1, Direction.EAST, true).fluid(2, 0, 2, Direction.EAST, "tritium", true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
