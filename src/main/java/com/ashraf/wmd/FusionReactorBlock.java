package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FusionReactorBlock extends UnitBlock {

    public static final MapCodec<FusionReactorBlock> CODEC = simpleCodec(FusionReactorBlock::new);

    public FusionReactorBlock(Properties properties) {
        super(properties, FusionReactorBlockEntity.X0, FusionReactorBlockEntity.X1, FusionReactorBlockEntity.Y0, FusionReactorBlockEntity.Y1,
                FusionReactorBlockEntity.Z0, FusionReactorBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !newState.is(this) && level instanceof net.minecraft.server.level.ServerLevel sl
                && level.getBlockEntity(pos) instanceof FusionReactorBlockEntity be && !be.minedNow(sl))
            sl.getServer().execute(() -> be.plasmaRelease(sl));
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof FusionReactorBlockEntity be) be.markMined(level);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FusionReactorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.FUSION_REACTOR_BE.get(), FusionReactorBlockEntity::clientTickFusion)
                : createTickerHelper(type, Wmd.FUSION_REACTOR_BE.get(), FusionReactorBlockEntity::serverTickFusion);
    }
}
