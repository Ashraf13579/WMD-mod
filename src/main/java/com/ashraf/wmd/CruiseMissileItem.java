package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CruiseMissileItem extends Item {

    public CruiseMissileItem(Properties properties) { super(properties); }

    public static MissileBuild buildOf(ItemStack stack) {
        MissileBuild b = stack.get(WmdComponents.MISSILE_BUILD.get());
        return b != null && b.isCruise() ? b : new MissileBuild(2, 2, 2, 2, "he", false, false, false, false, 0, MissileBuild.KIND_CRUISE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MissileBuild b = buildOf(stack);
        tooltip.add(Component.translatable("tooltip.wmd.cruise.grades", b.airframe(), b.guidance(), Component.translatable("warhead.wmd." + b.warheadType())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.cruise.stats", String.format("%,d", MissileStats.range(b)), String.format("%.0f", Balance.CRUISE_SPEED * 20.0),
                Balance.CRUISE_FUEL_CANISTERS).withStyle(ChatFormatting.GRAY));
        if (b.refined()) tooltip.add(Component.translatable("tooltip.wmd.part.refined").withStyle(ChatFormatting.GOLD));
        if (b.camPod()) tooltip.add(Component.translatable("tooltip.wmd.campod_fitted").withStyle(ChatFormatting.AQUA));
        if (!b.seeker().isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.seeker_fitted", Component.translatable("item.wmd." + b.seeker() + "_seeker")).withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.wmd.cruise.use"));
    }
}
