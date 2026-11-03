package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MortarBlock extends ItemUnitBlock {

    public static final MapCodec<MortarBlock> CODEC = simpleCodec(MortarBlock::new);

    public MortarBlock(Properties properties) {
        super(properties, MortarBlockEntity.X0, MortarBlockEntity.X1, MortarBlockEntity.Y0, MortarBlockEntity.Y1, MortarBlockEntity.Z0, MortarBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MortarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.MORTAR_BE.get(), ArtilleryGunBlockEntity::clientTickGun)
                : createTickerHelper(type, Wmd.MORTAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
