package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class HandLauncherItem extends Item {

    private final int kind;

    public HandLauncherItem(Properties p, int kind) {
        super(p);
        this.kind = kind;
    }

    public int kind() { return kind; }

    public Item round() { return kind == HandLock.MANPADS ? Wmd.MANPADS_ROUND.get() : Wmd.JAVELIN_ROUND.get(); }

    private ItemStack findRound(Player p) {
        Item r = round();
        for (ItemStack s : p.getInventory().items) if (s.is(r)) return s;
        for (ItemStack s : p.getInventory().offhand) if (s.is(r)) return s;
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && findRound(player).isEmpty()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.wmd.hand.no_round", round().getDescription()), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer sp) HandLock.tick(sp, kind);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer sp)) return;
        HandLock.Target t = HandLock.locked(sp, kind);
        HandLock.clear(sp, kind);
        if (t == null) return;
        if (!sp.getAbilities().instabuild) {
            ItemStack r = findRound(sp);
            if (r.isEmpty()) return;
            r.shrink(1);
        }
        HandMissileEntity.launch(sp, kind, t);
        sp.getCooldowns().addCooldown(this, Balance.HAND_COOLDOWN);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(kind == HandLock.MANPADS ? "tooltip.wmd.hand.manpads" : "tooltip.wmd.hand.javelin",
                (int) HandLock.range(kind)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.hand.use", round().getDescription()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
