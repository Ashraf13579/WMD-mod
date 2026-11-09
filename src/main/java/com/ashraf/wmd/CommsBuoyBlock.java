package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CommsBuoyBlock extends UnitBlock {

    public static final MapCodec<CommsBuoyBlock> CODEC = simpleCodec(CommsBuoyBlock::new);

    public CommsBuoyBlock(Properties properties) { super(properties, 0, 1, 0, 1, 0, 1); }

    @Override public boolean hullPlug(net.minecraft.core.BlockPos local) { return local.getY() == 1; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CommsBuoyBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COMMS_BUOY_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.COMMS_BUOY_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
