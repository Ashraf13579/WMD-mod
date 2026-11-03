package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RailgunBlock extends UnitBlock {

    public static final MapCodec<RailgunBlock> CODEC = simpleCodec(RailgunBlock::new);

    public RailgunBlock(Properties properties) {
        super(properties, RailgunBlockEntity.X0, RailgunBlockEntity.X1, RailgunBlockEntity.Y0, RailgunBlockEntity.Y1, RailgunBlockEntity.Z0, RailgunBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RailgunBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.RAILGUN_BE.get(), RailgunBlockEntity::clientTickRailgun)
                : createTickerHelper(type, Wmd.RAILGUN_BE.get(), RailgunBlockEntity::serverTickRailgun);
    }
}
