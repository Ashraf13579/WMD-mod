package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AirRocketItem extends Item {

    public AirRocketItem(Properties p) { super(p); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.air_rocket").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.air_rocket.use").withStyle(ChatFormatting.GRAY));
    }
}
