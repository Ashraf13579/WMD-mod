package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RunnerChargerBlock extends RobotDockBlock {

    public static final MapCodec<RunnerChargerBlock> CODEC = simpleCodec(RunnerChargerBlock::new);
    private static final List<BlockPos> FOOTPRINT = List.of(BlockPos.ZERO);

    public RunnerChargerBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RunnerChargerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.RUNNER_CHARGER_BE.get(), RobotDockBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.RUNNER_CHARGER_BE.get(), RobotDockBlockEntity::serverTick);
    }
}
