package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class NavalReactorBlock extends UnitBlock {

    public static final MapCodec<NavalReactorBlock> CODEC = simpleCodec(NavalReactorBlock::new);

    public NavalReactorBlock(Properties properties) { super(properties, 0, 2, 0, 2, 0, 2); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NavalReactorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.NAVAL_REACTOR_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.NAVAL_REACTOR_BE.get(), NavalReactorBlockEntity::serverTickReactor);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof NavalReactorBlockEntity be) {
            if (be.tryRepair(player) || be.tryInsertFuel(player)) return;
        }
        super.interact(level, controller, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof NavalReactorBlockEntity be)
            for (ItemStack s : be.takeAll()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
