package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class DroneItem extends Item {

    public final DroneType type;

    public DroneItem(DroneType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.wmd.drone_" + type.id() + ".tip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(type.big ? "item.wmd.drone.tip.hangar" : "item.wmd.drone.tip.shelf").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("item.wmd.drone.tip.later").withStyle(ChatFormatting.DARK_GRAY));
    }
}
