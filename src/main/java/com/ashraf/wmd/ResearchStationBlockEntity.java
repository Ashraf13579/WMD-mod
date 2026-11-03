package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class ResearchStationBlockEntity extends ItemProcessBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private static final class Store extends EnergyStorage {
        Store() { super(Balance.RES_BUFFER_FE, Balance.RES_MAX_IN_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final Store store = new Store();
    private String research = "";
    private String runId = "";
    private int progress;

    private final FluidTank fluid = new FluidTank(Balance.RES_TANK_MB, fs -> fluidOk(fs.getFluid()));
    private final Slot s0 = new Slot(64, false, st -> sampleOk(0, st));
    private final Slot s1 = new Slot(64, false, st -> sampleOk(1, st));
    private final Slot s2 = new Slot(64, false, st -> sampleOk(2, st));
    private final Slot blank = new Slot(16, false, st -> st.is(Wmd.CHIP_BLANK.get()));
    private final Slot chip = new Slot(16, true, st -> false).chute(1, 0, 0, Direction.EAST);
    private final Slot[] slots = { s0, s1, s2, blank, chip };
    private final Slot[] samples = { s0, s1, s2 };
    private final List<FluidTank> tanks = List.of(fluid, s0.gauge, s1.gauge, s2.gauge, blank.gauge, chip.gauge);
    private final IFluidHandler fluidIn = fillOnly(fluid);
    private final IItemHandler hatchIn = inHandler(s0, s1, s2, blank), chipOut = outHandler(chip);

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.RESEARCH_STATION_BE.get(), pos, state, 0);
    }

    public ResearchRecipes.Research research() { return ResearchRecipes.byId(research); }

    public String researchId() { return research; }

    public void select(String id) {
        ResearchRecipes.Research r = ResearchRecipes.byId(id);
        String nid = r == null ? "" : r.id();
        if (nid.equals(research)) return;
        research = nid;
        if (!fluid.isEmpty() && !fluidOk(fluid.getFluid().getFluid())) fluid.setFluid(FluidStack.EMPTY);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void selectClient(String id) { ResearchRecipes.Research r = ResearchRecipes.byId(id); research = r == null ? "" : r.id(); }

    private boolean sampleOk(int i, ItemStack st) {
        ResearchRecipes.Research r = research();
        return r != null && i < r.samples().size() && ResearchRecipes.matches(r.samples().get(i), st);
    }

    private boolean fluidOk(Fluid f) {
        Fluid want = ResearchRecipes.fluid(research());
        return want != Fluids.EMPTY && f.isSame(want);
    }

    private boolean loaded(ResearchRecipes.Research r) {
        for (int i = 0; i < r.samples().size(); i++) {
            ResearchRecipes.Sample s = r.samples().get(i);
            if (i >= samples.length || !ResearchRecipes.matches(s, samples[i].stack) || samples[i].count() < s.count()) return false;
        }
        if (r.fluidMb() > 0 && (fluid.getFluidAmount() < r.fluidMb() || !fluidOk(fluid.getFluid().getFluid()))) return false;
        return blank.count() >= 1;
    }

    private ResearchRecipes.Research active() { return progress > 0 ? ResearchRecipes.byId(runId) : research(); }

    private int fe() { ResearchRecipes.Research r = active(); return r == null ? Balance.RES_MINING_FE : r.fePerTick(); }

    @Override
    protected boolean process() {
        if (progress == 0) {
            ResearchRecipes.Research r = research();
            if (r == null) return fail(0);
            if (!chip.fits(ResearchRecipes.item(r.chipId()), 1)) return fail(OUT_FULL);
            if (!loaded(r)) return fail(NO_INPUT);
            for (int i = 0; i < r.samples().size(); i++) samples[i].take(r.samples().get(i).count());
            if (r.fluidMb() > 0) fluid.drain(r.fluidMb(), IFluidHandler.FluidAction.EXECUTE);
            blank.take(1);
            runId = r.id();
            progress = 1;
            store.use(r.fePerTick());
            setChanged();
            return true;
        }
        ResearchRecipes.Research r = ResearchRecipes.byId(runId);
        if (r == null) { progress = 0; runId = ""; setChanged(); return false; }
        store.use(r.fePerTick());
        if (++progress > r.ticks()) {
            progress = 0;
            runId = "";
            chip.add(ResearchRecipes.item(r.chipId()), 1);
            setChanged();
        }
        return true;
    }

    @Override
    protected float progress01() {
        ResearchRecipes.Research r = ResearchRecipes.byId(runId);
        return progress == 0 || r == null ? 0.0F : progress / (float) r.ticks();
    }

    @Override public boolean hasPower() { return store.getEnergyStored() >= fe(); }
    @Override protected IEnergyStorage energy() { return store; }
    @Override public IEnergyStorage guiEnergy() { return store; }
    @Override public int guiFeUse() { return fe(); }
    @Override public void empDrain() { super.empDrain(); store.set(0); setChanged(); }

    @Override protected String[] iconIds() { return new String[] { "chip_blank", "chip_blank", "chip_blank", "chip_blank", "chip_blank" }; }

    @Override
    public List<ItemStack> guiSlotIcons() {
        ResearchRecipes.Research r = research();
        long t = level != null ? level.getGameTime() : 0;
        List<ItemStack> l = new ArrayList<>();
        l.add(ItemStack.EMPTY);
        for (int i = 0; i < 3; i++) l.add(r != null && i < r.samples().size() ? ResearchRecipes.icon(r.samples().get(i), t) : new ItemStack(Items.BARRIER));
        l.add(new ItemStack(Wmd.CHIP_BLANK.get()));
        l.add(r != null ? ResearchRecipes.chip(r) : new ItemStack(Wmd.CHIP_BLANK.get()));
        return l;
    }

    @Override
    public List<Component> guiTankLabels() {
        ResearchRecipes.Research r = research();
        List<Component> l = new ArrayList<>();
        Fluid f = shownFluid();
        l.add(f == Fluids.EMPTY ? Component.translatable("gui.wmd.label.research_fluid") : Component.translatable(f.getFluidType().getDescriptionId()));
        for (int i = 0; i < 3; i++) {
            if (r == null || i >= r.samples().size()) l.add(Component.translatable("gui.wmd.label.research_unused"));
            else l.add(ResearchRecipes.name(r.samples().get(i)));
        }
        l.add(Component.translatable("gui.wmd.label.chip_blank"));
        l.add(Component.translatable("gui.wmd.label.chip"));
        return l;
    }

    private Fluid shownFluid() {
        if (!fluid.isEmpty()) return fluid.getFluid().getFluid();
        return ResearchRecipes.fluid(research());
    }

    @Override
    public boolean guiSlotOutput(int i) {
        int k = i - firstSlotTank();
        if (k >= 0 && k < samples.length) { ResearchRecipes.Research r = research(); if (r == null || k >= r.samples().size()) return true; }
        return super.guiSlotOutput(i);
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }

    @Override protected List<Fluid> tankFluids() { return List.of(shownFluid(), Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("research_fluid", "research_sample", "research_sample", "research_sample", "chip_blank", "chip"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 5; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", fe() * 20), Component.translatable("gui.wmd.info.research"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.RESEARCH_RUN.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return 0.3F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x5245_0000_0000_0000L;
        double[] slot = worldPoint(0.875, 0.894, 0.0), lens = worldPoint(-0.19, 0.90, 0.06), leds = worldPoint(0.875, 0.83, -0.42);
        VeilBridge.machineGlow(key, slot[0], slot[1], slot[2], 0.35F, 0.6F, 1.0F, 2.0F, 0.9F + 0.15F * (float) Math.sin(t * 0.15), t);
        VeilBridge.machineGlow(key + 1, lens[0], lens[1], lens[2], 0.6F, 0.8F, 1.0F, 1.5F, 0.7F, t);
        VeilBridge.machineGlow(key + 2, leds[0], leds[1], leds[2], 0.35F, 0.6F, 1.0F, 1.0F, 0.5F, t);
        if (t % 12 != 0) return;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, slot[0], slot[1] + 0.05, slot[2], 0, 0.02, 0);
    }

    @Override
    protected String detail() {
        ResearchRecipes.Research r = research();
        return "research " + (r == null ? "none" : r.id()) + ", fluid " + mb(fluid) + ", blank chips " + blank.count() + ", chips " + chip.count();
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 0, Direction.WEST) ? fluidIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, 0, 0, -1, Direction.NORTH)) return hatchIn;
        if (at(part, side, 1, 0, 0, Direction.EAST)) return chipOut;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? store : null; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putString("research", research);
        tag.putString("runId", runId);
        tag.putInt("progress", progress);
        tag.putInt("resEnergy", store.getEnergyStored());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        research = tag.getString("research");
        runId = tag.getString("runId");
        progress = tag.getInt("progress");
        if (tag.contains("resEnergy")) store.set(tag.getInt("resEnergy"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("research", research);
        tag.putString("runId", runId);
        tag.putInt("progress", progress);
        return tag;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).item(0, 0, -1, Direction.NORTH, false).fluid(-1, 0, 0, Direction.WEST, "brine", false)
            .item(1, 0, 0, Direction.EAST, true).power(0, 0, 1, Direction.SOUTH, false);
    private static final PortMap SCREEN_MAP_BRINE = shifted("brine"), SCREEN_MAP_NAPHTHA = shifted("naphtha");

    private static PortMap shifted(String fluidId) {
        PortMap m = new PortMap(PORT_MAP.minX, PORT_MAP.maxX, PORT_MAP.depth).fromZ(PORT_MAP.minZ);
        for (PortMap.Port p : PORT_MAP.ports) m.ports.add(new PortMap.Port(p.x(), p.y(), p.z() + 1, p.side(), p.kind(), p.out(), p.kind() == PortMap.FLUID ? fluidId : p.fluidId()));
        return m;
    }

    @Override
    public PortMap guiPortMap() {
        ResearchRecipes.Research r = research();
        return r != null && "wmd:naphtha".equals(r.fluidId()) ? SCREEN_MAP_NAPHTHA : SCREEN_MAP_BRINE;
    }
}
