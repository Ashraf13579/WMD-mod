package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LaserDefenceBlock extends UnitBlock {

    public static final MapCodec<LaserDefenceBlock> CODEC = simpleCodec(LaserDefenceBlock::new);

    public LaserDefenceBlock(Properties properties) {
        super(properties, LaserDefenceBlockEntity.X0, LaserDefenceBlockEntity.X1, LaserDefenceBlockEntity.Y0, LaserDefenceBlockEntity.Y1,
                LaserDefenceBlockEntity.Z0, LaserDefenceBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaserDefenceBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.LASER_DEFENCE_BE.get(), LaserDefenceBlockEntity::clientTickLaser)
                : createTickerHelper(type, Wmd.LASER_DEFENCE_BE.get(), LaserDefenceBlockEntity::serverTickLaser);
    }
}
