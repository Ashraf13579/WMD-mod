package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SeekerModuleItem extends Item {

    private final String kind;

    public SeekerModuleItem(Properties p, String kind) {
        super(p);
        this.kind = kind;
    }

    public String kind() { return kind; }

    public static String kindOf(ItemStack s) { return s.getItem() instanceof SeekerModuleItem m ? m.kind : ""; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.seeker." + kind, Balance.SEEKER_RADIUS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.seeker.slot").withStyle(ChatFormatting.DARK_GRAY));
    }
}
