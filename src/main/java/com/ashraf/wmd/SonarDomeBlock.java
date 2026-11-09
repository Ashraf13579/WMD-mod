package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SonarDomeBlock extends UnitBlock {

    public static final MapCodec<SonarDomeBlock> CODEC = simpleCodec(SonarDomeBlock::new);

    public SonarDomeBlock(Properties properties) {
        super(properties, SonarDomeBlockEntity.X0, SonarDomeBlockEntity.X1, SonarDomeBlockEntity.Y0, SonarDomeBlockEntity.Y1, SonarDomeBlockEntity.Z0, SonarDomeBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SonarDomeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SONAR_DOME_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.SONAR_DOME_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
