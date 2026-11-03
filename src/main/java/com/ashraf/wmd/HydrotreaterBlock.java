package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HydrotreaterBlock extends UnitBlock {

    public static final MapCodec<HydrotreaterBlock> CODEC = simpleCodec(HydrotreaterBlock::new);

    public HydrotreaterBlock(Properties properties) { super(properties, -1, 1, 0, 7, 0, 2); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HydrotreaterBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.HYDROTREATER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.HYDROTREATER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
