package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class StationKitItem extends Item {

    private final String type;

    public StationKitItem(Item.Properties p, String type) { super(p.stacksTo(1)); this.type = type; }

    public String type() { return type; }

    public static int port(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null || !d.contains("wmd_port") ? -1 : d.copyTag().getInt("wmd_port");
    }

    public static void setPort(ItemStack s, int port) {
        CompoundTag t = s.has(DataComponents.CUSTOM_DATA) ? s.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        t.putInt("wmd_port", port);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    static Component portText(int port) {
        if (port >= 4) return Component.translatable("tooltip.wmd.station_kit.port_far", port - 4, Component.translatable("tooltip.wmd.station_kit.dir" + (port - 4)));
        return port < 0 ? Component.translatable("tooltip.wmd.station_kit.port_auto") : Component.translatable("tooltip.wmd.station_kit.port", port, Component.translatable("tooltip.wmd.station_kit.dir" + port));
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.station_kit." + type).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.station_kit.docks", Component.translatable("tooltip.wmd.station_kit.spot." + type)).withStyle(ChatFormatting.AQUA));
    }
}
