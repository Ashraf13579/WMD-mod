package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AntimatterStoreBlock extends UnitBlock {

    public static final MapCodec<AntimatterStoreBlock> CODEC = simpleCodec(AntimatterStoreBlock::new);

    public AntimatterStoreBlock(Properties properties) { super(properties, -2, 2, 0, 3, 0, 4); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !newState.is(this) && !level.isClientSide && level.getBlockEntity(pos) instanceof AntimatterStoreBlockEntity be)
            be.removed(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof AntimatterStoreBlockEntity be) be.markMined(level);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AntimatterStoreBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.ANTIMATTER_STORE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.ANTIMATTER_STORE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
