package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class FlightCheckItem extends Item {

    public FlightCheckItem(Properties p) { super(p.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        check(level, player);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        check(ctx.getLevel(), ctx.getPlayer());
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    private static void check(Level level, Player player) {
        if (level.isClientSide || !AirGate.on() || !(player instanceof ServerPlayer sp)) return;
        UUID ship = FlightCheck.lookedAt(sp);
        if (ship == null) { sp.displayClientMessage(Component.translatable("gui.wmd.flightcheck.none", (int) Balance.FC_REACH), true); return; }
        FlightCheck.sendTo(sp, ship);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.wmd.flight_check_tablet.tip").withStyle(ChatFormatting.GRAY));
    }
}
