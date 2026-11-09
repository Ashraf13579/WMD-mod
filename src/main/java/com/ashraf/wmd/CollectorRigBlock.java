package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CollectorRigBlock extends ItemUnitBlock {

    public static final MapCodec<CollectorRigBlock> CODEC = simpleCodec(CollectorRigBlock::new);

    public CollectorRigBlock(Properties properties) {
        super(properties, CollectorRigBlockEntity.X0, CollectorRigBlockEntity.X1, CollectorRigBlockEntity.Y0, CollectorRigBlockEntity.Y1,
                CollectorRigBlockEntity.Z0, CollectorRigBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public boolean faceAway() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CollectorRigBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.COLLECTOR_RIG_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.COLLECTOR_RIG_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
