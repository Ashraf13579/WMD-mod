package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CoastalBatteryBlock extends ItemUnitBlock {

    public static final MapCodec<CoastalBatteryBlock> CODEC = simpleCodec(CoastalBatteryBlock::new);

    public CoastalBatteryBlock(Properties properties) {
        super(properties, CoastalBatteryBlockEntity.X0, CoastalBatteryBlockEntity.X1, CoastalBatteryBlockEntity.Y0, CoastalBatteryBlockEntity.Y1, CoastalBatteryBlockEntity.Z0, CoastalBatteryBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CoastalBatteryBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COASTAL_BATTERY_BE.get(), CoastalBatteryBlockEntity::clientTickBattery)
                : createTickerHelper(type, Wmd.COASTAL_BATTERY_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
