package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ListeningNodeBlock extends UnitBlock {

    public static final MapCodec<ListeningNodeBlock> CODEC = simpleCodec(ListeningNodeBlock::new);

    public ListeningNodeBlock(Properties properties) {
        super(properties, ListeningNodeBlockEntity.X0, ListeningNodeBlockEntity.X1, ListeningNodeBlockEntity.Y0, ListeningNodeBlockEntity.Y1, ListeningNodeBlockEntity.Z0, ListeningNodeBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ListeningNodeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.LISTENING_NODE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.LISTENING_NODE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
