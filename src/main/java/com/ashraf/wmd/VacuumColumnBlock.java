package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class VacuumColumnBlock extends UnitBlock {

    public static final MapCodec<VacuumColumnBlock> CODEC = simpleCodec(VacuumColumnBlock::new);

    public VacuumColumnBlock(Properties properties) { super(properties, -1, 1, 0, 9, 0, 2); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VacuumColumnBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.VACUUM_COLUMN_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.VACUUM_COLUMN_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
