package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FuelTankBlock extends P6DriveBlock {

    public static final MapCodec<FuelTankBlock> CODEC = simpleCodec(p -> new FuelTankBlock(p, false));

    private final boolean drop;

    public FuelTankBlock(Properties properties, boolean drop) {
        super(properties, drop ? "drop_tank" : "fuel_tank", 1, 1, drop ? 3 : 1, false);
        this.drop = drop;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<? extends AirEngineBlockEntity> beType() { return drop ? AirBlocks.DROP_TANK_BE.get() : AirBlocks.FUEL_TANK_BE.get(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FuelTankBlockEntity(pos, state); }
}
