package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TowedArrayWinchBlock extends UnitBlock {

    public static final MapCodec<TowedArrayWinchBlock> CODEC = simpleCodec(TowedArrayWinchBlock::new);

    public TowedArrayWinchBlock(Properties properties) {
        super(properties, TowedArrayWinchBlockEntity.X0, TowedArrayWinchBlockEntity.X1, TowedArrayWinchBlockEntity.Y0, TowedArrayWinchBlockEntity.Y1, TowedArrayWinchBlockEntity.Z0, TowedArrayWinchBlockEntity.Z1);
    }

    @Override public boolean hullPlug(net.minecraft.core.BlockPos local) { return local.getZ() == TowedArrayWinchBlockEntity.Z0; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TowedArrayWinchBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.TOWED_ARRAY_WINCH_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.TOWED_ARRAY_WINCH_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
