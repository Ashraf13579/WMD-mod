package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ReactorPortBlock extends ReactorPartBlock implements EntityBlock {

    public ReactorPortBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ReactorPortBlockEntity(pos, state); }
}
