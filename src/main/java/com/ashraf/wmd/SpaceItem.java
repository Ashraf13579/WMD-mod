package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SpaceItem extends Item {

    private final String id;

    public SpaceItem(Properties properties, String id) { super(properties); this.id = id; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (id.equals("rtg_cell")) tooltip.add(Component.translatable("tooltip.wmd.rtg_cell", Balance.SAT_LIFE_HOURS / 24).withStyle(ChatFormatting.GOLD));
        else tooltip.add(Component.translatable("tooltip.wmd." + id).withStyle(ChatFormatting.GRAY));
    }
}
