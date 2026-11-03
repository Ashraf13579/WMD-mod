package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SteamTurbineBlockEntity extends ProcessUnitBlockEntity {

    public static final int X0 = -4, X1 = 4, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 3;

    private final FluidTank steam = new FluidTank(Balance.STEAM_TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.STEAM.source().get());
    private final FluidTank spent = new FluidTank(Balance.STEAM_TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.SPENT_STEAM.source().get());
    private final List<FluidTank> tanks = List.of(steam, spent);
    private final IFluidHandler steamIn = fillOnly(steam), spentOut = drainOnly(spent);
    private final GenBuffer out = new GenBuffer(Balance.STEAM_TURBINE_BUFFER_FE, Balance.STEAM_TURBINE_OUT_PER_TICK, this::setChanged);
    private int feNow;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.STEAM_TURBINE_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean work() {
        reason = 0;
        feNow = 0;
        int use = Math.min(Balance.STEAM_TURBINE_MB, steam.getFluidAmount());
        if (use <= 0) { reason = 4; return false; }
        use = Math.min(use, spent.getCapacity() - spent.getFluidAmount());
        use = Math.min(use, (out.getMaxEnergyStored() - out.stored) / Balance.STEAM_TURBINE_FE_PER_MB);
        if (use <= 0) { reason = 3; return false; }
        steam.drain(use, IFluidHandler.FluidAction.EXECUTE);
        spent.fill(new FluidStack(WmdFluids.SPENT_STEAM.source().get(), use), IFluidHandler.FluidAction.EXECUTE);
        feNow = use * Balance.STEAM_TURBINE_FE_PER_MB;
        out.stored += feNow;
        flowAcc += use;
        if (level.getGameTime() % 100 == 0) setChanged();
        return true;
    }

    private float shaft, shaftSpeed;

    public static void clientTickTurbine(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        float target = be.isRunning() ? 0.25F + 0.9F * be.flowFraction() : 0.0F;
        be.shaftSpeed += Mth.clamp(target - be.shaftSpeed, -0.006F, 0.012F);
        if (be.shaftSpeed < 0.0005F && target == 0.0F) be.shaftSpeed = 0.0F;
        be.shaft = (be.shaft + be.shaftSpeed) % (Mth.TWO_PI * 64);
    }

    @Override
    public float spinAngle(float partialTick) { return shaft + shaftSpeed * partialTick; }

    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.STEAM.source().get(), WmdFluids.SPENT_STEAM.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("steam", "spent_steam"); }
    @Override protected int rateMbPerTick() { return Balance.STEAM_TURBINE_MB; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    public int guiStatus() {
        if (running) return 1;
        return reason;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.steam_turbine", Balance.STEAM_TURBINE_MB, String.format("%,d", Balance.STEAM_TURBINE_MB * Balance.STEAM_TURBINE_FE_PER_MB * 20)),
                Component.translatable("gui.wmd.info.steam_turbine_out"));
    }

    @Override
    protected String detail() { return "steam " + mb(steam) + ", spent steam " + mb(spent) + ", making " + feNow + " FE/t, stored " + out.stored + " FE"; }

    @Override protected SoundEvent runSound() { return WmdSounds.STT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 2); }
    @Override protected float soundVolume() { return 0.6F; }
    @Override protected float levelFraction() { return steam.getFluidAmount() / (float) Balance.STEAM_TURBINE_TANK_MB; }

    @Override protected List<double[]> vents() { return List.of(new double[] { -3.6, 1.6, 1.5 }, new double[] { 1.6, 1.6, 1.5 }); }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();
        long key = getBlockPos().asLong() ^ 0x5354_0000_0000_0000L;
        double[] lamp = worldPoint(0.21, 0.75, -0.55), pit = worldPoint(-0.25, 1.4, 1.0);
        VeilBridge.machineGlow(key, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.45F, 0.8F, 0.5F, t);
        VeilBridge.machineGlow(key + 1, pit[0], pit[1], pit[2], 1.0F, 0.75F, 0.45F, 2.5F, 0.7F, t);
        if (t % 40 == 0) {
            var r = level.random;
            double[] w = worldPoint(-4.6, 0.5, 1.0 + r.nextDouble());
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], 0, 0.02, 0);
        }
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -4, 0, 1, Direction.WEST) || at(part, side, -4, 0, 2, Direction.WEST)) return steamIn;
        if (at(part, side, 4, 0, 1, Direction.EAST) || at(part, side, 4, 0, 2, Direction.EAST)) return spentOut;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 3, Direction.SOUTH) ? out : null; }

    private static final PortMap PORT_MAP = new PortMap(-4, 4, 4)
            .fluid(-4, 0, 1, Direction.WEST, "steam", false).fluid(-4, 0, 2, Direction.WEST, "steam", false)
            .fluid(4, 0, 1, Direction.EAST, "spent_steam", true).fluid(4, 0, 2, Direction.EAST, "spent_steam", true)
            .power(0, 0, 3, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        out.stored = Mth.clamp(tag.getInt("stored"), 0, out.getMaxEnergyStored());
    }
}
