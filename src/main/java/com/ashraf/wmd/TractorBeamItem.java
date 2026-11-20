package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class TractorBeamItem extends Item {

    public TractorBeamItem(Item.Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (HandTech.fe(s) < Balance.TRB_SEARCH_FE) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.wmd.hand_tool.empty").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(s);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!level.isClientSide && user instanceof ServerPlayer sp) TractorBeam.tick(sp, stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!level.isClientSide && user instanceof ServerPlayer sp) TractorBeam.drop(sp);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (!level.isClientSide && e instanceof Player p) HandTech.inventoryTick(stack, p);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) { return slotChanged || oldStack.getItem() != newStack.getItem(); }

    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13.0F * HandTech.fe(s) / HandTech.cap(s)); }
    @Override public int getBarColor(ItemStack s) { return 0x3FA9FF; }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.hand_tool.fe", HandTech.fe(s) / 1000, HandTech.cap(s) / 1000).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.wmd.tractor_beam").withStyle(ChatFormatting.GRAY));
    }
}
