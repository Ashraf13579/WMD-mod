package com.ashraf.wmd;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class ArcFurnaceBlockEntity extends ItemProcessBlockEntity {

    private final Slot conc = new Slot(64, false, s -> s.is(wmd("titanium_sponge")) || s.is(wmd("tungsten_concentrate")));
    private final Slot coke = new Slot(64, false, s -> s.is(wmd("petroleum_coke")));
    private final Slot electrode = new Slot(16, false, s -> s.is(wmd("carbon_electrode")));
    private final Slot ingot = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { conc, coke, electrode, ingot };
    private final List<FluidTank> tanks = List.of(conc.gauge, coke.gauge, electrode.gauge, ingot.gauge);
    private final IItemHandler in = inHandler(conc, coke, electrode), outH = outHandler(ingot);
    private int progress, electrodeLife, cycleTicks;
    private boolean titanium = true;

    public ArcFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ARC_FURNACE_BE.get(), pos, state, Balance.ARC_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            boolean ti = conc.stack.is(wmd("titanium_sponge"));
            int need = ti ? Balance.ARC_TI_CONC : Balance.ARC_W_CONC;
            if (conc.count() < need) return fail(NO_INPUT);
            if (ti && coke.count() < Balance.ARC_TI_COKE) return fail(NO_INPUT);
            if (electrodeLife <= 0 && electrode.count() < 1) return fail(NO_ELECTRODE);
            if (!ingot.fits(wmd(ti ? "titanium_ingot" : "tungsten_ingot"), Balance.ARC_INGOTS_PER_CYCLE)) return fail(OUT_FULL);
            conc.take(need);
            if (ti && Balance.ARC_TI_COKE > 0) coke.take(Balance.ARC_TI_COKE);
            if (electrodeLife <= 0) { electrode.take(1); electrodeLife = Balance.ARC_CYCLES_PER_ELECTRODE; }
            electrodeLife--;
            titanium = ti;
            cycleTicks = ti ? Balance.ARC_TI_CYCLE_TICKS : Balance.ARC_W_CYCLE_TICKS;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > cycleTicks) {
            progress = 0;
            ingot.add(wmd(titanium ? "titanium_ingot" : "tungsten_ingot"), Balance.ARC_INGOTS_PER_CYCLE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "titanium_sponge", "petroleum_coke", "carbon_electrode", "titanium_ingot" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        if (in.get(0).is(wmd("titanium_sponge"))) return new ItemStack(wmd("titanium_ingot"), Balance.ARC_INGOTS_PER_CYCLE);
        if (in.get(0).is(wmd("tungsten_concentrate"))) return new ItemStack(wmd("tungsten_ingot"), Balance.ARC_INGOTS_PER_CYCLE);
        return ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 || cycleTicks <= 0 ? 0.0F : progress / (float) cycleTicks; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("charge", "coke", "electrode", "ingots"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ARC_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.arc_ti", Balance.ARC_TI_CONC, Balance.ARC_TI_COKE, Balance.ARC_INGOTS_PER_CYCLE, Balance.ARC_TI_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.arc_w", Balance.ARC_W_CONC, Balance.ARC_INGOTS_PER_CYCLE, Balance.ARC_W_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.ARC_FURNACE_RUN.get(); }
    @Override protected float soundPitch() { return 0.93F + 0.14F * Math.max(0.0F, Math.min(1.0F, progress01())); }
    @Override protected void runStarted(net.minecraft.world.level.Level level) { playOnce(level, WmdSounds.ARC_STRIKE.get(), 1.1F, 0.95F + level.random.nextFloat() * 0.1F); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return 0.9F; }

    @Override
    protected String detail() { return "concentrate " + conc.count() + ", coke " + coke.count() + ", electrodes " + electrode.count() + ", ingots " + ingot.count() + ", cycle " + (cycleTicks == 0 ? 0 : progress * 100 / cycleTicks) + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 3, 1, Direction.UP)) return in;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putInt("life", electrodeLife); tag.putInt("cycle", cycleTicks); tag.putBoolean("titanium", titanium); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); electrodeLife = tag.getInt("life"); cycleTicks = tag.getInt("cycle"); titanium = !tag.contains("titanium") || tag.getBoolean("titanium"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).item(0, 3, 1, Direction.UP, false).item(1, 0, 1, Direction.EAST, true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
