package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ItemUnitBlock extends UnitBlock {

    protected ItemUnitBlock(Properties properties, int x0, int x1, int y0, int y1, int z0, int z1) { super(properties, x0, x1, y0, y1, z0, z1); }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(controller) instanceof ItemProcessBlockEntity be && be.tryInsert(player)) return;
        super.interact(level, controller, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof ItemProcessBlockEntity be) {
            for (ItemStack s : be.takeAll()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
