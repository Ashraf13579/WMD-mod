package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EarlyWarningArrayBlock extends UnitBlock {

    public static final MapCodec<EarlyWarningArrayBlock> CODEC = simpleCodec(EarlyWarningArrayBlock::new);

    public EarlyWarningArrayBlock(Properties properties) {
        super(properties, EarlyWarningArrayBlockEntity.X0, EarlyWarningArrayBlockEntity.X1, EarlyWarningArrayBlockEntity.Y0, EarlyWarningArrayBlockEntity.Y1,
                EarlyWarningArrayBlockEntity.Z0, EarlyWarningArrayBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EarlyWarningArrayBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.EARLY_WARNING_ARRAY_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.EARLY_WARNING_ARRAY_BE.get(), EarlyWarningArrayBlockEntity::serverTickArray);
    }
}
