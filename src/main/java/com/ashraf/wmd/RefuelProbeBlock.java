package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RefuelProbeBlock extends P6DriveBlock {

    public static final MapCodec<RefuelProbeBlock> CODEC = simpleCodec(RefuelProbeBlock::new);

    public RefuelProbeBlock(Properties properties) { super(properties, "refuel_probe", 1, 1, 1, false); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.REFUEL_PROBE_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RefuelProbeBlockEntity(pos, state); }
}
