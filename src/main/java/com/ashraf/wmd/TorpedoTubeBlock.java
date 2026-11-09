package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TorpedoTubeBlock extends ItemUnitBlock {

    public static final MapCodec<TorpedoTubeBlock> CODEC = simpleCodec(TorpedoTubeBlock::new);

    public TorpedoTubeBlock(Properties properties) {
        super(properties, TorpedoTubeBlockEntity.X0, TorpedoTubeBlockEntity.X1, TorpedoTubeBlockEntity.Y0, TorpedoTubeBlockEntity.Y1, TorpedoTubeBlockEntity.Z0, TorpedoTubeBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public boolean faceAway() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TorpedoTubeBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.TORPEDO_TUBE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.TORPEDO_TUBE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
