package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HydrophoneArrayBlock extends UnitBlock {

    public static final MapCodec<HydrophoneArrayBlock> CODEC = simpleCodec(HydrophoneArrayBlock::new);

    public HydrophoneArrayBlock(Properties properties) {
        super(properties, HydrophoneArrayBlockEntity.X0, HydrophoneArrayBlockEntity.X1, HydrophoneArrayBlockEntity.Y0, HydrophoneArrayBlockEntity.Y1, HydrophoneArrayBlockEntity.Z0, HydrophoneArrayBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HydrophoneArrayBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.HYDROPHONE_ARRAY_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.HYDROPHONE_ARRAY_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
