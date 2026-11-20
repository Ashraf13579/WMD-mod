package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlinkGateSegmentBlock extends UnitBlock {

    public static final MapCodec<BlinkGateSegmentBlock> CODEC = simpleCodec(BlinkGateSegmentBlock::new);

    public BlinkGateSegmentBlock(Properties properties) { super(properties, -1, 1, 0, 1, 0, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return BlinkGatePieceBlockEntity.segment(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, BlinkGateBlocks.BLINK_SEGMENT_BE.get(), ProcessUnitBlockEntity::clientTick) : null;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, net.minecraft.world.phys.BlockHitResult hit) { BlinkGatePieceBlockEntity.openCore(level, controller, player); }

    @Override
    public void interact(Level level, BlockPos controller, Player player) { BlinkGatePieceBlockEntity.openCore(level, controller, player); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlinkGatePieceBlockEntity.removed(this, level, pos, newState, movedByPiston, false);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
