package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class DepthChargeItem extends Item {

    public DepthChargeItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(s);
        Vec3 look = player.getLookAngle();
        Vec3 at = player.getEyePosition().add(look.scale(1.0));
        java.util.UUID ship = SableBridge.active() ? SableBridge.shipUnder(player) : null;
        DepthChargeEntity.drop(sl, at, look.scale(0.5).add(0.0, 0.15, 0.0), Balance.DC_DEPTH_DEFAULT, player.getUUID(), ship);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), WmdSounds.DC_THROW.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!player.getAbilities().instabuild) s.shrink(1);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wmd.depth_charge.desc", (int) Balance.DC_DEPTH_DEFAULT).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
