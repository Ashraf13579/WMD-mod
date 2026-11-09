package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AsrocItem extends Item {

    public AsrocItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wmd.asroc.desc", (int) Balance.ASROC_MIN_RANGE, String.format("%,d", (int) Balance.ASROC_RANGE)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
