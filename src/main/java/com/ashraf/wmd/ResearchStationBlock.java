package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ResearchStationBlock extends ItemUnitBlock {

    public static final MapCodec<ResearchStationBlock> CODEC = simpleCodec(ResearchStationBlock::new);

    public ResearchStationBlock(Properties properties) { super(properties, -1, 1, 0, 0, -1, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ResearchStationBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.RESEARCH_STATION_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.RESEARCH_STATION_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
