package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ArtShellItem extends Item {

    private final int gun, type;

    public ArtShellItem(Properties properties, int gun, int type) {
        super(properties);
        this.gun = gun;
        this.type = type;
    }

    public int gun() { return gun; }

    public int type() { return type; }

    public boolean isPropellant() { return gun < 0; }

    public static String leaflet(ItemStack s) {
        net.minecraft.world.item.component.CustomData d = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getString(LEAFLET_KEY);
    }

    public static final String LEAFLET_KEY = "wmd_leaflet";

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isPropellant()) {
            tooltip.add(Component.translatable("tooltip.wmd.propellant_charge", Artillery.chargeRange(1), Balance.HOWITZER_CHARGES, Balance.HOWITZER_RANGE).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.wmd.art_shell." + Artillery.typeName(type)).withStyle(ChatFormatting.GRAY));
        if (gun == Artillery.SUPER_GUN) { tooltip.add(Component.translatable("tooltip.wmd.art_shell_gun.super").withStyle(ChatFormatting.DARK_GRAY)); return; }
        tooltip.add(Component.translatable(Artillery.isSpecial(type) ? "tooltip.wmd.art_shell_gun.rack" : "tooltip.wmd.art_shell_gun." + Artillery.GUNS[gun == Artillery.HOWITZER ? 1 : 0]).withStyle(ChatFormatting.DARK_GRAY));
        String msg = leaflet(stack);
        if (!msg.isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.leaflet_msg", msg).withStyle(ChatFormatting.WHITE));
    }
}
