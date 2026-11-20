package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ExoModuleItem extends Item {

    public final ExoModule module;

    public ExoModuleItem(ExoModule module, Item.Properties p) { super(p.stacksTo(1)); this.module = module; }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.exo_mod." + module.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.exo_mod.fit", Component.translatable("tooltip.wmd.exo_piece." + module.piece())).withStyle(ChatFormatting.DARK_GRAY));
    }
}
