package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public class CargoPodItem extends Item {

    public CargoPodItem(Item.Properties p) { super(p.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)); }

    static boolean fits(ItemStack s) { return !(s.getItem() instanceof CargoPodItem) && s.getItem().canFitInsideContainerItems(); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) CargoPodMenu.openItem(sp, hand);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.cargo_pod", Cargo.stacks(Cargo.contents(s)), Balance.CARGO_SLOTS).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.wmd.cargo_pod.how").withStyle(ChatFormatting.GRAY));
    }
}
