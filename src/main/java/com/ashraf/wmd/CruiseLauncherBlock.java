package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CruiseLauncherBlock extends ItemUnitBlock {

    public static final MapCodec<CruiseLauncherBlock> CODEC = simpleCodec(CruiseLauncherBlock::new);

    public CruiseLauncherBlock(Properties properties) {
        super(properties, CruiseLauncherBlockEntity.X0, CruiseLauncherBlockEntity.X1, CruiseLauncherBlockEntity.Y0, CruiseLauncherBlockEntity.Y1,
                CruiseLauncherBlockEntity.Z0, CruiseLauncherBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CruiseLauncherBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.CRUISE_LAUNCHER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.CRUISE_LAUNCHER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
