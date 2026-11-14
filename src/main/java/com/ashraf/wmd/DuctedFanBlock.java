package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DuctedFanBlock extends AirEngineBlock {

    public static final MapCodec<DuctedFanBlock> CODEC = simpleCodec(DuctedFanBlock::new);

    public DuctedFanBlock(Properties properties) { super(properties, "ducted_fan", 0, 1, 2, 3); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return Wmd.DUCTED_FAN_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newDuctedFan(pos, state); }
}
