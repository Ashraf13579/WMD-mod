package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GasTurbineBlock extends ItemUnitBlock {

    public static final MapCodec<GasTurbineBlock> CODEC = simpleCodec(GasTurbineBlock::new);

    public GasTurbineBlock(Properties properties) { super(properties, -2, 2, 0, 2, 0, 2); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GasTurbineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.GAS_TURBINE_BE.get(), GasTurbineBlockEntity::clientTickTurbine)
                : createTickerHelper(type, Wmd.GAS_TURBINE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
