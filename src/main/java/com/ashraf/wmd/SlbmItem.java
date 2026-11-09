package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class SlbmItem extends Item {

    public SlbmItem(Properties properties) { super(properties); }

    public static MissileBuild buildOf(ItemStack stack) {
        MissileBuild b = stack.get(WmdComponents.MISSILE_BUILD.get());
        return b != null && b.isSlbm() ? b : new MissileBuild(3, 3, 3, 3, "he", false, false, false, false, 0, MissileBuild.KIND_SLBM);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MissileBuild b = buildOf(stack);
        tooltip.add(Component.translatable("tooltip.wmd.slbm.grades", b.guidance(), Component.translatable("warhead.wmd." + b.warheadType())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.slbm.stats", String.format("%,d", MissileStats.range(b)), (int) Balance.SLBM_MIN_RANGE, MissileStats.health(b)).withStyle(ChatFormatting.AQUA));
        if (b.refined()) tooltip.add(Component.translatable("tooltip.wmd.part.refined").withStyle(ChatFormatting.GOLD));
        if (b.uplink()) tooltip.add(Component.translatable("tooltip.wmd.uplink_fitted").withStyle(ChatFormatting.AQUA));
        if (b.camPod()) tooltip.add(Component.translatable("tooltip.wmd.campod_fitted").withStyle(ChatFormatting.AQUA));
        if (!b.seeker().isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.seeker_fitted", Component.translatable("item.wmd." + b.seeker() + "_seeker")).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.wmd.missile.tooltip_miss", (int) Config.missRadius(b.guidance())).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.slbm.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
