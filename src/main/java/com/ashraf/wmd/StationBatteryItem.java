package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class StationBatteryItem extends Item {

    public StationBatteryItem(Properties properties) { super(properties.stacksTo(8)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp) || !(ctx.getLevel() instanceof ServerLevel sl)) return ctx.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!Orbit.isOrbit(sl)) { sp.displayClientMessage(Component.translatable("message.wmd.station.battery_orbit").withStyle(ChatFormatting.YELLOW), true); return InteractionResult.FAIL; }
        Station.Module m = StationLife.inside(sp);
        if (m == null || !(Station.CORE.equals(m.type()) || Station.HABITAT.equals(m.type()))) {
            sp.displayClientMessage(Component.translatable("message.wmd.station.battery_where").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        int slot = Orbit.slotAt(sp.getX(), sp.getZ());
        if (!sp.getUUID().equals(Orbit.OrbitSlots.get(sl.getServer()).ownerOf(slot)) && !sp.hasPermissions(2)) {
            sp.displayClientMessage(Component.translatable("message.wmd.not_yours").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (Station.wrecked(sl.getServer(), m)) { sp.displayClientMessage(Component.translatable("message.wmd.station.battery_wrecked").withStyle(ChatFormatting.RED), true); return InteractionResult.FAIL; }
        int spot = Station.placeBattery(sl, m, ctx.getClickedPos());
        if (spot < 0) { sp.displayClientMessage(Component.translatable("message.wmd.station.battery_full").withStyle(ChatFormatting.YELLOW), true); return InteractionResult.FAIL; }
        if (!sp.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        sp.displayClientMessage(Component.translatable("message.wmd.station.battery_in", Station.capacity(sl.getServer(), slot) / 1_000_000L).withStyle(ChatFormatting.AQUA), true);
        sl.playSound(null, ctx.getClickedPos(), WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.station_battery", Balance.STATION_BATTERY_FE / 1_000_000L).withStyle(ChatFormatting.GRAY));
    }
}
