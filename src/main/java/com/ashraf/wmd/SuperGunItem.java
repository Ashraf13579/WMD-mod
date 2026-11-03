package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SuperGunItem extends Item {

    private final boolean charge;

    public SuperGunItem(Properties properties, boolean charge) {
        super(properties);
        this.charge = charge;
    }

    public boolean isCharge() { return charge; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (charge) tooltip.add(Component.translatable("tooltip.wmd.super_charge", Balance.SUPER_GUN_CHARGES, Balance.SUPER_GUN_RANGE).withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("tooltip.wmd.shell_super_he").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.super_gun_ammo").withStyle(ChatFormatting.DARK_GRAY));
    }
}
