package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class PayloadUnitItem extends Item {

    public PayloadUnitItem(Properties properties) { super(properties); }

    public static String typeOf(ItemStack stack) { return Satellites.validType(stack.get(WmdComponents.SAT_TYPE.get())); }

    public static ItemStack of(String type) {
        ItemStack s = new ItemStack(Wmd.PAYLOAD_UNIT.get());
        s.set(WmdComponents.SAT_TYPE.get(), Satellites.validType(type));
        return s;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String type = typeOf(stack);
        tooltip.add(Component.translatable("tooltip.wmd.payload_unit.type", Component.translatable("warhead.wmd." + Satellites.PREFIX + type)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.wmd.satellite." + type).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.satellite.life", Balance.SAT_LIFE_HOURS / 24).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wmd.payload_unit.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
