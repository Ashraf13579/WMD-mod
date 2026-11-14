package com.ashraf.wmd;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WingClicks {

    private WingClicks() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        BlockState st = event.getLevel().getBlockState(event.getPos());
        if (!(st.getBlock() instanceof WingSectionBlock) || !WingSectionBlock.takesLayer(st, event.getHitVec(), event.getItemStack())) return;
        if (!event.getLevel().isClientSide) WingSectionBlock.stackLayer(event.getLevel(), event.getPos(), st, event.getEntity(), event.getHitVec(), event.getItemStack());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
