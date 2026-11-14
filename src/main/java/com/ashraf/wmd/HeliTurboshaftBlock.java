package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HeliTurboshaftBlock extends HeliBlock {

    public static final MapCodec<HeliTurboshaftBlock> CODEC = simpleCodec(HeliTurboshaftBlock::new);

    public HeliTurboshaftBlock(Properties properties) { super(properties, "heli_turboshaft", 2); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.HELI_TURBOSHAFT_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newHeliTurboshaft(pos, state); }
}
