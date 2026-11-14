package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AshmItem extends Item {

    public AshmItem(Properties properties) { super(properties); }

    static boolean refined(ItemStack s) {
        Integer g = s.get(WmdComponents.WARHEAD_GRADE.get());
        return g != null && g > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.ashm.stats", String.format("%,d", (int) Balance.ASHM_RANGE), Math.round(Balance.ASHM_SPEED * 20.0)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.ashm.use").withStyle(ChatFormatting.GRAY));
        if (refined(stack)) tooltip.add(Component.translatable("tooltip.wmd.part.refined").withStyle(ChatFormatting.GOLD));
    }
}
