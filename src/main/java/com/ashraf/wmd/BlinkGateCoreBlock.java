package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlinkGateCoreBlock extends UnitBlock {

    public static final MapCodec<BlinkGateCoreBlock> CODEC = simpleCodec(BlinkGateCoreBlock::new);

    public BlinkGateCoreBlock(Properties properties) {
        super(properties, BlinkGateCoreBlockEntity.X0, BlinkGateCoreBlockEntity.X1, BlinkGateCoreBlockEntity.Y0, BlinkGateCoreBlockEntity.Y1,
                BlinkGateCoreBlockEntity.Z0, BlinkGateCoreBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BlinkGateCoreBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, BlinkGateBlocks.BLINK_CORE_BE.get(), BlinkGateCoreBlockEntity::clientTickGate)
                : createTickerHelper(type, BlinkGateBlocks.BLINK_CORE_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlinkGateCoreBlockEntity be) be.redstone(level.hasNeighborSignal(pos));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level.getBlockEntity(pos) instanceof BlinkGateCoreBlockEntity be)
            for (ItemStack s : be.dismantle()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.blink_gate_core", Balance.BG_JUMP_FE / 1_000_000, String.format("%,d", Balance.BG_BLOCK_FE),
                Balance.BG_BUFFER_FE / 1_000_000).withStyle(ChatFormatting.GRAY));
    }
}
