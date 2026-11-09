package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FireControlConsoleBlock extends UnitBlock {

    public static final MapCodec<FireControlConsoleBlock> CODEC = simpleCodec(FireControlConsoleBlock::new);

    public FireControlConsoleBlock(Properties properties) {
        super(properties, FireControlConsoleBlockEntity.X0, FireControlConsoleBlockEntity.X1, FireControlConsoleBlockEntity.Y0, FireControlConsoleBlockEntity.Y1,
                FireControlConsoleBlockEntity.Z0, FireControlConsoleBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FireControlConsoleBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.FIRE_CONTROL_CONSOLE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.FIRE_CONTROL_CONSOLE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
