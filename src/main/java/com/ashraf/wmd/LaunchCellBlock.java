package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LaunchCellBlock extends ItemUnitBlock {

    public static final MapCodec<LaunchCellBlock> CODEC = simpleCodec(LaunchCellBlock::new);

    public LaunchCellBlock(Properties properties) {
        super(properties, LaunchCellBlockEntity.X0, LaunchCellBlockEntity.X1, LaunchCellBlockEntity.Y0, LaunchCellBlockEntity.Y1, LaunchCellBlockEntity.Z0, LaunchCellBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaunchCellBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.LAUNCH_CELL_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.LAUNCH_CELL_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
