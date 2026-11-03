package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class BombItem extends Item {

    private final BombType type;

    public BombItem(BombType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public BombType type() { return type; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.wmd.bomb.blast", Component.translatable("warhead.wmd." + type.blast.id), type.blast.tier).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.wmd." + type.id).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(type.heavy ? "tooltip.wmd.bomb.heavy" : "tooltip.wmd.bomb.light").withStyle(ChatFormatting.DARK_GRAY));
    }
}
