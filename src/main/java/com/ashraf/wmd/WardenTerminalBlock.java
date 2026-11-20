package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class WardenTerminalBlock extends WardenTallBlock {

    public WardenTerminalBlock(Properties p) { super(p); }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (player.getMainHandItem().getItem() instanceof HackingDeviceItem) return net.minecraft.world.InteractionResult.PASS;
        if (level instanceof net.minecraft.server.level.ServerLevel sl) WardenFight.uplinkHint(sl, player);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WardenTerminalBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != Wmd.WARDEN_TERMINAL_BE.get()) return null;
        if (level.isClientSide) return (lv, pos, st, be) -> WardenTerminalBlockEntity.clientTick((WardenTerminalBlockEntity) be);
        return (lv, pos, st, be) -> ((WardenTerminalBlockEntity) be).serverTick();
    }
}
