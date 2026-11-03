package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class StationRepairKitItem extends Item {

    public StationRepairKitItem(Item.Properties p) { super(p); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !Orbit.isOrbit(level)) return InteractionResult.PASS;
        int slot = Orbit.slotAt(ctx.getClickedPos().getX(), ctx.getClickedPos().getZ());
        Station.Module m = Station.moduleAt(level.getServer(), slot, ctx.getClickedPos());
        if (m == null) return InteractionResult.PASS;
        int put = Station.repair(level, slot, m);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.station.repaired", Component.translatable("station.wmd.module." + m.type()), put).withStyle(ChatFormatting.GREEN), true);
            if (!ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        level.playSound(null, ctx.getClickedPos(), WmdSounds.OR_CAPSULE_HATCH.get(), SoundSource.BLOCKS, 1.0F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.station_repair_kit").withStyle(ChatFormatting.GRAY));
    }
}
