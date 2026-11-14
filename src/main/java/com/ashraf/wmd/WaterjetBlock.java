package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class WaterjetBlock extends P6DriveBlock {

    public static final MapCodec<WaterjetBlock> CODEC = simpleCodec(WaterjetBlock::new);

    public WaterjetBlock(Properties properties) { super(properties, "waterjet", 1, 1, 3, true); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.WATERJET_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newWaterjet(pos, state); }
}
