package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class SonarDomeBlockEntity extends SonarSensorBlockEntity {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2, PING_FLASH = 30;
    public static final int P_OK = 1, P_COOLDOWN = 2, P_NO_POWER = 3, P_DRY = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.SOUTH, false);

    private long lastPing = -1_000_000L;
    private int lastPingShips;

    public SonarDomeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SONAR_DOME_BE.get(), pos, state, Balance.SONAR_DOME_FE); }

    @Override protected double range() { return deep() ? Balance.SONAR_DOME_RANGE_UP : Balance.SONAR_DOME_RANGE; }
    @Override protected boolean takesDeepUpgrade() { return true; }
    @Override protected int kind() { return SonarNet.DOME; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    public long lastPing() { return lastPing; }
    public int lastPingShips() { return lastPingShips; }

    public long lastSurvey() { return lastSurvey; }

    public int cooldown(long now) { return (int) Math.max(0L, Math.max(lastPing, lastSurvey) + Balance.SONAR_PING_COOLDOWN - now); }

    public boolean flashing(long now) { return now - Math.max(lastPing, lastSurvey) < PING_FLASH; }

    private long lastSurvey = -1_000_000L;

    public int survey(Player by) {
        if (!(level instanceof ServerLevel sl)) return 0;
        long now = sl.getGameTime();
        if (cooldown(now) > 0) return P_COOLDOWN;
        if (!inWater(sl)) return P_DRY;
        if (!isRunning() || !useExtraEnergy(Balance.SURVEY_FE)) return P_NO_POWER;
        Vec3 ear = ear();
        NoduleSurvey.start(sl, getOwner(), frequency, ear, SableBridge.shipIdAt(sl, worldPosition), by == null ? null : by.getUUID());
        lastSurvey = now;
        NoiseNet.pulse(sl, ear, Balance.SONAR_PING_DB, NoiseNet.Kind.PING, getOwner(), Balance.SONAR_PING_TICKS);
        SeaSound.nf(sl, WmdSounds.SON_PING.get(), WmdSounds.SON_PING_FAR.get(), 48.0, ear, 6.0F, 1.25F);
        setChanged();
        sync();
        return P_OK;
    }

    @Override
    public int ping(Player by) {
        if (!(level instanceof ServerLevel sl)) return 0;
        long now = sl.getGameTime();
        if (cooldown(now) > 0) return P_COOLDOWN;
        if (!inWater(sl)) return P_DRY;
        if (!isRunning() || !useExtraEnergy(Balance.SONAR_PING_FE)) return P_NO_POWER;
        Vec3 ear = ear();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        lastPingShips = SonarNet.ping(sl, getOwner(), frequency, ear, ship);
        lastPing = now;
        SeaSound.nf(sl, WmdSounds.SON_PING.get(), WmdSounds.SON_PING_FAR.get(), 48.0, ear, 6.0F, 1.0F);
        Wmd.LOGGER.info("Sonar ping by {} at {}: {} ship(s) within {}", by == null ? getOwnerName() : by.getName().getString(), BlockPos.containing(ear).toShortString(),
                lastPingShips, (int) Balance.SONAR_PING_RANGE);
        setChanged();
        sync();
        return P_OK;
    }

    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }

    @Override
    protected void writeExtra(CompoundTag tag) {
        tag.putLong("sonPing", lastPing);
        tag.putInt("sonPingN", lastPingShips);
        tag.putLong("sonSurvey", lastSurvey);
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        if (tag.contains("sonPing")) lastPing = tag.getLong("sonPing");
        lastPingShips = tag.getInt("sonPingN");
        if (tag.contains("sonSurvey")) lastSurvey = tag.getLong("sonSurvey");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeExtra(tag);
    }

    public List<SonarNet.Fix> fixes() {
        return level instanceof ServerLevel sl ? SonarNet.pingFixes(sl, getOwner(), frequency) : List.of();
    }
}
