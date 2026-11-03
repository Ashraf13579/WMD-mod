package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class LaunchMissileItem extends Item {

    private final int tier;

    public LaunchMissileItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() { return tier; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wmd.missile.tooltip_tier", tier, Missiles.height(tier))
                .withStyle(ChatFormatting.GRAY));
        MissileBuild sb = MissileBuild.of(stack);
        tooltip.add(Component.translatable("item.wmd.missile.tooltip_fuel", MissileStats.fuel(sb),
                (Missiles.loadTicks(tier) + MissileStats.fuelTicks(sb, tier)) / 20).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.wmd.missile.tooltip_stats", String.format("%.1f", MissileStats.weight(sb)), String.format("%,d", MissileStats.range(sb)),
                String.format("%.1f", MissileStats.speed(sb)), MissileStats.health(sb)).withStyle(ChatFormatting.AQUA));
        MissileBuild b = stack.get(WmdComponents.MISSILE_BUILD.get());
        if (b != null) {
            tooltip.add(Component.translatable("item.wmd.missile.tooltip_parts", b.airframe(), b.motor(), b.guidance()).withStyle(ChatFormatting.GRAY));
            if (b.divert()) tooltip.add(Component.translatable("item.wmd.motor_divert").withStyle(ChatFormatting.GRAY));
            if (b.uplink()) tooltip.add(Component.translatable("tooltip.wmd.uplink_fitted").withStyle(ChatFormatting.AQUA));
            if (b.camPod()) tooltip.add(Component.translatable("tooltip.wmd.campod_fitted").withStyle(ChatFormatting.AQUA));
            if (!b.seeker().isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.seeker_fitted", Component.translatable("item.wmd." + b.seeker() + "_seeker")).withStyle(net.minecraft.ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.wmd.missile.tooltip_warhead",
                    Component.translatable("warhead.wmd." + b.warheadType()), b.warhead()).withStyle(ChatFormatting.GRAY));
            if (b.refined()) tooltip.add(Component.translatable("tooltip.wmd.part.refined").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.wmd.missile.tooltip_miss", (int) Config.missRadius(b.guidance())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
