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

public class LeafletItem extends Item {

    public static final String MSG = "wmd_leaflet", FROM = "wmd_leaflet_from";

    public LeafletItem(Properties properties) { super(properties); }

    public static ItemStack of(String msg, String from, int count) {
        ItemStack s = new ItemStack(Wmd.LEAFLET.get(), count);
        CompoundTag t = new CompoundTag();
        t.putString(MSG, msg);
        t.putString(FROM, from);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    private static String get(ItemStack s, String key) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getString(key);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        String msg = get(s, MSG);
        if (!msg.isEmpty() && level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.wmd.leaflet", get(s, FROM).isEmpty() ? "?" : get(s, FROM), msg).withStyle(ChatFormatting.WHITE), false);
            ClientLeaflet.show(msg);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String msg = get(stack, MSG), from = get(stack, FROM);
        if (msg.isEmpty()) { tooltip.add(Component.translatable("tooltip.wmd.leaflet_blank").withStyle(ChatFormatting.GRAY)); return; }
        tooltip.add(Component.literal("\"" + msg + "\"").withStyle(ChatFormatting.WHITE));
        if (!from.isEmpty()) tooltip.add(Component.translatable("tooltip.wmd.leaflet_from", from).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.leaflet_read").withStyle(ChatFormatting.GRAY));
    }
}
