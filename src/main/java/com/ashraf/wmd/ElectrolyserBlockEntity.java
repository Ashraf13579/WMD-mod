package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class ElectrolyserBlockEntity extends ItemProcessBlockEntity {

    private static final TagKey<Item> BAUXITE_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:raw_materials/bauxite"));

    private final Slot bauxite = new Slot(64, false, s -> s.is(wmd("raw_bauxite")) || s.is(BAUXITE_TAG));
    private final Slot electrode = new Slot(16, false, s -> s.is(wmd("carbon_electrode")));
    private final Slot aluminium = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot[] slots = { bauxite, electrode, aluminium };
    private final List<FluidTank> tanks = List.of(bauxite.gauge, electrode.gauge, aluminium.gauge);
    private final IItemHandler in = inHandler(bauxite, electrode), outH = outHandler(aluminium);
    private int progress, electrodeLife;

    public ElectrolyserBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ELECTROLYSER_BE.get(), pos, state, Balance.ELECTRO_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (bauxite.count() < Balance.ELECTRO_BAUXITE_PER_CYCLE) return fail(NO_INPUT);
            if (electrodeLife <= 0 && electrode.count() < 1) return fail(NO_ELECTRODE);
            if (!aluminium.fits(wmd("aluminium_ingot"), Balance.ELECTRO_ALU_PER_CYCLE)) return fail(OUT_FULL);
            bauxite.take(Balance.ELECTRO_BAUXITE_PER_CYCLE);
            if (electrodeLife <= 0) { electrode.take(1); electrodeLife = Balance.ELECTRO_CYCLES_PER_ELECTRODE; }
            electrodeLife--;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.ELECTRO_CYCLE_TICKS) {
            progress = 0;
            aluminium.add(wmd("aluminium_ingot"), Balance.ELECTRO_ALU_PER_CYCLE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "raw_bauxite", "carbon_electrode", "aluminium_ingot" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return !in.isEmpty() && in.get(0).getCount() >= Balance.ELECTRO_BAUXITE_PER_CYCLE ? new ItemStack(wmd("aluminium_ingot"), Balance.ELECTRO_ALU_PER_CYCLE) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.ELECTRO_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("bauxite", "electrode", "aluminium"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ELECTRO_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.electrolyser", Balance.ELECTRO_BAUXITE_PER_CYCLE, Balance.ELECTRO_ALU_PER_CYCLE, Balance.ELECTRO_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.EL_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected String detail() { return "bauxite " + bauxite.count() + ", electrodes " + electrode.count() + ", aluminium " + aluminium.count() + ", cycle " + progress * 100 / Balance.ELECTRO_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 2, 1, Direction.UP)) return in;
        if (at(part, side, 2, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putInt("life", electrodeLife); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); electrodeLife = tag.getInt("life"); }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).item(-2, 2, 1, Direction.UP, false).item(2, 0, 1, Direction.EAST, true).power(-1, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
