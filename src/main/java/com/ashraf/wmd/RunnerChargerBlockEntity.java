package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class RunnerChargerBlockEntity extends RobotDockBlockEntity {

    public RunnerChargerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.RUNNER_CHARGER_BE.get(), pos, state, Balance.CHARGER_BUFFER, Balance.CHARGER_RECEIVE, Balance.CHARGER_CHARGE);
    }

    @Override protected BlockPos socketCell() { return BlockPos.ZERO; }
    @Override protected Direction socketFace() { return Direction.SOUTH; }
}
