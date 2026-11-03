package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SmokeMachineBlock extends UnitBlock {

    public static final MapCodec<SmokeMachineBlock> CODEC = simpleCodec(SmokeMachineBlock::new);

    public SmokeMachineBlock(Properties properties) {
        super(properties, SmokeMachineBlockEntity.X0, SmokeMachineBlockEntity.X1, SmokeMachineBlockEntity.Y0, SmokeMachineBlockEntity.Y1,
                SmokeMachineBlockEntity.Z0, SmokeMachineBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SmokeMachineBlockEntity(pos, state); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof SmokeMachineBlockEntity be) {
            net.minecraft.world.item.ItemStack rest = be.takeCanisters();
            if (!rest.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rest);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.SMOKE_MACHINE_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.SMOKE_MACHINE_BE.get(), SmokeMachineBlockEntity::serverTickSmoke);
    }
}
