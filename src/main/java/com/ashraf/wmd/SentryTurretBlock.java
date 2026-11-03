package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SentryTurretBlock extends UnitBlock {

    public static final MapCodec<SentryTurretBlock> CODEC = simpleCodec(SentryTurretBlock::new);

    public SentryTurretBlock(Properties properties) { super(properties, 0, 0, 0, 1, 0, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SentryTurretBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SENTRY_TURRET_BE.get(), SentryTurretBlockEntity::clientTickTurret)
                : createTickerHelper(type, Wmd.SENTRY_TURRET_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
