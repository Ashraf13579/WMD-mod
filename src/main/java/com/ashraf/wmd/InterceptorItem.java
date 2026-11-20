package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class InterceptorItem extends Item {

    private final int tier;

    public InterceptorItem(Properties properties, int tier) {
        super(properties.stacksTo(8));
        this.tier = tier;
    }

    public int tier() { return tier; }

    public static Item ofTier(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 1 -> Wmd.INTERCEPTOR_T1.get();
            case 2 -> Wmd.INTERCEPTOR_T2.get();
            case 3 -> Wmd.INTERCEPTOR_T3.get();
            default -> Wmd.INTERCEPTOR_T4.get();
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0) tooltip.add(Component.translatable("tooltip.wmd.interceptor.refined", (int) (Balance.INT_REFINED_BONUS * 100)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wmd.interceptor", tier, (int) (Interceptors.killChance(tier, 1) * 100), (int) (Interceptors.killChance(tier, tier) * 100)).withStyle(ChatFormatting.GRAY));
    }
}
