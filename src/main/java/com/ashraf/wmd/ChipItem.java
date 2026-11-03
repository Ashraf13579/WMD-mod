package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ChipItem extends Item {

    private final String research;

    public ChipItem(Properties properties, String research) {
        super(properties);
        this.research = research;
    }

    public String research() { return research; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResearchRecipes.Research r = ResearchRecipes.byId(research);
        if (r == null) {
            tooltip.add(Component.translatable("tooltip.wmd.chip_blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.wmd.chip.unlocks").withStyle(ChatFormatting.GRAY));
        for (String id : r.unlocks())
            tooltip.add(Component.literal("  ").append(ResearchRecipes.item(id).getDescription()).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.wmd.chip.made").withStyle(ChatFormatting.DARK_GRAY));
    }
}
