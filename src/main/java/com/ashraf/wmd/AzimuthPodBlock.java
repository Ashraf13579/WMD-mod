package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AzimuthPodBlock extends P6DriveBlock {

    public static final MapCodec<AzimuthPodBlock> CODEC = simpleCodec(AzimuthPodBlock::new);

    public AzimuthPodBlock(Properties properties) { super(properties, "azimuth_pod", 2, 1, 2, false); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.AZIMUTH_POD_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newAzimuthPod(pos, state); }
}
