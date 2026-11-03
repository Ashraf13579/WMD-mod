package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HowitzerBlock extends ItemUnitBlock {

    public static final MapCodec<HowitzerBlock> CODEC = simpleCodec(HowitzerBlock::new);

    public HowitzerBlock(Properties properties) {
        super(properties, HowitzerBlockEntity.X0, HowitzerBlockEntity.X1, HowitzerBlockEntity.Y0, HowitzerBlockEntity.Y1, HowitzerBlockEntity.Z0, HowitzerBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HowitzerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.HOWITZER_BE.get(), ArtilleryGunBlockEntity::clientTickGun)
                : createTickerHelper(type, Wmd.HOWITZER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
