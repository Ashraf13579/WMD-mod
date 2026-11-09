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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class WireDrawerBlockEntity extends ItemProcessBlockEntity {

    static final String[][] METALS = { { "copper", "copper_wire" }, { "gold", "gold_wire" }, { "aluminum", "aluminium_cable" } };
    @SuppressWarnings("unchecked")
    private static final TagKey<Item>[] TAGS = new TagKey[METALS.length];
    static {
        for (int i = 0; i < METALS.length; i++) TAGS[i] = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/" + METALS[i][0]));
    }

    static String wireOf(ItemStack s) {
        if (s.isEmpty()) return null;
        if (s.is(Items.COPPER_INGOT)) return "copper_wire";
        if (s.is(Items.GOLD_INGOT)) return "gold_wire";
        if (s.is(wmd("aluminium_ingot"))) return "aluminium_cable";
        for (int i = 0; i < METALS.length; i++) if (s.is(TAGS[i])) return METALS[i][1];
        return null;
    }

    private final Slot ingot = new Slot(64, false, s -> wireOf(s) != null);
    private final Slot wire = new Slot(64, true, s -> false).chute(2, 0, 0, Direction.EAST);
    private final Slot[] slots = { ingot, wire };
    private final List<FluidTank> tanks = List.of(ingot.gauge, wire.gauge);
    private final IItemHandler in = inHandler(ingot), outH = outHandler(wire);
    private int progress;
    private String product = "copper_wire";

    public WireDrawerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.WIRE_DRAWER_BE.get(), pos, state, Balance.WIRE_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (ingot.count() < 1) return fail(NO_INPUT);
            String p = wireOf(ingot.stack);
            if (p == null) return fail(NO_INPUT);
            if (!wire.fits(wmd(p), Balance.WIRE_PER_INGOT)) return fail(OUT_FULL);
            ingot.take(1);
            product = p;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.WIRE_CYCLE_TICKS) {
            progress = 0;
            wire.add(wmd(product), Balance.WIRE_PER_INGOT);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "aluminium_ingot", "copper_wire" }; }

    @Override
    public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(wmd("copper_wire"))); }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        String p = in.isEmpty() ? null : wireOf(in.get(0));
        return p == null ? ItemStack.EMPTY : new ItemStack(wmd(p), Balance.WIRE_PER_INGOT);
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.WIRE_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("metal_ingot", "wires"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.WIRE_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.wire_drawer", Balance.WIRE_PER_INGOT, Balance.WIRE_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.WD_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(1, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x5744_0000_0000_0000L;
        double[] led = worldPoint(1.056, 0.569, -0.456), bar = worldPoint(0.797, 0.563, -0.45);
        VeilBridge.machineGlow(key, led[0], led[1], led[2], 0.3F, 1.0F, 0.4F, 0.8F, 0.5F, t);
        VeilBridge.machineGlow(key + 1, bar[0], bar[1], bar[2], 0.35F, 0.6F, 1.0F, 1.0F, 0.6F, t);
    }

    @Override
    protected String detail() { return "ingots " + ingot.count() + ", wire " + wire.count() + ", cycle " + progress * 100 / Balance.WIRE_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 2, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putString("product", product); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); if (tag.contains("product")) product = tag.getString("product"); }

    private static final PortMap PORT_MAP = new PortMap(0, 2, 1).item(0, 0, 0, Direction.WEST, false).item(2, 0, 0, Direction.EAST, true).power(1, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
