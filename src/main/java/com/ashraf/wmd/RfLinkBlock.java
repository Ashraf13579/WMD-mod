package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RfLinkBlock extends UnitBlock {

    public static final MapCodec<RfLinkBlock> CODEC = simpleCodec(p -> new RfLinkBlock(p, true));

    public final boolean sender;

    public RfLinkBlock(Properties properties, boolean sender) {
        super(properties, 0, 0, 0, 1, 0, 0);
        this.sender = sender;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RfLinkBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<RfLinkBlockEntity> mine = sender ? RfPowerBlocks.SENDER_BE.get() : RfPowerBlocks.RECEIVER_BE.get();
        return level.isClientSide ? createTickerHelper(type, mine, RfLinkBlockEntity::clientTickRf)
                : createTickerHelper(type, mine, ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(controller) instanceof RfLinkBlockEntity be && CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        super.interact(level, controller, player, hit);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(sender ? "tooltip.wmd.rf_sender" : "tooltip.wmd.rf_receiver",
                String.format("%,d", (sender ? Balance.RF_SEND_FE : Balance.RF_OUT_FE) * 20), Balance.RF_EFFICIENCY, String.format("%,d", Balance.RF_RANGE))
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
