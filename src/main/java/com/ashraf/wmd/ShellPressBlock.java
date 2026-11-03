package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ShellPressBlock extends ItemUnitBlock {

    public static final MapCodec<ShellPressBlock> CODEC = simpleCodec(ShellPressBlock::new);

    public ShellPressBlock(Properties properties) {
        super(properties, ShellPressBlockEntity.X0, ShellPressBlockEntity.X1, ShellPressBlockEntity.Y0, ShellPressBlockEntity.Y1, ShellPressBlockEntity.Z0, ShellPressBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ShellPressBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SHELL_PRESS_BE.get(), ShellPressBlockEntity::clientTickPress)
                : createTickerHelper(type, Wmd.SHELL_PRESS_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
