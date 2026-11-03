package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class TargetDesignatorItem extends Item {

    public static final int RANGE = 512;

    public TargetDesignatorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        player.playSound(WmdSounds.DESIGNATOR_LOCK.get(), 0.8F, 1.0F);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPYGLASS; }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 1200; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wmd.target_designator.tip1", RANGE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wmd.target_designator.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wmd.target_designator.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
