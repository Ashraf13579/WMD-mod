package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AamItem extends Item {

    private final int kind;

    public AamItem(Properties properties, int kind) {
        super(properties);
        this.kind = kind;
    }

    public int kind() { return kind; }

    static Item of(int kind) {
        return kind == AirMissiles.HEAT ? AirBlocks.AAM_HEAT.get() : kind == AirMissiles.RADAR ? AirBlocks.AAM_RADAR.get() : kind == AirMissiles.ARM ? AirBlocks.AAM_ARM.get()
                : kind == AirMissiles.AGM ? AirBlocks.AAM_AGM.get() : null;
    }

    static String mesh(int kind) { return kind == AirMissiles.HEAT ? "aam_heat" : kind == AirMissiles.RADAR ? "aam_radar" : kind == AirMissiles.ARM ? "aam_arm"
            : kind == AirMissiles.AGM ? "aam_agm" : null; }

    static int paint(int kind) { return kind == AirMissiles.HEAT || kind == AirMissiles.AGM ? 2 : 3; }

    static boolean refined(ItemStack s) { return s.getItem() instanceof AamItem && s.getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.aam." + AirMissiles.NAMES[kind]).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.aam.stats", (int) Balance.AAM_RANGE[kind], (int) Math.round(Balance.AAM_SPEED[kind] * 20)).withStyle(ChatFormatting.AQUA));
        if (refined(stack)) tooltip.add(Component.translatable("tooltip.wmd.aam.refined", Math.round(Balance.AAM_REFINED_BONUS * 100)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wmd.aam.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
