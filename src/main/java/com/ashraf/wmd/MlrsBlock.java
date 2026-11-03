package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MlrsBlock extends ItemUnitBlock {

    public static final MapCodec<MlrsBlock> CODEC = simpleCodec(MlrsBlock::new);

    public MlrsBlock(Properties properties) {
        super(properties, MlrsBlockEntity.X0, MlrsBlockEntity.X1, MlrsBlockEntity.Y0, MlrsBlockEntity.Y1, MlrsBlockEntity.Z0, MlrsBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MlrsBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.MLRS_BE.get(), ArtilleryGunBlockEntity::clientTickGun)
                : createTickerHelper(type, Wmd.MLRS_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
