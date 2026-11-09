package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

public class NoduleFieldBlock extends Block {

    public NoduleFieldBlock(Properties p) { super(p); }

    public static Properties props() {
        return Properties.of().mapColor(MapColor.TERRACOTTA_BLACK).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GRAVEL);
    }

    public static class BlockItemTip extends net.minecraft.world.item.BlockItem {
        public BlockItemTip(Block b, Item.Properties p) { super(b, p); }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.wmd.nodule_field").withStyle(ChatFormatting.GRAY));
        }
    }
}
