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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HawsePipeBlock extends UnitBlock {

    public static final MapCodec<HawsePipeBlock> CODEC = simpleCodec(HawsePipeBlock::new);

    public HawsePipeBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 0); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean hullPlug(BlockPos local) { return true; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HawsePipeBlockEntity(pos, state); }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof HawsePipeBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        interact(level, controller, player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        player.displayClientMessage(Component.translatable("message.wmd.anchor.pipe"), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.hawse_pipe", Balance.ANCHOR_PIPES_MAX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
