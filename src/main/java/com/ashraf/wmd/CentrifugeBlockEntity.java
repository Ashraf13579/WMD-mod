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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CentrifugeBlockEntity extends ItemProcessBlockEntity {

    private static final int SCALE = 100;

    private final Slot cake = new Slot(64, false, s -> s.is(wmd("yellowcake")));
    private final Slot enriched = new Slot(64, true, s -> false).chute(0, 1, 0, Direction.NORTH);
    private final Slot depleted = new Slot(64, true, s -> false).chute(0, 1, 0, Direction.NORTH);
    private final Slot[] slots = { cake, enriched, depleted };
    private final List<FluidTank> tanks = List.of(cake.gauge, enriched.gauge, depleted.gauge);
    private final IItemHandler in = inHandler(cake), outH = outHandler(enriched, depleted);
    private int progress;
    private int neighbours = -1;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CENTRIFUGE_BE.get(), pos, state, Balance.CENT_FE_PER_TICK);
    }

    public int countNeighbours() {
        Level level = getLevel();
        if (level == null) return 0;
        Direction f = facing();
        int n = 0;
        for (int dx = -1; dx <= 1; dx += 2) {
            BlockPos p = Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), dx, 0, 0);
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof CentrifugeBlockEntity) n++;
        }
        return Math.min(n, Balance.CENT_MAX_NEIGHBOURS);
    }

    static int speedPct(int neighbours) { return 100 + Balance.CENT_NEIGHBOUR_BONUS_PCT * neighbours; }

    private int cycleWork() { return Balance.CENT_CYCLE_TICKS * SCALE; }

    @Override
    protected boolean process() {
        Level level = getLevel();
        if (neighbours < 0 || (level != null && level.getGameTime() % 20 == 0)) neighbours = countNeighbours();
        if (progress == 0) {
            if (cake.count() < Balance.CENT_YELLOWCAKE) return fail(NO_INPUT);
            if (!enriched.fits(wmd("enriched_uranium"), Balance.CENT_ENRICHED) || !depleted.fits(wmd("depleted_uranium"), Balance.CENT_DEPLETED)) return fail(OUT_FULL);
            cake.take(Balance.CENT_YELLOWCAKE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        progress += speedPct(neighbours);
        if (progress >= cycleWork()) {
            progress = 0;
            enriched.add(wmd("enriched_uranium"), Balance.CENT_ENRICHED);
            depleted.add(wmd("depleted_uranium"), Balance.CENT_DEPLETED);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "yellowcake", "enriched_uranium", "depleted_uranium" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) { return new ItemStack(wmd("enriched_uranium"), Balance.CENT_ENRICHED); }

    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) cycleWork(); }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("yellowcake", "enriched", "depleted"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        int n = countNeighbours(), pct = speedPct(n);
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CENT_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.centrifuge", Balance.CENT_YELLOWCAKE, Balance.CENT_ENRICHED, Balance.CENT_DEPLETED, Balance.CENT_CYCLE_TICKS * 100 / pct / 20),
                Component.translatable("gui.wmd.info.cascade", n, pct - 100));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CENTRIFUGE_RUN.get(); }
    @Override protected void runStarted(net.minecraft.world.level.Level level) { playOnce(level, WmdSounds.CENTRIFUGE_START.get(), 0.6F, 1.0F); }
    @Override protected void runStopped(net.minecraft.world.level.Level level) { playOnce(level, WmdSounds.CENTRIFUGE_STOP.get(), 0.6F, 1.0F); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x4345_0000_0000_0000L;
        double[] ring = worldPoint(-0.09, 2.24, -0.48), lamp = worldPoint(0.14, 2.24, -0.48);
        VeilBridge.machineGlow(key, ring[0], ring[1], ring[2], 0.35F, 0.65F, 1.0F, 1.4F, 0.7F + 0.08F * (float) Math.sin(t * 0.3), t);
        VeilBridge.machineGlow(key + 1, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.45F, 0.6F, 0.45F, t);
    }

    @Override
    protected String detail() {
        return "yellowcake " + cake.count() + ", enriched " + enriched.count() + ", depleted " + depleted.count() + ", cascade +" + (speedPct(Math.max(0, neighbours)) - 100)
                + "%, cycle " + (int) (progress01() * 100) + "%";
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.NORTH)) return in;
        if (at(part, side, 0, 1, 0, Direction.NORTH)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).item(0, 0, 0, Direction.NORTH, false).item(0, 1, 0, Direction.NORTH, true).power(0, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
