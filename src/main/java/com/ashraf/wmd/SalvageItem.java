package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SalvageItem extends Item {

    private final String id;

    public SalvageItem(Properties properties, String id) {
        super(properties);
        this.id = id;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd." + id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.salvaged").withStyle(ChatFormatting.DARK_GRAY));
    }
}
