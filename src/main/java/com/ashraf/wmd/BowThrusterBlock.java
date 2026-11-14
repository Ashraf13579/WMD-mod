package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BowThrusterBlock extends P6DriveBlock {

    public static final MapCodec<BowThrusterBlock> CODEC = simpleCodec(BowThrusterBlock::new);

    public BowThrusterBlock(Properties properties) { super(properties, "bow_thruster", 1, 1, 1, false); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.BOW_THRUSTER_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newBowThruster(pos, state); }
}
