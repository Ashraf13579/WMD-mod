package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CoolingTowerBlockEntity extends ProcessUnitBlockEntity {

    public static final int X0 = -5, X1 = 5, Y0 = 0, Y1 = 15, Z0 = 0, Z1 = 10;
    private static final PortMap PORT_MAP = new PortMap(-5, 5, 11)
            .fluid(-3, 0, 10, Direction.SOUTH, "spent_steam", false).fluid(-2, 0, 10, Direction.SOUTH, "spent_steam", false)
            .fluid(2, 0, 10, Direction.SOUTH, "water", true).fluid(3, 0, 10, Direction.SOUTH, "water", true)
            .power(0, 0, 10, Direction.SOUTH, false);

    private static final double[] MOUTH = { 0.0, Y1 + 1.0, (Z0 + Z1) / 2.0 };
    private static final double MOUTH_R = 3.5;

    private static final double[] LAMP = { 0.75, 0.75, 10.55 };

    private final FluidTank in = new FluidTank(Balance.TOWER_TANK_MB,
            fs -> fs.getFluid() == WmdFluids.SPENT_STEAM.source().get() || fs.getFluid() == WmdFluids.STEAM.source().get());
    private final FluidTank water = new FluidTank(Balance.TOWER_TANK_MB, fs -> fs.getFluid() == Fluids.WATER);
    private final List<FluidTank> tanks = List.of(in, water);
    private final IFluidHandler steamIn = fillOnly(in), waterOut = drainOnly(water);

    private int waterFrac;

    public CoolingTowerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.COOLING_TOWER_BE.get(), pos, state, Balance.TOWER_FE_PER_TICK);
    }

    @Override
    protected boolean work() {
        reason = 0;
        int take = Math.min(Balance.TOWER_CONDENSE_MB, in.getFluidAmount());
        if (take <= 0) { reason = 4; return false; }

        int room = water.getCapacity() - water.getFluidAmount();
        int maxTake = (int) ((room * 100L + 99 - waterFrac) / Balance.TOWER_WATER_PCT);
        take = Math.min(take, Math.max(0, maxTake));
        if (take <= 0) { reason = 3; return false; }
        int hundredths = take * Balance.TOWER_WATER_PCT + waterFrac;
        int made = Math.min(room, hundredths / 100);
        waterFrac = hundredths - made * 100;
        if (waterFrac > 99) waterFrac = 99;
        in.drain(take, IFluidHandler.FluidAction.EXECUTE);
        if (made > 0) water.fill(new FluidStack(Fluids.WATER, made), IFluidHandler.FluidAction.EXECUTE);
        flowAcc += take;
        if (level.getGameTime() % 100 == 0) setChanged();
        return true;
    }

    private boolean legacyChecked;

    public static void serverTickTower(Level level, BlockPos pos, BlockState state, CoolingTowerBlockEntity be) {
        if (!be.legacyChecked) {
            BlockPos a = be.cell(X1, Y1, Z1), b = be.cell(X0, Y1, Z0);
            if (!level.isLoaded(a) || !level.isLoaded(b)) return;
            be.legacyChecked = true;
            if (!be.owns(level, a) && !be.owns(level, b)) {
                level.destroyBlock(pos, true);
                return;
            }
        }
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
    }

    private BlockPos cell(int x, int y, int z) { return Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), x, y, z); }

    private boolean owns(Level level, BlockPos p) {
        return level.getBlockEntity(p) instanceof MachinePartBlockEntity part && worldPosition.equals(part.getController());
    }

    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.SPENT_STEAM.source().get(), Fluids.WATER); }
    @Override protected List<String> labelKeys() { return List.of("spent_steam", "water"); }
    @Override protected int rateMbPerTick() { return Balance.TOWER_CONDENSE_MB; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.TOWER_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.tower_condense", Balance.TOWER_CONDENSE_MB, Balance.TOWER_CONDENSE_MB * Balance.TOWER_WATER_PCT / 100),
                Component.translatable("gui.wmd.info.tower_dump"));
    }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected String detail() { return "steam in " + mb(in) + ", water " + mb(water); }

    @Override protected SoundEvent runSound() { return WmdSounds.CT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 9); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected float levelFraction() { return water.getFluidAmount() / (float) Balance.TOWER_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        long t = level.getGameTime();
        double[] lamp = worldPoint(LAMP[0], LAMP[1], LAMP[2]);
        VeilBridge.machineGlow(getBlockPos().asLong() ^ 0x4354_0000_0000_0000L, lamp[0], lamp[1], lamp[2], 0.35F, 0.6F, 1.0F, 0.8F, 0.5F, t);
        float f = 0.4F + 0.6F * flowFraction();
        double a = r.nextDouble() * Math.PI * 2.0, d = Math.sqrt(r.nextDouble()) * MOUTH_R;
        double[] w = worldPoint(MOUTH[0] + Math.cos(a) * d, MOUTH[1], MOUTH[2] + Math.sin(a) * d);

        if (t % 2 == 0 && r.nextFloat() < f)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, w[0], w[1], w[2], (r.nextDouble() - 0.5) * 0.02, 0.12 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.02);

        if (r.nextFloat() < f)
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1] + 0.2, w[2], (r.nextDouble() - 0.5) * 0.06, 0.22 + r.nextDouble() * 0.12, (r.nextDouble() - 0.5) * 0.06);
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -3, 0, 10, Direction.SOUTH) || at(part, side, -2, 0, 10, Direction.SOUTH)) return steamIn;
        if (at(part, side, 2, 0, 10, Direction.SOUTH) || at(part, side, 3, 0, 10, Direction.SOUTH)) return waterOut;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 10, Direction.SOUTH) ? energy() : null; }

    private boolean testPowered;

    void setTestPowered() { testPowered = true; setChanged(); }

    void testFill() { water.fill(new FluidStack(Fluids.WATER, water.getCapacity()), IFluidHandler.FluidAction.EXECUTE); setChanged(); }

    @Override public boolean hasPower() { return testPowered || super.hasPower(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (testPowered) tag.putBoolean("testPowered", true);
        tag.putInt("waterFrac", waterFrac);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        testPowered = tag.getBoolean("testPowered");
        waterFrac = Math.max(0, Math.min(99, tag.getInt("waterFrac")));
    }
}
