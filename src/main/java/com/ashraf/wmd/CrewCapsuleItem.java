package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CrewCapsuleItem extends Item {

    public CrewCapsuleItem(Item.Properties p) { super(p.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.crew_capsule", Balance.CREW_SEATS).withStyle(ChatFormatting.GRAY));
    }
}
