package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BombSightBlock extends UnitBlock {

    public static final MapCodec<BombSightBlock> CODEC = simpleCodec(BombSightBlock::new);

    public BombSightBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BombSightBlockEntity(pos, state); }

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BombSightBlockEntity be && be.pulsing() && direction == be.backFace().getOpposite() ? 15 : 0;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.BOMB_SIGHT_BE.get(), BombSightBlockEntity::clientTickSight)
                : createTickerHelper(type, Wmd.BOMB_SIGHT_BE.get(), BombSightBlockEntity::serverTickSight);
    }
}
