package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ShipTurbineBlock extends P6DriveBlock {

    public static final MapCodec<ShipTurbineBlock> CODEC = simpleCodec(ShipTurbineBlock::new);

    public ShipTurbineBlock(Properties properties) { super(properties, "ship_turbine", 2, 2, 4, false); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return AirBlocks.SHIP_TURBINE_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ShipTurbineBlockEntity(pos, state); }
}
