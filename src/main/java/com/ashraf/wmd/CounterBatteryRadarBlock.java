package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CounterBatteryRadarBlock extends UnitBlock {

    public static final MapCodec<CounterBatteryRadarBlock> CODEC = simpleCodec(CounterBatteryRadarBlock::new);

    public CounterBatteryRadarBlock(Properties properties) {
        super(properties, CounterBatteryRadarBlockEntity.X0, CounterBatteryRadarBlockEntity.X1, CounterBatteryRadarBlockEntity.Y0, CounterBatteryRadarBlockEntity.Y1,
                CounterBatteryRadarBlockEntity.Z0, CounterBatteryRadarBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CounterBatteryRadarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COUNTER_BATTERY_RADAR_BE.get(), CounterBatteryRadarBlockEntity::clientTickRadar)
                : createTickerHelper(type, Wmd.COUNTER_BATTERY_RADAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
