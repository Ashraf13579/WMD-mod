package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TurbofanBlock extends AirEngineBlock {

    public static final MapCodec<TurbofanBlock> CODEC = simpleCodec(TurbofanBlock::new);

    public TurbofanBlock(Properties properties) { super(properties, "turbofan", -1, 1, 3, 5); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return Wmd.TURBOFAN_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newTurbofan(pos, state); }
}
