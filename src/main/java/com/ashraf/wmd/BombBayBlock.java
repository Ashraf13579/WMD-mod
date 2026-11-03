package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BombBayBlock extends UnitBlock {

    public static final MapCodec<BombBayBlock> CODEC = simpleCodec(BombBayBlock::new);

    public BombBayBlock(Properties properties) { super(properties, 0, 2, 0, 1, 0, 8); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BombRackBlockEntity(Wmd.BOMB_BAY_BE.get(), pos, state, true); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof BombRackBlockEntity be)
            for (net.minecraft.world.item.ItemStack s : be.takeBombs()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.BOMB_BAY_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.BOMB_BAY_BE.get(), BombRackBlockEntity::serverTickRack);
    }
}
