package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SonarConsoleBlock extends UnitBlock {

    public static final MapCodec<SonarConsoleBlock> CODEC = simpleCodec(SonarConsoleBlock::new);

    public SonarConsoleBlock(Properties properties) {
        super(properties, SonarConsoleBlockEntity.X0, SonarConsoleBlockEntity.X1, SonarConsoleBlockEntity.Y0, SonarConsoleBlockEntity.Y1, SonarConsoleBlockEntity.Z0, SonarConsoleBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SonarConsoleBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SONAR_CONSOLE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.SONAR_CONSOLE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
