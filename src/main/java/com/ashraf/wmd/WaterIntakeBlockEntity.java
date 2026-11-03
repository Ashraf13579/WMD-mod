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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class WaterIntakeBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank water = new FluidTank(Balance.INTAKE_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final Slot impeller = new Slot(4, false, s -> s.is(wmd("bronze_impeller")) || s.is(wmd("titanium_impeller")));
    private final Slot[] slots = { impeller };

    private final FluidTank heavy = new FluidTank(Balance.HEAVY_WATER_TANK_MB, fs -> fs.getFluid() == WmdFluids.HEAVY_WATER.source().get());
    private final List<FluidTank> tanks = List.of(water, heavy, impeller.gauge);
    private final IFluidHandler out = drainOnly(water), outHeavy = drainOnly(heavy);

    private int enrichAcc;

    private int hwClient;
    private final IItemHandler in = inHandler(impeller);

    private int life, tier;
    private int sources = -1, sourceTimer;

    private int depth;
    public static final int REACH = 3;

    private long lastRun = -1000;
    private float extendShown;
    private long extendTime;

    protected static final int NO_WATER = 6, NO_IMPELLER = 7;

    public WaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.WATER_INTAKE_BE.get(), pos, state, Balance.INTAKE_FE_PER_TICK);
    }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.HEAVY_WATER || super.acceptsUpgrade(t); }

    @Override
    protected void upgradesChanged() { if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    private int hwLevel() { return level != null && level.isClientSide ? hwClient : upgradeLevel(UpgradeItem.Type.HEAVY_WATER); }

    private boolean enrich() {
        int lvl = upgradeLevel(UpgradeItem.Type.HEAVY_WATER);
        if (lvl <= 0) return false;
        int room = heavy.getCapacity() - heavy.getFluidAmount();
        int feed = Math.min(Math.min(lvl * Balance.HEAVY_WATER_FEED_PER_LEVEL, water.getFluidAmount()), room * Balance.HEAVY_WATER_RATIO - enrichAcc);
        if (feed <= 0) return false;
        if (!useExtraEnergy(lvl * Balance.HEAVY_WATER_FE_PER_LEVEL)) return false;
        water.drain(feed, IFluidHandler.FluidAction.EXECUTE);
        enrichAcc += feed;
        int made = enrichAcc / Balance.HEAVY_WATER_RATIO;
        if (made > 0) {
            heavy.fill(new FluidStack(WmdFluids.HEAVY_WATER.source().get(), made), IFluidHandler.FluidAction.EXECUTE);
            enrichAcc -= made * Balance.HEAVY_WATER_RATIO;
        }
        return true;
    }

    @Override
    protected boolean process() {
        boolean enriched = enrich();
        if (life <= 0) {
            if (impeller.count() < 1) return enriched | fail(NO_IMPELLER);
            tier = impeller.stack.is(wmd("titanium_impeller")) ? 2 : 1;
            impeller.take(1);
            life = tier == 2 ? Balance.INTAKE_LIFE_TITANIUM : Balance.INTAKE_LIFE_BRONZE;
            setChanged();
        }
        if (--sourceTimer <= 0) { int d = findWater(); if (d != depth) { depth = d; setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); } sources = depth > 0 ? countSources(depth) : 0; sourceTimer = 40; }
        if (depth <= 0 || sources <= 0) return enriched | fail(NO_WATER);
        int rate = tier == 2 ? Balance.INTAKE_MB_TITANIUM : Balance.INTAKE_MB_BRONZE;
        if (sources < Balance.INTAKE_MIN_SOURCES) rate = Math.max(1, rate / 10);
        int room = water.getCapacity() - water.getFluidAmount();
        if (room <= 0) return enriched | fail(OUT_FULL);
        int got = Math.min(rate, room);
        water.fill(new FluidStack(Fluids.WATER, got), IFluidHandler.FluidAction.EXECUTE);
        flowAcc += got;
        life--;
        if (life % 200 == 0) setChanged();
        return true;
    }

    private int findWater() {
        if (level == null) return 0;
        Direction f = facing();
        for (int d = 1; d <= REACH; d++) {
            FluidState fs = level.getFluidState(Machines.toWorld(worldPosition, f, -1, -d, 2));
            if (fs.is(Fluids.WATER)) return d;
        }
        return 0;
    }

    private int countSources(int d) {
        if (level == null) return 0;
        int n = 0;
        Direction f = facing();
        for (int x = -3; x <= 1; x++) for (int z = 0; z <= 4; z++) for (int y = -d - 1; y <= -d; y++) {
            FluidState fs = level.getFluidState(Machines.toWorld(worldPosition, f, x, y, z));
            if (fs.isSource() && fs.is(Fluids.WATER)) n++;
        }
        return n;
    }

    public int depth() { return depth; }

    public float extend01() {
        if (level == null) return 0.0F;
        long now = level.getGameTime();
        if (running) lastRun = now;
        float target = now - lastRun < 60 ? 1.0F : 0.0F;
        long t = System.currentTimeMillis();
        float dt = Math.min(0.1F, (t - extendTime) / 1000.0F);
        extendTime = t;
        extendShown += (target - extendShown) * Math.min(1.0F, dt * 2.2F);
        return extendShown;
    }

    public float reachPx() { return depth > 0 ? (depth - 1) * 16.0F + 6.0F : 52.0F; }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag t = super.getUpdateTag(registries);
        t.putInt("depth", depth);
        t.putInt("hw", upgradeLevel(UpgradeItem.Type.HEAVY_WATER));
        return t;
    }

    public int lifeFraction1000() { int max = tier == 2 ? Balance.INTAKE_LIFE_TITANIUM : Balance.INTAKE_LIFE_BRONZE; return life <= 0 ? 0 : Math.min(1000, life * 1000 / max); }

    @Override protected String[] iconIds() { return new String[] { "bronze_impeller" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return life <= 0 ? -1.0F : lifeFraction1000() / 1000.0F; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, WmdFluids.HEAVY_WATER.source().get(), Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("water", "heavy_water", "impeller"); }
    @Override protected int rateMbPerTick() { return tier == 2 ? Balance.INTAKE_MB_TITANIUM : Balance.INTAKE_MB_BRONZE; }
    @Override protected int inputTanks() { return 0; }
    @Override public String guiTankUnit(int t) { return t < 2 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.INTAKE_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.intake", Balance.INTAKE_MB_BRONZE * 20, Balance.INTAKE_MB_TITANIUM * 20, Balance.INTAKE_MIN_SOURCES),
                Component.translatable("gui.wmd.info.intake_life", Balance.INTAKE_LIFE_BRONZE / 1200, Balance.INTAKE_LIFE_TITANIUM / 1200),
                Component.translatable("gui.wmd.info.heavy_water", Balance.HEAVY_WATER_FEED_PER_LEVEL * 20, Balance.HEAVY_WATER_RATIO, Balance.HEAVY_WATER_FE_PER_LEVEL * 20, Balance.UPGRADE_MAX_LEVEL));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.WI_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected float soundVolume() { return 0.6F; }

    @Override
    protected void clientEffects(Level level) {
        if (level.getGameTime() % 4 != 0) return;
        var r = level.random;
        double[] w = worldPoint(-0.56, 0.9, 1.55);
        level.addParticle(ParticleTypes.SPLASH, w[0] + (r.nextDouble() - 0.5) * 0.6, w[1], w[2] + (r.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, w[0] + (r.nextDouble() - 0.5) * 0.6, w[1] + 0.1, w[2] + (r.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
    }

    @Override
    protected String detail() { return "water " + mb(water) + (hwLevel() > 0 ? ", heavy water " + mb(heavy) + " (upgrade level " + hwLevel() + ")" : "") + ", water " + (depth > 0 ? depth + " down" : "none within " + REACH) + ", impeller " + (life <= 0 ? "none" : (tier == 2 ? "titanium " : "bronze ") + lifeFraction1000() / 10 + "%") + ", sources " + Math.max(0, sources); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 0, Direction.WEST) ? (hwLevel() > 0 ? outHeavy : out) : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, -1, 1, 0, Direction.NORTH) ? in : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 0, Direction.EAST) ? energy() : null; }

    @Override
    protected void saveExtra(CompoundTag tag) { tag.putInt("life", life); tag.putInt("tier", tier); tag.putInt("depth", depth); tag.putInt("enrich", enrichAcc); }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("life")) { life = tag.getInt("life"); tier = tag.getInt("tier"); }
        if (tag.contains("depth")) depth = tag.getInt("depth");
        if (tag.contains("enrich")) enrichAcc = tag.getInt("enrich");
        if (tag.contains("hw")) hwClient = tag.getInt("hw");

        if (!heavy.isEmpty() && heavy.getFluid().getFluid() != WmdFluids.HEAVY_WATER.source().get()) heavy.setFluid(FluidStack.EMPTY);
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 2).fluid(-1, 0, 0, Direction.WEST, "water", true).item(-1, 1, 0, Direction.NORTH, false).power(1, 0, 0, Direction.EAST, false);
    private static final PortMap PORT_MAP_HEAVY = new PortMap(-1, 1, 2).fluid(-1, 0, 0, Direction.WEST, "heavy_water", true).item(-1, 1, 0, Direction.NORTH, false).power(1, 0, 0, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return hwLevel() > 0 ? PORT_MAP_HEAVY : PORT_MAP; }

    @Override
    public List<ItemStack> guiSlotIcons() {
        List<ItemStack> l = new java.util.ArrayList<>(super.guiSlotIcons());
        if (tier == 2 && !l.isEmpty()) l.set(l.size() - 1, new ItemStack(wmd("titanium_impeller")));
        return l;
    }
}
