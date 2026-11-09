package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class NoisemakerItem extends Item {

    public NoisemakerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(s);
        Vec3 at = null;
        UUID ship = SableBridge.active() ? SableBridge.shipUnder(player) : null;
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        if (info != null) {
            Vec3 c = info.worldBounds().getCenter();
            at = new Vec3(c.x, info.worldBounds().minY - 1.5, c.z);
        } else {
            Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(2.5));
            if (sl.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER)) at = p;
        }
        if (at == null || !sl.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER)) {
            player.displayClientMessage(Component.translatable("message.wmd.noisemaker.no_water"), true);
            return InteractionResultHolder.fail(s);
        }
        NoisemakerEntity.drop(sl, at, player.getUUID());
        player.displayClientMessage(Component.translatable("message.wmd.noisemaker.out", Balance.TORP_DECOY_TICKS / 20), true);
        if (!player.getAbilities().instabuild) s.shrink(1);
        player.getCooldowns().addCooldown(this, Balance.TORP_DECOY_TICKS);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wmd.noisemaker.desc", Balance.TORP_DECOY_TICKS / 20).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
