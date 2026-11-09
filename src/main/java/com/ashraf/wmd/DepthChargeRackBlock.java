package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DepthChargeRackBlock extends ItemUnitBlock {

    public static final MapCodec<DepthChargeRackBlock> CODEC = simpleCodec(DepthChargeRackBlock::new);

    public DepthChargeRackBlock(Properties properties) {
        super(properties, DepthChargeRackBlockEntity.X0, DepthChargeRackBlockEntity.X1, DepthChargeRackBlockEntity.Y0, DepthChargeRackBlockEntity.Y1,
                DepthChargeRackBlockEntity.Z0, DepthChargeRackBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public boolean faceAway() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DepthChargeRackBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.DEPTH_CHARGE_RACK_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.DEPTH_CHARGE_RACK_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
