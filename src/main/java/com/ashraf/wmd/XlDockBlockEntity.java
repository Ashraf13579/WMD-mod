package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class XlDockBlockEntity extends RobotDockBlockEntity {

    public XlDockBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.XL_DOCK_BE.get(), pos, state, Balance.XL_DOCK_BUFFER, Balance.XL_DOCK_RECEIVE, Balance.XL_DOCK_CHARGE);
    }

    @Override protected BlockPos socketCell() { return new BlockPos(2, 0, 16); }
}
