package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FlareStackBlock extends UnitBlock {

    public static final MapCodec<FlareStackBlock> CODEC = simpleCodec(FlareStackBlock::new);

    public FlareStackBlock(Properties properties) {
        super(properties, -1, 1, 0, 1, 0, 2);
        for (int y = 2; y <= 11; y++) footprint().add(new BlockPos(0, y, 1));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FlareStackBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.FLARE_STACK_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.FLARE_STACK_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
