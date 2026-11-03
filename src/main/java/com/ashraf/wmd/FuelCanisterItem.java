package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class FuelCanisterItem extends Item {

    private final String tip;

    public FuelCanisterItem(Properties properties, String tip) { super(properties); this.tip = tip; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd." + tip, String.format("%,d", Balance.ALKYLATE_CANISTER_MB), String.format("%,d", Balance.ALKY_ACID_LPG_MB)).withStyle(ChatFormatting.GRAY));
    }
}
