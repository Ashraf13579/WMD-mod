package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CiwsAmmoItem extends Item {

    public CiwsAmmoItem(Properties properties) { super(properties.stacksTo(16)); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.ciws_ammo", Balance.CIWS_BOX).withStyle(ChatFormatting.GRAY));
    }
}
