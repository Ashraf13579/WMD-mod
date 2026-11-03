package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class StageItem extends Item {

    public static final int CORE = 1, UPPER = 2, BOOSTER = 3;
    private final int stageKind;

    public StageItem(Properties properties, int stageKind) { super(properties); this.stageKind = stageKind; }

    public int stageKind() { return stageKind; }

    public MissileBuild defaultBuild() {
        return switch (stageKind) {
            case CORE -> new MissileBuild(4, 4, 1, 1, "he", false, false, false, false, 0, 1);
            case UPPER -> new MissileBuild(4, 4, 4, 4, "thermo", false, false, false, false, 0, 2);
            default -> new MissileBuild(3, 3, 1, 1, "he", false, false, false, false, 0, 3);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.stage." + stageKind));
        MissileBuild b = stack.get(WmdComponents.MISSILE_BUILD.get());
        if (b != null) {
            tooltip.add(Component.translatable("tooltip.wmd.stage.grades", b.airframe(), b.motor()));
            if (stageKind == UPPER) tooltip.add(Component.translatable("tooltip.wmd.stage.payload", Component.translatable("warhead.wmd." + b.warheadType()), b.guidance()));
            if (b.uplink()) tooltip.add(Component.translatable("tooltip.wmd.uplink_fitted").withStyle(net.minecraft.ChatFormatting.AQUA));
            if (b.camPod()) tooltip.add(Component.translatable("tooltip.wmd.campod_fitted").withStyle(net.minecraft.ChatFormatting.AQUA));
            if (!b.seeker().isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.seeker_fitted", Component.translatable("item.wmd." + b.seeker() + "_seeker")).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }
}
