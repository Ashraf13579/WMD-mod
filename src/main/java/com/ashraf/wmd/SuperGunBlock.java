package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SuperGunBlock extends ItemUnitBlock {

    public static final MapCodec<SuperGunBlock> CODEC = simpleCodec(SuperGunBlock::new);

    public SuperGunBlock(Properties properties) {
        super(properties, SuperGunBlockEntity.X0, SuperGunBlockEntity.X1, SuperGunBlockEntity.Y0, SuperGunBlockEntity.Y1, SuperGunBlockEntity.Z0, SuperGunBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SuperGunBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SUPER_GUN_BE.get(), SuperGunBlockEntity::clientTickSuper)
                : createTickerHelper(type, Wmd.SUPER_GUN_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
