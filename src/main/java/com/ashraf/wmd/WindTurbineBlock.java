package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class WindTurbineBlock extends UnitBlock {

    public static final MapCodec<WindTurbineBlock> CODEC = simpleCodec(WindTurbineBlock::new);

    public WindTurbineBlock(Properties properties) { super(properties, -1, 1, 0, 15, -1, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected boolean mountable() { return false; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WindTurbineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.WIND_TURBINE_BE.get(), (l, p, s, be) -> { ProcessUnitBlockEntity.clientTick(l, p, s, be); be.animate(l); })
                : createTickerHelper(type, Wmd.WIND_TURBINE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
