package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MlrsPodItem extends Item {

    private final int type;

    public MlrsPodItem(Properties properties, int type) {
        super(properties);
        this.type = type;
    }

    public int type() { return type; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(type == Artillery.GUIDED ? "tooltip.wmd.mlrs_pod_guided" : "tooltip.wmd.mlrs_pod", MlrsBlockEntity.POD_ROCKETS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.mlrs_pod_use", Balance.MLRS_RANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
