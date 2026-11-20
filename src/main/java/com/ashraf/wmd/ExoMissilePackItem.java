package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ExoMissilePackItem extends Item {

    public ExoMissilePackItem(Item.Properties p) { super(p.stacksTo(16)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) return ExoModule.MISSILES.fitted(player) ? InteractionResultHolder.success(s) : InteractionResultHolder.pass(s);
        if (player instanceof ServerPlayer sp && ExoMods.load(sp, s)) {
            sp.displayClientMessage(Component.translatable("message.wmd.exo_mod.loaded", Balance.EXO_MSL_SALVO).withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.consume(s);
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.exo_micro_missiles", Balance.EXO_MSL_SALVO).withStyle(ChatFormatting.GRAY));
    }
}
