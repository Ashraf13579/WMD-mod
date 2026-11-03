package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ForcefieldBlock extends UnitBlock {

    public static final MapCodec<ForcefieldBlock> CODEC = simpleCodec(ForcefieldBlock::new);

    public ForcefieldBlock(Properties properties) {
        super(properties, ForcefieldBlockEntity.X0, ForcefieldBlockEntity.X1, ForcefieldBlockEntity.Y0, ForcefieldBlockEntity.Y1,
                ForcefieldBlockEntity.Z0, ForcefieldBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ForcefieldBlockEntity(pos, state); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level.getBlockEntity(pos) instanceof ForcefieldBlockEntity be)
            be.leaveCooldown(level.getGameTime());
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.FORCEFIELD_BE.get(), ForcefieldBlockEntity::clientTickField)
                : createTickerHelper(type, Wmd.FORCEFIELD_BE.get(), ForcefieldBlockEntity::serverTickField);
    }
}
