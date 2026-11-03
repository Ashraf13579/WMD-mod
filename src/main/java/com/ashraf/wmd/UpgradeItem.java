package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class UpgradeItem extends Item {

    public enum Type {
        HEAVY_WATER("heavy_water", Balance.UPGRADE_MAX_LEVEL),

        SPEED("speed", Balance.UPGRADE_MAX_LEVEL), EFFICIENCY("efficiency", Balance.UPGRADE_MAX_LEVEL), THROUGHPUT("throughput", Balance.UPGRADE_MAX_LEVEL),
        BUFFER("buffer", Balance.UPGRADE_MAX_LEVEL), MUFFLER("muffler", Balance.UPGRADE_MAX_LEVEL), AUTO_EJECT("auto_eject", 1), CIWS_DRUM("ciws_drum", 1);

        public final String id;

        public final int maxLevel;

        Type(String id, int maxLevel) { this.id = id; this.maxLevel = maxLevel; }

        static Type byIndex(int i) { Type[] v = values(); return i >= 0 && i < v.length ? v[i] : null; }
    }

    public final Type type;

    public UpgradeItem(Type type, Properties properties) {
        super(properties.stacksTo(Math.max(1, type.maxLevel)));
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.upgrade." + type.id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(type.maxLevel > 1 ? "tooltip.wmd.upgrade.how" : "tooltip.wmd.upgrade.how_one", type.maxLevel).withStyle(ChatFormatting.DARK_GRAY));
    }
}
