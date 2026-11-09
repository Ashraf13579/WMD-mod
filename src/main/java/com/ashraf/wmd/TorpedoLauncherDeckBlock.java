package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TorpedoLauncherDeckBlock extends ItemUnitBlock {

    public static final MapCodec<TorpedoLauncherDeckBlock> CODEC = simpleCodec(TorpedoLauncherDeckBlock::new);

    public TorpedoLauncherDeckBlock(Properties properties) {
        super(properties, TorpedoLauncherDeckBlockEntity.X0, TorpedoLauncherDeckBlockEntity.X1, TorpedoLauncherDeckBlockEntity.Y0, TorpedoLauncherDeckBlockEntity.Y1, TorpedoLauncherDeckBlockEntity.Z0, TorpedoLauncherDeckBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TorpedoLauncherDeckBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.TORPEDO_LAUNCHER_DECK_BE.get(), TorpedoLauncherDeckBlockEntity::clientTickDeck)
                : createTickerHelper(type, Wmd.TORPEDO_LAUNCHER_DECK_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
