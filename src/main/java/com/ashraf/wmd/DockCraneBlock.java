package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DockCraneBlock extends UnitBlock {

    public static final MapCodec<DockCraneBlock> CODEC = simpleCodec(DockCraneBlock::new);

    public DockCraneBlock(Properties properties) {
        super(properties, DockCraneBlockEntity.X0, DockCraneBlockEntity.X1, DockCraneBlockEntity.Y0, DockCraneBlockEntity.Y1, DockCraneBlockEntity.Z0, DockCraneBlockEntity.Z1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof DockCraneBlockEntity be)
            for (net.minecraft.world.item.ItemStack s : be.takeAll()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DockCraneBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.DOCK_CRANE_BE.get(), DockCraneBlockEntity::clientTickCrane)
                : createTickerHelper(type, Wmd.DOCK_CRANE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }
}
