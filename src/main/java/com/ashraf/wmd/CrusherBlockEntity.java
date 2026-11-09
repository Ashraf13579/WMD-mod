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

public class CrusherBlockEntity extends ItemProcessBlockEntity {

    private static final String[] METALS = {"tungsten", "titanium", "uranium", "tin", "lead", "silver", "platinum"};

    private static final int CONCENTRATES = 3;
    private static final TagKey<Item>[] RAW_TAGS = tags("raw_materials"), ORE_TAGS = tags("ores");

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] tags(String kind) {
        TagKey<Item>[] t = new TagKey[METALS.length];
        for (int i = 0; i < METALS.length; i++) t[i] = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:" + kind + "/" + METALS[i]));
        return t;
    }

    static String chunkProduct(ItemStack s) {
        if (s.is(wmd("rutile_chunk"))) return "titanium_concentrate";
        if (s.is(wmd("wolframite_chunk"))) return "tungsten_concentrate";
        if (s.is(wmd("uraninite_chunk"))) return "uranium_concentrate";
        return null;
    }

    static int yieldOf(ItemStack s) { return chunkProduct(s) != null ? Balance.CRUSH_CHUNK_YIELD : Balance.CRUSH_YIELD; }

    static String productOf(ItemStack s) {
        if (s.isEmpty()) return null;
        String c = chunkProduct(s);
        if (c != null) return c;
        for (int i = 0; i < METALS.length; i++) {
            String m = METALS[i];
            if (s.is(wmd("raw_" + m)) || s.is(wmd(m + "_ore")) || s.is(wmd("deepslate_" + m + "_ore")) || s.is(RAW_TAGS[i]) || s.is(ORE_TAGS[i]))
                return i < CONCENTRATES ? m + "_concentrate" : m + "_dust";
        }
        return null;
    }

    private final Slot ore = new Slot(64, false, s -> productOf(s) != null);
    private final Slot conc = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { ore, conc };
    private final List<FluidTank> tanks = List.of(ore.gauge, conc.gauge);
    private final IItemHandler in = inHandler(ore), outH = outHandler(conc);
    private int progress;
    private String product = "tungsten_concentrate";
    private int yield = Balance.CRUSH_YIELD;

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CRUSHER_BE.get(), pos, state, Balance.CRUSH_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (ore.count() < 1) return fail(NO_INPUT);
            String p = productOf(ore.stack);
            if (p == null) return fail(NO_INPUT);
            int y = yieldOf(ore.stack);
            if (!conc.fits(wmd(p), y)) return fail(OUT_FULL);
            ore.take(1);
            product = p;
            yield = y;
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.CRUSH_CYCLE_TICKS) {
            progress = 0;
            conc.add(wmd(product), yield);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "raw_tungsten", "tungsten_concentrate" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        String p = in.isEmpty() ? null : productOf(in.get(0));
        return p == null ? ItemStack.EMPTY : new ItemStack(wmd(p), Balance.CRUSH_YIELD);
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.CRUSH_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("raw_ore", "concentrate"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CRUSH_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.crusher", Balance.CRUSH_YIELD, Balance.CRUSH_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CRUSHER_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override
    protected String detail() { return "ore " + ore.count() + ", concentrate " + conc.count() + ", cycle " + progress * 100 / Balance.CRUSH_CYCLE_TICKS + "%"; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 2, 1, Direction.UP)) return in;
        if (at(part, side, 1, 0, 1, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 2, Direction.EAST) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); tag.putString("product", product); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); if (tag.contains("product")) product = tag.getString("product"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).item(0, 2, 1, Direction.UP, false).item(1, 0, 1, Direction.EAST, true).power(1, 0, 2, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
