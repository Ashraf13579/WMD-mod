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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class RollingMillBlockEntity extends ItemProcessBlockEntity {

    static final String[][] METALS = { { "steel", "steel_block", "steel_plate" }, { "aluminum", "aluminium_block", "aluminium_plate" }, { "titanium", "titanium_block", "titanium_plate" },
            { "tungsten", "tungsten_block", "tungsten_plate" } };
    @SuppressWarnings("unchecked")
    private static final TagKey<Item>[] TAGS = new TagKey[METALS.length];
    static {
        for (int i = 0; i < METALS.length; i++) TAGS[i] = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:storage_blocks/" + METALS[i][0]));
    }

    static String plateOf(ItemStack s) {
        if (s.isEmpty()) return null;
        for (int i = 0; i < METALS.length; i++) if (s.is(wmd(METALS[i][1])) || s.is(TAGS[i])) return METALS[i][2];
        return null;
    }

    private final Slot block = new Slot(64, false, s -> plateOf(s) != null);
    private final Slot plates = new Slot(64, true, s -> false).chute(3, 0, 0, Direction.EAST);
    private final Slot[] slots = { block, plates };
    private final List<FluidTank> tanks = List.of(block.gauge, plates.gauge);
    private final IItemHandler in = inHandler(block), outH = outHandler(plates);
    private int progress;
    private String product = "steel_plate";

    public RollingMillBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ROLLING_MILL_BE.get(), pos, state, Balance.MILL_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (block.count() < 1) return fail(NO_INPUT);
            String p = plateOf(block.stack);
            if (p == null) return fail(NO_INPUT);
            if (!plates.fits(wmd(p), Balance.MILL_PLATES)) return fail(OUT_FULL);
            block.take(1);
            product = p;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.MILL_CYCLE_TICKS) {
            progress = 0;
            plates.add(wmd(product), Balance.MILL_PLATES);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "steel_block", "steel_plate" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        String p = in.isEmpty() ? null : plateOf(in.get(0));
        return p == null ? ItemStack.EMPTY : new ItemStack(wmd(p), Balance.MILL_PLATES);
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.MILL_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("metal_block", "plates"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.MILL_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.rolling_mill", Balance.MILL_PLATES, Balance.MILL_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.ROLLING_MILL_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(1, 0, 0); }
    @Override protected float soundVolume() { return 0.7F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x524D_0000_0000_0000L;
        double[] slab = worldPoint(0.78, 1.0, 0.125), gap = worldPoint(1.5, 0.91, 0.125), lamp = worldPoint(0.91, 0.92, 1.1);
        VeilBridge.machineGlow(key, slab[0], slab[1], slab[2], 1.0F, 0.55F, 0.15F, 2.5F, 1.1F + 0.1F * (float) Math.sin(t * 0.2), t);
        VeilBridge.machineGlow(key + 1, gap[0], gap[1], gap[2], 1.0F, 0.75F, 0.4F, 1.5F, 0.8F, t);
        VeilBridge.machineGlow(key + 2, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.4F, 0.8F, 0.5F, t);
    }

    @Override
    protected String detail() { return "blocks " + block.count() + ", plates " + plates.count() + ", cycle " + progress * 100 / Balance.MILL_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 3, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putString("product", product); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); if (tag.contains("product")) product = tag.getString("product"); }

    private static final PortMap PORT_MAP = new PortMap(0, 3, 2).item(0, 0, 0, Direction.WEST, false).item(3, 0, 0, Direction.EAST, true).power(2, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
