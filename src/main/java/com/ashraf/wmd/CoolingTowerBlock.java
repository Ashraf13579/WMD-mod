package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CoolingTowerBlock extends UnitBlock {

    public static final MapCodec<CoolingTowerBlock> CODEC = simpleCodec(CoolingTowerBlock::new);

    public CoolingTowerBlock(Properties properties) {
        super(properties, CoolingTowerBlockEntity.X0, CoolingTowerBlockEntity.X1, CoolingTowerBlockEntity.Y0, CoolingTowerBlockEntity.Y1,
                CoolingTowerBlockEntity.Z0, CoolingTowerBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CoolingTowerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COOLING_TOWER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.COOLING_TOWER_BE.get(), CoolingTowerBlockEntity::serverTickTower);
    }
}
