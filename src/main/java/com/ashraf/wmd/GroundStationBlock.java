package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GroundStationBlock extends UnitBlock {

    public static final MapCodec<GroundStationBlock> CODEC = simpleCodec(GroundStationBlock::new);

    public GroundStationBlock(Properties properties) {
        super(properties, GroundStationBlockEntity.X0, GroundStationBlockEntity.X1, GroundStationBlockEntity.Y0, GroundStationBlockEntity.Y1,
                GroundStationBlockEntity.Z0, GroundStationBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GroundStationBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.GROUND_STATION_BE.get(), GroundStationBlockEntity::clientTickStation)
                : createTickerHelper(type, Wmd.GROUND_STATION_BE.get(), GroundStationBlockEntity::serverTickStation);
    }
}
