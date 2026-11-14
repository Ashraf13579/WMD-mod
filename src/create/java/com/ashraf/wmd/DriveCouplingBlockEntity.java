package com.ashraf.wmd;

import java.util.List;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class DriveCouplingBlockEntity extends GeneratingKineticBlockEntity {

    private int buffer;
    private int rpm = Balance.DC_RPM_DEFAULT;
    private boolean reverse;
    private long winFe;
    private int winTicks;
    private int avgFe;
    private boolean drive;
    private int demand;

    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) {
            int t = Math.max(0, Math.min(n, Balance.DC_BUFFER_FE - buffer));
            if (t > 0 && !sim) { buffer += t; setChanged(); }
            return t;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return buffer; }
        @Override public int getMaxEnergyStored() { return Balance.DC_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public DriveCouplingBlockEntity(BlockPos pos, BlockState state) { super(CreateCompat.DRIVE_COUPLING_BE.get(), pos, state); }

    IEnergyStorage energy() { return energy; }
    Direction facing() { return getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING); }
    int rpm() { return rpm; }
    boolean reversed() { return reverse; }
    boolean running() { return avgFe > 0 || (drive && demand > 0 && getSpeed() != 0.0F); }

    void nextRpm() {
        int i = 0;
        while (i < Balance.DC_RPMS.length && Balance.DC_RPMS[i] != rpm) i++;
        rpm = Balance.DC_RPMS[(i + 1) % Balance.DC_RPMS.length];
        changed();
    }

    void toggleReverse() { reverse = !reverse; changed(); }

    private void changed() {
        updateGeneratedRotation();
        setChanged();
        sendData();
    }

    private DriveEngine engine() {
        if (level == null) return null;
        Direction back = facing().getOpposite();
        BlockPos at = worldPosition.relative(back);
        BlockPos ctrl = level.getBlockEntity(at) instanceof MachinePartBlockEntity part ? part.validController() : at;
        return ctrl != null && level.getBlockEntity(ctrl) instanceof DriveEngine e && e.driveFlange(at, back.getOpposite()) ? e : null;
    }

    boolean driving() { return drive; }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (level.getGameTime() % 10 == (worldPosition.asLong() & 7)) {
            DriveEngine e = engine();
            int d = e == null ? 0 : e.driveDemand();
            if ((e != null) != drive) { drive = e != null; demand = d; avgFe = 0; buffer = 0; changed(); }
            else if (drive && Math.abs(d - demand) > demand * 0.05 + 1) {
                demand = d;
                if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
                setChanged();
                sendData();
            }
        }
        if (drive) {
            DriveEngine e = demand > 0 && getSpeed() != 0.0F && !isOverStressed() ? engine() : null;
            if (e != null) e.driveIn(demand, false);
            return;
        }

        if (buffer < Balance.DC_BUFFER_FE) {
            Direction back = facing().getOpposite();
            IEnergyStorage src = level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(back), back.getOpposite());
            if (src != null && src.canExtract()) buffer += src.extractEnergy(Math.min(Balance.DC_MAX_FE_PER_TICK, Balance.DC_BUFFER_FE - buffer), false);
        }
        int use = Math.min(buffer, Balance.DC_MAX_FE_PER_TICK);
        buffer -= use;
        winFe += use;
        if (++winTicks >= Balance.DC_WINDOW_TICKS) {
            int avg = (int) (winFe / winTicks);
            winFe = 0;
            winTicks = 0;
            int now = avg >= Balance.DC_MIN_FE_PER_TICK ? avg : 0;
            if ((now > 0) != (avgFe > 0) || Math.abs(now - avgFe) > avgFe * 0.05 + 1) {
                avgFe = now;
                changed();
            }
        }
    }

    @Override
    public float getGeneratedSpeed() { return !drive && avgFe > 0 ? (reverse ? -rpm : rpm) : 0.0F; }

    @Override
    public float calculateStressApplied() {
        if (!drive) return super.calculateStressApplied();
        float sp = Math.max(1.0F, Math.abs(getTheoreticalSpeed()));
        float s = demand * (float) Balance.SCREW_SU_PER_FE / sp;
        lastStressApplied = s;
        return s;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float c = !drive && avgFe > 0 && rpm > 0 ? avgFe * Balance.DC_SU_PER_FE / rpm : 0.0F;
        lastCapacityProvided = c;
        return c;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        if (drive) tooltip.add(Component.literal("    ").append(Component.translatable("goggle.wmd.drive_coupling.drive", demand, Math.round(demand * Balance.SCREW_SU_PER_FE))
                .withStyle(demand > 0 && !isOverStressed() ? ChatFormatting.AQUA : ChatFormatting.GRAY)));
        else tooltip.add(Component.literal("    ").append(Component.translatable("goggle.wmd.drive_coupling.power", avgFe, Balance.DC_MAX_FE_PER_TICK)
                .withStyle(avgFe > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("dcBuf", buffer);
        tag.putInt("dcRpm", rpm);
        tag.putBoolean("dcRev", reverse);
        tag.putInt("dcAvg", avgFe);
        tag.putBoolean("dcDrive", drive);
        tag.putInt("dcDemand", demand);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        buffer = Mth.clamp(tag.getInt("dcBuf"), 0, Balance.DC_BUFFER_FE);
        int r = tag.getInt("dcRpm");
        rpm = r > 0 ? r : Balance.DC_RPM_DEFAULT;
        reverse = tag.getBoolean("dcRev");
        avgFe = Math.max(0, tag.getInt("dcAvg"));
        drive = tag.getBoolean("dcDrive");
        demand = Math.max(0, tag.getInt("dcDemand"));
    }
}
