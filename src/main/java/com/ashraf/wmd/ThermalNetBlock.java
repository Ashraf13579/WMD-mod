package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ThermalNetBlock extends CamoNetBlock {

    public static final MapCodec<ThermalNetBlock> CODEC = simpleCodec(ThermalNetBlock::new);

    public ThermalNetBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.THERMAL_NET_BE.get(), CamoNetBlockEntity::serverTick);
    }

    public static class Entity extends CamoNetBlockEntity {
        public Entity(BlockPos pos, BlockState state) { super(Wmd.THERMAL_NET_BE.get(), pos, state); }
    }
}
