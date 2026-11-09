package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TorpedoRackBlock extends ItemUnitBlock {

    public static final MapCodec<TorpedoRackBlock> CODEC = simpleCodec(TorpedoRackBlock::new);

    public TorpedoRackBlock(Properties properties) {
        super(properties, TorpedoRackBlockEntity.X0, TorpedoRackBlockEntity.X1, TorpedoRackBlockEntity.Y0, TorpedoRackBlockEntity.Y1, TorpedoRackBlockEntity.Z0, TorpedoRackBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TorpedoRackBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.TORPEDO_RACK_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.TORPEDO_RACK_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
