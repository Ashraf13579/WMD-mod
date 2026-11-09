package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class HydrophoneArrayBlockEntity extends SonarSensorBlockEntity {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 2, Z0 = -1, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);

    public HydrophoneArrayBlockEntity(BlockPos pos, BlockState state) { super(Wmd.HYDROPHONE_ARRAY_BE.get(), pos, state, Balance.SONAR_ARRAY_FE); }

    @Override protected double range() { return deep() ? Balance.SONAR_ARRAY_RANGE_UP : Balance.SONAR_ARRAY_RANGE; }
    @Override protected boolean takesDeepUpgrade() { return true; }
    @Override protected int kind() { return SonarNet.ARRAY; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    public boolean hearing() { return isRunning() && wet && !heard.isEmpty(); }
}
