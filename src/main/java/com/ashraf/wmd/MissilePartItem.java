package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MissilePartItem extends Item {

    private final int slot, tier;
    private final String variant;

    public MissilePartItem(Properties properties, int slot, int tier) {
        this(properties, slot, tier, slot == 3 ? "he" : "");
    }

    public MissilePartItem(Properties properties, int slot, int tier, String variant) {
        super(properties);
        this.slot = slot;
        this.tier = tier;
        this.variant = variant;
    }

    public int slot() { return slot; }

    public static final String JET = "jet";
    public boolean isJet() { return slot == 1 && JET.equals(variant); }

    public int tier() { return tier; }

    public String variant() { return variant; }

    public MissileBuild look() {
        int g = Math.max(1, tier);
        if (slot == 1 && variant.equals(JET)) return new MissileBuild(g, g, g, g, "he", false, false, false, false, 0, MissileBuild.KIND_CRUISE);
        return new MissileBuild(g, g, g, g, slot == 3 ? variant : "he", false, slot == 1 && variant.equals("divert")).withHypersonic(slot == 1 && variant.equals("hypersonic"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.part.tier", Component.translatable("tier.wmd." + tier)));
        if (slot == 3) tooltip.add(Component.translatable("tooltip.wmd.part.warhead_type", Component.translatable("warhead.wmd." + variant)).withStyle(ChatFormatting.GRAY));
        if (!variant.isEmpty() && slot != 3) tooltip.add(Component.translatable("tooltip.wmd.part." + variant).withStyle(ChatFormatting.GRAY));
        if (slot == 3 && stack.getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0)
            tooltip.add(Component.translatable("tooltip.wmd.part.refined").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wmd.part.use"));
    }
}
