package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class OxygenTankItem extends Item {

    public OxygenTankItem(Item.Properties p) { super(p.stacksTo(1)); }

    public static ItemStack full(Item item) {
        ItemStack s = new ItemStack(item);
        EvaSuitItem.setOxygen(s, Balance.O2_FULL);
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tank = player.getItemInHand(hand);
        ItemStack suit = EvaSuitItem.torso(player);
        if (suit.isEmpty()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.wmd.oxygen.no_suit").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(tank);
        }
        int have = EvaSuitItem.oxygen(tank), room = Balance.O2_FULL - EvaSuitItem.oxygen(suit), move = Math.min(have, room);
        if (move <= 0) return InteractionResultHolder.fail(tank);
        if (!level.isClientSide) {
            EvaSuitItem.setOxygen(suit, EvaSuitItem.oxygen(suit) + move);
            EvaSuitItem.setOxygen(tank, have - move);
            level.playSound(null, player.blockPosition(), WmdSounds.OR_O2_POUR.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.wmd.oxygen.refilled", EvaSuitItem.clock(EvaSuitItem.oxygen(suit))).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(tank, level.isClientSide);
    }

    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13.0F * EvaSuitItem.oxygen(s) / Balance.O2_FULL); }
    @Override public int getBarColor(ItemStack s) { return 0x4AA3FF; }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.oxygen_tank", EvaSuitItem.clock(EvaSuitItem.oxygen(s)), EvaSuitItem.clock(Balance.O2_FULL)).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.wmd.oxygen_tank.use").withStyle(ChatFormatting.GRAY));
    }
}
