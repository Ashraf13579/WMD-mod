package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class WindTurbineBlockEntity extends ProcessUnitBlockEntity {

    static final double HUB_UP = 234.0 / 16.0;

    private int stored;
    private int fePerTickNow;
    private float clear = 1.0F;
    private int syncedInfo = -1;
    private boolean scanned;

    private float rotor, rotorSpeed, cups, yawShown = Float.NaN;

    private final IEnergyStorage out = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, stored), Balance.WIND_OUT_PER_TICK);
            if (t > 0 && !sim) { stored -= t; setChanged(); }
            return t;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.WIND_BUFFER_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) { super(Wmd.WIND_TURBINE_BE.get(), pos, state, 0); }

    static double windDeg(double t) { return 360.0 * ((t / Balance.WIND_TURN_TICKS) % 1.0) + 35.0 * Math.sin(t / 7000.0); }

    static double gust(double t) { return 1.0 + Balance.WIND_GUST * (0.6 * Math.sin(t / 311.0) + 0.4 * Math.sin(t / 97.0 + 1.3)); }

    int heightFe() {
        double k = Mth.clamp((worldPosition.getY() + HUB_UP - Balance.WIND_Y_LOW) / (double) (Balance.WIND_Y_HIGH - Balance.WIND_Y_LOW), 0.0, 1.0);
        return (int) Math.round(Balance.WIND_FE_LOW + (Balance.WIND_FE_HIGH - Balance.WIND_FE_LOW) * k);
    }

    private double weather() {
        if (level == null || !level.isRaining()) return 1.0;
        return level.isThundering() ? Balance.WIND_THUNDER : Balance.WIND_RAIN;
    }

    private void scanClear() {
        BlockPos hub = BlockPos.containing(worldPosition.getX() + 0.5, worldPosition.getY() + HUB_UP, worldPosition.getZ() + 0.5);
        int r = Balance.WIND_CLEAR_R, solid = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > r * r) continue;
            p.set(hub.getX() + dx, hub.getY() + dy, hub.getZ() + dz);
            int ly = p.getY() - worldPosition.getY();
            if (Math.abs(p.getX() - worldPosition.getX()) <= 1 && Math.abs(p.getZ() - worldPosition.getZ()) <= 1 && ly >= 0 && ly <= 15) continue;
            if (!level.isLoaded(p)) continue;
            if (!level.getBlockState(p).isAir()) solid++;
        }
        clear = (float) Math.max(0.0, 1.0 - Balance.WIND_BLOCK_PER * solid);
    }

    private int makeNow() {
        if (level == null || level.dimensionType().hasCeiling()) return 0;
        return (int) Math.round(heightFe() * gust(level.getGameTime()) * weather() * clear);
    }

    @Override
    public void empDrain() { stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (level == null) return false;
        if (!scanned || (level.getGameTime() + (worldPosition.asLong() & 63)) % Balance.WIND_SCAN_TICKS == 0) { scanClear(); scanned = true; }
        fePerTickNow = makeNow();
        if (level.getGameTime() % 20 == 0) {
            int i = info();
            if (syncedInfo < 0 || (i >> 10) != (syncedInfo >> 10) || Math.abs((i & 1023) - (syncedInfo & 1023)) >= 3) {
                syncedInfo = i;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        if (fePerTickNow <= 0) return false;
        int add = Math.min(fePerTickNow, Balance.WIND_BUFFER_FE - stored);
        if (add <= 0) return false;
        stored += add;
        flowAcc += add;
        return true;
    }

    private int info() { return Math.min(fePerTickNow, 1023) | Math.round(clear * 100.0F) << 10; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return Math.max(1, heightFe()); }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public int guiFeUse() { return 0; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        int hub = (int) Math.floor(worldPosition.getY() + HUB_UP);
        return List.of(Component.translatable("gui.wmd.info.wind_output", fePerTickNow * 20, heightFe() * 20, hub),
                Component.translatable("gui.wmd.info.wind_height", Balance.WIND_FE_LOW * 20, Balance.WIND_Y_LOW, Balance.WIND_FE_HIGH * 20, Balance.WIND_Y_HIGH),
                Component.translatable("gui.wmd.info.wind_weather", Math.round((Balance.WIND_RAIN - 1.0) * 100), Math.round((Balance.WIND_THUNDER - 1.0) * 100)),
                Component.translatable("gui.wmd.info.wind_clear", Math.round(clear * 100.0F), Balance.WIND_CLEAR_R));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.WI_WIND_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 12, 0); }
    @Override protected float soundVolume() { return 0.3F; }

    @Override
    protected String detail() { return String.format("%d FE/t (%d at this height), clear air %d%%, stored %,d FE", fePerTickNow, heightFe(), Math.round(clear * 100.0F), stored); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? out : null; }

    private static final PortMap PORTS = new PortMap(-1, 1, 3).fromZ(-1).power(0, 0, 1, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORTS; }

    void animate(Level level) {
        float target = 0.025F + 0.27F * Mth.clamp(fePerTickNow / (float) Balance.WIND_FE_HIGH, 0.0F, 1.2F);
        if (!isRunning()) target = 0.025F;
        rotorSpeed += (target - rotorSpeed) * 0.02F;
        rotor = (rotor + rotorSpeed) % Mth.TWO_PI;
        cups = (cups + rotorSpeed * 2.5F + 0.02F) % Mth.TWO_PI;
        float want = (float) (windDeg(level.getGameTime()) - facingDeg());
        if (Float.isNaN(yawShown)) yawShown = want;
        yawShown += Mth.wrapDegrees(want - yawShown) * 0.01F;
    }

    private float facingDeg() {
        return switch (facing()) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; };
    }

    public float rotorAngle(float pt) { return rotor + rotorSpeed * pt; }
    public float cupAngle(float pt) { return cups + (rotorSpeed * 2.5F + 0.02F) * pt; }
    public float yawDeg() { return Float.isNaN(yawShown) ? 0.0F : yawShown; }

    @Override
    protected void clientEffects(Level level) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
        tag.putFloat("clear", clear);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stored")) stored = Mth.clamp(tag.getInt("stored"), 0, Balance.WIND_BUFFER_FE);
        if (tag.contains("clear")) clear = tag.getFloat("clear");
        if (tag.contains("windinfo")) {
            int i = tag.getInt("windinfo");
            fePerTickNow = i & 1023;
            clear = ((i >> 10) & 127) / 100.0F;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("windinfo", info());
        return tag;
    }
}
